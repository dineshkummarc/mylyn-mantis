/*******************************************************************************
 * Copyright (c) 2006 - 2006 Mylar eclipse.org project and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Mylar project committers - initial API and implementation
 *******************************************************************************/

package com.itsolut.mantis.core;

import org.eclipse.mylar.tasks.core.AbstractQueryHit;
import org.eclipse.mylar.tasks.core.AbstractRepositoryTask;
import org.eclipse.mylar.tasks.core.TaskList;

/**
 * @author Steffen Pingel
 */
public class MantisQueryHit extends AbstractQueryHit {

	public MantisQueryHit(TaskList taskList, String repositoryUrl, String description, String id) {
		super(taskList, repositoryUrl, description, id);
	}

	public MantisQueryHit(TaskList taskList, String handle) {
		super(taskList, AbstractRepositoryTask.getRepositoryUrl(handle), "", AbstractRepositoryTask.getTaskId(handle));
	} 

	@Override
	protected AbstractRepositoryTask createTask() {
		MantisTask newTask = new MantisTask(getHandleIdentifier(), getSummary(), true);
		newTask.setPriority(priority);
		return newTask;
	}

	@Override
	public String getUrl() {
		return getRepositoryUrl() + IMantisClient.TICKET_URL + getId();
	}

}
