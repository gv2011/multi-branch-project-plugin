package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.Date;

import jenkins.scm.api.SCMHead;
import edu.umd.cs.findbugs.annotations.CheckForNull;

public interface BranchAgeSupplier {
	
	@CheckForNull
	Date lastChange(final SCMHead branch);

}
