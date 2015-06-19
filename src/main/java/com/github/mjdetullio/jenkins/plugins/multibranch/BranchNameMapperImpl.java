package com.github.mjdetullio.jenkins.plugins.multibranch;

import jenkins.scm.api.SCMHead;

final class BranchNameMapperImpl implements BranchNameMapper {

	@Override
	public SCMHead getBranch(final String projectName) {
		if(!projectNameSupported(projectName)) throw new IllegalArgumentException();
		return new SCMHead("feature/"+(projectName.substring("f-".length())));
	}

	@Override
	public String getProjectName(final SCMHead branch) {
		if(!branchNameSupported(branch)) throw new IllegalArgumentException();
		return "f-"+(branch.getName().substring("feature/".length()));
	}

	@Override
	public boolean projectNameSupported(final String projectName) {
		if(projectName==null) return false;
		else return projectName.startsWith("f-");
	}

	@Override
	public boolean branchNameSupported(final SCMHead branch) {		
		if(branch==null) return false;
		else {
			final String branchName = branch.getName();
			if(branchName==null) return false;
			else return branchName.startsWith("feature/");
		}
	}

}
