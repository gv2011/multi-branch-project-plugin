/*
 * The MIT License
 *
 * Copyright (c) 2015, Zalando SE
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.zalando.jenkins.multibranch.impl;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;

import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.util.Timer;

import org.zalando.jenkins.multibranch.BranchId;
import org.zalando.jenkins.multibranch.BranchNameMapper;
import org.zalando.jenkins.multibranch.BranchesSynchronizer;
import org.zalando.jenkins.multibranch.SubProjectRepository;
import org.zalando.jenkins.multibranch.util.AgeFilter;
import org.zalando.jenkins.multibranch.util.Duration;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;

public final class StaticWiring<PA extends ItemGroup<P>, P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>>{
	
	private static final Integer maxCount = 50;
	private static final Duration minAge = Duration.of(24, TimeUnit.HOURS);
	private final BranchNameMapper           mapper;
	private final BranchesSynchronizer<P>    branchesSynchronizer;
	private final SCMSourceCriteria          listeningBranchPreseletor;
	private final SubProjectRepository<P>    subProjectRepository;
	private Integer normalCount;
	
	public StaticWiring(
			final Class<P>           projectClass,
			final PA                 parentProject, 
			
			final Path               rootDirectory,
			final Path               subProjectsDirectory,
		    final Path               templateDir,
		    final String             templateName,
		    
		    final Function<String,P> subProjectFactory,
		    final Long               maxAge
		    ) {
		
		mapper = new BranchNameMapperImpl(subProjectsDirectory, templateName);

		final SubProjectRegistry<PA, P, R> subProjectRegistry = new SubProjectRegistry<PA,P,R>(
				rootDirectory,
				projectClass, 
				parentProject,
				subProjectsDirectory, 
				templateDir, 
				templateName,
				mapper, 
				subProjectFactory);
		
		subProjectRepository = subProjectRegistry;
		
		
		final Function<BranchId, Date> lastChangeSupplier = new Function<BranchId, Date>(){
			@Override
			public Date apply(final BranchId branch) {
				return subProjectRegistry.getLastChange(branch);
			}};
			
		final Function<ImmutableSortedSet<BranchId>, ImmutableSet<BranchId>> branchFilter = 
				new AgeFilter<>(lastChangeSupplier, normalCount, maxCount, minAge);
		
		final Runnable jenkinsUpdate = new JenkinsUpdate(Jenkins.getInstance());
		
		final ExecutorService executor = Timer.get();
		branchesSynchronizer = new BranchesSynchronizerImpl<P,R>(
				parentProject, 
				subProjectRegistry, 
				mapper, 
				branchFilter, 
				jenkinsUpdate, 
				executor);
		
		listeningBranchPreseletor = new ListeningBranchPreselector(
				mapper, 
				maxAge, 
				subProjectRegistry);
	}

	
	public SubProjectRepository<P> getSubProjectRepository() {
		return subProjectRepository;
	}

	public BranchesSynchronizer<P> getSynchronizer() {
		return branchesSynchronizer;
	}

	public BranchNameMapper getBranchNameMapper() {
		return  mapper;
	}

	public SCMSourceCriteria getListeningBranchPreseletor() {
		return listeningBranchPreseletor;
	}
	
}
