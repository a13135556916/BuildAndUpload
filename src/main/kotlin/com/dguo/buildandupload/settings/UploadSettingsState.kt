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
		var workingDirectory: String = "",
		var deleteExecutablePath: String = "",
		var deleteAdditionalArgs: String = "",
		var manifestScriptPath: String = "tasks/collect_deploy_manifest.sh",
		var manifestWorkingDirectory: String = "",
		var manifestBaseCommit: String = "",
		var manifestHeadRef: String = "HEAD",
		var deployedCommitFilePath: String = ""
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

	var deleteExecutablePath: String
		get() = state.deleteExecutablePath
		set(value) {
			state.deleteExecutablePath = value
		}

	var deleteAdditionalArgs: String
		get() = state.deleteAdditionalArgs
		set(value) {
			state.deleteAdditionalArgs = value
		}

	var manifestScriptPath: String
		get() = state.manifestScriptPath
		set(value) {
			state.manifestScriptPath = value
		}

	var manifestWorkingDirectory: String
		get() = state.manifestWorkingDirectory
		set(value) {
			state.manifestWorkingDirectory = value
		}

	var manifestBaseCommit: String
		get() = state.manifestBaseCommit
		set(value) {
			state.manifestBaseCommit = value
		}

	var manifestHeadRef: String
		get() = state.manifestHeadRef
		set(value) {
			state.manifestHeadRef = value
		}

	var deployedCommitFilePath: String
		get() = state.deployedCommitFilePath
		set(value) {
			state.deployedCommitFilePath = value
		}
}
