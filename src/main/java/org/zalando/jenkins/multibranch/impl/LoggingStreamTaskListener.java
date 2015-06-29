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

import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.jenkins.multibranch.LoggingTaskListener;

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
