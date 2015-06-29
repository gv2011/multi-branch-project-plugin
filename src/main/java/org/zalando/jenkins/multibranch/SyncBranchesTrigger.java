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

import hudson.Extension;
import hudson.Util;
import hudson.console.AnnotatedLargeText;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Items;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.jelly.XMLOutput;
import org.kohsuke.stapler.DataBoundConstructor;

import antlr.ANTLRException;
import edu.umd.cs.findbugs.annotations.CheckForNull;

/**
 * An internal cron-based trigger used to sync branches (sub-projects) for
 * multi-branch project types.  It is not used to trigger builds.
 */
public class SyncBranchesTrigger<P extends AbstractMultiBranchProject<?,?>> extends Trigger<P> {
	private static final String CLASSNAME = SyncBranchesTrigger.class.getName();
	private static final Logger LOGGER = Logger.getLogger(CLASSNAME);

	/**
	 * Creates a new {@link SyncBranchesTrigger} that gets {@link #run() run}
	 * periodically.
	 *
	 * @param cronTabSpec - cron this trigger should run on
	 */
	@DataBoundConstructor
	public SyncBranchesTrigger(final String cronTabSpec) throws ANTLRException {
		super(cronTabSpec);
	}
	
	

	@Override
	public synchronized void start(final P project, final boolean newInstance) {
		super.start(project, newInstance);
	}

	@CheckForNull
	private synchronized P optionalJob(){
		return job;
	}

	private P job(){
		final P job = optionalJob();
		if(job==null) throw new IllegalStateException("No job.");
		return job;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void run() {
		/*
		 * The #start(Item, boolean) method provides the job so this will be null
		 * only when invoked directly before starting.
		 */
		final P job = optionalJob();
		if (job == null) {
			return;
		}

		try {
//			final StreamTaskListener listener = new StreamTaskListener(getLogFile());
//
//			final long start = System.currentTimeMillis();
//
//			listener.getLogger().println(
//					"Started on " + DateFormat.getDateTimeInstance().format(
//							new Date()));

			job.syncBranches(getLogFile().toPath());

//			listener.getLogger().println("Done. Took " + Util.getTimeSpanString(
//					System.currentTimeMillis() - start));
//
//			listener.close();
		} catch (final IOException e) {
			LOGGER.log(Level.SEVERE,
					"Failed to record sync branches log for " + job, e);
		}
	}

	/**
	 * Returns the file that records the last/current sync branches activity.
	 */
	public File getLogFile() {
		return new File(job().getRootDir(), "sync-branches.log");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Collection<? extends Action> getProjectActions() {
		return Collections.singleton(new SyncBranchesAction());
	}

	/**
	 * Action object for {@link hudson.model.Project}. Used to display the last
	 * sync branches log.
	 */
	public final class SyncBranchesAction implements Action {
		/**
		 * Used by index.jelly to load the proper sidepanel.jelly.
		 *
		 * @return action owner
		 */
		public AbstractProject<?, ?> getOwner() {
			return job.asProject();
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getIconFileName() {
			return "clipboard.png";
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return "Sync Branches Log";
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getUrlName() {
			return "syncBranchesLog";
		}

		/**
		 * Used by index.jelly to display the log.
		 *
		 * @return the log
		 */
		public String getLog() throws IOException {
			return Util.loadFile(getLogFile());
		}

		/**
		 * Writes the annotated log to the given output.
		 */
		public void writeLogTo(final XMLOutput out) throws IOException {
			new AnnotatedLargeText<SyncBranchesAction>(getLogFile(),
					Charset.defaultCharset(), true, this).writeHtmlTo(0,
					out.asWriter());
		}
	}

	/**
	 * Descriptor for this trigger that will prevent it from showing in the
	 * configuration.
	 */
	@Extension
	public static class DescriptorImpl extends TriggerDescriptor {
		/**
		 * Trigger should not appear in configuration, so mark this as false.
		 * <p/> Inherited docs: <p/> {@inheritDoc}
		 */
		@Override
		public boolean isApplicable(final Item item) {
			return false;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public String getDisplayName() {
			return SyncBranchesTrigger.class.getSimpleName();
		}
	}

	/**
	 * Gives this class an alias for configuration XML.
	 */
	@Initializer(before = InitMilestone.PLUGINS_STARTED)
	public static void registerXStream() {
		Items.XSTREAM.alias("sync-branches-trigger", SyncBranchesTrigger.class);
	}
}
