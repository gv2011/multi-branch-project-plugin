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

import hudson.model.TaskListener;

import java.io.IOException;
import java.util.Date;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSourceCriteria;

import org.zalando.jenkins.multibranch.BranchAgeListener;
import org.zalando.jenkins.multibranch.BranchId;
import org.zalando.jenkins.multibranch.BranchNameMapper;

@SuppressWarnings("serial")
final class ListeningBranchPreselector implements SCMSourceCriteria{

	private final BranchNameMapper branchNameMapper;
	private final Long maxAge;
	BranchAgeListener branchAgeListener;
	
	
	ListeningBranchPreselector(
			final BranchNameMapper branchNameMapper,
			final Long maxAge,
			final BranchAgeListener branchAgeListener
			) {
		super();
		this.branchNameMapper = branchNameMapper;
		this.maxAge = maxAge;
		this.branchAgeListener = branchAgeListener;
	}



	@Override
	public boolean isHead(final Probe probe, final TaskListener listener)
			throws IOException {
		boolean accepted;
		if(probe==null){
			listener.error("Null probe.");
			accepted = false;
		}else{
			final String name = probe.name();
			if(name==null){
				listener.error("Null branch name.");
				accepted = false;
			}else{
				final SCMHead scmHead = new SCMHead(name);
				if(!branchNameMapper.branchNameSupported(scmHead)){
					listener.error("The branch name "+scmHead+" is not supported.");
					accepted = false;
				}else{
					final BranchId branch = branchNameMapper.fromSCMHead(scmHead);
					final Date lastChange = new Date(probe.lastModified());
					if(maxAge==null) accepted = true;
					else{
						final long age = System.currentTimeMillis()-lastChange.getTime();
						accepted = age <= maxAge.longValue();
						if(!accepted) listener.getLogger().println(
								"Branch "+scmHead+" is too old (last change at "+lastChange+").");
					}
					if(accepted) branchAgeListener.registerLastChange(branch, lastChange);
				}
			}
		}
		return accepted;
	}

}
