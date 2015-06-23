package com.github.mjdetullio.jenkins.plugins.multibranch.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.NavigableMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

/**
 * Limits a list of elements to a maximum length. The normal limit is given by
 * <code>normalCount</code>. If elements are younger than <code>minAge</code>,
 * they are included beyond the <code>normalCount</code> limit.
 * Under no circumstances more than <code>maxCount</code> elements are included.
 */
final class AgeFilter<B> implements Function<Iterable<? extends B>, ImmutableSet<B>>{
	
	private final Function<B, Date> lastChangeSupplier;

	final Integer normalCount;
	final Integer maxCount;
	final Long minAge;
	
	

	public AgeFilter(
			final Function<B, Date> lastChangeSupplier, final Integer normalCount,
			final Integer maxCount, final Long minTime) {
		super();
		this.lastChangeSupplier = lastChangeSupplier;
		this.normalCount = normalCount;
		this.maxCount = maxCount;
		this.minAge = minTime;
	}



	@Override
	public ImmutableSet<B> apply(@Nullable final Iterable<? extends B> branches) {
			//Sort branches by age and ignore branches without a date or older than maxTime
			NavigableMap<Long, B> byAge = new TreeMap<>();
			final long now = System.currentTimeMillis();
			for(final B branch: branches){
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
			
			NavigableMap<Long, B> result = null;
			if(normalCount==null?false:byAge.size()>normalCount.intValue()){
				if(minAge!=null){
					//Include all that are younger than minTime, even if maxCount is exceeded:
					final NavigableMap<Long, B> youngest = byAge.headMap(minAge.longValue(), true);
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
			return ImmutableSet.copyOf(result.values());
		}


}
