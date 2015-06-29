package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;

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
				return mapper.directorySupported(subDir);
			}
		};
		for (final Path subDir : Files.newDirectoryStream(subProjectsDirectory, filter)) {
			try {
				repository.loadExistingSubProject(subDir);
			} catch (final Exception e) {
				LOG.error(format("Could not load project from directory {}. This will make it "
						+ "impossible to build a branch with name {}.", subDir, 
						mapper.fromDirectory(subDir)));
			}
		}
	}
}

}
