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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.property.panel.api.ControlType
import com.android.tools.property.panel.api.ControlTypeProvider
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.EnumSupport
import com.android.tools.property.panel.api.EnumSupportProvider
import com.android.tools.property.panel.api.PropertiesView
import com.android.tools.property.panel.api.Watermark

private const val VIEW_NAME = "LayoutInspectorPropertyEditor"
private const val WATERMARK_MESSAGE = "No view selected."
private const val WATERMARK_ACTION_MESSAGE = "Select a view in the Component Tree."

class InspectorPropertiesView(model: InspectorPropertiesModel) : PropertiesView<InspectorPropertyItem>(VIEW_NAME, model) {

  private val enumSupportProvider = object : EnumSupportProvider<InspectorPropertyItem> {
    // TODO: Make this a 1 liner
    override fun invoke(property: InspectorPropertyItem): EnumSupport? = null
  }

  private val controlTypeProvider = object : ControlTypeProvider<InspectorPropertyItem> {
    // TODO: Make this a 1 liner
    override fun invoke(property: InspectorPropertyItem): ControlType = ControlType.TEXT_EDITOR
  }

  private val editorProvider = EditorProvider.create(enumSupportProvider, controlTypeProvider)

  init {
    watermark = Watermark(WATERMARK_MESSAGE, WATERMARK_ACTION_MESSAGE, "")
    val tab = addTab("")
    tab.builders.add(InspectorTableBuilder(model, controlTypeProvider, editorProvider))
  }
}
