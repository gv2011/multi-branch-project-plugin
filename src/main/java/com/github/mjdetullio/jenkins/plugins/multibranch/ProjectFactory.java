package com.github.mjdetullio.jenkins.plugins.multibranch;


public interface ProjectFactory<P> {

	P createNewSubProject(String branchName);

}
