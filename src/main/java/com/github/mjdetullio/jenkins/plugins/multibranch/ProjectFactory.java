package com.github.mjdetullio.jenkins.plugins.multibranch;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.TopLevelItem;

public interface ProjectFactory<P extends AbstractProject<P, B> & TopLevelItem, B extends AbstractBuild<P, B>> {

	P createNewSubProject(String branchName);

}
