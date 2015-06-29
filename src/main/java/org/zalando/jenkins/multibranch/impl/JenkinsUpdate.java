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

import jenkins.model.Jenkins;

final class JenkinsUpdate implements Runnable {

	JenkinsUpdate(final Jenkins jenkins) {
		super();
		this.jenkins = jenkins;
	}

	private final Jenkins jenkins;

	@Override
	public void run() {
		// notify the queue as the projects might be now tied to different node
		jenkins.getQueue().scheduleMaintenance();

		// this is to reflect the upstream build adjustments done above
		jenkins.rebuildDependencyGraphAsync();
	}

}
