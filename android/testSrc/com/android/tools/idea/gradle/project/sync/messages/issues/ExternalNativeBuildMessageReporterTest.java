/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.project.sync.messages.issues;

import com.android.builder.model.SyncIssue;
import com.android.ide.common.blame.Message;
import com.android.ide.common.blame.SourceFilePosition;
import com.android.ide.common.blame.SourcePosition;
import com.android.tools.idea.gradle.output.parser.BuildOutputParser;
import com.android.tools.idea.gradle.project.sync.messages.SyncMessage;
import com.android.tools.idea.gradle.project.sync.messages.reporter.SyncMessageReporterStub;
import com.android.tools.idea.gradle.service.notification.errors.AbstractSyncErrorHandler;
import com.android.tools.idea.gradle.util.PositionInFile;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.android.builder.model.SyncIssue.TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION;
import static com.android.ide.common.blame.Message.Kind.ERROR;
import static com.android.ide.common.blame.Message.Kind.WARNING;
import static com.android.tools.idea.gradle.util.GradleUtil.getGradleBuildFile;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ExternalNativeBuildMessageReporter}.
 */
public class ExternalNativeBuildMessageReporterTest extends AndroidGradleTestCase {
  private SyncIssue mySyncIssue;
  private SyncMessageReporterStub myReporterStub;
  private BuildOutputParser myOutputParser;
  private SyncErrorHandlerStub myErrorHandler;
  private ExternalNativeBuildMessageReporter myReporter;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mySyncIssue = mock(SyncIssue.class);
    myReporterStub = new SyncMessageReporterStub(getProject());
    myOutputParser = mock(BuildOutputParser.class);
    myErrorHandler = new SyncErrorHandlerStub();
    AbstractSyncErrorHandler[] errorHandlers = {myErrorHandler};
    myReporter = new ExternalNativeBuildMessageReporter(myReporterStub, myOutputParser, errorHandlers);
  }

  public void testGetSupportedIssueType() {
    assertEquals(TYPE_EXTERNAL_NATIVE_BUILD_PROCESS_EXCEPTION, myReporter.getSupportedIssueType());
  }

  public void testReportWithWarning() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    String nativeToolOutput = "Failed to compile something";
    when(mySyncIssue.getData()).thenReturn(nativeToolOutput);

    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertNotNull(buildFile);

    int line = 6;
    int column = 8;
    SourcePosition sourcePosition = new SourcePosition(line, column, 0);
    SourceFilePosition sourceFilePosition = new SourceFilePosition(virtualToIoFile(buildFile), sourcePosition);
    Message compilerMessage = new Message(WARNING, nativeToolOutput, sourceFilePosition);

    List<Message> compilerMessages = Lists.newArrayList(compilerMessage);
    when(myOutputParser.parseGradleOutput(nativeToolOutput)).thenReturn(compilerMessages);

    myReporter.report(mySyncIssue, appModule, buildFile);

    SyncMessage message = myReporterStub.getReportedMessage();
    assertNotNull(message);

    String[] text = message.getText();
    assertThat(text).hasLength(1);
    assertEquals(nativeToolOutput, text[0]);

    PositionInFile position = message.getPosition();
    assertNotNull(position);
    assertEquals(buildFile, position.file);
    assertEquals(line, position.line);
    assertEquals(column, position.column);

    assertThat(message.getQuickFixes()).isEmpty();

    assertFalse(myErrorHandler.isInvoked());
  }

  public void testReportWithError() throws Exception {
    loadSimpleApplication();
    Module appModule = myModules.getAppModule();

    String nativeToolOutput = "Failed to compile something";
    when(mySyncIssue.getData()).thenReturn(nativeToolOutput);

    VirtualFile buildFile = getGradleBuildFile(appModule);
    assertNotNull(buildFile);

    int line = 6;
    int column = 8;
    SourcePosition sourcePosition = new SourcePosition(line, column, 0);
    SourceFilePosition sourceFilePosition = new SourceFilePosition(virtualToIoFile(buildFile), sourcePosition);
    Message compilerMessage = new Message(ERROR, nativeToolOutput, sourceFilePosition);

    List<Message> compilerMessages = Lists.newArrayList(compilerMessage);
    when(myOutputParser.parseGradleOutput(nativeToolOutput)).thenReturn(compilerMessages);

    myReporter.report(mySyncIssue, appModule, buildFile);

    assertNull(myReporterStub.getReportedMessage());

    NotificationData notification = myReporterStub.getReportedNotification();
    assertNotNull(notification);

    assertTrue(myErrorHandler.isInvoked());
  }

  private static class SyncErrorHandlerStub extends AbstractSyncErrorHandler {
    private boolean myInvoked;

    @Override
    public boolean handleError(@NotNull List<String> message,
                               @NotNull ExternalSystemException error,
                               @NotNull NotificationData notification,
                               @NotNull Project project) {
      myInvoked = true;
      return true;
    }

    boolean isInvoked() {
      return myInvoked;
    }
  }
}