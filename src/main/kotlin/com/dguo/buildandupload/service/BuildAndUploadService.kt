package com.dguo.buildandupload.service

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.dguo.buildandupload.settings.UploadSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import java.awt.event.InputEvent
import java.nio.file.Path

@Service(Service.Level.PROJECT)
class BuildAndUploadService(private val project: Project) {
	companion object {
		private val DEPLOYMENT_ACTION_IDS = listOf(
			"PublishGroup.Upload",
			"UploadToDefaultServer",
			"PublishGroup.UploadTo",
			"UploadToServer"
		)

		fun getInstance(project: Project): BuildAndUploadService =
			project.getService(BuildAndUploadService::class.java)
	}

	fun upload(files: List<Path>) {
		if (files.isEmpty()) {
			BuildAndUploadNotifier.error(project, "没有可上传的文件。")
			return
		}

		val settings = UploadSettingsState.getInstance()
		val executable = settings.executablePath.trim()
		if (executable.isBlank()) {
			uploadViaDeployment(files)
			return
		}

		val parsedArgs = ParametersListUtil.parse(settings.additionalArgs)
		val workDir = settings.workingDirectory.takeIf { it.isNotBlank() }
		ApplicationManager.getApplication().executeOnPooledThread {
			try {
				for (file in files) {
					val commandLine = GeneralCommandLine(executable)
						.withWorkDirectory(workDir)
						.withParameters(parsedArgs + file.toAbsolutePath().toString())
					val result = CapturingProcessHandler(commandLine).runProcess(UploadSettingsState.PROCESS_TIMEOUT_MS)
					if (result.exitCode != 0) {
						val details = result.stderr.ifBlank { result.stdout }
						throw RuntimeException("${file.fileName} 上传失败：$details")
					}
				}
				BuildAndUploadNotifier.info(project, "上传完成，共 ${files.size} 个文件。")
			} catch (ex: Exception) {
				BuildAndUploadNotifier.error(project, "上传失败：${ex.message}")
			}
		}
	}

	private fun uploadViaDeployment(files: List<Path>) {
		val fileSystem = LocalFileSystem.getInstance()
		val virtualFiles = files.mapNotNull { fileSystem.refreshAndFindFileByNioFile(it) }
		if (virtualFiles.isEmpty()) {
			BuildAndUploadNotifier.error(project, "无法定位需要上传的文件。")
			return
		}

		val action = resolveDeploymentAction()
		if (action == null) {
			BuildAndUploadNotifier.error(project, "当前 IDE 未提供 Deployment 上传功能，请安装并启用相关插件。")
			return
		}

		val dataContext: DataContext = SimpleDataContext.builder()
			.add(CommonDataKeys.PROJECT, project)
			.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, virtualFiles.toTypedArray())
			.add(CommonDataKeys.VIRTUAL_FILE, virtualFiles.first())
			.build()

		ApplicationManager.getApplication().invokeLater {
			try {
				val event = AnActionEvent.createFromAnAction(action, null as InputEvent?, "unknown", dataContext)
				action.actionPerformed(event)
				BuildAndUploadNotifier.info(project, "已调用 IDE 部署上传 ${virtualFiles.size} 个文件，请查看 Deployment 日志。")
			} catch (ex: Exception) {
				BuildAndUploadNotifier.error(project, "调用 IDE 部署上传失败：${ex.message}")
			}
		}
	}

	private fun resolveDeploymentAction(): AnAction? {
		val actionManager = ActionManager.getInstance()
		return DEPLOYMENT_ACTION_IDS.asSequence()
			.mapNotNull(actionManager::getAction)
			.firstOrNull()
	}
}
