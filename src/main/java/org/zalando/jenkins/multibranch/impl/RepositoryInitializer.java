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
package org.zalando.jenkins.multibranch.impl;

import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.jenkins.multibranch.BranchNameMapper;

final class RepositoryInitializer {
	
private static final Logger LOG = LoggerFactory.getLogger(RepositoryInitializer.class);

private final BranchNameMapper mapper;

private final Path subProjectsDirectory;


	
RepositoryInitializer(final BranchNameMapper mapper, final Path subProjectsDirectory) {
	this.mapper = mapper;
	this.subProjectsDirectory = subProjectsDirectory;
}



void loadFromDisk(final SubProjectRegistry<?,?,?> repository) throws IOException{
	repository.getTemplateProject();
	if (Files.exists(subProjectsDirectory)) {
		if (!Files.isDirectory(subProjectsDirectory))
			throw new IllegalStateException(format("{} is not a directory.", subProjectsDirectory));
		final Filter<Path> filter = new Filter<Path>() {
			@Override
			public boolean accept(final Path subDir) throws IOException {
				final boolean accepted = mapper.directorySupported(subDir);
				if(!accepted) LOG.debug("Ignoring directory {}.", subDir);
				return accepted;
			}
		};
		for (final Path subDir : Files.newDirectoryStream(subProjectsDirectory, filter)) {
			try {
				repository.loadExistingSubProjectInternal(subDir);
			} catch (final Exception e) {
				LOG.error(format("Could not load project from directory {}. This will make it "
						+ "impossible to build a branch with name {}.", subDir, 
						mapper.fromDirectory(subDir)));
			}
		}
	}
}

}
