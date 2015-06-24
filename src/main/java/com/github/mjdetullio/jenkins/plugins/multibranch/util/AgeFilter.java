package com.github.mjdetullio.jenkins.plugins.multibranch.util;

import java.util.ArrayList;
import java.util.Date;
import java.util.NavigableMap;
import java.util.TreeMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;

/**
 * Limits a list of elements to a maximum length. The normal limit is given by
 * <code>normalCount</code>. If elements are younger than <code>minAge</code>,
 * they are included beyond the <code>normalCount</code> limit.
 * Under no circumstances more than <code>maxCount</code> elements are included.
 */
public final class AgeFilter<I extends Iterable<? extends B>,B> implements Function<I, ImmutableSet<B>>{
	
	private static final Logger LOG = LoggerFactory.getLogger(AgeFilter.class);
	
	private final Function<B, Date> lastChangeSupplier;

	final @Nullable Integer normalCount;
	final @Nullable Integer maxCount;
	final @Nullable Duration minAge;
	
	

	public AgeFilter(
			final Function<B, Date> lastChangeSupplier, final @Nullable Integer normalCount,
			final @Nullable Integer maxCount, final @Nullable Duration minAge) {
		super();
		this.lastChangeSupplier = lastChangeSupplier;
		this.normalCount = normalCount;
		this.maxCount = maxCount;
		this.minAge = minAge;
	}



	@Override
	public ImmutableSet<B> apply(@Nullable final I branches) {
			//Sort branches by age and ignore branches without a date or older than maxTime
			NavigableMap<Duration, B> byAge = new TreeMap<>();
			final Date now = new Date();
			int count=0;
			for(final B branch: branches){
				count++;
				final Date lastChange = lastChangeSupplier.apply(branch);
				if(lastChange!=null){
					final Duration age = Duration.fromUntil(lastChange, now);
					byAge.put(age, branch);
				}else{
					LOG.warn("Age of {} unknown.");
				}
			}
			
			//Hard limit of maxCount branches:
			if(maxCount!=null){
				final int max = maxCount.intValue();
				if(byAge.size()>max){
					final Duration firstExcluded = new ArrayList<>(byAge.keySet()).get(max);
					byAge = byAge.headMap(firstExcluded, false);
					if(LOG.isDebugEnabled()){
						final int omitted = count-byAge.size();
						LOG.debug("Ommiting oldest {} items because of hard maximum limit of {} items.", omitted , max);
					}
				}
			}
			
			NavigableMap<Duration, B> result = null;
			if(normalCount==null?false:byAge.size()>normalCount.intValue()){
				if(minAge!=null){
					//Include all that are younger than minTime, even if maxCount is exceeded:
					final NavigableMap<Duration, B> youngest = byAge.headMap(minAge, true);
					if(youngest.size()>=normalCount.intValue()) {
						result = youngest;
						LOG.debug("Selected {} items because they all are younger than the minimum age of {}.", 
								result.size() , minAge);
					}
				}
				if(result==null){
					//Include the maxCount youngest entries:
					final Duration firstExcluded = new ArrayList<>(byAge.keySet()).get(normalCount.intValue());
					result = byAge.headMap(firstExcluded, false);
					LOG.debug("Selected the {} youngest items.", result.size());
				}
			}else{
				//Don't filter further
				result = byAge;
			}
			LOG.debug("Selected {} from {} items.", result.size(), count);
			return ImmutableSet.copyOf(result.values());
		}


}
