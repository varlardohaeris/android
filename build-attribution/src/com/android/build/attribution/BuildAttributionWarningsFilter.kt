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
package com.android.build.attribution

import com.android.build.attribution.data.SuppressedWarnings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.project.Project

@State(name = "BuildAttributionWarningsFilter")
class BuildAttributionWarningsFilter : PersistentStateComponent<SuppressedWarnings> {
  private var suppressedWarnings: SuppressedWarnings = SuppressedWarnings()

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BuildAttributionWarningsFilter {
      return ServiceManager.getService(project, BuildAttributionWarningsFilter::class.java)
    }
  }

  // TODO(b/138362804): use task class name instead of gradle's task name, to avoid having to suppress tasks per variant
  fun applyAlwaysRunTaskFilter(taskName: String, pluginDisplayName: String): Boolean {
    return !suppressedWarnings.alwaysRunTasks.contains(Pair(taskName, pluginDisplayName))
  }

  fun applyPluginSlowingConfigurationFilter(pluginDisplayName: String): Boolean {
    return !suppressedWarnings.pluginsSlowingConfiguration.contains(pluginDisplayName)
  }

  fun applyNonIncrementalAnnotationProcessorFilter(annotationProcessorClassName: String): Boolean {
    return !suppressedWarnings.nonIncrementalAnnotationProcessors.contains(annotationProcessorClassName)
  }

  fun suppressAlwaysRunTaskWarning(taskName: String, pluginDisplayName: String) {
    suppressedWarnings.alwaysRunTasks.add(Pair(taskName, pluginDisplayName))
  }

  fun suppressPluginSlowingConfigurationWarning(pluginDisplayName: String) {
    suppressedWarnings.pluginsSlowingConfiguration.add(pluginDisplayName)
  }

  fun suppressNonIncrementalAnnotationProcessorWarning(annotationProcessorClassName: String) {
    suppressedWarnings.nonIncrementalAnnotationProcessors.add(annotationProcessorClassName)
  }

  fun unsuppressAlwaysRunTaskWarning(taskName: String, pluginDisplayName: String) {
    suppressedWarnings.alwaysRunTasks.remove(Pair(taskName, pluginDisplayName))
  }

  fun unsuppressPluginSlowingConfigurationWarning(pluginDisplayName: String) {
    suppressedWarnings.pluginsSlowingConfiguration.remove(pluginDisplayName)
  }

  fun unsuppressNonIncrementalAnnotationProcessorWarning(annotationProcessorClassName: String) {
    suppressedWarnings.nonIncrementalAnnotationProcessors.remove(annotationProcessorClassName)
  }

  override fun getState(): SuppressedWarnings? {
    return suppressedWarnings
  }

  override fun loadState(state: SuppressedWarnings) {
    suppressedWarnings = state
  }
}