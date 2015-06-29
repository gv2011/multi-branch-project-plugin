/*
 * The MIT License
 *
 * Copyright (c) 2015, Zalando SE
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.zalando.jenkins.multibranch.impl;

import java.nio.file.Path;
import java.util.Date;

import javax.annotation.Nullable;

import org.zalando.jenkins.multibranch.BranchId;
import org.zalando.jenkins.multibranch.SubProject;

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
