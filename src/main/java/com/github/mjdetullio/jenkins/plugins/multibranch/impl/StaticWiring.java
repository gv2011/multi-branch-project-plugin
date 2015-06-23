package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.ItemGroup;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.util.Timer;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchesSynchronizer;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProjectRepository;
import com.google.common.base.Function;

public final class StaticWiring<PA extends ItemGroup<P>, P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>>{
	
	private final BranchNameMapper           mapper;
	private final BranchesSynchronizer<P>    branchesSynchronizer;
	private final SCMSourceCriteria          listeningBranchPreseletor;
	private final SubProjectRegistry<PA,P,R> subProjectRegistry;
	
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
		
		mapper = new BranchNameMapperImpl(rootDirectory);

		subProjectRegistry = new SubProjectRegistry<PA,P,R>(
				projectClass, 
				parentProject,
				subProjectsDirectory, 
				templateDir, 
				templateName,
				mapper, 
				subProjectFactory);
		
		final Runnable jenkinsUpdate = new JenkinsUpdate(Jenkins.getInstance());
		final ExecutorService executor = Timer.get();
		branchesSynchronizer = new BranchesSynchronizerImpl<P,R>(
				parentProject, 
				subProjectRegistry, 
				mapper, 
				jenkinsUpdate, 
				executor);
		
		listeningBranchPreseletor = new ListeningBranchPreselector(
				mapper, 
				maxAge, 
				subProjectRegistry);
	}

	
	public SubProjectRepository<P> getSubProjectRegistry() {
		return subProjectRegistry;
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
