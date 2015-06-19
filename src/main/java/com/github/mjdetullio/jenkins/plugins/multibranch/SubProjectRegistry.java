package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import jenkins.scm.api.SCMHead;
import edu.umd.cs.findbugs.annotations.CheckForNull;

class SubProjectRegistry<P extends AbstractProject<P, B> & TopLevelItem, B extends AbstractBuild<P, B>>
implements BranchAgeSupplier{
	
	private final Object lock = new Object();
	private final Map<String,SubProject<P,B>> projectsByName = new HashMap<>();
	private final BranchNameMapper mapper = new BranchNameMapperImpl();
	private final BranchesFilter branchesFilter = new AgeBranchesFilter(this, 12, 50, TimeUnit.HOURS.toMillis(24), TimeUnit.DAYS.toMillis(7));
	

	final BranchNameMapper getBranchNameMapper() {
		return mapper;
	}

	List<P> getProjects() {
		synchronized(lock){
			final List<P> result = new ArrayList<>(projectsByName.size());
			for(final SubProject<P,B> p: projectsByName.values()) {
				final P project = p.getProject();
				if(project!=null)result.add(project);
			}
			return Collections.unmodifiableList(result);
		}
	}

	@CheckForNull
	P getProject(final String name) {
		final SubProject<P, B> subProject = getSubProject(name);
		return subProject==null?null:subProject.getProject();
	}
	
	SubProject<P,B> getOrAddSubProject(final SCMHead branch) {
		synchronized(lock){
			final String projectName = mapper.getProjectName(branch);
			SubProject<P, B> subProject = projectsByName.get(projectName);
			if(subProject==null){
				subProject = new SubProject<>(branch, null);
				projectsByName.put(projectName, subProject);
			}
			return subProject;
		}
	}
	
	@CheckForNull
	private SubProject<P,B> getSubProject(final String name) {
		synchronized(lock){
			return projectsByName.get(name);
		}
	}
	
	void addProject(final P project) {	
		if(project==null) throw new IllegalArgumentException();
		final String projectName = project.getName();
		if(projectName==null) throw new IllegalArgumentException();
		final SCMHead branch = mapper.getBranch(projectName);
		synchronized(lock){
			final SubProject<P,B> subProject = getOrAddSubProject(branch);
			subProject.setProject(project);
		}
	}

	boolean createProjectIfItDoesNotExist(final String projectName, final TaskListener listener, final ProjectFactory<P,B> projectFactory){
		boolean create;
		synchronized(lock){
			create = !projectsByName.containsKey(projectName);
			}
		if(create){
			//Race condition tolerated: If meanwhile a project with the same name was created by a
			//different thread, addSubProject will fail.
			listener.getLogger().println("Creating project " + projectName);
			final P newSubProject = projectFactory.createNewSubProject(projectName);
			addProject(newSubProject);
		}
		return create;
	}

	void deleteProject(final String projectName) throws IOException, InterruptedException {
		P project;
		synchronized(lock){
			project = getProject(projectName);
			if(project!=null) projectsByName.remove(projectName);
		}
		if(project!=null) project.delete();
	}
	
	void onDeleted(final P project){
		synchronized(lock){
			projectsByName.remove(project.getName());
		}
	}

	void registerLastChange(final SCMHead branch, final Date lastChange) {
		getOrAddSubProject(branch).setLastChange(lastChange);
	}
	
	
	
	@Override
	@CheckForNull
	public Date lastChange(final SCMHead branch) {
		if(!mapper.branchNameSupported(branch)) return null;
		else{
			final SubProject<P, B> subProject = getSubProject(mapper.getProjectName(branch));
			if(subProject==null) return null;
			else return subProject.lastChange();
		}
	}
	
	BranchesFilter getBranchesFilter(){
		return branchesFilter;
	}

	
private static class SubProject<P extends AbstractProject<P, B> & TopLevelItem, B extends AbstractBuild<P, B>> {
	
	@SuppressWarnings("unused")
	private final SCMHead branch;
	private final AtomicReference<P> project = new AtomicReference<>();
	private final AtomicReference<Date> lastChange = new AtomicReference<>();

	private SubProject(final SCMHead branch, final P project) {
		super();
		this.branch = branch;
		this.project.set(project);
	}

	private Date lastChange() {
		return lastChange.get();
	}

	@CheckForNull
	private P getProject(){
		return project.get();
	}
	
	private void setProject(final P project){
		final boolean success = this.project.compareAndSet(null, project);
		if(!success) throw new IllegalArgumentException("Project "+project.getName()+" already exists.");
	}
	
	private void setLastChange(final Date lastChange) {
		this.lastChange.set(lastChange);		
	}
}

}
