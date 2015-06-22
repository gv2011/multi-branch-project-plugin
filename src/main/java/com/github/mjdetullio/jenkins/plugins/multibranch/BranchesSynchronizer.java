package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;

import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;

import com.google.common.base.Function;

final class BranchesSynchronizer<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>>{
	
private final ItemGroup<? extends Item> parentProject;
private final SubProjectRegistry<P,?> subProjectRegistry;
private final Function<Iterable<? extends SCMHead>, Set<SCMHead>> branchesFilter;
private final BranchNameMapper branchNameMapper;
private final ProjectFactory<P> projectFactory;
private final Runnable jenkinsUpdate;
private final ExecutorService executor;
private final AtomicBoolean syncInProgress = new AtomicBoolean();

BranchesSynchronizer(
		final ItemGroup<? extends Item> parentProject,
		final SubProjectRegistry<P, ?> subProjectRegistry,
		final Function<Iterable<? extends SCMHead>, Set<SCMHead>> branchesFilter, 
		final BranchNameMapper branchNameMapper,
		final ProjectFactory<P> projectFactory, 
		final Runnable jenkinsUpdate,
		final ExecutorService executor) {
	super();
	this.parentProject = parentProject;
	this.subProjectRegistry = subProjectRegistry;
	this.branchesFilter = branchesFilter;
	this.branchNameMapper = branchNameMapper;
	this.projectFactory = projectFactory;
	this.jenkinsUpdate = jenkinsUpdate;
	this.executor = executor;
}


Future<Void> synchronizeBranches(final SCMSource scmSource, final P templateProject, final TaskListener listener){
	return executor.submit(new Callable<Void>(){
		@Override
		public Void call() throws Exception {
			if(syncInProgress.compareAndSet(false, true)){
				try{
			        final SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
			        try {
			        	doSynchronizeBranches(scmSource, templateProject, listener);
			        } finally {
			            SecurityContextHolder.setContext(oldContext);
			        }
				}
				finally{syncInProgress.set(false);}
			}else{
				listener.getLogger().println("Skipping this synchronization run because there is still one active.");
			}
			return null;
		}});
}


/**
 * Synchronizes the available sub-projects with the available branches and
 * updates all sub-project configurations with the configuration specified
 * by this project.
 */
private void doSynchronizeBranches(final SCMSource scmSource, final P templateProject, final TaskListener listener)
	throws IOException, InterruptedException {
	final PrintStream log = listener.getLogger();
	log.println("Synchronizing branches as user "+Jenkins.getAuthentication()+".");
	// Get all branches from SCM:
	final Set<SCMHead> allBranches;
	if(scmSource==null){
		allBranches = Collections.emptySet();
		log.println("No SCM available, therefore there are no branches.");
	}else{
		allBranches = scmSource.fetch(listener);
	}
	
	//Apply filter:
	final Set<SCMHead> branches = branchesFilter.apply(allBranches);
	log.println("Selected "+branches.size()+" from "+allBranches.size()+" branches.");
	

	final Set<SCMHead> newBranches = new HashSet<SCMHead>();
	final Map<String,SCMHead> branchesByProjectName = new HashMap<String,SCMHead>();
	for (final SCMHead branch : branches) {
		final String projectName = branchNameMapper.getProjectName(branch);
		branchesByProjectName.put(projectName, branch);
		try {
			final boolean created = subProjectRegistry.
					createProjectIfItDoesNotExist(projectName, listener, projectFactory);
			if(created) newBranches.add(branch);
		} catch (final Throwable e) {
			e.printStackTrace(listener.fatalError(e.getMessage()));
		}
	}

	deleteProjects(listener, branchesByProjectName);

	// Sync config for existing branch projects
	final XmlFile configFile = templateProject.getConfigFile();
	for (final P project : subProjectRegistry.getProjects()) {
		log.println(
				"Syncing configuration to project "+ project.getName());
		try {
			final boolean wasDisabled = project.isDisabled();

			configFile.unmarshal(project);

			/*
			 * Build new SCM with the URL and branch already set.
			 *
			 * SCM must be set first since getRootDirFor(project) will give
			 * the wrong location during save, load, and elsewhere if SCM
			 * remains null (or NullSCM).
			 */
			final SCMHead branch = branchesByProjectName.get(project.getName());
			if(scmSource==null) throw new IllegalStateException("No scmSource.");
			project.setScm(scmSource.build(branch));

			if (!wasDisabled) {
				project.enable();
			}

			// Work-around for JENKINS-21017
			project.setCustomWorkspace(
					templateProject.getCustomWorkspace());

			project.onLoad(parentProject, project.getName());
		} catch (final Throwable e) {
			e.printStackTrace(listener.fatalError(e.getMessage()));
		}
	}
	
	jenkinsUpdate.run();

	// Trigger build for new branches
	// TODO make this optional
	for (final SCMHead branch : newBranches) {
		listener.getLogger().println(
				"Scheduling build for branch " + branch);
		try {
			final String projectName = branchNameMapper.getProjectName(branch);
			final P project = subProjectRegistry.getProject(projectName);
			if(project!=null) project.scheduleBuild(
					new SCMTrigger.SCMTriggerCause("New branch detected."));
		} catch (final Throwable e) {
			e.printStackTrace(listener.fatalError(e.getMessage()));
		}
	}
}


/**
 * Delete all the sub-projects for branches that no longer exist
 */
private void deleteProjects(final TaskListener listener, final Map<String, SCMHead> branchesByProjectName) {
	final PrintStream log = listener.getLogger();
	final Iterator<P> subProjects = subProjectRegistry.getProjects().iterator();
	while (subProjects.hasNext()) {
		final P project = subProjects.next();
		final String projectName = project.getName();
		if (!branchesByProjectName.containsKey(projectName)) {
			log.println("Deleting project " + projectName);
			try {
				subProjectRegistry.deleteProject(projectName);
			} catch (final Throwable e) {
				e.printStackTrace(listener.fatalError(e.getMessage()));
			}
		}
	}
}

}
