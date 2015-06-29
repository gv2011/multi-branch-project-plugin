package com.github.mjdetullio.jenkins.plugins.multibranch;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSortedSet;

public interface SubProjectRepository<P> extends SubProjectFactory<P>, BranchAgeListener{
	
	ImmutableSortedSet<SubProject<P>> getProjects();
	
	ImmutableSortedSet<BranchId> getBranches();
	
List<P> getDelegates();

@Nullable
SubProject<P> getProject(final BranchId branch);

void delete(final BranchId project) throws IOException, InterruptedException;

public void ensureInitialized();


}
