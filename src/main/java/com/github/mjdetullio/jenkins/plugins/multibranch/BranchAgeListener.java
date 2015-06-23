package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.Date;

public interface BranchAgeListener {

	void registerLastChange(BranchId branch, Date lastChange);

}
