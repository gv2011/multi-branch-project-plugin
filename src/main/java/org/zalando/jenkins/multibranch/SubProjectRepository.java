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
package org.zalando.jenkins.multibranch;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableSortedSet;

public interface SubProjectRepository<P> extends SubProjectFactory<P>, BranchAgeListener{
	
	ImmutableSortedSet<SubProject<P>> getProjects();
	
	ImmutableSortedSet<BranchId> getBranches();
	
List<P> getDelegates();

SubProject<P> getProject(final BranchId branch) throws ProjectDoesNotExixtException;

@Nullable
SubProject<P> getOptionalProject(final BranchId branch);

void delete(final BranchId project) throws IOException, InterruptedException, ProjectDoesNotExixtException;

public void ensureInitialized();

public static final class ProjectDoesNotExixtException extends Exception{
	private static final long serialVersionUID = -3355036226913409225L;
	public ProjectDoesNotExixtException(final String message) {
		super(message);
	}		
}

}
