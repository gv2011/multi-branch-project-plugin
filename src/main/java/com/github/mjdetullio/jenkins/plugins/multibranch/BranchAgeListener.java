package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.Date;

import jenkins.scm.api.SCMHead;

public interface BranchAgeListener {

	void registerLastChange(SCMHead branch, Date lastChange);

}
