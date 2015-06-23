package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.IOException;
import java.nio.file.Path;


public interface SubProjectFactory<P> {

	SubProject<P> createNewSubProject(BranchId branch);

	SubProject<P> getTemplateProject();

	SubProject<P> loadExistingSubProject(Path directory) throws IOException;

}
