package com.github.mjdetullio.jenkins.plugins.multibranch.impl;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Items;
import hudson.scm.NullSCM;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.mjdetullio.jenkins.plugins.multibranch.BranchId;
import com.github.mjdetullio.jenkins.plugins.multibranch.BranchNameMapper;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProject;
import com.github.mjdetullio.jenkins.plugins.multibranch.SubProjectFactory;

abstract class SubProjectFactoryImpl<PA extends ItemGroup<P>, P extends AbstractProject<P,R>, R extends AbstractBuild<P,R>> 
implements SubProjectFactory<P>{

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(SubProjectFactoryImpl.class);

	private final Class<P> projectClass;
	private final PA parent;
	private final Path subProjectsDirectory;
	private final Path templateDir;
	private final String templateName;
	protected final BranchNameMapper nameMapper;
	
	
	public SubProjectFactoryImpl(final Class<P> projectClass, final PA parent,
			final Path subProjectsDirectory, final Path templateDir, final String templateName,
			final BranchNameMapper nameMapper) {
		super();
		this.projectClass = projectClass;
		this.parent = parent;
		this.subProjectsDirectory = subProjectsDirectory;
		this.templateDir = templateDir.toAbsolutePath().normalize();
		this.templateName = templateName;
		this.nameMapper = nameMapper;
	}
	
	@Override
	public SubProject<P> createNewSubProject(final BranchId branch) {
		final String name = branch.toProjectName();
		final Path subProjectDirectory = subProjectsDirectory.resolve(name);
		if(Files.exists(subProjectDirectory)) throw new IllegalStateException("Not empty.");
		final P delegate = createDelegate(name);
		return new SubProjectImpl<P>(branch, subProjectDirectory, delegate);
	}
	
	

	@Override
	public SubProject<P> getTemplateProject() {
		if(Files.exists(templateDir.resolve("config.xml"))) return loadTemplateProject();
		else return createTemplateProject();
	}

	private SubProject<P> createTemplateProject() {
		final P delegate = createDelegate(templateName);
		initTemplate(delegate);
		return new SubProjectImpl<P>(templateName, templateDir, delegate);
	}
	
	private SubProject<P> loadTemplateProject(){
		P delegate;
		try {
			delegate = projectClass.cast(Items.load(parent, templateDir.toFile()));
		} catch (final IOException e) {
			throw new RuntimeException("Could not load template project.", e);
		}
		initTemplate(delegate);
		return new SubProjectImpl<P>(templateName, templateDir, delegate);
	}

	private void initTemplate(final P delegate) {
		try {
			if (!(delegate.getScm() instanceof NullSCM)) {
				delegate.setScm(new NullSCM());
			}
			delegate.disable();
		} catch (final IOException e) {
			throw new RuntimeException("Could not initialize template project.", e);
		}
	}

	@Override
	public SubProject<P> loadExistingSubProject(Path subProjectDir) throws IOException {
		subProjectDir = subProjectDir.toAbsolutePath().normalize();
		if(!Files.isDirectory(subProjectDir)) throw new IllegalArgumentException();
		final BranchId branch = nameMapper.fromDirectory(subProjectDir);
		if(!subProjectsDirectory.equals(subProjectDir.getParent())) throw new IllegalArgumentException();
		return loadExistingSubProject(branch, subProjectDir);
	}

	protected SubProject<P> loadExistingSubProject(final BranchId branch, final Path subProjectDir)
			throws IOException {
		final Item item = (Item) Items.getConfigFile(subProjectDir.toFile()).read();
		item.onLoad(parent, branch.toProjectName());

		final P delegate = projectClass.cast(item);
		
		// Handle offline tampering of disabled setting
//		if (isDisabled() && !project.isDisabled()) {
//			project.disable();
//		}
		return new SubProjectImpl<P>(branch, subProjectDir, delegate);
	}

	protected abstract P createDelegate(String name);

}
