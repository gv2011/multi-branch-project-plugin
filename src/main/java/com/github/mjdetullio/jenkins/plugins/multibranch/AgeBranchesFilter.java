package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import jenkins.scm.api.SCMHead;

public class AgeBranchesFilter implements BranchesFilter{
	
	private final BranchAgeSupplier branchAgeSupplier;

	final Integer normalCount;
	final Integer maxCount;
	final Long minTime;
	final Long maxTime;
	
	

	public AgeBranchesFilter(
			final BranchAgeSupplier branchAgeSupplier, final Integer normalCount,
			final Integer maxCount, final Long minTime, final Long maxTime) {
		super();
		this.branchAgeSupplier = branchAgeSupplier;
		this.normalCount = normalCount;
		this.maxCount = maxCount;
		this.minTime = minTime;
		this.maxTime = maxTime;
	}



	@Override
	public Set<SCMHead> filterBranches(final Iterable<? extends SCMHead> branches) {
			//Sort branches by age and ignore branches without a date or older than maxTime
			NavigableMap<Long, SCMHead> byAge = new TreeMap<>();
			final long now = System.currentTimeMillis();
			for(final SCMHead branch: branches){
				final Date lastChange = branchAgeSupplier.lastChange(branch);
				if(lastChange!=null){
					final long age = now-lastChange.getTime();
					if(maxTime==null?true:age<=maxTime.longValue()) byAge.put(age, branch);
				}
			}
			
			//Hard limit of maxCount branches:
			if(maxCount==null?false:byAge.size()>maxCount.intValue()){
				final Long firstExcluded = new ArrayList<>(byAge.keySet()).get(maxCount.intValue());
				byAge = byAge.headMap(firstExcluded, false);
			}
			
			NavigableMap<Long, SCMHead> result = null;
			if(normalCount==null?false:byAge.size()>normalCount.intValue()){
				if(minTime!=null){
					//Include all that are younger than minTime, even if maxCount is exceeded:
					final NavigableMap<Long, SCMHead> youngest = byAge.headMap(minTime.longValue(), true);
					if(youngest.size()>=normalCount.intValue()) result = youngest;
				}
				if(result==null){
					//Include the maxCount youngest entries:
					final Long firstExcluded = new ArrayList<>(byAge.keySet()).get(normalCount.intValue());
					result = byAge.headMap(firstExcluded, false);
				}
			}else{
				//Don't filter further
				result = byAge;
			}
			return Collections.unmodifiableSortedSet(new TreeSet<>(result.values()));
		}

}
