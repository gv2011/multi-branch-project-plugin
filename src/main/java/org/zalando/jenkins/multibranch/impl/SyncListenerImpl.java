package org.zalando.jenkins.multibranch.impl;

import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;
import hudson.util.StreamTaskListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.jfree.util.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class SyncListenerImpl implements SyncListener{

	private static Logger LOG = LoggerFactory.getLogger(SyncListenerImpl.class);
	
	private final PrintWriter out;
	
	SyncListenerImpl(final Path logFile){
		try {
			out = new PrintWriter(
					Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING));
		} catch (final IOException e) {throw new RuntimeException(
				format("Could not create {} instance.", SyncListenerImpl.class.getName()), e);
		}
	}

	@Override
	public void close() throws IOException {
		out.close();
	}

	@Override
	public void error(Throwable t, final String msg) {
		LOG.error(msg, t);
		out.println(msg);
		t.printStackTrace(out);
		t = t.getCause();
		while(t!=null){
			out.println("caused by:");
			out.println(t.toString());
			t.printStackTrace(out);
			t = t.getCause();
		}		
	}

	@Override
	public void error(final Throwable t, final String msgPattern, final Object arg0) {
		error(t, format(msgPattern, arg0));
	}
	
	

	@Override
	public void error(final String msg) {
		LOG.error(msg);
		out.println(msg);
	}

	@Override
	public void error(final String msgPattern, final Object arg0) {
		error(format(msgPattern, arg0));
	}

	@Override
	public void info(final String msg) {
		LOG.info(msg);
		out.println(msg);
	}

	@Override
	public void info(final String msgPattern, final Object arg0) {
		info(format(msgPattern, arg0));
	}

	@Override
	public StreamTaskListener asTaskListener() {
		try {
			return new StreamTaskListener(new StreamImpl());
		} catch (final IOException e) {throw new RuntimeException(e);}
	}

	private final class StreamImpl extends StringWriter {
		@Override
		public void close() throws IOException {
			Log.info(toString());
		}
	}


}
