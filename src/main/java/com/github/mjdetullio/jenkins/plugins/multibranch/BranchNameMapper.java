package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.nio.file.Path;

import jenkins.scm.api.SCMHead;

public interface BranchNameMapper {
	
	BranchId fromProjectName(String projectName);

	BranchId fromSCMHead(SCMHead branch);

	BranchId fromDirectory(Path directory);

	boolean projectNameSupported(String projectName);

	boolean branchNameSupported(SCMHead branch);

	boolean directorySupported(Path dir);

}
