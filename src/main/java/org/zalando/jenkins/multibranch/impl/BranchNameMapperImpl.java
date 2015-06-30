/*
 * The MIT License
 *
 * Copyright (c) 2015, , Zalando SE
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

import static com.google.common.base.Objects.equal;
import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;
import hudson.Util;

import java.nio.file.Path;

import jenkins.scm.api.SCMHead;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zalando.jenkins.multibranch.BranchId;
import org.zalando.jenkins.multibranch.BranchNameMapper;

final class BranchNameMapperImpl implements BranchNameMapper {

	private static Logger LOG = LoggerFactory.getLogger(BranchNameMapperImpl.class);
	
	private static final String PREFIX = "f-";


	private final Path rootDirectory;
	private final String templateProjectName;
	
	BranchNameMapperImpl(final Path rootDirectory, final String templateProjectName) {
		this.rootDirectory = rootDirectory.toAbsolutePath().normalize();
		this.templateProjectName = templateProjectName;
	}
	
	
	@Override
	public BranchId fromProjectName(final String projectName) {
		if(!projectNameSupported(projectName))
			throw new IllegalArgumentException(format("The project name \"{}\" is not supported.", projectName));
		return new BranchIdImp(new SCMHead("feature/"+(projectName.substring("f-".length()))));
	}

	@Override
	public BranchId fromSCMHead(final SCMHead branch) {
		if(!branchNameSupported(branch)) throw new IllegalArgumentException();
		return new BranchIdImp(branch);
	}

	@Override
	public BranchId fromDirectory(final Path directory) {
		if(!directorySupported(directory)) throw new IllegalArgumentException();
		return new BranchIdImp(getBranch(getProjectName(directory)));
	}

	
	@Override
	public boolean projectNameSupported(final String projectName) {
		if(equal(projectName, templateProjectName)) {
			LOG.debug("Project name {} is not supported because it is the template name.", projectName);
			return false;
		} else if(!projectName.startsWith(PREFIX)){
			LOG.debug("Project name {} is not supported because it does not start with {}.", projectName, PREFIX);
			return false;
		}
		else if(hasSpecialCharacters(projectName)){
			LOG.debug("Project name {} is not supported because it has special characters.", projectName);
			return false;
		}
		else return true;
	}

	@Override
	public boolean branchNameSupported(final SCMHead branch) {		
		if(branch==null) return false;
		else {
			final String branchName = branch.getName();
			if(branchName==null) return false;
			else if(!branchName.startsWith("feature/")) return false;
			else return projectNameSupported(getProjectNameInternal(branch));
		}
	}

	@Override
	public boolean directorySupported(Path directory) {
		directory = directory.toAbsolutePath().normalize();
		if(!directory.getParent().equals(rootDirectory)) {
			LOG.debug("{} is not supported because it is not a child of {}.", directory, rootDirectory);
			return false;
		}
		else return projectNameSupported(getProjectName(directory));
	}
	

	private SCMHead getBranch(final String projectName) {
		if(!projectNameSupported(projectName)) throw new IllegalArgumentException();
		return new SCMHead("feature/"+(projectName.substring("f-".length())));
	}

	private String getProjectNameInternal(final SCMHead branch) {
		return "f-"+(branch.getName().substring("feature/".length()));
	}

	private boolean hasSpecialCharacters(final String projectName) {
		return !Util.rawEncode(projectName).equals(projectName);
	}

	private String getProjectName(final Path directory) {
		return directory.getFileName().toString();
	}

	
	private final class BranchIdImp implements BranchId{
		private final SCMHead scmHead;
		private BranchIdImp(final SCMHead scmHead) {
			this.scmHead = scmHead;
		}
		@Override
		public int compareTo(final BranchId o) {
			return scmHead.compareTo(o.toSCMHead());
		}
		@Override
		public String toProjectName() {
			return getProjectNameInternal(scmHead);
		}
		@Override
		public Path toDirectoryName() {
			return rootDirectory.resolve(toProjectName());
		}
		@Override
		public SCMHead toSCMHead() {
			return scmHead;
		}
		@Override
		public int hashCode() {
			return scmHead.hashCode();
		}
		@Override
		public String toString() {
			return scmHead.getName();
		}
		@Override
		public boolean equals(final Object obj) {
			if (this == obj) return true;
			else if (!(obj instanceof BranchId)) return false;
			else return scmHead.equals(((BranchId)obj).toSCMHead());
		}
	}

}
