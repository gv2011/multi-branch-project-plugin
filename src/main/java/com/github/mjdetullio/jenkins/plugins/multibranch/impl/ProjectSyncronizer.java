package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;

import java.io.PrintStream;
import java.util.concurrent.Callable;

import jenkins.scm.api.SCMSource;

import com.github.mjdetullio.jenkins.plugins.multibranch.SubProject;

public class ProjectSyncronizer<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> implements Callable<Void>{
	
	private final ItemGroup<? extends Item> parentProject;
	private final SubProject<P> templateProject;
	private final SubProject<P> subProject;
	private final SCMSource scmSource;
	private final TaskListener listener;
	
	
	
	public ProjectSyncronizer(final ItemGroup<? extends Item> parentProject,
			final SubProject<P> templateProject, final SubProject<P> subProject,
			final SCMSource scmSource, final TaskListener listener) {
		super();
		this.parentProject = parentProject;
		this.templateProject = templateProject;
		this.subProject = subProject;
		this.scmSource = scmSource;
		this.listener = listener;
	}



	@Override
	public Void call() throws Exception {
		if(subProject.isTemplate()) throw new UnsupportedOperationException();
		final PrintStream log = listener.getLogger();
		log.println(
				"Syncing configuration to project "+ subProject.name());
		final XmlFile configFile = templateProject.delegate().getConfigFile();
		final P delegate = subProject.delegate();
		configFile.unmarshal(delegate);

		/*
		 * Build new SCM with the URL and branch already set.
		 *
		 * SCM must be set first since getRootDirFor(project) will give
		 * the wrong location during save, load, and elsewhere if SCM
		 * remains null (or NullSCM).
		 */
		final SCM scm = scmSource.build(subProject.branch().toSCMHead());
		if(scm==null|| scm instanceof NullSCM) throw new IllegalStateException(format("No SCM for {}.", subProject));
		delegate.setScm(scm);

		// Work-around for JENKINS-21017
		delegate.setCustomWorkspace(
				templateProject.delegate().getCustomWorkspace());
		
		delegate.makeDisabled(false);

		delegate.onLoad(parentProject, subProject.name());
		return null;
		}
	}


