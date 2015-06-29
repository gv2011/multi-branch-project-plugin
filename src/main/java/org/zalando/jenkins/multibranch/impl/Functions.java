/*
 * The MIT License
 *
 * Copyright (c) 2015, Eberhard Iglhaut (Zalando)
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

import jenkins.scm.api.SCMHead;

import org.zalando.jenkins.multibranch.BranchId;
import org.zalando.jenkins.multibranch.BranchNameMapper;
import org.zalando.jenkins.multibranch.SubProject;

import com.google.common.base.Function;

public final class Functions {

	public static final <P> Function<SubProject<P>,P> delegate(){
		return new Function<SubProject<P>,P>(){
			@Override
			public P apply(final SubProject<P> subProject) {
				return subProject.delegate();
			}
		};
	}
	
	public static final Function<SubProject<?>,BranchId> BRANCH_ID = 
		new Function<SubProject<?>,BranchId>(){
			@Override
			public BranchId apply(final SubProject<?> subProject) {
				final BranchId branch = subProject.branch();
				if(branch==null) throw new IllegalArgumentException();
				return branch;
			}
		};

	public static Function<SCMHead,BranchId> fromSCMHead(final BranchNameMapper branchNameMapper) {
		return new Function<SCMHead,BranchId>(){
			@Override
			public BranchId apply(final SCMHead scmHead) {
				return branchNameMapper.fromSCMHead(scmHead);
			}
		};
	}
	
	
}
