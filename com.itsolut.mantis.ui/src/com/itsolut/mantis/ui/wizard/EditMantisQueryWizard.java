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

package com.itsolut.mantis.ui.wizard;

import org.eclipse.mylyn.tasks.core.IRepositoryQuery;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.internal.tasks.core.RepositoryQuery;
import org.eclipse.mylyn.internal.tasks.core.deprecated.AbstractLegacyRepositoryConnector;
import org.eclipse.mylyn.internal.tasks.ui.TasksUiPlugin;
import org.eclipse.mylyn.internal.tasks.ui.deprecated.AbstractRepositoryQueryWizard;
import org.eclipse.mylyn.internal.tasks.ui.util.TasksUiInternal;

/**
 * @author Steffen Pingel
 */
public class EditMantisQueryWizard extends AbstractRepositoryQueryWizard {

//	private MantisCustomQueryPage queryPage;

	public EditMantisQueryWizard(TaskRepository repository, IRepositoryQuery query) {
		super(repository, query);
	}

	@Override
	public void addPages() {
		page = new MantisCustomQueryPage(repository, query);
		page.setWizard(this);
		addPage(page);
	}

	@Override
	public boolean canFinish() {
		if (page.getNextPage() == null) {
			return page.isPageComplete();
		}
		return page.getNextPage().isPageComplete();
	}

//	@Override
//	public boolean performFinish() {
//		RepositoryQuery q = queryPage.getQuery();
//		if (q != null) {
//			TasksUiInternal.getTaskList().deleteQuery(q);
//			TasksUiInternal.getTaskList().addQuery(q);
//
//			AbstractLegacyRepositoryConnector connector = (AbstractLegacyRepositoryConnector)TasksUiPlugin.getRepositoryManager().getRepositoryConnector(
//					repository.getConnectorKind());
//			if (connector != null) {
//				TasksUiInternal.synchronizeQuery(connector, q, null, true);
//			}
//		}
//
//		return true;
//	}

}