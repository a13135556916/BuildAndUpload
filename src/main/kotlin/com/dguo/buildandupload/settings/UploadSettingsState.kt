package com.dguo.buildandupload.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(name = "BuildAndUploadSettings", storages = [Storage("BuildAndUploadSettings.xml")])
class UploadSettingsState : PersistentStateComponent<UploadSettingsState.Data> {
	data class Data(
		var executablePath: String = "",
		var additionalArgs: String = "",
		var workingDirectory: String = ""
	)

	companion object {
		const val PROCESS_TIMEOUT_MS: Int = 60000

		fun getInstance(): UploadSettingsState =
			ApplicationManager.getApplication().getService(UploadSettingsState::class.java)
	}

	private var state = Data()

	override fun getState(): Data = state

	override fun loadState(state: Data) {
		this.state = state
	}

	var executablePath: String
		get() = state.executablePath
		set(value) {
			state.executablePath = value
		}

	var additionalArgs: String
		get() = state.additionalArgs
		set(value) {
			state.additionalArgs = value
		}

	var workingDirectory: String
		get() = state.workingDirectory
		set(value) {
			state.workingDirectory = value
		}
}
