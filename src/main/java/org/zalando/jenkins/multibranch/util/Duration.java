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
package org.zalando.jenkins.multibranch.util;

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
