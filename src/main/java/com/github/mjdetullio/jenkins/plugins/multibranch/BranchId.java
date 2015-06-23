package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.nio.file.Path;

import jenkins.scm.api.SCMHead;

public interface BranchId extends Comparable<BranchId>{
	
	String toProjectName();
	Path toDirectoryName();
	SCMHead toSCMHead();

}
