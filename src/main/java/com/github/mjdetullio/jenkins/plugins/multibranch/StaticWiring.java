package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;

import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.util.Timer;

import com.google.common.base.Function;

final class StaticWiring<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>>{
	
	private final BranchNameMapper mapper;
	private final Function<Iterable<? extends SCMHead>, Set<SCMHead>> branchesFilter;
	private final BranchesSynchronizer<P,R> branchesSynchronizer;
	private final SubProjectRegistry<P,R> subProjectRegistry;
	@SuppressWarnings("unused")
	private final ProjectFactory<P> projectFactory;
	private final Function<SCMHead, Date> branchAgeSupplier;
	@SuppressWarnings("unused")
	private final ItemGroup<? extends Item> parentProject;
	private final Runnable jenkinsUpdate;
	private final SCMSourceCriteria listeningBranchPreseletor;
	private final ExecutorService jenkinsExecutor;
	
	StaticWiring(final ProjectFactory<P> projectFactory, final ItemGroup<? extends Item> parentProject, final Jenkins jenkins) {
		super();
		this.projectFactory = projectFactory;
		this.parentProject = parentProject;
		this.jenkinsUpdate = new JenkinsUpdate(jenkins);
		this.mapper = new BranchNameMapperImpl();
		this.subProjectRegistry = new SubProjectRegistry<P,R>(mapper);
		this.branchAgeSupplier = new Function<SCMHead, Date>(){
			@Override
			public Date apply(@Nullable final SCMHead branch) {
				return getSubProjectRegistry().lastChange(branch);
			}};
		this.branchesFilter = new AgeBranchesFilter(branchAgeSupplier, 12, 50, TimeUnit.HOURS.toMillis(24));
		this.jenkinsExecutor = Timer.get();
		this.branchesSynchronizer = new BranchesSynchronizer<>(
				parentProject, subProjectRegistry, branchesFilter, mapper, projectFactory,  jenkinsUpdate, jenkinsExecutor);
		this.listeningBranchPreseletor = new ListeningBranchPreselector(mapper, TimeUnit.DAYS.toMillis(7), subProjectRegistry);
	}

	final ExecutorService getJenkinsExecutor() {
		return jenkinsExecutor;
	}

	SubProjectRegistry<P, R> getSubProjectRegistry() {
		return subProjectRegistry;
	}

	BranchesSynchronizer<P, R> getSynchronizer() {
		return branchesSynchronizer;
	}

	BranchNameMapper getBranchNameMapper() {
		return  mapper;
	}

	SCMSourceCriteria getListeningBranchPreseletor() {
		return listeningBranchPreseletor;
	}
	
	
}
