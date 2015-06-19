package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.triggers.SCMTrigger;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSource;

import com.google.common.base.Function;

class BranchesSynchronizer<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>>{
	
	private final ItemGroup<? extends Item> parentProject;
	private final SubProjectRegistry<P,?> subProjectRegistry;
	private final Function<Iterable<? extends SCMHead>, Set<SCMHead>> branchesFilter;
	private final BranchNameMapper branchNameMapper;
	private final ProjectFactory<P> projectFactory;
	private final Jenkins jenkins;
	
	
	BranchesSynchronizer(
			final ItemGroup<? extends Item> parentProject,
			final SubProjectRegistry<P, ?> subProjectRegistry,
			final Function<Iterable<? extends SCMHead>, Set<SCMHead>> branchesFilter, 
			final BranchNameMapper branchNameMapper,
			final ProjectFactory<P> projectFactory, final Jenkins jenkins) {
		super();
		this.parentProject = parentProject;
		this.subProjectRegistry = subProjectRegistry;
		this.branchesFilter = branchesFilter;
		this.branchNameMapper = branchNameMapper;
		this.projectFactory = projectFactory;
		this.jenkins = jenkins;
	}



	/**
	 * Synchronizes the available sub-projects with the available branches and
	 * updates all sub-project configurations with the configuration specified
	 * by this project.
	 */
	synchronized void synchronizeBranches(final SCMSource scmSource, final P templateProject, final TaskListener listener)
			throws IOException, InterruptedException {
		final PrintStream log = listener.getLogger();
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
				final boolean created = subProjectRegistry.createProjectIfItDoesNotExist(projectName, listener, projectFactory);
				if(created) newBranches.add(branch);
			} catch (final Throwable e) {
				e.printStackTrace(listener.fatalError(e.getMessage()));
			}
		}

		// Delete all the sub-projects for branches that no longer exist
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
		// notify the queue as the projects might be now tied to different node
		jenkins.getQueue().scheduleMaintenance();

		// this is to reflect the upstream build adjustments done above
		jenkins.rebuildDependencyGraphAsync();

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

}
