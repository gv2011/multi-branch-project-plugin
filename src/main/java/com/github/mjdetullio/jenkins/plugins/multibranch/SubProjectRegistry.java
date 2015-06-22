package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.AbstractItem;
import hudson.model.TaskListener;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import jenkins.scm.api.SCMHead;
import edu.umd.cs.findbugs.annotations.CheckForNull;

final class SubProjectRegistry<P extends AbstractItem,B>
implements BranchAgeListener{
	
	private final Lock lock = new ReentrantLock();
	private final Map<String,SubProject> projectsByName = new HashMap<>();
	
	private final BranchNameMapper mapper;
	
	SubProjectRegistry(final BranchNameMapper mapper) {
		super();
		this.mapper = mapper;
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
	
	List<P> getProjects() {
		lock();
		try{
			final List<P> result = new ArrayList<>(projectsByName.size());
			for(final SubProject p: projectsByName.values()) {
				final P project = p.getProject();
				if(project!=null)result.add(project);
			}
			return Collections.unmodifiableList(result);
		} finally{unlock();}
	}

	@CheckForNull
	P getProject(final String name) {
		final SubProject subProject = getSubProject(name);
		return subProject==null?null:subProject.getProject();
	}
	
	SubProject getOrAddSubProject(final SCMHead branch) {
		lock();
		try{
			final String projectName = mapper.getProjectName(branch);
			SubProject subProject = projectsByName.get(projectName);
			if(subProject==null){
				subProject = new SubProject(branch, null);
				projectsByName.put(projectName, subProject);
			}
			return subProject;
		} finally{unlock();}
	}
	
	@CheckForNull
	private SubProject getSubProject(final String name) {
		lock();
		try{
			return projectsByName.get(name);
		} finally{unlock();}
	}
	
	void addProject(final P project) {	
		if(project==null) throw new IllegalArgumentException();
		final String projectName = project.getName();
		if(projectName==null) throw new IllegalArgumentException();
		final SCMHead branch = mapper.getBranch(projectName);
		lock();
		try{
			final SubProject subProject = getOrAddSubProject(branch);
			subProject.setProject(project);
		} finally{unlock();}
	}

	boolean createProjectIfItDoesNotExist(final String projectName, final TaskListener listener, final ProjectFactory<P> projectFactory){
		final boolean create = getProject(projectName)==null;
		if(create){
			listener.getLogger().println("Creating project " + projectName);
			lock();
			try{
				final P newSubProject = projectFactory.createNewSubProject(projectName);
				addProject(newSubProject);
			} finally{unlock();}
		}
		return create;
	}

	void deleteProject(final String projectName) throws IOException, InterruptedException {
		P project;
		lock();
		try{
			final SubProject subProject = getSubProject(projectName);
			if(subProject!=null){
				project = subProject.getProject();
				if(project!=null) {
					boolean success = false;
					try{
					  delete(project);
					  projectsByName.remove(projectName);
					  success = true;				  
					}finally{
						if(!success) subProject.setBroken();
					}
				}
			}
		} finally{unlock();}
	}
	
	private void delete(final P project) throws IOException, InterruptedException {
		final File projectDir = project.getRootDir().getAbsoluteFile();
		project.delete();
		if(projectDir.exists()) throw new RuntimeException(projectDir+" has not been removed.");
	}

	void onDeleted(final P project){
		lock();
		try{
			projectsByName.remove(project.getName());
		} finally{unlock();}
	}

	@Override
	public void registerLastChange(final SCMHead branch, final Date lastChange) {
		getOrAddSubProject(branch).setLastChange(lastChange);
	}

	
	@CheckForNull
	Date lastChange(final SCMHead branch) {
		if(!mapper.branchNameSupported(branch)) return null;
		else{
			final SubProject subProject = getSubProject(mapper.getProjectName(branch));
			if(subProject==null) return null;
			else return subProject.lastChange();
		}
	}
	
	
private class SubProject {
	
	@SuppressWarnings("unused")
	private final SCMHead branch;
	private final P project;
	private Date lastChange;
	private boolean broken;

	private SubProject(final SCMHead branch, final P project) {
		super();
		this.branch = branch;
		this.project = project;
	}

	private synchronized void setBroken() {
		broken = true;		
	}

	private synchronized Date lastChange() {
		return lastChange;
	}

	@CheckForNull
	private synchronized P getProject(){
		return project;
	}
	
	private synchronized void setProject(final P project){
		if(broken) throw new IllegalStateException("Project "+project+" is broken and cannot be replaced.");
		if(project!=null) throw new IllegalArgumentException("Project "+project+" already exists.");
	}
	
	private synchronized void setLastChange(final Date lastChange) {
		this.lastChange = lastChange;	
	}
}

}
