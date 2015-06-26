package com.github.mjdetullio.jenkins.plugins.multibranch.util;

import hudson.Util;

import java.util.Date;
import java.util.concurrent.TimeUnit;

public final class Duration implements Comparable<Duration>{
	
	private final long millis;

	public final long toMillis() {
		return millis;
	}

	private Duration(final long millis) {
		assert millis>=0;
		this.millis = millis;
	}

	public static Duration ofMllis(final long millis) {
		return new Duration(millis);
	}

	public static Duration of(final long amount, final TimeUnit unit){
		return ofMllis(unit.toMillis(amount));
	}

	public static Duration fromUntil(final Date from, final Date until) {
		return ofMllis(until.getTime()-from.getTime());
	}
	
	public static Duration since(final Date date) {
		return ofMllis(System.currentTimeMillis()-date.getTime());
	}



	@Override
	public int compareTo(final Duration o) {
		return (int)(millis-o.millis);
	}
	@Override
	public String toString() {
		final String timeSpanString = Util.getTimeSpanString(Math.abs(millis));
		if(millis<0) return "-"+timeSpanString;
		else return timeSpanString;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (int) (millis ^ (millis >>> 32));
		return result;
	}

	@Override
	public boolean equals(final Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		else return compareTo((Duration) obj)==0;
	}


}
