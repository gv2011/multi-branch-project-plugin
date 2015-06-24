package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mjdetullio.jenkins.plugins.multibranch.LoggingTaskListener;

public class LoggingStreamTaskListener extends StreamTaskListener implements LoggingTaskListener{

	private static final long serialVersionUID = 2040568644856629487L;

	private static Logger LOG = LoggerFactory.getLogger(LoggingStreamTaskListener.class);
	
	public LoggingStreamTaskListener(final Path out)
			throws IOException {
		super(out.toFile(), StandardCharsets.UTF_8);
	}

	@Override
	public PrintWriter error(final String msg) {
		LOG.warn(msg);
		return super.error(msg);
	}

	@Override
	public PrintWriter fatalError(final String msg) {
		LOG.error(msg);
		return super.fatalError(msg);
	}

	@Override
	public void fatalError(final Throwable t, final String message) {
		LOG.error(message, t);
		t.printStackTrace(super.error(message));
	}

	
	
}
