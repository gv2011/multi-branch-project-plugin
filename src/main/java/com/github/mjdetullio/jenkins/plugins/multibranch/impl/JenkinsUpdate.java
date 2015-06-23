package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

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
