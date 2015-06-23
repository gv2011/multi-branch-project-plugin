package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.TaskListener;

import java.util.concurrent.Future;

import jenkins.scm.api.SCMSource;

/**
 * Factores out the synchromnization logic from AbstractMultiBranchProject.
 */
public interface BranchesSynchronizer<P>{
	
Future<Void> synchronizeBranches(final SCMSource scmSource, final P templateProject, final TaskListener listener);
}
