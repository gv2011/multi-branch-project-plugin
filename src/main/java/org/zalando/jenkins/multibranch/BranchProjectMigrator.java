/*
 * The MIT License
 *
 * Copyright (c) 2014-2015, Matthew DeTullio, Eberhard Iglhaut (Zalando)
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
package org.zalando.jenkins.multibranch;

import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;
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
