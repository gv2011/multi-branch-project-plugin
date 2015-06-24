package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import java.nio.file.Path;
import java.util.Date;

import javax.annotation.Nullable;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProject;

class SubProjectImpl<P> implements SubProject<P>{
	private final @Nullable BranchId branch;
	private final boolean template;
	private final String name;
	private final Path rootDir;
	private final P delegate;
	
	private Date lastChange;
	private boolean broken;

	
	SubProjectImpl(final BranchId branch, final Path rootDir, final P delegate) {
		super();
		this.branch = branch;
		this.template = false;
		this.name = branch.toProjectName();
		this.rootDir = rootDir;
		this.delegate = delegate;
	}

	SubProjectImpl(final String name,
			final Path rootDir, final P delegate) {
		super();
		this.branch = null;
		this.template = true;
		this.name = name;
		this.rootDir = rootDir;
		this.delegate = delegate;
	}

	@Override
	public synchronized void setBroken() {
		broken = true;		
	}

	@Override
	public synchronized Date lastScmChange() {
		return lastChange;
	}

	public synchronized void setLastChange(final Date lastChange) {
		this.lastChange = lastChange;	
	}

	@Override
	public boolean isTemplate() {
		return template;
	}

	@Override
	public BranchId branch() {
		return branch;
	}

	@Override
	public P delegate() {
		return delegate;
	}

	@Override
	public synchronized boolean isBroken() {
		return broken;
	}

	@Override
	public Path rootDirectory() {
		return rootDir;
	}

	@Override
	public String name() {
		return name;
	}


	@Override
	public synchronized void setLastScmChange(final Date lastChange) {
		this.lastChange = lastChange;
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj) return true;
		else if (!(obj instanceof SubProject)) return false;
		else return compareTo((SubProject<?>) obj)==0;
	}

	@Override
	public int compareTo(final SubProject<?> o) {
		return o==null?1:name.compareTo(o.name());
	}

	@Override
	public String toString() {
		return name;
	}


}
