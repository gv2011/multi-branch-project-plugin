package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.TaskListener;

import java.io.IOException;
import java.util.Date;

import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMSourceCriteria;

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
				final SCMHead branch = new SCMHead(name);
				if(!branchNameMapper.branchNameSupported(branch)){
					listener.error("The branch name "+branch+" is not supported.");
					accepted = false;
				}else{
					final Date lastChange = new Date(probe.lastModified());
					if(maxAge==null) accepted = true;
					else{
						final long age = System.currentTimeMillis()-lastChange.getTime();
						accepted = age <= maxAge.longValue();
						if(!accepted) listener.getLogger().println(
								"Branch "+branch+" is too old (last change at "+lastChange+").");
					}
					if(accepted) branchAgeListener.registerLastChange(branch, lastChange);
				}
			}
		}
		return accepted;
	}

}
