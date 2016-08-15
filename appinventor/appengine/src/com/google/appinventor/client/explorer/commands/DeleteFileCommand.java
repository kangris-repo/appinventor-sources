// -*- mode: java; c-basic-offset: 2; -*-
// Copyright 2009-2011 Google, All Rights reserved
// Copyright 2011-2012 MIT, All rights reserved
// Released under the Apache License, Version 2.0
// http://www.apache.org/licenses/LICENSE-2.0

package com.google.appinventor.client.explorer.commands;

import com.google.appinventor.client.Ode;
import com.google.appinventor.client.OdeAsyncCallback;
import com.google.appinventor.client.explorer.project.Project;
import static com.google.appinventor.client.Ode.MESSAGES;
import com.google.appinventor.shared.rpc.project.ProjectNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidBlocksNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidFormNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidSourceNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidTaskNode;
import com.google.appinventor.shared.rpc.project.youngandroid.YoungAndroidYailNode;
import com.google.gwt.user.client.Window;

import java.util.ArrayList;

/**
 * Command for deleting files.
 *
 */
public class DeleteFileCommand extends ChainableCommand {
  /**
   * Creates a new command for deleting a file.
   */
  public DeleteFileCommand() {
    super(null); // no next command
  }

  @Override
  public boolean willCallExecuteNextCommand() {
    return true;
  }

  @Override
  public void execute(final ProjectNode node) {
    if (deleteConfirmation()) {
      final Ode ode = Ode.getInstance();

      if (node instanceof YoungAndroidSourceNode) {
        // node could be either a YoungAndroidFormNode or a YoungAndroidTaskNode or a YoungAndroidBlocksNode.
        // Before we delete the form, we need to close both the form editor and the blocks editor
        // (in the browser).
        final String qualifiedFormName = ((YoungAndroidSourceNode) node).getQualifiedName();
        final String formFileId = YoungAndroidFormNode.getFormFileId(qualifiedFormName);
        final String taskFileId = YoungAndroidTaskNode.getTaskFileId(qualifiedFormName);
        final String blocksFileId = YoungAndroidBlocksNode.getBlocklyFileId(qualifiedFormName);
        final String yailFileId = YoungAndroidYailNode.getYailFileId(qualifiedFormName);
        final long projectId = node.getProjectId();
        ArrayList<String> fileIds = new ArrayList<String>(2);
        for (ProjectNode sourceNode : node.getProjectRoot().getAllSourceNodes()) {
          if (sourceNode.getFileId().equals(formFileId) || sourceNode.getFileId().equals(taskFileId) ||
              sourceNode.getFileId().equals(blocksFileId)) {
              fileIds.add(sourceNode.getFileId());
          }
        }
        ode.getEditorManager().closeFileEditors(projectId, fileIds.toArray(new String[0]));

        // When we tell the project service to delete either the form (.scm) file or the task (.tsk) or the blocks
        // (.bky) file, it will delete all of them, including the yail (.yail) file.
        ode.getProjectService().deleteFile(ode.getSessionId(), projectId, node.getFileId(),
            new OdeAsyncCallback<Long>(
        // message on failure
            MESSAGES.deleteFileError()) {
          @Override
          public void onSuccess(Long date) {
            // Remove all related nodes (form, blocks, yail) from the project.
            Project project = getProject(node);
            for (ProjectNode sourceNode : node.getProjectRoot().getAllSourceNodes()) {
              if (sourceNode.getFileId().equals(formFileId) ||
                  sourceNode.getFileId().equals(taskFileId) ||
                  sourceNode.getFileId().equals(blocksFileId) ||
                  sourceNode.getFileId().equals(yailFileId)) {
                project.deleteNode(sourceNode);
              }
            }
            ode.getDesignToolbar().removeScreen(project.getProjectId(), 
                ((YoungAndroidSourceNode) node).getContextName());
            ode.updateModificationDate(projectId, date);
            executeNextCommand(node);
          }

          @Override
          public void onFailure(Throwable caught) {
            super.onFailure(caught);
            executionFailedOrCanceled();
          }
        });
      } else {  // asset file
        ode.getProjectService().deleteFile(ode.getSessionId(),
            node.getProjectId(), node.getFileId(),
            new OdeAsyncCallback<Long>(
                // message on failure
                MESSAGES.deleteFileError()) {
              @Override
              public void onSuccess(Long date) {
                getProject(node).deleteNode(node);
                ode.updateModificationDate(node.getProjectId(), date);
                executeNextCommand(node);
              }

              @Override
              public void onFailure(Throwable caught) {
                super.onFailure(caught);
                executionFailedOrCanceled();
              }
            });
      }
    } else {
      executionFailedOrCanceled();
    }
  }

  /**
   * Shows a confirmation dialog.
   *
   * @return {@code true} if the delete file command should be executed or
   *         {@code false} if it should be canceled
   */
  protected boolean deleteConfirmation() {
    return Window.confirm(MESSAGES.reallyDeleteFile());
  }
}
