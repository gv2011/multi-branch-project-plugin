package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import jenkins.scm.api.SCMHead;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProject;
import com.google.common.base.Function;

public final class Functions {

	public static final <P> Function<SubProject<P>,P> delegate(){
		return new Function<SubProject<P>,P>(){
			@Override
			public P apply(final SubProject<P> subProject) {
				return subProject.delegate();
			}
		};
	}
	
	public static final Function<SubProject<?>,BranchId> BRANCH_ID = 
		new Function<SubProject<?>,BranchId>(){
			@Override
			public BranchId apply(final SubProject<?> subProject) {
				final BranchId branch = subProject.branch();
				if(branch==null) throw new IllegalArgumentException();
				return branch;
			}
		};

	public static Function<SCMHead,BranchId> fromSCMHead(final BranchNameMapper branchNameMapper) {
		return new Function<SCMHead,BranchId>(){
			@Override
			public BranchId apply(final SCMHead scmHead) {
				return branchNameMapper.fromSCMHead(scmHead);
			}
		};
	}
	
	
}
