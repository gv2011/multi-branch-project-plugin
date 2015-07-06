package org.zalando.jenkins.multibranch.util;

import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;
import hudson.Util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FileUtils {
	
	private static final Logger LOG = LoggerFactory.getLogger(FileUtils.class);
	
	public static final boolean tryDelete(final Path fileOrDirectory){
		try {
			Util.deleteRecursive(fileOrDirectory.toFile());
			return !Files.exists(fileOrDirectory);
		} catch (final IOException e) {
			LOG.debug(format("Could not delete {}.", fileOrDirectory), e);
			return false;
		}
		
	}

}
