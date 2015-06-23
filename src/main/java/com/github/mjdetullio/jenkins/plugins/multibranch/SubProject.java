package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.nio.file.Path;
import java.util.Date;

import javax.annotation.Nullable;

public interface SubProject<P> {
	
	boolean isTemplate();
	
	@Nullable BranchId branch();
	
	String name();
	
	@Nullable
	P delegate();
	
	boolean isBroken();
	
	Path rootDirectory();
	
	@Nullable
	Date lastScmChange();
	
	void setBroken();

	void setLastScmChange(Date lastChange);
}
