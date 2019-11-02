/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.build.attribution.ui

import com.android.build.attribution.ui.data.CriticalPathPluginUiData
import com.android.build.attribution.ui.data.TaskUiData
import com.android.build.attribution.ui.data.TimeWithPercentage
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.Icon



fun TimeWithPercentage.durationString() = "%.3f s".format(timeMs.toDouble() / 1000)

fun TimeWithPercentage.percentageString() = "%.1f%%".format(percentage)

fun colorIcon(color: Color): Icon = JBUI.scale(ColorIcon(12, color))

fun emptyIcon(): Icon = EmptyIcon.ICON_16

fun getTaskIcon(taskData: TaskUiData): Icon = emptyIcon()

fun getPluginIcon(pluginData: CriticalPathPluginUiData): Icon = emptyIcon()