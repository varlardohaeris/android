/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.explorer.adbimpl;

import com.android.ddmlib.*;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class AdbShellCommandsUtil {
  @NotNull private static final Logger LOGGER = Logger.getInstance(AdbShellCommandsUtil.class);
  @NotNull private static final String ERROR_LINE_MARKER = "ERR-ERR-ERR-ERR";
  @NotNull private static final String COMMAND_ERROR_CHECK_SUFFIX = " || echo " + ERROR_LINE_MARKER;

  public static AdbShellCommandResult executeCommand(@NotNull IDevice device, @NotNull String command)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    return executeCommandImpl(device, command, true);
  }

  public static AdbShellCommandResult executeCommandNoErrorCheck(@NotNull IDevice device, @NotNull String command)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    return executeCommandImpl(device, command, false);
  }

  private static AdbShellCommandResult executeCommandImpl(@NotNull IDevice device, @NotNull String command, boolean errorCheck)
    throws TimeoutException, AdbCommandRejectedException, ShellCommandUnresponsiveException, IOException {
    long startTime = System.nanoTime();

    List<String> commandOutput = new ArrayList<>();
    // Adding the " || echo xxx" command to the command allows us to detect non-zero status code
    // from the command by analysing the output and looking for the "xxx" marker.
    String fullCommand = errorCheck ? String.format("%s%s", command, COMMAND_ERROR_CHECK_SUFFIX) : command;
    device.executeShellCommand(fullCommand, new MultiLineReceiver() {
      @Override
      public void processNewLines(@NotNull String[] lines) {
        Arrays.stream(lines).forEach(commandOutput::add);
      }

      @Override
      public boolean isCancelled() {
        return false;
      }
    });

    // Look for error marker in the last 2 output lines
    boolean isError = false;
    if (errorCheck &&
        commandOutput.size() >= 2 &&
        Objects.equals(commandOutput.get(commandOutput.size() - 2), ERROR_LINE_MARKER) &&
        Objects.equals(commandOutput.get(commandOutput.size() - 1), "")) {
      isError = true;
      commandOutput.remove(commandOutput.get(commandOutput.size() - 1));
      commandOutput.remove(commandOutput.get(commandOutput.size() - 1));
    }

    if (LOGGER.isTraceEnabled()) {
      long endTime = System.nanoTime();
      LOGGER.trace(String.format("command took %,d ms to execute: %s", (endTime - startTime) / 1_000_000, command));
    }

    return new AdbShellCommandResult(command, commandOutput, isError);
  }
}