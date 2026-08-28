package com.dguo.buildandupload.service

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.dguo.buildandupload.settings.UploadSettingsState
import com.dguo.buildandupload.util.ClassFileLocator
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.execution.ParametersListUtil
import java.awt.event.InputEvent
import java.nio.file.Path
import java.util.LinkedHashSet

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

	fun uploadManifest(entries: List<DeployManifestEntry>, onSuccess: (() -> Unit)? = null) {
		if (entries.isEmpty()) {
			BuildAndUploadNotifier.error(project, "部署清单中没有可上传的文件。")
			return
		}

		val javaFiles = collectManifestJavaFiles(entries)
		if (javaFiles.isNotEmpty()) {
			JavaCompileService.compile(
				project = project,
				javaFiles = javaFiles,
				startMessage = "按部署清单开始编译 ${javaFiles.size} 个 Java 文件 ...",
				successMessage = "部署清单中的 Java 编译完成，开始上传生成的 .class。"
			) {
				val uploadFiles = resolveManifestUploadFiles(entries)
				if (uploadFiles.isEmpty()) {
					BuildAndUploadNotifier.error(project, "部署清单中的 Java 已编译，但未找到可上传的 .class 文件。")
					return@compile
				}
				uploadViaDeployment(uploadFiles) {
					BuildAndUploadNotifier.info(project, "按部署清单上传完成，共 ${uploadFiles.size} 个文件。")
					onSuccess?.invoke()
				}
			}
			return
		}

		uploadViaDeployment(entries.map { it.localPath }) {
			BuildAndUploadNotifier.info(project, "按部署清单上传完成，共 ${entries.size} 个文件。")
			onSuccess?.invoke()
		}
	}

	fun deleteManifest(deletePaths: List<String>, onSuccess: (() -> Unit)? = null) {
		if (deletePaths.isEmpty()) {
			onSuccess?.invoke()
			return
		}

		val settings = UploadSettingsState.getInstance()
		val executable = settings.deleteExecutablePath.trim()
		if (executable.isBlank()) {
			BuildAndUploadNotifier.info(project, "存在 ${deletePaths.size} 个待删除文件，但未配置删除脚本，已跳过删除。")
			onSuccess?.invoke()
			return
		}

		val parsedArgs = ParametersListUtil.parse(settings.deleteAdditionalArgs)
		val workDir = settings.workingDirectory.takeIf { it.isNotBlank() }
		ApplicationManager.getApplication().executeOnPooledThread {
			try {
				for (remotePath in deletePaths) {
					val commandLine = GeneralCommandLine(executable)
						.withWorkDirectory(workDir)
						.withParameters(parsedArgs + remotePath)
					val result = CapturingProcessHandler(commandLine).runProcess(UploadSettingsState.PROCESS_TIMEOUT_MS)
					if (result.exitCode != 0) {
						val details = result.stderr.ifBlank { result.stdout }
						throw RuntimeException("$remotePath 删除失败：$details")
					}
				}
				BuildAndUploadNotifier.info(project, "按部署清单删除完成，共 ${deletePaths.size} 个文件。")
				onSuccess?.invoke()
			} catch (ex: Exception) {
				BuildAndUploadNotifier.error(project, "按部署清单删除失败：${ex.message}")
			}
		}
	}

	private fun uploadViaDeployment(files: List<Path>, onSuccess: (() -> Unit)? = null) {
		ApplicationManager.getApplication().invokeLater {
			try {
				val fileSystem = LocalFileSystem.getInstance()
				val virtualFiles = files.mapNotNull { fileSystem.refreshAndFindFileByNioFile(it) }
				if (virtualFiles.isEmpty()) {
					BuildAndUploadNotifier.error(project, "无法定位需要上传的文件。")
					return@invokeLater
				}

				val action = resolveDeploymentAction()
				if (action == null) {
					BuildAndUploadNotifier.error(project, "当前 IDE 未提供 Deployment 上传功能，请安装并启用相关插件。")
					return@invokeLater
				}

				val dataContext: DataContext = SimpleDataContext.builder()
					.add(CommonDataKeys.PROJECT, project)
					.add(CommonDataKeys.VIRTUAL_FILE_ARRAY, virtualFiles.toTypedArray())
					.add(CommonDataKeys.VIRTUAL_FILE, virtualFiles.first())
					.build()

				val event = AnActionEvent.createFromAnAction(action, null as InputEvent?, "unknown", dataContext)
				action.actionPerformed(event)
				BuildAndUploadNotifier.info(project, "已调用 IDE 部署上传 ${virtualFiles.size} 个文件，请查看 Deployment 日志。")
				onSuccess?.invoke()
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

	private fun collectManifestJavaFiles(entries: List<DeployManifestEntry>): List<VirtualFile> {
		val fileSystem = LocalFileSystem.getInstance()
		return entries.asSequence()
			.map { entry -> entry.localPath }
			.filter { path -> path.fileName.toString().endsWith(".java", ignoreCase = true) }
			.mapNotNull(fileSystem::findFileByNioFile)
			.distinctBy(VirtualFile::getPath)
			.toList()
	}

	private fun resolveManifestUploadFiles(entries: List<DeployManifestEntry>): List<Path> =
		ReadAction.compute<List<Path>, RuntimeException> {
			val fileIndex = ProjectFileIndex.getInstance(project)
			val files = LinkedHashSet<Path>()
			val unresolvedJavaFiles = mutableListOf<String>()

			for (entry in entries) {
				val localPath = entry.localPath
				if (!localPath.fileName.toString().endsWith(".java", ignoreCase = true)) {
					files.add(localPath)
					continue
				}

				val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(localPath)
				if (virtualFile == null) {
					unresolvedJavaFiles += localPath.toString()
					continue
				}

				val classFiles = locateCompiledClassFiles(fileIndex, virtualFile)
				if (classFiles.isEmpty()) {
					unresolvedJavaFiles += localPath.toString()
					continue
				}
				files.addAll(classFiles)
			}

			if (unresolvedJavaFiles.isNotEmpty()) {
				BuildAndUploadNotifier.error(project, "以下 Java 文件编译后仍未找到 .class：${unresolvedJavaFiles.joinToString("; ")}")
			}
			files.toList()
		}

	private fun locateCompiledClassFiles(fileIndex: ProjectFileIndex, virtualFile: VirtualFile): List<Path> {
		val module: Module = fileIndex.getModuleForFile(virtualFile) ?: return emptyList()
		val sourceRoot = fileIndex.getSourceRootForFile(virtualFile) ?: return emptyList()
		val moduleOutputPath = CompilerPaths.getModuleOutputDirectory(module, false)?.path ?: return emptyList()
		val relativeSourcePath = VfsUtilCore.getRelativePath(virtualFile, sourceRoot) ?: return emptyList()
		return ClassFileLocator.locate(moduleOutputPath, relativeSourcePath)
	}
}
