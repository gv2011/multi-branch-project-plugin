package com.github.mjdetullio.jenkins.plugins.multibranch;

import jenkins.scm.api.SCMHead;

public interface BranchNameMapper {
	
	SCMHead getBranch(String projectName);

	String getProjectName(SCMHead branch);

	boolean projectNameSupported(String projectName);

	boolean branchNameSupported(SCMHead branch);

}
