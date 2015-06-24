/*
 * The MIT License
 *
 * Copyright (c) 2014-2015, Matthew DeTullio, Stephen Connolly
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
package com.github.mjdetullio.jenkins.plugins.multibranch;

import static com.github.mjdetullio.jenkins.plugins.multibranch.util.FormattingUtils.format;
import static com.google.common.base.Objects.equal;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import hudson.Extension;
import hudson.Util;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BallColor;
import hudson.model.DependencyGraph;
import hudson.model.Descriptor;
import hudson.model.HealthReport;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.JobProperty;
import hudson.model.JobPropertyDescriptor;
import hudson.model.Result;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.model.View;
import hudson.model.ViewDescriptor;
import hudson.model.ViewGroup;
import hudson.model.ViewGroupMixIn;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.SaveableListener;
import hudson.scm.NullSCM;
import hudson.tasks.Publisher;
import hudson.triggers.Trigger;
import hudson.util.CopyOnWriteList;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.TimeUnit2;
import hudson.views.DefaultViewsTabBar;
import hudson.views.ViewsTabBar;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.servlet.ServletException;

import jenkins.model.Jenkins;
import jenkins.model.ProjectNamingStrategy;
import jenkins.scm.api.SCMSourceCriteria;
import jenkins.scm.api.SCMSourceDescriptor;
import jenkins.scm.api.SCMSourceOwner;
import jenkins.scm.api.SCMSource;
import jenkins.scm.impl.SingleSCMSource;
import net.sf.json.JSONException;
import net.sf.json.JSONObject;

import org.apache.commons.io.FileUtils;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import antlr.ANTLRException;

import com.github.mjdetullio.jenkins.plugins.multibranch.impl.StaticWiring;
import com.google.common.base.Function;


/**
 * @author Matthew DeTullio
 */
public abstract class AbstractMultiBranchProject<P extends AbstractProject<P, B> & TopLevelItem, B extends AbstractBuild<P, B>>
extends AbstractProject<P, B>
implements TopLevelItem, ItemGroup<P>, ViewGroup, SCMSourceOwner {
	
	static{
		Logging.tweak();
	}
	
	private static final Logger LOG = LoggerFactory.getLogger(AbstractMultiBranchProject.class);

	static final String TEMPLATE = "template";
	private static final String DEFAULT_SYNC_SPEC = "H/5 * * * *";

	private static final Long MAX_AGE = TimeUnit.DAYS.toMillis(7);
	
	//Saved state:
	private volatile boolean allowAnonymousSync;
	private volatile SCMSource scmSource;

	@SuppressWarnings("unused")
	private List<String> disabledSubProjects;
	private List<View> viewsx;

	private volatile String primaryView;
	
	
	//Dependent variables:
	private transient SCMSource scmSourceCache;
	
	private transient volatile StaticWiring<ItemGroup<P>, P,B> staticWiring;

	private transient volatile ViewGroupMixIn viewGroupMixIn;
	private transient volatile ViewsTabBar viewsTabBar;

	private transient boolean syncInProgress;
	private transient boolean repeatSync;


	
	
	protected abstract Class<P> projectClass();

	/**
	 * {@inheritDoc}
	 */
	public AbstractMultiBranchProject(final ItemGroup<?> parent, final String name) {
		super(parent, name);
		try {
			init();
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	protected abstract P createNewSubProject(final String branchName);
	
	protected synchronized final String name(){
		final String name = this.name;
		if(name==null) throw new IllegalStateException("No name.");
		return name;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onLoad(final ItemGroup<? extends Item> parent, final String name)
			throws IOException {
		super.onLoad(parent, name);
		new BranchProjectMigrator(getFullName(), getTemplateDir(), getBranchesDir(), isDisabled()).run();
		init();
	}
	
	private synchronized List<View> getViewList(){
		if(viewsx==null){
			viewsx = new CopyOnWriteArrayList<View>();
		}
		return viewsx;
	}

	/**
	 * Common initialization that is invoked when either a new project is
	 * created with the constructor {@link AbstractMultiBranchProject
	 * (ItemGroup, String)} or when a project is loaded from disk with {@link
	 * #onLoad(ItemGroup, String)}.
	 * @throws IOException 
	 */
	protected void init() throws IOException {
//		if (disabledSubProjects == null) {
//			disabledSubProjects = new PersistedList<String>(this);
//		}

		final List<View> views = getViewList();
		if (views.size() == 0) {
			final BranchListView listView = new BranchListView("All", this);
			views.add(listView);
			listView.setIncludeRegex(".*");
			try {
				listView.save();
			} catch (final IOException e) {
				LOG.warn("Failed to save initial multi-branch project view", e);
			}
		}
		if (primaryView == null) {
			primaryView = views.get(0).getViewName();
		}
		viewsTabBar = new DefaultViewsTabBar();
		viewGroupMixIn = new ViewGroupMixIn(this) {
			@Override
			protected List<View> views() {
				return views;
			}

			@Override
			protected String primaryView() {
				return primaryView;
			}

			@Override
			protected void primaryView(final String name) {
				primaryView = name;
			}
		};

		getStaticWiring().getSubProjectRepository().getTemplateProject();
		
//		try {
//			P templateProject;
//			if (!(new File(getTemplateDir(), "config.xml").isFile())) {
//				templateProject = getProjectFactory().apply(TEMPLATE);
//			} else {
//				//noinspection unchecked
//				templateProject = projectClass().cast(Items.load(this, getTemplateDir()));
//			}
//
//			// Prevent tampering
//			if (!(templateProject.getScm() instanceof NullSCM)) {
//				templateProject.setScm(new NullSCM());
//			}
//			templateProject.disable();
//			this.templateProject = templateProject;
//		} catch (final IOException e) {
//			LOGGER.log(Level.WARNING,
//					"Failed to load template project " + getTemplateDir(), e);
//		}
//		
//		if (getBranchesDir().isDirectory()) {
//			for (final File subProjectDir : getBranchesDir().listFiles(new FileFilter() {
//				@Override
//				public boolean accept(final File pathname) {
//					return pathname.isDirectory() && new File(pathname,
//							"config.xml").isFile();
//				}
//			})) {
//				try {
//					final Item item = (Item) Items.getConfigFile(subProjectDir).read();
//					item.onLoad(this, rawDecode(subProjectDir.getName()));
//
//					final P project = projectClass().cast(item);
//					
//					getSubProjects().addExistingProject(project);
//
//					// Handle offline tampering of disabled setting
//					if (isDisabled() && !project.isDisabled()) {
//						project.disable();
//					}
//				} catch (final IOException e) {
//					LOGGER.log(Level.WARNING,
//							"Failed to load branch project " + subProjectDir, e);
//				}
//			}
//		}

		// Will check triggers(), add & start default sync cron if not there
		getSyncBranchesTrigger();
	}


	/**
	 * Stapler URL binding for ${rootUrl}/job/${project}/branch/${branchProject}
	 *
	 * @param name - Branch project name
	 * @return {@link #getItem(String)}
	 */
	public P getBranch(final String name) {
		return getItem(name);
	}

	/**
	 * Retrieves the template sub-project.  Used by configure-entries.jelly.
	 * @throws IOException 
	 */
	public P getTemplate() {
		return getStaticWiring().getSubProjectRepository().getTemplateProject().delegate();
	}


	/**
	 * Returns the "branches" directory inside the project directory.  This is
	 * where the sub-project directories reside.
	 *
	 * @return File - "branches" directory inside the project directory.
	 */
	public File getBranchesDir() {
		final File dir = new File(getRootDir(), "branches");
		if (!dir.isDirectory() && !dir.mkdirs()) {
			LOG.warn(format("Could not create branches directory {}.", dir));
		}
		return dir;
	}

	/**
	 * Returns the "template" directory inside the project directory.  This is
	 * the template project's directory.
	 *
	 * @return File - "template" directory inside the project directory.
	 */
	public File getTemplateDir() {
		return new File(getRootDir(), TEMPLATE);
	}

	//region ItemGroup implementation

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized Collection<P> getItems() {
		return getStaticWiring().getSubProjectRepository().getDelegates();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Nullable
	public synchronized P getItem(final String name) {
		final StaticWiring<ItemGroup<P>, P, B> w = getStaticWiring();
		final SubProjectRepository<P> repo = w.getSubProjectRepository();
		final SubProject<P> project;
		if(equal(name, TEMPLATE)) {
			project = repo.getTemplateProject();
		} else{
			final BranchId branch = w.getBranchNameMapper().fromProjectName(name);
			project = repo.getProject(branch);
		}
		return project==null?null:project.delegate();
	}
	
	private StaticWiring<ItemGroup<P>, P, B> getStaticWiring(){
		StaticWiring<ItemGroup<P>, P, B> result = staticWiring;
		if(result==null){
			synchronized(this){
				if(staticWiring==null){
				    final Function<String,P> subProjectFactory = new Function<String,P>(){
						@Override
						public P apply(@Nullable final String name) {
							return createNewSubProject(name);
						}
				    };
				    staticWiring = new StaticWiring<ItemGroup<P>,P,B>(
							projectClass(),
							this, 
							getRootDir().toPath(),
							getBranchesDir().toPath(),
							getTemplateDir().toPath(),
						    TEMPLATE,
						    subProjectFactory,
						    MAX_AGE
							);	
				}
				result = staticWiring;
			}
		}
		return result;
	}
	
	
	/**
	 * {@inheritDoc}
	 * @throws IOException 
	 */
	@Override
	public synchronized void onDeleted(final P subProject) throws IOException {
		final StaticWiring<ItemGroup<P>, P, B> w = getStaticWiring();
		final BranchId branch = w.getBranchNameMapper().fromProjectName(subProject.getName());
		try {
			w.getSubProjectRepository().delete(branch);
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getUrlChildPrefix() {
		return "branch";
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public File getRootDirFor(final P child) {
		File subProjectDir;
		if (isTemplateSubProject(child)) {
			subProjectDir = getTemplateDir();
		}
		else{// All others are branches
			subProjectDir = new File(getBranchesDir(), Util.rawEncode(child.getName()));
		}
		return subProjectDir;
	}

	private boolean isTemplateSubProject(final P child) {
		// Null SCM should be the template
		return child.getScm() == null || child.getScm() instanceof NullSCM;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onRenamed(final P item, final String oldName, final String newName) {
		throw new UnsupportedOperationException(
				"Renaming sub-projects is not supported.  They should only be added or deleted.");
	}

	//endregion ItemGroup implementation

	//region ViewGroup implementation

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean canDelete(final View view) {
		return viewGroupMixIn.canDelete(view);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deleteView(final View view) throws IOException {
		viewGroupMixIn.deleteView(view);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Exported
	public Collection<View> getViews() {
		return viewGroupMixIn.getViews();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public View getView(final String name) {
		return viewGroupMixIn.getView(name);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Exported
	public View getPrimaryView() {
		return viewGroupMixIn.getPrimaryView();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onViewRenamed(final View view, final String oldName, final String newName) {
		viewGroupMixIn.onViewRenamed(view, oldName, newName);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ViewsTabBar getViewsTabBar() {
		return viewsTabBar;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public ItemGroup<? extends TopLevelItem> getItemGroup() {
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Action> getViewActions() {
		return Collections.emptyList();
	}

	//endregion ViewGroup implementation

	//region SCMSourceOwner implementation

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<SCMSource> getSCMSources() {
		final SCMSource scmSource = getSCMSource();
		if (scmSource == null) {
			return Collections.emptyList();
		}
		return Arrays.asList(scmSource);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Nullable
	public SCMSource getSCMSource(@Nullable final String sourceId) {
		final SCMSource scmSource = getSCMSource();
		if (scmSource != null && scmSource.getId().equals(sourceId)) {
			return scmSource;
		}
		return null;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onSCMSourceUpdated(final SCMSource source) {
		getSyncBranchesTrigger().run();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	@Nullable
	public SCMSourceCriteria getSCMSourceCriteria(final SCMSource source) {
		return getStaticWiring().getListeningBranchPreseletor();
	}

	//endregion SCMSourceOwner implementation

	/**
	 * Returns the project's only SCMSource.  Used by configure-entries.jelly.
	 *
	 * @return the project's only SCMSource (may be null)
	 */
	@Nullable
	public synchronized SCMSource getSCMSource() {
		final SCMSource scmSource = this.scmSource;
		if(scmSourceCache!=scmSource){
			//scmSource from persistence mechanism
			if(scmSource.getOwner()!=this){
				scmSource.setOwner(this);
			}
			scmSourceCache = scmSource;
		}
		if(scmSourceCache.getOwner()!=this){
			scmSourceCache.setOwner(this);
		}
		return scmSourceCache;
	}
	
	private synchronized void setSCMSource(final SCMSource scmSource) {		
		if(scmSource.getOwner()!=this){
			scmSource.setOwner(this);
		}
		this.scmSource = scmSource;		
		this.scmSourceCache = scmSource;		
	}


	/**
	 * Gets whether anonymous sync is allowed from <code>${JOB_URL}/syncBranches</code>
	 */
	public boolean isAllowAnonymousSync() {
		return allowAnonymousSync;
	}

	/**
	 * Sets whether anonymous sync is allowed from <code>${JOB_URL}/syncBranches</code>.
	 *
	 * @param b - true/false
	 * @throws IOException - if problems saving
	 */
	public void setAllowAnonymousSync(final boolean b) throws IOException {
		allowAnonymousSync = b;
		save();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isBuildable() {
		return false;
	}

	/**
	 * Stapler URL binding for creating views for our branch projects.  Unlike
	 * normal views, this only requires permission to configure the project,
	 * not
	 * create view permission.
	 *
	 * @param req - Stapler request
	 * @param rsp - Stapler response
	 * @throws IOException              - if problems
	 * @throws ServletException         - if problems
	 * @throws java.text.ParseException - if problems
	 * @throws Descriptor.FormException - if problems
	 */
	public synchronized void doCreateView(final StaplerRequest req,
			final StaplerResponse rsp) throws IOException, ServletException,
			ParseException, Descriptor.FormException {
		checkPermission(CONFIGURE);
		viewGroupMixIn.addView(View.create(req, rsp, this));
	}

	/**
	 * Stapler URL binding used by the newView page to check for existing
	 * views.
	 *
	 * @param value - desired name of view
	 * @return {@link hudson.util.FormValidation#ok()} or {@link
	 * hudson.util.FormValidation#error(String)|
	 */
	public FormValidation doViewExistsCheck(@QueryParameter final String value) {
		checkPermission(CONFIGURE);

		final String view = Util.fixEmpty(value);
		if (view == null || getView(view) == null) {
			return FormValidation.ok();
		} else {
			return FormValidation.error(
					jenkins.model.Messages.Hudson_ViewAlreadyExists(view));
		}
	}

	/**
	 * Overrides the {@link hudson.model.AbstractProject} implementation
	 * because the user is not redirected to the parent properly for this
	 * project type.
	 * <p/>
	 * Inherited docs:
	 * <p/>
	 * {@inheritDoc}
	 *
	 * @param req - Stapler request
	 * @param rsp - Stapler response
	 * @throws IOException          - if problems
	 * @throws InterruptedException - if problems
	 */
	@Override
	@RequirePOST
	public void doDoDelete(final StaplerRequest req, final StaplerResponse rsp)
			throws IOException, InterruptedException {
		delete();
		if (req == null || rsp == null) {
			return;
		}
		rsp.sendRedirect2(req.getContextPath() + '/' + getParent().getUrl());
	}

	/**
	 * Exposes a URI that allows the trigger of a branch sync.
	 *
	 * @param req - Stapler request
	 * @param rsp - Stapler response
	 * @throws IOException          - if problems
	 * @throws InterruptedException - if problems
	 */
	@RequirePOST
	public void doSyncBranches(final StaplerRequest req, final StaplerResponse rsp)
			throws IOException, InterruptedException {
		if (!allowAnonymousSync) {
			checkPermission(CONFIGURE);
		}
		getSyncBranchesTrigger().run();
	}

	
	protected final synchronized CopyOnWriteList<JobProperty<? super P>> properties(){
		final CopyOnWriteList<JobProperty<? super P>> properties = this.properties;
		if(properties==null) throw new IllegalStateException("No properties.");
		return properties;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void doConfigSubmit(final StaplerRequest req, final StaplerResponse rsp)
			throws ServletException, Descriptor.FormException, IOException {
		checkPermission(CONFIGURE);

		description = req.getParameter("description");

		makeDisabled(req.getParameter("disable") != null);

		allowAnonymousSync = req.getSubmittedForm().has("allowAnonymousSync");

		try {
			final JSONObject json = req.getSubmittedForm();

			setDisplayName(json.optString("displayNameOrNull"));

			/*
			 * Save job properties to the parent project.
			 * Needed for things like project-based matrix authorization so the
			 * parent project's ACL works as desired.
			 */
			final DescribableList<JobProperty<?>, JobPropertyDescriptor> t = new DescribableList<JobProperty<?>, JobPropertyDescriptor>(
					NOOP, getAllProperties());
			t.rebuild(req, json.optJSONObject("properties"),
					JobPropertyDescriptor.getPropertyDescriptors(
							this.getClass()));
			final CopyOnWriteList<JobProperty<? super P>> properties = properties();
			properties.clear();
			for (final JobProperty p : t) {
				// Hack to set property owner since it is not exposed
				// p.setOwner(this)
				try {
					final Field f = JobProperty.class.getDeclaredField("owner");
					f.setAccessible(true);
					f.set(p, this);
				} catch (final Throwable e) {
					LOG.warn("Unable to set job property owner", e);
				}
				// End hack
				//noinspection unchecked
				properties.add(p);
			}

			final String syncBranchesCron = json.getString("syncBranchesCron");
			try {
				restartSyncBranchesTrigger(syncBranchesCron);
			} catch (final ANTLRException e) {
				throw new IllegalArgumentException(
						"Failed to instantiate SyncBranchesTrigger", e);
			}

			primaryView = json.getString("primaryView");

			SCMSource scmSource;
			final JSONObject scmSourceJson = json.optJSONObject("scmSource");
			if (scmSourceJson == null) {
				scmSource = null;
			} else {
				final int value = Integer.parseInt(scmSourceJson.getString("value"));
				final SCMSourceDescriptor descriptor = getSCMSourceDescriptors(
						true).get(
						value);
				scmSource = descriptor.newInstance(req, scmSourceJson);
				scmSource.setOwner(this);
			}
			setSCMSource(scmSource);

			final P templateProject = getTemplate();
			templateProject.doConfigSubmit(
					new TemplateStaplerRequestWrapper(req), rsp);

			save();
			// TODO could this be used to trigger syncBranches()?
			ItemListener.fireOnUpdated(this);

			final String name = name();
			final String newName = req.getParameter("name");
			final ProjectNamingStrategy namingStrategy = Jenkins.getInstance().getProjectNamingStrategy();
			if (newName != null && !newName.equals(name)) {
				// check this error early to avoid HTTP response splitting.
				Jenkins.checkGoodName(newName);
				namingStrategy.checkName(newName);
				rsp.sendRedirect("rename?newName=" + URLEncoder.encode(newName,
						"UTF-8"));
			} else {
				if (namingStrategy.isForceExistingJobs()) {
					namingStrategy.checkName(name);
				}
				// templateProject.doConfigSubmit(req, rsp) already does this
				//noinspection ThrowableResultOfMethodCallIgnored
				//FormApply.success(".").generateResponse(req, rsp, null);
			}
		} catch (final JSONException e) {
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);
			pw.println(
					"Failed to parse form data. Please report this problem as a bug");
			pw.println("JSON=" + req.getSubmittedForm());
			pw.println();
			e.printStackTrace(pw);

			rsp.setStatus(SC_BAD_REQUEST);
			sendError(sw.toString(), req, rsp, true);
		}
		//endregion Job mirror

		//region AbstractProject mirror
		updateTransientActions();

		// notify the queue as the project might be now tied to different node
		Jenkins.getInstance().getQueue().scheduleMaintenance();

		// this is to reflect the upstream build adjustments done above
		Jenkins.getInstance().rebuildDependencyGraphAsync();
		//endregion AbstractProject mirror
		
		final SyncBranchesTrigger<?> syncBranchesTrigger = getSyncBranchesTrigger();
//		new Thread(getName()+"-sync-branches"){
//			@Override
//			public void run() {
		syncBranchesTrigger.run();
//			}			
//		}.start();
	}


	/**
	 * Stops all triggers, then if a non-null spec is given clears all triggers
	 * and creates a new {@link SyncBranchesTrigger} from the spec, and finally
	 * starts all triggers.
	 */
	@SuppressWarnings("unchecked")
	private synchronized void restartSyncBranchesTrigger(final String cronTabSpec)
			throws IOException, ANTLRException {
		for (final Trigger<?> trigger : triggers()) {
			trigger.stop();
		}

		if (cronTabSpec != null) {
			// triggers() should only have our single SyncBranchesTrigger
			triggers().clear();

			addTrigger(new SyncBranchesTrigger<>(cronTabSpec));
		}

		for (@SuppressWarnings("rawtypes") final Trigger trigger : triggers()) {
			trigger.start(this, false);
		}
	}
	
	
	/**
	 * Checks the validity of the triggers associated with this project (there
	 * should always be one trigger of type {@link SyncBranchesTrigger}),
	 * corrects it if necessary, and returns the trigger.
	 *
	 * @return SyncBranchesTrigger that is non-null and valid
	 */
	private synchronized SyncBranchesTrigger<?> getSyncBranchesTrigger() {
		if (triggers().size() != 1
				|| !(triggers().get(0) instanceof SyncBranchesTrigger)
				|| triggers().get(0).getSpec() == null) {
			// triggers() isn't valid (for us), so let's fix it
			String spec = DEFAULT_SYNC_SPEC;
			if (triggers().size() > 1) {
				for (final Trigger<?> trigger : triggers()) {
					if (trigger instanceof SyncBranchesTrigger
							&& trigger.getSpec() != null) {
						spec = trigger.getSpec();
						break;
					}
				}
			}

			// Errors shouldn't occur here since spec should be valid
			try {
				restartSyncBranchesTrigger(spec);
			} catch (final IOException e) {
				LOG.warn("Failed to add trigger SyncBranchesTrigger", e);
			} catch (final ANTLRException e) {
				LOG.warn("Failed to instantiate SyncBranchesTrigger", e);
			}
		}

		return (SyncBranchesTrigger<?>) triggers().get(0);
	}

	/**
	 * Synchronizes the available sub-projects by checking if the project is
	 * disabled, then calling {@link #_syncBranches(TaskListener)} and logging
	 * its exceptions to the listener.
	 * @throws IOException 
	 */
	public void syncBranches(final TaskListener listener) throws IOException {
		Logging.diagnose();
//		boolean startSync;
		if (isDisabled()) {
			listener.getLogger().println("Project disabled.");
//			startSync = false;
		}
		else{
			getStaticWiring().getSynchronizer().synchronizeBranches(getSCMSource(), getTemplate(), listener);
//			synchronized(this){
//				//Ensure there is only one active sync thread at any time.
//				//If there is a new request while a sync is in progress, 
//				//do the sync again after it has finished.
//				if(syncInProgress){
//					startSync = false;
//					repeatSync = true;
//				}else{
//					startSync = true;
//					repeatSync = false;
//					syncInProgress = true;
//				}
//			}
//		}
//		while(startSync){
//			try {
//				final Future<Void> f = getStaticWiring().getSynchronizer().synchronizeBranches(scmSource, templateProject, listener);
//				f.get();
//			} catch (final Throwable e) {
//				e.printStackTrace(listener.fatalError(e.getMessage()));
//			} finally{
//				synchronized(this){
//					if(repeatSync){
//						repeatSync = false;
//						startSync = true;
//						syncInProgress = true;
//					}else{
//						syncInProgress = false;
//						startSync = false;
//					}
//				}
//			}
		}
	}

	
	/**
	 * Used by Jelly to populate the Sync Branches Schedule field on the
	 * configuration page.
	 *
	 * @return String - cron
	 */
	public String getSyncBranchesCron() {
		return getSyncBranchesTrigger().getSpec();
	}

	/**
	 * Used as the color of the status ball for the project.
	 * <p/>
	 * Kanged from Branch API.
	 *
	 * @return the color of the status ball for the project.
	 */
	@Override
	@Exported(visibility = 2, name = "color")
	public BallColor getIconColor() {
		if (isDisabled()) {
			return BallColor.DISABLED;
		}

		BallColor c = BallColor.DISABLED;
		boolean animated = false;

		for (final P item : getItems()) {
			BallColor d = item.getIconColor();
			animated |= d.isAnimated();
			d = d.noAnime();
			if (d.compareTo(c) < 0) {
				c = d;
			}
		}

		if (animated) {
			c = c.anime();
		}

		return c;
	}

	/**
	 * Get the current health reports for a job.
	 * <p/>
	 * Kanged from Branch API.
	 *
	 * @return the health reports. Never returns null
	 */
	@Override
	@Exported(name = "healthReport")
	public List<HealthReport> getBuildHealthReports() {
		// TODO: cache reports?
		int branchCount = 0;
		int branchBuild = 0;
		int branchSuccess = 0;
		long branchAge = 0;

		for (final P item : getItems()) {
			branchCount++;
			final B lastBuild = item.getLastBuild();
			if (lastBuild != null) {
				branchBuild++;
				final Result r = lastBuild.getResult();
				if (r != null && r.isBetterOrEqualTo(Result.SUCCESS)) {
					branchSuccess++;
				}
				branchAge += TimeUnit2.MILLISECONDS.toDays(
						lastBuild.getTimeInMillis()
								- System.currentTimeMillis());
			}
		}

		final List<HealthReport> reports = new ArrayList<HealthReport>();
		if (branchCount > 0) {
			reports.add(new HealthReport(branchSuccess * 100 / branchCount,
					Messages._Health_BranchSuccess()));
			reports.add(new HealthReport(branchBuild * 100 / branchCount,
					Messages._Health_BranchBuilds()));
			reports.add(new HealthReport(Math.min(100,
					Math.max(0, (int) (100 - (branchAge / branchCount)))),
					Messages._Health_BranchAge()));
			Collections.sort(reports);
		}

		return reports;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void makeDisabled(final boolean b) throws IOException {
		super.makeDisabled(b);

		// Manage the sub-projects
		if (b) {
			/*
			 * Populate list only if it is empty.  Running this loop when the
			 * parent (and therefore, all sub-projects) are already disabled will
			 * add all branches.  Obviously not desirable.
			 */
//			if (disabledSubProjects.isEmpty()) {
//				for (final P project : getSubProjects().values()) {
//					if (project.isDisabled()) {
//						disabledSubProjects.add(project.getName());
//					}
//				}
//			}

			// Always forcefully disable all sub-projects
			for (final P project : getItems()) {
				project.disable();
			}
		} else {
			// Re-enable only the projects that weren't manually marked disabled
			for (final P project : getItems()) {
//				if (!disabledSubProjects.contains(project.getName())) {
					project.enable();
//				}
			}

			// Clear the list so it can be rebuilt when parent is disabled
//			disabledSubProjects.clear();

			/*
			 * Apparently the great authors of PersistedList decided you don't
			 * need to "persist" when the list is cleared...
			 */
			save();
		}
	}

	/**
	 * Returns a list of ViewDescriptors that we want to use for this project
	 * type.  Used by newView.jelly.
	 */
	public static List<ViewDescriptor> getViewDescriptors() {
		return Arrays.asList(
				(ViewDescriptor) Jenkins.getInstance().getDescriptorByType(
						BranchListView.DescriptorImpl.class));
	}

	//
	//
	// selective Project mirror below
	// TODO: cleanup more
	//
	//

	/**
	 * Used by SyncBranchesAction to load the sidepanel when the user is on the
	 * Sync Branches Log page.
	 *
	 * @return this
	 */
	public AbstractProject<?, ?> asProject() {
		return this;
	}

	@Override
	public DescribableList<Publisher, Descriptor<Publisher>> getPublishersList() {
		// TODO: is this ok?
		return getTemplate().getPublishersList();
	}

	@Override
	protected void buildDependencyGraph(final DependencyGraph graph) {
		// no-op
		// TODO: build for each sub-project?
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean isFingerprintConfigured() {
		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void submit(final StaplerRequest req, final StaplerResponse rsp)
			throws IOException,
			ServletException, Descriptor.FormException {
		// No-op
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected List<Action> createTransientActions() {
		final List<Action> r = super.createTransientActions();

		for (final Trigger<?> trigger : triggers()) {
			r.addAll(trigger.getProjectActions());
		}

		return r;
	}

	//
	//
	// END: selective Project mirror
	//
	//

	/**
	 * Returns a list of SCMSourceDescriptors that we want to use for this
	 * project type.  Used by configure-entries.jelly.
	 */
	public static List<SCMSourceDescriptor> getSCMSourceDescriptors(
			final boolean onlyUserInstantiable) {
		final List<SCMSourceDescriptor> descriptors = SCMSourceDescriptor.forOwner(
				AbstractMultiBranchProject.class, onlyUserInstantiable);

		/*
		 * No point in having SingleSCMSource as an option, so axe it.
		 * Might as well use the regular project if you really want this...
		 */
		for (final SCMSourceDescriptor descriptor : descriptors) {
			if (descriptor instanceof SingleSCMSource.DescriptorImpl) {
				descriptors.remove(descriptor);
				break;
			}
		}

		return descriptors;
	}

	/**
	 * Inverse function of {@link hudson.Util#rawEncode(String)}.
	 * <p/>
	 * Kanged from Branch API.
	 *
	 * @param s the encoded string.
	 * @return the decoded string.
	 */
	public static String rawDecode(final String s) {
		final byte[] bytes; // should be US-ASCII but we can be tolerant
		try {
			bytes = s.getBytes("UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException(
					"JLS specification mandates UTF-8 as a supported encoding",
					e);
		}

		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		for (int i = 0; i < bytes.length; i++) {
			final int b = bytes[i];
			if (b == '%' && i + 2 < bytes.length) {
				final int u = Character.digit((char) bytes[++i], 16);
				final int l = Character.digit((char) bytes[++i], 16);

				if (u != -1 && l != -1) {
					buffer.write((char) ((u << 4) + l));
					continue;
				}

				// should be a valid encoding but we can be tolerant
				i -= 2;
			}
			buffer.write(b);
		}

		try {
			return new String(buffer.toByteArray(), "UTF-8");
		} catch (final UnsupportedEncodingException e) {
			throw new IllegalStateException(
					"JLS specification mandates UTF-8 as a supported encoding",
					e);
		}
	}

	/**
	 * Triggered by different listeners to enforce state for multi-branch
	 * projects and their sub-projects.
	 * <ul>
	 * <li>Ensures multi-branch project's SCM is a {@link NullSCM}.</li>
	 * <li>Watches for changes to the template project and corrects the SCM and
	 * enabled/disabled state if modified.</li>
	 * <li>Looks for rogue template project in the branches directory and
	 * removes it if no such sub-project exists.</li>
	 * <li>Re-disables sub-projects if they were enabled when the parent
	 * project was disabled.</li>
	 * </ul>
	 *
	 * @param item - the item that was just updated
	 */
	public static void enforceProjectStateOnUpdated(final Item item) {
		if (item instanceof AbstractMultiBranchProject) {
			final AbstractMultiBranchProject<?,?> project = (AbstractMultiBranchProject<?,?>) item;
			if (!(project.getScm() instanceof NullSCM)) {
				try {
					project.setScm(new NullSCM());
				} catch (final IOException e) {
					LOG.warn("Unable to correct project configuration.");
				}
			}
		}

		if (item.getParent() instanceof AbstractMultiBranchProject) {
			final AbstractMultiBranchProject<?,?> parent = (AbstractMultiBranchProject<?,?>) item.getParent();
			final AbstractProject<?,?> template = parent.getTemplate();

			// Direct memory reference comparison
			if (item == template) {
				try {
					if (!(template.getScm() instanceof NullSCM)) {
						template.setScm(new NullSCM());
					}

					if (!template.isDisabled()) {
						template.disable();
					}
				} catch (final IOException e) {
					LOG.warn("Unable to correct template configuration.");
				}

				/*
				 * Remove template from branches directory if there isn't a
				 * sub-project with the same name.
				 */
				if (parent.getItem(TEMPLATE)==null) {
					try {
						FileUtils.deleteDirectory(
								new File(parent.getBranchesDir(), TEMPLATE));
					} catch (final IOException e) {
						LOG.warn("Unable to delete rogue template dir.");
					}
				}
			}

			// Don't allow sub-projects to be enabled if parent is disabled
			final AbstractProject<?,?> project = (AbstractProject<?,?>) item;
			if (parent.isDisabled() && !project.isDisabled()) {
				try {
					project.disable();
				} catch (final IOException e) {
					LOG.warn("Unable to keep sub-project disabled.");
				}
			}
		}
	}

	/**
	 * Additional listener for normal changes to Items in the UI, used to
	 * enforce state for multi-branch projects and their sub-projects.
	 */
	@Extension
	public static final class BranchProjectItemListener extends ItemListener {
		@Override
		public void onUpdated(final Item item) {
			enforceProjectStateOnUpdated(item);
		}
	}

	/**
	 * Additional listener for changes to Items via config.xml POST, used to
	 * enforce state for multi-branch projects and their sub-projects.
	 */
	@Extension
	public static final class BranchProjectSaveListener extends
			SaveableListener {
		@Override
		public void onChange(final Saveable o, final XmlFile file) {
			if (o instanceof Item) {
				enforceProjectStateOnUpdated((Item) o);
			}
		}
	}
}
