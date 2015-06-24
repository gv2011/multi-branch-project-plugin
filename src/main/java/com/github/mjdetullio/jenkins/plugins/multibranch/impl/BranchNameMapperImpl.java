package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;
import static com.google.common.base.Objects.equal;
import hudson.Util;

import java.nio.file.Path;

import jenkins.scm.api.SCMHead;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;

final class BranchNameMapperImpl implements BranchNameMapper {
	
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
		if(projectName==null) return false;
		else if(equal(projectName, templateProjectName)) return false;
		else return projectName.startsWith("f-") && noSpecialCharacters(projectName);
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
		if(!directory.getParent().equals(rootDirectory)) return false;
		else return projectNameSupported(getProjectName(directory));
	}
	

	private SCMHead getBranch(final String projectName) {
		if(!projectNameSupported(projectName)) throw new IllegalArgumentException();
		return new SCMHead("feature/"+(projectName.substring("f-".length())));
	}

	private String getProjectNameInternal(final SCMHead branch) {
		return "f-"+(branch.getName().substring("feature/".length()));
	}

	private boolean noSpecialCharacters(final String projectName) {
		return Util.rawEncode(projectName).equals(projectName);
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
			return scmHead.toString();
		}
		@Override
		public boolean equals(final Object obj) {
			if (this == obj) return true;
			else if (!(obj instanceof BranchId)) return false;
			else return scmHead.equals(((BranchId)obj).toSCMHead());
		}
	}

}
