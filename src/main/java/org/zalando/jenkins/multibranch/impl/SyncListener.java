package org.zalando.jenkins.multibranch.impl;

import hudson.model.TaskListener;

import java.io.Closeable;

public interface SyncListener extends Closeable {

	void error(Throwable t, String msg);

	void error(Throwable t, String msgPattern, Object arg0);

	void error(String msg);

	void error(String msgPattern, Object arg0);

	void info(String msg);

	void info(String msgPattern, Object arg0);

	TaskListener asTaskListener();

}
