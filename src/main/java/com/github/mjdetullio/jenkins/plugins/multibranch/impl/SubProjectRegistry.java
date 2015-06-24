package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import static com.google.common.collect.ImmutableSortedSet.copyOf;
import static com.google.common.collect.Lists.transform;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nullable;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProject;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProjectRepository;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;

class SubProjectRegistry<PA extends ItemGroup<P>, P extends AbstractProject<P,R>, R extends AbstractBuild<P,R>>
extends SubProjectFactoryImpl<PA,P,R>
implements SubProjectRepository<P>{
	
	private final Lock lock = new ReentrantLock();
	private final Map<BranchId,SubProject<P>> projects = Maps.newHashMap();
	private final Function<String,P> delegateConstructor;
	private final Map<BranchId,Date> branchChangeDates = new WeakHashMap<>();
	
	private SubProject<P> templateProject;
	
	public SubProjectRegistry(final Class<P> projectClass, final PA parent,
			final Path subProjectsDirectory, final Path templateDir, final String templateName,
			final BranchNameMapper nameMapper, 
			final Function<String,P> delegateConstructor) {
		super(projectClass, parent, subProjectsDirectory, templateDir, templateName,
				nameMapper);
		this.delegateConstructor = delegateConstructor;
	}

	private void lock(){
		boolean success = false;
		try {
			success = lock.tryLock(10, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {}
		if(!success) throw new IllegalStateException(this+" is locked.");
	}

	private void unlock(){
		lock.unlock();
	}
	
	
	@Override
	public ImmutableSortedSet<SubProject<P>> getProjects() {
		lock();
		try{
			return ImmutableSortedSet.copyOf(projects.values());
		} finally{unlock();}
	}

	@Override
	@Nullable
	public SubProject<P> getProject(final BranchId branch) {
		lock();
		try{
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
			if(projects.containsKey(branch)) throw new IllegalArgumentException();
			final SubProject<P> project = super.createNewSubProject(branch);
			projects.put(branch, project);
			return project;
		} finally{unlock();}
	}

	@Override
	public SubProject<P> getTemplateProject() {
		lock();
		try{
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
			return project;
		} finally{unlock();}
	}
	
	

	
	@Override
	public void delete(final BranchId branch) throws IOException,
			InterruptedException {
		lock();
		try{
			//Remove first to prevent recursive calls via onDeleted():
			final SubProject<P> project = projects.remove(branch);
			if(project!=null){
				boolean success = false;
				try{
					project.delegate().delete();
					success = true;
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
			branchChangeDates.put(branch, lastChange);
			final SubProject<P> project = getProject(branch);
			if(project!=null) project.setLastScmChange(lastChange);
		} finally{unlock();}
	}

	public @Nullable Date getLastChange(final BranchId branch) {
		lock();
		try{
			return branchChangeDates.get(branch);
		} finally{unlock();}
	}

	@Override
	protected P createDelegate(final String name) {
		return delegateConstructor.apply(name);
	}

	
}
