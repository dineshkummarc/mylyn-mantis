/*******************************************************************************
 * Copyright (C) 2010 Robert Munteanu <robert.munteanu@gmail.com>
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package com.itsolut.mantis.tests;

import java.math.BigInteger;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.axis.configuration.FileProvider;
import org.eclipse.mylyn.commons.net.AbstractWebLocation;
import org.eclipse.mylyn.commons.net.AuthenticationCredentials;
import org.eclipse.mylyn.commons.net.AuthenticationType;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.TaskRepositoryLocationFactory;

import com.itsolut.mantis.binding.MantisConnectLocator;
import com.itsolut.mantis.binding.MantisConnectPortType;
import com.itsolut.mantis.core.IMantisClient;
import com.itsolut.mantis.core.MantisClientFactory;
import com.itsolut.mantis.core.MantisCorePlugin;

/**
 * The <tt>MantisRepositoryAccessor</tt> provides test-specific methods for easy
 * access to a Mantis Repository.
 * 
 * @author Robert Munteanu
 */
public class MantisRepositoryAccessor {
	
	private final String username;
	private final String password;
	private final String repositoryUrl;
	
	

	public MantisRepositoryAccessor(String username, String password, String repositoryUrl) {
		
		this.username = username;
		this.password = password;
		this.repositoryUrl = repositoryUrl;
	}

	private List<Integer> tasksToDelete = new ArrayList<Integer>();

	private IMantisClient client;

	private AbstractWebLocation location;

	private MantisConnectPortType mantisConnectPort;

	private TaskRepository repository;
	
	public AbstractWebLocation getLocation() {

		return location;
	}

	public IMantisClient getClient() {

		return client;
	}
	
	public TaskRepository getRepository() {
		
		return repository;
	}
	
	public MantisConnectPortType getMantisConnectPort() {
	
		return mantisConnectPort;
	}

	public void init() throws Exception {

		repository = new TaskRepository(MantisCorePlugin.REPOSITORY_KIND, repositoryUrl);
		repository.setCredentials(AuthenticationType.REPOSITORY, new AuthenticationCredentials(username,
				password), false);
		location = new TaskRepositoryLocationFactory().createWebLocation(repository);

		client = MantisClientFactory.getDefault().createClient(location);

		FileProvider provider = new FileProvider(this.getClass().getClassLoader().getResourceAsStream(
				"test-client-config.wsdd"));

		MantisConnectLocator locator = new MantisConnectLocator(provider);
		mantisConnectPort = locator.getMantisConnectPort(new URL(repositoryUrl));

	}
	
	public void registerIssueToDelete(int issueId) {
		
		tasksToDelete.add(issueId);
	}

	public void deleteIssues() throws Exception {

		for (Integer taskToDelete : tasksToDelete)
			mantisConnectPort.mc_issue_delete(username, password, BigInteger.valueOf(taskToDelete.intValue()));
	}
	
}