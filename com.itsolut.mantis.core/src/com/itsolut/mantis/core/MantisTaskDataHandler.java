/*******************************************************************************
 * Copyright (c) 2008 - 2008 Standards for Technology in Automotive Retail
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     David Carver - STAR - adapted from Bugzilla mylyn 3.0 implementation.
 *******************************************************************************/

package com.itsolut.mantis.core;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
import org.eclipse.mylyn.tasks.core.ITaskMapping;
import org.eclipse.mylyn.tasks.core.RepositoryResponse;
import org.eclipse.mylyn.tasks.core.TaskRepository;
import org.eclipse.mylyn.tasks.core.RepositoryResponse.ResponseKind;
import org.eclipse.mylyn.tasks.core.data.AbstractTaskDataHandler;
import org.eclipse.mylyn.tasks.core.data.TaskAttachmentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskAttribute;
import org.eclipse.mylyn.tasks.core.data.TaskAttributeMapper;
import org.eclipse.mylyn.tasks.core.data.TaskCommentMapper;
import org.eclipse.mylyn.tasks.core.data.TaskData;
import org.eclipse.mylyn.tasks.core.data.TaskOperation;

import com.itsolut.mantis.core.MantisAttributeMapper.Attribute;
import com.itsolut.mantis.core.exception.InvalidTicketException;
import com.itsolut.mantis.core.exception.MantisException;
import com.itsolut.mantis.core.model.MantisAttachment;
import com.itsolut.mantis.core.model.MantisComment;
import com.itsolut.mantis.core.model.MantisProjectCategory;
import com.itsolut.mantis.core.model.MantisRelationship;
import com.itsolut.mantis.core.model.MantisTicket;
import com.itsolut.mantis.core.model.MantisVersion;
import com.itsolut.mantis.core.model.MantisTicket.Key;
import com.itsolut.mantis.core.util.MantisUtils;

/**
 * @author Dave Carver
 */
public class MantisTaskDataHandler extends AbstractTaskDataHandler {

    private final MantisRepositoryConnector connector;

    private static final String CONTEXT_ATTACHMENT_FILENAME = "mylyn-context.zip";

    private static final String CONTEXT_ATTACHMENT_DESCRIPTION = "mylyn/context/zip";

    private static final String CONTEXT_ATTACHMENT_DESCRIPTION_LEGACY = "mylar/context/zip";

    private static final String CONTEXT_ATTACHMENT_FILENAME_LEGACY = "mylar-context.zip";

    public MantisTaskDataHandler(MantisRepositoryConnector connector) {
        this.connector = connector;
    }

    @Override
    public TaskAttributeMapper getAttributeMapper(TaskRepository taskRepository) {
        return new MantisAttributeMapper(taskRepository);
    }

    @Override
    public boolean initializeTaskData(TaskRepository repository, TaskData data,
            ITaskMapping initializationData, IProgressMonitor monitor)
    throws CoreException {
        try {
            IMantisClient client = connector.getClientManager().getRepository(
                    repository);
            client.updateAttributes(monitor, false);
            createDefaultAttributes(data, client, false);
            TaskAttribute projectAttribute = getAttribute(data, MantisAttributeMapper.Attribute.PROJECT.getKey().toString());
            projectAttribute.setValue(initializationData.getProduct());
            createProjectSpecificAttributes(data, client);
            return true;
        } catch (OperationCanceledException e) {
            throw e;
        } catch (Exception e) {
            throw new CoreException(MantisCorePlugin.toStatus(e));
        }
    }

    @Override
    public RepositoryResponse postTaskData(TaskRepository repository,
            TaskData taskData, Set<TaskAttribute> oldAttributes,
            IProgressMonitor monitor) throws CoreException {
        try {
            IMantisClient client = connector.getClientManager().getRepository(
                    repository);
            MantisTicket ticket = getMantisTicket(repository, taskData);


            if (taskData.isNew()) {
                int id = client.createTicket(ticket);
                return new RepositoryResponse(ResponseKind.TASK_UPDATED, id
                        + "");
            } else {
                String newComment = "";
                TaskAttribute newCommentAttribute = taskData.getRoot()
                .getMappedAttribute(TaskAttribute.COMMENT_NEW);
                if (newCommentAttribute != null) {
                    newComment = newCommentAttribute.getValue();
                }
                client.updateTicket(ticket, newComment);
                return new RepositoryResponse(ResponseKind.TASK_UPDATED, ticket
                        .getId()
                        + "");
            }
        } catch (Exception e) {
            MantisCorePlugin.log(e);
            throw new CoreException(MantisCorePlugin.toStatus(e));
        }
    }


    public MantisTicket getMantisTicket(TaskRepository repository, TaskData data) throws InvalidTicketException {
        MantisTicket ticket;
        if (data.getTaskId() == null || data.getTaskId().length() == 0) {
            ticket = new MantisTicket();
        } else {
            ticket = new MantisTicket(Integer.parseInt(data.getTaskId()));
        }


        Collection<TaskAttribute> attributes = data.getRoot().getAttributes().values();

        for (TaskAttribute attribute : attributes) {
            //			if (MantisAttributeMapper..isInternalAttribute(attribute.getId())) {
            //				// ignore
            //			} else if (!attribute.isReadOnly()) {
            if (attribute.getId().equals("project")) {
                ticket.putValue(attribute.getId(), attribute.getValue());
            }
            if (attribute.getId().equals(TaskAttribute.OPERATION) || attribute.getMetaData().isReadOnly()) {

            } else {
                ticket.putValue(attribute.getId(), attribute.getValue());
            }

            //			}
        }

        //		// handle operations
        //		TaskOperation operation = data.getRoot().get.getSelectedOperation();
        //		if (operation != null) {
        //			String action = operation.getKnobName();
        //			if ("assign_to".equals(action)) {
        //				ticket.putBuiltinValue(Key.ASSIGNED_TO, operation.getOptionSelection());
        //				ticket.putBuiltinValue(Key.STATUS, "assigned");
        //			}else if( "resolve_as".equals(action)) {
        //				ticket.putBuiltinValue(Key.RESOLUTION, operation.getOptionSelection());
        //				ticket.putBuiltinValue(Key.STATUS, "resolved");
        //			}
        //		}

        return ticket;
    }

    public TaskData getTaskData(TaskRepository repository, String taskId,
            IProgressMonitor monitor) throws CoreException {
        try {
            int id = Integer.parseInt(taskId);
            return downloadTaskData(repository, id);
        } catch (NumberFormatException e) {
            throw new CoreException(new Status(Status.ERROR,
                    MantisCorePlugin.PLUGIN_ID, "Task id must be numeric", e));
        }
    }

    protected TaskData downloadTaskData(TaskRepository repository, int id)
    throws CoreException {
        if (!MantisRepositoryConnector.hasRichEditor(repository)) {
            throw new CoreException(new Status(Status.ERROR,
                    MantisCorePlugin.PLUGIN_ID, 0,
                    "Does not have a rich editor", null));
        }

        TaskAttributeMapper attributeMapper = getAttributeMapper(repository);
        TaskData data = new TaskData(attributeMapper, connector
                .getConnectorKind(), repository.getRepositoryUrl(), Integer
                .toString(id));

        try {
            IMantisClient client = connector.getClientManager().getRepository(
                    repository);
            client.updateAttributes(new NullProgressMonitor(), false);
            MantisTicket ticket = client.getTicket(id);
            // createDefaultAttributes(data, client, true);
            // updateTaskData(repository, attributeMapper, data, client,
            // ticket);
            // createProjectSpecificAttributes(data, client);
            return createTaskDataFromTicket(client, repository, ticket,
                    new NullProgressMonitor());
        } catch (Exception e) {
            MantisCorePlugin.log(e);
            throw new CoreException(new Status(IStatus.ERROR,
                    MantisCorePlugin.PLUGIN_ID, 0, "Ticket download from "
                    + repository.getRepositoryUrl() + " for task " + id
                    + " failed, please see details.", e));
        }
    }

    public static void updateTaskData(TaskRepository repository,
            TaskAttributeMapper attributeMapper, TaskData data,
            IMantisClient client, MantisTicket ticket) throws CoreException {

        if (ticket.getCreated() != null) {
            data.getRoot().getAttribute(
                    MantisAttributeMapper.Attribute.DATE_SUBMITTED.getKey())
                    .setValue(
                            MantisUtils.toMantisTime(ticket.getCreated()) + "");
        }

        Date lastChanged = ticket.getLastChanged();

        Map<String, String> valueByKey = ticket.getValues();
        for (String key : valueByKey.keySet()) {
            if (valueByKey.get(key) != null) {
                getAttribute(data, key).setValue(valueByKey.get(key));
            }
        }

        addComments(data, ticket);
        addAttachments(repository, data, ticket);
        addRelationships(data, ticket);
        // addOperations(data, client, ticket);
        addOperation(data, ticket, "leave", "Keep current status");
        // addOperation(data, ticket, "assign_to", "Assign To:");
        addOperation(data, ticket, "resolve", "Resolve as ");

        if (lastChanged != null) {
            data.getRoot().getAttribute(
                    MantisAttributeMapper.Attribute.LAST_UPDATED.getKey())
                    .setValue(MantisUtils.toMantisTime(lastChanged) + "");
        }

    }


    // TODO Reuse Labels from BugzillaServerFacade
    private static void addOperation(TaskData data, MantisTicket ticket,
            String action, String label) {
        if (label != null) {
            TaskAttribute attribute = data.getRoot().createAttribute(
                    TaskAttribute.PREFIX_OPERATION + action);
            TaskOperation.applyTo(attribute, action, label);
            if ("resolve".equals(action)) {
                attribute.getMetaData().putValue(
                        TaskAttribute.META_ASSOCIATED_ATTRIBUTE_ID,
                        MantisAttributeMapper.Attribute.RESOLUTION.getKey());
            }
        }
    }

    // Resolve As Operation
    // MantisResolution[] options = client.getTicketResolutions();
    // TaskAttribute operationResolveAs = data.getRoot().createAttribute(
    // TaskAttribute.OPERATION);
    // for (MantisResolution op : options) {
    // operationResolveAs.putOption(op.getName(), op.getName());
    // }
    // operationResolveAs.setValue("fixed");
    // TaskOperation.applyTo(operationResolveAs, "resolve_as", "Resolve As");

    private static void addRelationships(TaskData data, MantisTicket ticket) {

        // relationships - support only child issues
        MantisRelationship[] relationsShips = ticket.getRelationships();
        for (MantisRelationship mantisRelationship : relationsShips) {

            int targetId = mantisRelationship.getTargetId();
            Attribute attribute = null;

            switch (mantisRelationship.getType()) {
                case PARENT:
                    attribute = Attribute.PARENT_OF;
                    break;

                case CHILD:
                    attribute = Attribute.CHILD_OF;
                    break;

                case DUPLICATE:
                    attribute = Attribute.DUPLICATE_OF;
                    break;

                case HAS_DUPLICATE:
                    attribute = Attribute.HAS_DUPLICATE;
                    break;

                case RELATED:
                    attribute = Attribute.RELATED_TO;
                    break;

                case UNKNOWN:
                default:
                    break;
            }

            if (attribute == null)
                continue;

            TaskAttribute taskAttribute = data.getRoot().getAttribute(attribute.getKey());
            if ( taskAttribute == null)
                taskAttribute = data.getRoot().createAttribute(attribute.getKey());

            taskAttribute.addValue(String.valueOf(targetId));


        }

    }


    private static void addAttachments(TaskRepository repository,
            TaskData data, MantisTicket ticket) {

        int i = 1;
        if (ticket.getAttachments() == null) {
            return;
        }

        for (MantisAttachment attachment : ticket.getAttachments()) {
            TaskAttribute attribute = data.getRoot().createAttribute(
                    TaskAttribute.PREFIX_ATTACHMENT + i);
            TaskAttachmentMapper taskAttachment = TaskAttachmentMapper
            .createFrom(attribute);
            taskAttachment.setFileName(attachment.getFilename());
            if (CONTEXT_ATTACHMENT_FILENAME.equals(attachment.getFilename())) {
                taskAttachment.setDescription(CONTEXT_ATTACHMENT_DESCRIPTION);
            } else if (CONTEXT_ATTACHMENT_FILENAME_LEGACY.equals(attachment
                    .getFilename())) {
                taskAttachment
                .setDescription(CONTEXT_ATTACHMENT_DESCRIPTION_LEGACY);
            } else {
                taskAttachment.setDescription(attachment.getFilename());
            }
            taskAttachment.setLength(Long.parseLong(Integer.toString(attachment
                    .getSize())));
            taskAttachment.setCreationDate(attachment.getCreated());
            taskAttachment.setUrl(MantisUtils.getRepositoryBaseUrl(repository
                    .getRepositoryUrl())
                    + IMantisClient.TICKET_ATTACHMENT_URL + attachment.getId());
            taskAttachment
            .setAttachmentId(Integer.toString(attachment.getId()));
            taskAttachment.applyTo(attribute);
            i++;
        }
    }

    private static void addComments(TaskData data, MantisTicket ticket) {
        int i = 1;
        if (ticket.getComments() == null) {
            return;
        }
        for (MantisComment comment : ticket.getComments()) {
            TaskAttribute attribute = data.getRoot().createAttribute(
                    TaskAttribute.PREFIX_COMMENT + i);
            TaskCommentMapper taskComment = TaskCommentMapper
            .createFrom(attribute);
            String report = comment.getReporter();
            if (report == null) {
                taskComment.setAuthor(data.getAttributeMapper()
                        .getTaskRepository().createPerson("unknown"));

            } else {
                taskComment.setAuthor(data.getAttributeMapper()
                        .getTaskRepository()
                        .createPerson(comment.getReporter()));
            }
            taskComment.setNumber(i);
            String commentText = comment.getText();
            taskComment.setText(commentText);
            taskComment.setCreationDate(comment.getDateSubmitted());
            taskComment.applyTo(attribute);
            i++;
        }
    }

    public static void createDefaultAttributes(TaskData data, IMantisClient client, boolean existingTask) throws CoreException {
        createDefaultAttributes(data, client, null, existingTask);
    }

    public static void createDefaultAttributes(TaskData data,
            IMantisClient client, MantisTicket ticket, boolean existingTask) throws CoreException {
        if (existingTask) {
        }

        // The order here is important as it controls how it appears in the
        // Editor.

        createAttribute(data, MantisAttributeMapper.Attribute.PROJECT, null);
        createAttribute(data, MantisAttributeMapper.Attribute.CATEGORY, null);

        createAttribute(data, MantisAttributeMapper.Attribute.RESOLUTION,
                client.getTicketResolutions(), client.getTicketResolutions()[0]
                                                                             .getName());
        createAttribute(data, MantisAttributeMapper.Attribute.STATUS, client
                .getTicketStatus(), client.getTicketStatus()[0].getName());
        createAttribute(data, MantisAttributeMapper.Attribute.PRIORITY, client
                .getPriorities(), client.getPriorities()[0].getName());
        createAttribute(data, MantisAttributeMapper.Attribute.SEVERITY, client
                .getSeverities(), client.getSeverities()[0].getName());
        createAttribute(data, MantisAttributeMapper.Attribute.REPRODUCIBILITY,
                client.getReproducibility(), client.getReproducibility()[0]
                                                                         .getName());
        createAttribute(data, MantisAttributeMapper.Attribute.VERSION, null);
        createAttribute(data, MantisAttributeMapper.Attribute.FIXED_IN, null);

        createAttribute(data, MantisAttributeMapper.Attribute.PROJECTION,
                client.getProjection(), client.getProjection()[0].getName());
        createAttribute(data, MantisAttributeMapper.Attribute.ETA, client
                .getETA(), client.getETA()[0].getName());

        createAttribute(data, MantisAttributeMapper.Attribute.DESCRIPTION);
        createAttribute(data,
                MantisAttributeMapper.Attribute.STEPS_TO_REPRODUCE);
        createAttribute(data, MantisAttributeMapper.Attribute.ADDITIONAL_INFO);
        createAttribute(data, MantisAttributeMapper.Attribute.NEW_COMMENT, null);

        createAttribute(data, MantisAttributeMapper.Attribute.VIEW_STATE,
                client.getViewState(), client.getViewState()[0].getName());

        String[] projectUsers = null;
        if (ticket != null) {
            projectUsers = client.getUsers(ticket.getValue(MantisTicket.Key.PROJECT));
        }

        createAttribute(data, MantisAttributeMapper.Attribute.ASSIGNED_TO, projectUsers);
        if (existingTask) {
            createAttribute(data, MantisAttributeMapper.Attribute.REPORTER, projectUsers);
        }
        createAttribute(data, MantisAttributeMapper.Attribute.SUMMARY);
        createAttribute(data, MantisAttributeMapper.Attribute.DATE_SUBMITTED);
        createAttribute(data, MantisAttributeMapper.Attribute.LAST_UPDATED);

        // operations
        data.getRoot().createAttribute(TaskAttribute.OPERATION).getMetaData()
        .setType(TaskAttribute.TYPE_OPERATION);

    }

    public static void createProjectSpecificAttributes(TaskData data,
            IMantisClient client) {

        try {
            // categories
            TaskAttribute attr = getAttribute(data,
                    MantisAttributeMapper.Attribute.CATEGORY.getKey());
            attr.clearOptions();
            boolean first = MantisUtils.isEmpty(attr.getValue());
            for (MantisProjectCategory mp : client.getProjectCategories(data
                    .getRoot().getAttribute(
                            MantisAttributeMapper.Attribute.PROJECT.getKey())
                            .getValue())) {
                if (first) {
                    attr.setValue(mp.toString());
                    first = false;
                }
                attr.putOption(mp.toString(), mp.toString());
            }

            // versions
            TaskAttribute repInVerAttr = getAttribute(data,
                    MantisAttributeMapper.Attribute.VERSION.getKey());
            repInVerAttr.clearOptions();
            repInVerAttr.putOption("none", ""); // empty option

            TaskAttribute fixInVerAttr = getAttribute(data,
                    MantisAttributeMapper.Attribute.FIXED_IN.getKey());
            fixInVerAttr.clearOptions();
            fixInVerAttr.putOption("none", "");// Add empty option

            for (MantisVersion v : client.getVersions(getAttribute(data,
                    MantisAttributeMapper.Attribute.PROJECT.getKey())
                    .getValue())) {

                /*
                 * Only display released versions for the reported in field,
                 * matches the behaviour of the mantis web interface.
                 */
                if (v.isReleased())
                    repInVerAttr.putOption(v.getName(), v.getName());
                fixInVerAttr.putOption(v.getName(), v.getName());
            }

            /* If the value is empty then the issue has not yet been fixed */
            if (MantisUtils.isEmpty(fixInVerAttr.getValue()))
                fixInVerAttr.setValue("none");

            if (MantisUtils.isEmpty(repInVerAttr.getValue()))
                repInVerAttr.setValue("none");

        } catch (MantisException ex) {
            MantisCorePlugin.log(new Status(Status.ERROR,
                    MantisCorePlugin.PLUGIN_ID, 0, ex.getMessage(), ex));
        }
    }

    private static TaskAttribute createAttribute(TaskData data,
            MantisAttributeMapper.Attribute attribute, Object[] values,
            boolean allowEmtpy) {
        TaskAttribute attr = data.getRoot().createAttribute(attribute.getKey());
        attr.getMetaData().setReadOnly(attribute.isReadOnly()).setLabel(
                attribute.toString()).setKind(attribute.getKind()).setType(
                        attribute.getType());
        if (values != null && values.length > 0) {
            if (allowEmtpy) {
                attr.putOption("", "");
            }
            for (Object value : values) {
                attr.putOption(value.toString(), value.toString());
            }
        }
        return attr;
    }

    private static TaskAttribute createAttribute(TaskData data,
            MantisAttributeMapper.Attribute attribute) {
        TaskAttribute attr = data.getRoot().createAttribute(attribute.getKey());
        attr.getMetaData().setReadOnly(attribute.isReadOnly()).setLabel(
                attribute.toString()).setKind(attribute.getKind()).setType(
                        attribute.getType());
        return attr;
    }

    private static TaskAttribute createAttribute(TaskData data,
            MantisAttributeMapper.Attribute attribute, Object[] values,
            String defaultValue) {
        TaskAttribute rta = createAttribute(data, attribute, values, false);
        rta.setValue(defaultValue);
        return rta;
    }

    private static TaskAttribute getAttribute(TaskData data, String key) {
        TaskAttribute attribute = data.getRoot().getAttribute(key);
        if (attribute == null) {
            attribute = data.getRoot().createAttribute(key);
        }
        return attribute;
    }

    private static TaskAttribute createAttribute(TaskData data,
            MantisAttributeMapper.Attribute attribute, Object[] values) {
        return createAttribute(data, attribute, values, false);
    }

    //
    // public String postTaskData(TaskRepository repository, RepositoryTaskData
    // taskData, IProgressMonitor monitor) throws CoreException {
    // try {
    // MantisTicket ticket =
    // MantisRepositoryConnector.getMantisTicket(repository, taskData);
    // IMantisClient server = ((MantisRepositoryConnector)
    // connector).getClientManager().getRepository(repository);
    // if (taskData.isNew()) {
    // int id = server.createTicket(ticket);
    // return Integer.toString(id);
    // } else {
    //
    // String comment = taskData.getNewComment();
    // // XXX: new comment is now an attribute
    // taskData.removeAttribute(RepositoryTaskAttribute.COMMENT_NEW);
    // server.updateTicket(ticket, comment);
    // return null;
    // }
    // } catch (Exception e) {
    // MantisCorePlugin.log(e);
    // throw new CoreException(MantisCorePlugin.toStatus(e));
    // }
    // }
    //
    //

    // @Override
    // public Set<String> getSubTaskIds(RepositoryTaskData taskData) {
    //
    // RepositoryTaskAttribute taskAttribute =
    // taskData.getAttribute(MantisAttribute.RELATIONSHIPS.getMantisKey());
    // if ( taskAttribute == null)
    // return Collections.<String>emptySet();
    //
    // return new HashSet<String>(taskAttribute.getValues());
    // }
    //

    /**
     * Given a Mantis Ticket create the necessary TaskData object
     * 
     * @param IMantisClient
     *            client
     * @param TaskRepository
     *            repository
     * @param MantisTicket
     *            ticket
     * @param IProgressMontiro
     *            monitor
     * 
     * @since 3.0
     */
    public TaskData createTaskDataFromTicket(IMantisClient client,
            TaskRepository repository, MantisTicket ticket,
            IProgressMonitor monitor) throws CoreException {
        TaskData taskData = new TaskData(getAttributeMapper(repository),
                MantisCorePlugin.REPOSITORY_KIND,
                repository.getRepositoryUrl(), ticket.getId() + "");
        try {
            createDefaultAttributes(taskData, client, ticket, true);
            updateTaskData(repository, getAttributeMapper(repository),
                    taskData, client, ticket);
            createProjectSpecificAttributes(taskData, client);

            if (!MantisRepositoryConnector.hasRichEditor(repository)) {
                // updateTaskDataFromTicket(taskData, ticket, client);
                taskData.setPartial(true);
            }
            return taskData;
        } catch (OperationCanceledException e) {
            throw e;
        } catch (Exception e) {
            // TODO catch TracException
            throw new CoreException(MantisCorePlugin.toStatus(e));
        }
    }

    /**
     * Updates attributes of <code>taskData</code> from <code>ticket</code>.
     */
    public void updateTaskDataFromTicket(TaskData taskData,
            MantisTicket ticket, IMantisClient client) {

        TaskAttribute attributeSummary = taskData.getRoot().getAttribute(
                TaskAttribute.SUMMARY);
        if (ticket.getValue(Key.SUMMARY) != null) {
            attributeSummary.setValue(ticket.getValue(Key.SUMMARY));
        }

        TaskAttribute attributeCompletionDate = taskData.getRoot()
        .getAttribute(TaskAttribute.DATE_COMPLETION);

        if (MantisUtils.isCompleted(ticket.getValue(Key.STATUS))) {
            attributeCompletionDate
            .setValue(ticket.getLastChanged().toString());
        } else {
            attributeCompletionDate.setValue(null);
        }

        String priority = ticket.getValue(Key.PRIORITY);
        TaskAttribute attributePriority = taskData.getRoot().getAttribute(
                TaskAttribute.PRIORITY);
        attributePriority.setValue(MantisPriorityLevel.getMylynPriority(
                priority).toString());

        TaskAttribute attributeCreationDate = taskData.getRoot().getAttribute(
                TaskAttribute.DATE_CREATION);
        if (ticket.getCreated() != null) {
            attributeCreationDate.setValue(ticket.getCreated().toString());
        }
    }
}