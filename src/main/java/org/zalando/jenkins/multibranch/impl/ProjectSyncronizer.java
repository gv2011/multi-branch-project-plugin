/*
 * The MIT License
 *
 * Copyright (c) 2014-2015, Matthew DeTullio, Stephen Connolly, Zalando SE
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

import static org.zalando.jenkins.multibranch.util.FormattingUtils.format;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.scm.NullSCM;
import hudson.scm.SCM;

import java.util.concurrent.Callable;

import jenkins.scm.api.SCMSource;

import org.zalando.jenkins.multibranch.SubProject;

public class ProjectSyncronizer<P extends AbstractProject<P,R>,R extends AbstractBuild<P,R>> implements Callable<Void>{
	
	private final ItemGroup<? extends Item> parentProject;
	private final SubProject<P> templateProject;
	private final SubProject<P> subProject;
	private final SCMSource scmSource;
	private final SyncListener listener;
	
	
	
	public ProjectSyncronizer(final ItemGroup<? extends Item> parentProject,
			final SubProject<P> templateProject, final SubProject<P> subProject,
			final SCMSource scmSource, final SyncListener listener) {
		super();
		this.parentProject = parentProject;
		this.templateProject = templateProject;
		this.subProject = subProject;
		this.scmSource = scmSource;
		this.listener = listener;
	}



	@Override
	public Void call() throws Exception {
		if(subProject.isTemplate()) throw new UnsupportedOperationException();
		listener.info("Syncing configuration to project {}.", subProject.name());
		final XmlFile configFile = templateProject.delegate().getConfigFile();
		final P delegate = subProject.delegate();
		configFile.unmarshal(delegate);

		/*
		 * Build new SCM with the URL and branch already set.
		 *
		 * SCM must be set first since getRootDirFor(project) will give
		 * the wrong location during save, load, and elsewhere if SCM
		 * remains null (or NullSCM).
		 */
		final SCM scm = scmSource.build(subProject.branch().toSCMHead());
		if(scm==null|| scm instanceof NullSCM) throw new IllegalStateException(format("No SCM for {}.", subProject));
		delegate.setScm(scm);

		// Work-around for JENKINS-21017
		delegate.setCustomWorkspace(
				templateProject.delegate().getCustomWorkspace());
		
		delegate.makeDisabled(false);

		delegate.onLoad(parentProject, subProject.name());
		return null;
		}
	}


