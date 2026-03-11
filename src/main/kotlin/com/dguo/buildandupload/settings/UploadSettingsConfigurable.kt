package com.dguo.buildandupload.settings

import com.intellij.openapi.options.SearchableConfigurable
import javax.swing.JComponent

class UploadSettingsConfigurable : SearchableConfigurable {
	private var component: UploadSettingsComponent? = null

	override fun getId(): String = "com.dguo.buildandupload.settings"

	override fun getDisplayName(): String = "Build & Upload"

	override fun createComponent(): JComponent {
		val newComponent = UploadSettingsComponent()
		component = newComponent
		return newComponent.panel
	}

	override fun getPreferredFocusedComponent(): JComponent? = component?.getPreferredFocusedComponent()

	override fun isModified(): Boolean {
		val comp = component ?: return false
		val settings = UploadSettingsState.getInstance()
		return comp.executablePath != settings.executablePath ||
			comp.additionalArgs != settings.additionalArgs ||
			comp.workingDirectory != settings.workingDirectory
	}

	override fun apply() {
		val comp = component ?: return
		val settings = UploadSettingsState.getInstance()
		settings.executablePath = comp.executablePath
		settings.additionalArgs = comp.additionalArgs
		settings.workingDirectory = comp.workingDirectory
	}

	override fun reset() {
		val comp = component ?: return
		val settings = UploadSettingsState.getInstance()
		comp.executablePath = settings.executablePath
		comp.additionalArgs = settings.additionalArgs
		comp.workingDirectory = settings.workingDirectory
	}

	override fun disposeUIResources() {
		component = null
	}
}
