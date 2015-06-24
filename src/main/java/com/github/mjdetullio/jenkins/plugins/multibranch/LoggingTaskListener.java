package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.TaskListener;

public interface LoggingTaskListener extends TaskListener{

	void fatalError(final Throwable t, final String message);

}
