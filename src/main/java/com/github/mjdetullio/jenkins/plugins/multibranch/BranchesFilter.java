package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.Set;

import jenkins.scm.api.SCMHead;

public interface BranchesFilter {

	Set<SCMHead> filterBranches(final Iterable<? extends SCMHead> branches);

}
