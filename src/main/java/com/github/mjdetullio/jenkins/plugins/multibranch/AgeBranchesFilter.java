package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.annotation.Nullable;

import jenkins.scm.api.SCMHead;

import com.google.common.base.Function;

public class AgeBranchesFilter implements Function<Iterable<? extends SCMHead>, Set<SCMHead>>{
	
	private final Function<SCMHead, Date> lastChangeSupplier;

	final Integer normalCount;
	final Integer maxCount;
	final Long minTime;
	
	

	public AgeBranchesFilter(
			final Function<SCMHead, Date> lastChangeSupplier, final Integer normalCount,
			final Integer maxCount, final Long minTime) {
		super();
		this.lastChangeSupplier = lastChangeSupplier;
		this.normalCount = normalCount;
		this.maxCount = maxCount;
		this.minTime = minTime;
	}



	@Override
	public Set<SCMHead> apply(@Nullable final Iterable<? extends SCMHead> branches) {
			//Sort branches by age and ignore branches without a date or older than maxTime
			NavigableMap<Long, SCMHead> byAge = new TreeMap<>();
			final long now = System.currentTimeMillis();
			for(final SCMHead branch: branches){
				final Date lastChange = lastChangeSupplier.apply(branch);
				if(lastChange!=null){
					final long age = now-lastChange.getTime();
					byAge.put(age, branch);
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
