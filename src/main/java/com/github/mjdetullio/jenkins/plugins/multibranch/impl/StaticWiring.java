package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

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

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchesSynchronizer;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProjectRepository;
import com.github.mjdetullio.jenkins.plugins.multibranch.util.AgeFilter;
import com.github.mjdetullio.jenkins.plugins.multibranch.util.Duration;
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
		
		mapper = new BranchNameMapperImpl(rootDirectory, templateName);

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
