package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;
import static com.google.common.collect.ImmutableSortedSet.copyOf;
import static com.google.common.collect.Lists.transform;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.triggers.SCMTrigger;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import jenkins.model.Jenkins;
import jenkins.scm.api.SCMSource;

import org.acegisecurity.Authentication;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchesSynchronizer;
import com.github.mjdetullio.jenkins.plugins.multibranch.LoggingTaskListener;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProject;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProjectRepository;
import com.github.mjdetullio.jenkins.plugins.multibranch.util.Consumer;
import com.github.mjdetullio.jenkins.plugins.multibranch.util.Duration;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;

/**
 * Factores out the synchromnization logic from AbstractMultiBranchProject.
 */
public final class BranchesSynchronizerImpl<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>>
implements BranchesSynchronizer<P>{
	
private static Logger LOG = LoggerFactory.getLogger(BranchesSynchronizerImpl.class);
	
private final ItemGroup<? extends Item> parentProject;
private final SubProjectRepository<P> subProjectRegistry;
private final BranchNameMapper branchNameMapper;
private final Function<ImmutableSortedSet<BranchId>, ImmutableSet<BranchId>> branchFilter;
private final Runnable jenkinsUpdate;
private final ExecutorService executor;
private final AtomicBoolean syncInProgress = new AtomicBoolean();


BranchesSynchronizerImpl(
		final ItemGroup<? extends Item> parentProject,
		final SubProjectRepository<P> subProjectRegistry,
		final BranchNameMapper branchNameMapper,
		final Function<ImmutableSortedSet<BranchId>, ImmutableSet<BranchId>> branchFilter,
		final Runnable jenkinsUpdate,
		final ExecutorService executor) {
	super();
	this.parentProject = parentProject;
	this.subProjectRegistry = subProjectRegistry;
	this.branchNameMapper = branchNameMapper;
	this.branchFilter = branchFilter;
	this.jenkinsUpdate = jenkinsUpdate;
	this.executor = executor;
}


	@Override
	public Future<Void> synchronizeBranches(final SCMSource scmSource,
			final P templateProject, final Path logFile) {
		LOG.debug("Adding synchronizeBranches task.");
		return executor.submit(new Callable<Void>() {
			@Override
			public Void call() throws Exception {
				try {
					if (syncInProgress.compareAndSet(false, true)) {
						try (final LoggingStreamTaskListener listener = new LoggingStreamTaskListener(logFile)) {
							final Date start = logStart(listener);
							try {
								final SecurityContext oldContext = ACL.impersonate(ACL.SYSTEM);
								try {
									doSynchronizeBranches(scmSource,templateProject, listener);
								} catch (final Throwable t) {
									LOG.error("Error during branch synchronization.",t);
									t.printStackTrace(listener.fatalError(t.getMessage()));
								} finally {
									SecurityContextHolder.setContext(oldContext);
								}
							} finally {
								syncInProgress.set(false);
								logFinished(listener, start);
							}
						}
					} else {
						LOG.warn("Skipped synchronization run (still active).");
					}
				} catch (final Throwable t) {
					LOG.error("Branch synchronization failed.", t);
				}
				return null;
			}
		});
	}

private Date logStart(final LoggingTaskListener listener) {
	final Date start = new Date();
	final String msg = format("Started on {}.",start);
	LOG.info(msg);
	listener.getLogger().println(msg);
	return start;
}

private void logFinished(final LoggingTaskListener listener, final Date start) {
	final String msg = format("Done. Took {}.", Duration.since(start));
	LOG.info(msg);
	listener.getLogger().println(msg);
}



/**
 * Synchronizes the available sub-projects with the available branches and
 * updates all sub-project configurations with the configuration specified
 * by the parent project.
 */
private void doSynchronizeBranches(
		final SCMSource scmSource, 
		final P templateProject, 
		final LoggingTaskListener listener)
	throws IOException, InterruptedException {
	final PrintStream log = listener.getLogger();
	final Authentication user = Jenkins.getAuthentication();
	LOG.info("Synchronizing branches as user {}.",user==null?null:user.getName()+".");
	
	// Get all SCM branches when this method starts (snapshot):
	log.println(format("---\nReading branches from {}.",scmSource));
	final ImmutableSortedSet<BranchId> allBranches = fetchBranches(scmSource, listener);
	log.println(format("Finished reading branches.\n---"));

	// Get all current branches (snapshot):	
	final ImmutableSortedSet<BranchId> existingBranches = subProjectRegistry.getBranches();
	
	final ImmutableSortedSet<BranchId> newBranches = copyOf(Sets.difference(allBranches, existingBranches));
	forEach(newBranches, new Consumer<BranchId>(){
		@Override
		public void accept(final BranchId branch) {
			subProjectRegistry.createNewSubProject(branch);			
		}}, listener, "Creating {} new sub-projects:");

	final ImmutableSortedSet<BranchId> branchesToDelete = copyOf(Sets.difference(existingBranches, allBranches));
	forEach(branchesToDelete, new Consumer<BranchId>(){
		@Override
		public void accept(final BranchId branch) throws IOException, InterruptedException {
			subProjectRegistry.delete(branch);			
		}}, listener, "Deleting {} old sub-projects:");
	
	forEach(allBranches, new Consumer<BranchId>(){
		@Override
		public void accept(final BranchId branch) throws Exception {
			getProjectSynchronizer(branch, scmSource, listener).call();			
		}}, listener, "Synchronizing {} sub-projects:");
	
	log.println("Updating Jenkins");
	jenkinsUpdate.run();

	// Trigger build for new branches
	// TODO make this optional
	forEach(newBranches, new Consumer<BranchId>(){
		@Override
		public void accept(final BranchId branch) throws Exception {
			final SubProject<P> project = subProjectRegistry.getProject(branch);
			if(project==null) throw new IllegalStateException(format("No project found for {}.", branch));
			final SCMTrigger.SCMTriggerCause cause = new SCMTrigger.SCMTriggerCause("New branch detected.");
			project.delegate().scheduleBuild(cause);
		}}, listener, "Triggering build for {} sub-projects:");
}


private ImmutableSortedSet<BranchId> fetchBranches(final SCMSource scmSource, final TaskListener listener) throws InterruptedException, IOException {
	final ImmutableSortedSet<BranchId> all = copyOf(transform(ImmutableList.copyOf(scmSource.fetch(listener)), 
			Functions.fromSCMHead(branchNameMapper)));
	final ImmutableSortedSet<BranchId> selected = copyOf(branchFilter.apply(all));
	return selected;
}


protected Callable<Void> getProjectSynchronizer(final BranchId branch, final SCMSource scmSource, final LoggingTaskListener listener) 
		throws IOException {
	final SubProject<P> templateProject = subProjectRegistry.getTemplateProject();
	final SubProject<P> subProject = subProjectRegistry.getProject(branch);
	return new ProjectSyncronizer<P,R>(parentProject, templateProject, subProject, scmSource, listener);
	}


private <T> void forEach(final Collection<? extends T> elements, final Consumer<T> action, 
		final LoggingTaskListener listener, final String message)
		throws InterruptedException {
	final PrintStream log = listener.getLogger();
	log.println(format(message, elements.size()));
	for (final T element : elements) {
		try{
			action.accept(element);
			log.println(format("{}: DONE.",element));
		} catch (final InterruptedException e) {
			listener.error(format("Interrupted while doing {}.",element));
			throw e;
		} catch (final Exception e) {
			listener.fatalError(e, format("{}: FAILED. Exception: ",element));
		}
	}
}



}
