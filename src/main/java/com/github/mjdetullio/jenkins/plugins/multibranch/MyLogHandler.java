package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class MyLogHandler extends Handler {
	
	private final Path file = FileSystems.getDefault().getPath("/home/jenkins/work/log/multibranch.log");
	private final Object lock = new Object();

	@Override
	public void publish(final LogRecord record) {
		final StringBuilder sb = new StringBuilder();
		sb.append(record.getMessage());
		final Throwable thrown = record.getThrown();
		if(thrown!=null){
			final StringWriter out = new StringWriter();
			thrown.printStackTrace(new PrintWriter(out));
			sb.append(out);
		}
		sb.append('\n');
		final byte[] line = sb.toString().getBytes(StandardCharsets.UTF_8);
		try {
			synchronized(lock){
				Files.write(file, line, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			}
		} catch (final IOException e) {
			e.printStackTrace(System.err);
		}
	}
	
	public void writeLn(final String msg){
		System.out.println(msg);
		final byte[] bytes = (msg+'\n').getBytes(StandardCharsets.UTF_8);
		try {
			synchronized(lock){
				Files.write(file, bytes, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
			}
		} catch (final IOException e) {
			e.printStackTrace(System.err);
		}
		
	}

	@Override
	public void flush() {
		// TODO Auto-generated method stub

	}

	@Override
	public void close() throws SecurityException {
		// TODO Auto-generated method stub

	}

}
