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

import static com.google.common.collect.ImmutableSortedSet.copyOf;
import static com.google.common.collect.Lists.transform;
import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.jenkins.multibranch.BranchId;
import org.zalando.jenkins.multibranch.BranchNameMapper;
import org.zalando.jenkins.multibranch.SubProject;
import org.zalando.jenkins.multibranch.SubProjectRepository;
import org.zalando.jenkins.multibranch.util.DiagnosticLock;
import org.zalando.jenkins.multibranch.util.Duration;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;

class SubProjectRegistry<PA extends ItemGroup<P>, P extends AbstractProject<P,R>, R extends AbstractBuild<P,R>>
extends SubProjectFactoryImpl<PA,P,R>
implements SubProjectRepository<P>{
	
	private static final Logger LOG = LoggerFactory.getLogger(SubProjectRepository.class);
	
	private static final ConcurrentMap<Path,Object> USED_PATHS = new ConcurrentHashMap<>();
	
	private static final Duration LOCK_TIMEOUT = Duration.of(60, TimeUnit.SECONDS);

	private final DiagnosticLock lock;
	private final Map<BranchId,SubProject<P>> projects = Maps.newHashMap();
	private final Function<String,P> delegateConstructor;
	private final Map<BranchId,Date> branchChangeDates = new WeakHashMap<>();
	private final RepositoryInitializer initializer;
	
	private SubProject<P> templateProject;

	private boolean initialized;

	public SubProjectRegistry(final Path parentDir, final Class<P> projectClass, final PA parent,
			final Path subProjectsDirectory, final Path templateDir, final String templateName,
			final BranchNameMapper nameMapper, 
			final Function<String,P> delegateConstructor) {
		super(projectClass, parent, subProjectsDirectory, templateDir, templateName,
				nameMapper);
		checkOnlyOneInstancePerDirectory(parentDir);
		this.delegateConstructor = delegateConstructor;
		lock = new DiagnosticLock(parent.getFullName(), LOCK_TIMEOUT);
		initializer = new RepositoryInitializer(nameMapper, subProjectsDirectory);
	}

	private static void checkOnlyOneInstancePerDirectory(final Path parentDir) {
		final Object before = USED_PATHS.putIfAbsent(parentDir, new Object());
		if(before!=null) 
			throw new IllegalStateException(format("There is already a project handling {}", parentDir));
	}

	private void lock(){
		lock.lock();
	}

	private void unlock(){
		lock.unlock();
	}
	
	
	@Override
	public ImmutableSortedSet<SubProject<P>> getProjects() {
		lock();
		try{
			ensureInitialized();
			return ImmutableSortedSet.copyOf(projects.values());
		} finally{unlock();}
	}

	@Override
	public void ensureInitialized() {
		lock.checkLocked();
		if(!initialized){
			boolean success = false;
			try {
				initialized = true; //Set this now to prevent recursion.
				initializer.loadFromDisk(this);
				success = true;
			} catch (final IOException e) {
				throw new IllegalStateException("Initialization failed.");
			} finally{
				if(!success) initialized = false;
			}
		}		
	}

	@Override
	@Nullable
	public SubProject<P> getProject(final BranchId branch) {
		lock();
		try{
			ensureInitialized();
			return projects.get(branch);
		} finally{unlock();}
	}
	
	
	@Override
	public List<P> getDelegates() {
		return transform(ImmutableList.copyOf(getProjects()), Functions.<P>delegate());
	}
	

	@Override
	public ImmutableSortedSet<BranchId> getBranches() {
		return copyOf(transform(ImmutableList.copyOf(getProjects()), Functions.BRANCH_ID));
	}

	@Override
	public SubProject<P> createNewSubProject(final BranchId branch) {
		lock();
		try{
			ensureInitialized();
			if(projects.containsKey(branch)) throw new IllegalArgumentException();
			final SubProject<P> project = super.createNewSubProject(branch);
			projects.put(branch, project);
			LOG.info("Created new project {} in directory {}.", project, project.rootDirectory());
			return project;
		} finally{unlock();}
	}

	@Override
	public SubProject<P> getTemplateProject() {
		lock();
		try{
			ensureInitialized();
			if(templateProject==null) templateProject = super.getTemplateProject();
			return templateProject;
		} finally{unlock();}
	}

	@Override
	protected SubProject<P> loadExistingSubProject(final BranchId branch, final Path subProjectDir)
			throws IOException {
		lock();
		try{
			if(projects.containsKey(branch)) throw new IllegalArgumentException();
			final SubProject<P> project = super.loadExistingSubProject(branch, subProjectDir);
			projects.put(branch, project);
			LOG.info("Loaded existing project {} from directory {}.", project, subProjectDir);
			return project;
		} finally{unlock();}
	}
	
	

	
	@Override
	public SubProject<P> loadExistingSubProject(final Path subProjectDir){
		throw new UnsupportedOperationException(
				format("Loading is handled by the {} itself.", SubProjectRepository.class.getSimpleName()));
	}

	@Override
	public void delete(final BranchId branch) throws IOException,
			InterruptedException {
		lock();
		try{
			ensureInitialized();
			//Remove first to prevent recursive calls via onDeleted():
			final SubProject<P> project = projects.remove(branch);
			if(project!=null){
				boolean success = false;
				try{
					project.delegate().delete();
					success = true;
					LOG.info("Deleted and removed project {} (directory {}).", project, project.rootDirectory());
				}finally{
					if(!success){
						//Add project again if deletion did not work:
						project.setBroken();
						projects.put(branch, project);
					}
				}
			}
		} finally{unlock();}
	}


	@Override
	public void registerLastChange(final BranchId branch, final Date lastChange) {
		lock();
		try{
			ensureInitialized();
			branchChangeDates.put(branch, lastChange);
			final SubProject<P> project = getProject(branch);
			if(project!=null) project.setLastScmChange(lastChange);
		} finally{unlock();}
	}

	public @Nullable Date getLastChange(final BranchId branch) {
		lock();
		try{
			ensureInitialized();
			return branchChangeDates.get(branch);
		} finally{unlock();}
	}

	@Override
	protected P createDelegate(final String name) {
		lock.checkLocked();
		return delegateConstructor.apply(name);
	}

	
}
