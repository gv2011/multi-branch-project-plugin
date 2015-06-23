package com.github.mjdetullio.jenkins.plugins.multibranch;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;
import hudson.Util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jenkins.model.Jenkins;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;

class BranchProjectMigrator implements Runnable{
	
	private static final Logger LOG = LoggerFactory.getLogger(BranchProjectMigrator.class);

	private final String fullName;
	private final File templateDir;
	private final File branchesDir;
	private final boolean isDisabled;
	
	

	BranchProjectMigrator(final String fullName, final File templateDir,
			final File branchesDir, final boolean isDisabled) {
		super();
		this.fullName = fullName;
		this.templateDir = templateDir;
		this.branchesDir = branchesDir;
		this.isDisabled = isDisabled;
	}



	@Override
	public void run() {
//		if (disabledSubProjects == null) {
//		disabledSubProjects = new PersistedList<String>(this);
//	}

	final List<File> subProjects = new ArrayList<File>();
	subProjects.add(templateDir);

	final File[] files = branchesDir.listFiles();
	if (files != null) {
		subProjects.addAll(Arrays.asList(files));
	}

	for (final File subProjectDir : subProjects) {
		final File configFile = new File(subProjectDir, "config.xml");

		if (!subProjectDir.isDirectory() || !configFile.exists()
				|| !configFile.isFile()) {
			continue;
		}

		try {
			String xml = FileUtils.readFileToString(configFile);

			xml = xml.replaceFirst(
					"(?m)^<(freestyle-branch-project|com\\.github\\.mjdetullio\\.jenkins\\.plugins\\.multibranch\\.FreeStyleBranchProject)( plugin=\".*?\")?>$",
					"<project>");
			xml = xml.replaceFirst(
					"(?m)^</(freestyle-branch-project|com\\.github\\.mjdetullio\\.jenkins\\.plugins\\.multibranch\\.FreeStyleBranchProject)>$",
					"</project>");
			xml = xml.replaceFirst(
					"(?m)^  <template>(true|false)</template>(\r?\n)", "");

			/*
			 * Previously, sub-projects would reference the parent to see if
			 * they were disabled.  Now the parent must track each
			 * sub-project to see if it should stay disabled when the parent
			 * is re-enabled.
			 *
			 * If disabled, this needs to be propagated down to the
			 * sub-projects.
			 */
			if (isDisabled) {
				if (!subProjectDir.equals(templateDir) && xml.matches(
						"(?ms).+(\r?\n)  <disabled>true</disabled>(\r?\n).+")) {
//					disabledSubProjects.add(
//							rawDecode(subProjectDir.getName()));
				}

				xml = xml.replaceFirst(
						"(?m)^  <disabled>false</disabled>$",
						"  <disabled>true</disabled>");
			}

			FileUtils.writeStringToFile(configFile, xml);
		} catch (final IOException e) {
			LOG.warn("Unable to migrate " + configFile, e);
		}

		final String branchFullName =
				fullName + '/' + subProjectDir.getName();

		// Replacement mirrors jenkins.model.Jenkins#expandVariablesForDirectory
		final File[] builds = new File(Util.replaceMacro(
				Jenkins.getInstance().getRawBuildsDir(),
				ImmutableMap.of(
						"JENKINS_HOME",
						Jenkins.getInstance().getRootDir().getPath(),
						"ITEM_ROOTDIR", subProjectDir.getPath(),
						"ITEM_FULLNAME", branchFullName,
						"ITEM_FULL_NAME", branchFullName.replace(':', '$')
				))).listFiles();

		if (builds == null) {
			continue;
		}

		for (final File buildDir : builds) {
			final File buildFile = new File(buildDir, "build.xml");

			if (!buildDir.isDirectory() || !buildFile.exists()
					|| !buildFile.isFile()) {
				continue;
			}

			try {
				String xml = FileUtils.readFileToString(buildFile);

				xml = xml.replaceFirst(
						"(?m)^<(freestyle-branch-build|com\\.github\\.mjdetullio\\.jenkins\\.plugins\\.multibranch\\.FreeStyleBranchBuild)( plugin=\".*?\")?>$",
						"<build>");
				xml = xml.replaceFirst(
						"(?m)^</(freestyle-branch-build|com\\.github\\.mjdetullio\\.jenkins\\.plugins\\.multibranch\\.FreeStyleBranchBuild)>$",
						"</build>");
				xml = xml.replaceAll(
						" class=\"(freestyle-branch-build|com\\.github\\.mjdetullio\\.jenkins\\.plugins\\.multibranch\\.FreeStyleBranchBuild)\"",
						" class=\"build\"");

				FileUtils.writeStringToFile(buildFile, xml);
			} catch (final IOException e) {
				LOG.warn(format("Unable to migrate ", buildFile), e);
			}
		}
	}
}

}
