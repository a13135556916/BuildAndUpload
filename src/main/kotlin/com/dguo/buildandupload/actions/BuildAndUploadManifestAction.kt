package com.dguo.buildandupload.actions

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.dguo.buildandupload.service.BuildAndUploadService
import com.dguo.buildandupload.service.DeployManifestEntry
import com.dguo.buildandupload.service.DeployManifestService
import com.dguo.buildandupload.service.DeployManifestResult
import com.dguo.buildandupload.service.JavaCompileService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.VcsLogDataKeys
import java.nio.file.Path
import java.util.LinkedHashSet
import java.util.Locale

private data class XmlFilteredManifest(
	val entries: List<DeployManifestEntry>,
	val deletePaths: List<String>,
	val ignoredXmlPaths: List<String>
)

class BuildAndUploadManifestAction : AnAction() {
	override fun update(e: AnActionEvent) {
		e.presentation.isEnabledAndVisible = e.project != null
	}

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return
		FileDocumentManager.getInstance().saveAllDocuments()

		try {
			val suggestedBaseCommit = runCatching {
				DeployManifestService.suggestBaseCommit(project)
			}.getOrDefault("")
			val inputBaseCommit = Messages.showInputDialog(
				project,
				"请输入本次生成部署清单使用的 base commit。\n未配置默认基线时可直接手动输入。",
				"Build & Upload By Manifest",
				Messages.getQuestionIcon(),
				suggestedBaseCommit,
				null
			)?.trim() ?: return
			if (inputBaseCommit.isEmpty()) {
				BuildAndUploadNotifier.error(project, "base commit 不能为空。")
				return
			}

			runManifestUpload(project, inputBaseCommit)
		} catch (ex: Exception) {
			BuildAndUploadNotifier.error(project, ex.message ?: "执行部署清单上传失败")
		}
	}

	companion object {
		fun runManifestUpload(
			project: com.intellij.openapi.project.Project,
			baseCommit: String,
			headRef: String? = null,
			markDeployedAfterSuccess: Boolean = true
		) {
			runManifestUpload(project, baseCommit, headRef, allowCompile = true, markDeployedAfterSuccess = markDeployedAfterSuccess)
		}

		private fun runManifestUpload(
			project: com.intellij.openapi.project.Project,
			baseCommit: String,
			headRef: String?,
			allowCompile: Boolean,
			markDeployedAfterSuccess: Boolean
		) {
			val result = DeployManifestService.generate(project, baseCommit, headRef)
			val filtered = filterXmlEntries(result)
			val manifestContainsJavaSources = filtered.entries.any { entry ->
				entry.localPath.fileName.toString().endsWith(".java", ignoreCase = true)
			}
			val javaFiles = collectJavaFiles(result, filtered.entries)
			if (allowCompile && !manifestContainsJavaSources && javaFiles.isNotEmpty()) {
				JavaCompileService.compile(
					project = project,
					javaFiles = javaFiles,
					startMessage = "部署清单涉及 ${javaFiles.size} 个 Java 文件，开始编译 ...",
					successMessage = "Java 编译完成，重新生成部署清单。"
				) {
					runManifestUpload(
						project = project,
						baseCommit = baseCommit,
						headRef = headRef,
						allowCompile = false,
						markDeployedAfterSuccess = markDeployedAfterSuccess
					)
				}
				return
			}

			if (result.missingClassPaths.isNotEmpty()) {
				BuildAndUploadNotifier.error(
					project,
					"部署清单存在缺失 .class 的 Java 文件，请先编译：${result.missingClassPaths.joinToString("; ")}"
				)
				return
			}

			if (filtered.entries.isEmpty() && filtered.deletePaths.isEmpty()) {
				if (filtered.ignoredXmlPaths.isNotEmpty()) {
					BuildAndUploadNotifier.info(
						project,
						"本次仅涉及 XML 文件，已全部忽略自动上传：${filtered.ignoredXmlPaths.joinToString("; ")}"
					)
				} else {
					BuildAndUploadNotifier.info(project, "部署清单为空，无需上传。")
				}
				return
			}

			if (result.skippedPaths.isNotEmpty()) {
				BuildAndUploadNotifier.info(project, "本次有 ${result.skippedPaths.size} 个文件被脚本跳过。")
			}

			BuildAndUploadNotifier.info(
				project,
				"部署清单已生成，base=${result.baseCommit}，head=${result.headCommit}，准备上传 ${filtered.entries.size} 个文件，删除 ${filtered.deletePaths.size} 个文件。清单目录：${result.outputDir}"
			)

			val finish = {
				if (markDeployedAfterSuccess) {
					DeployManifestService.markDeployed(result)
				}
				notifyIgnoredXml(project, filtered.ignoredXmlPaths)
			}

			if (filtered.entries.isEmpty()) {
				BuildAndUploadService.getInstance(project).deleteManifest(filtered.deletePaths, finish)
				return
			}

			BuildAndUploadService.getInstance(project).uploadManifest(filtered.entries) {
				BuildAndUploadService.getInstance(project).deleteManifest(filtered.deletePaths, finish)
			}
		}

		private fun collectJavaFiles(result: DeployManifestResult, entries: List<DeployManifestEntry>): List<VirtualFile> {
			val fileSystem = LocalFileSystem.getInstance()
			val javaPaths = LinkedHashSet<Path>()

			entries.asSequence()
				.map { entry -> entry.localPath }
				.filter { path -> path.fileName.toString().endsWith(".java", ignoreCase = true) }
				.mapTo(javaPaths) { path -> path }

			entries.asSequence()
				.map { entry -> entry.sourcePath }
				.filter { sourcePath -> sourcePath.endsWith(".java", ignoreCase = true) }
				.mapTo(javaPaths) { sourcePath -> resolvePath(result.workingDir, sourcePath) }

			result.missingClassPaths.asSequence()
				.filter { sourcePath -> sourcePath.endsWith(".java", ignoreCase = true) }
				.mapTo(javaPaths) { sourcePath -> resolvePath(result.workingDir, sourcePath) }

				return javaPaths.mapNotNull(fileSystem::findFileByNioFile)
		}

		private fun filterXmlEntries(result: DeployManifestResult): XmlFilteredManifest {
			val ignoredXmlPaths = LinkedHashSet<String>()
			val uploadEntries = result.entries.filterNot { entry ->
				isXmlPath(entry.localPath.toString()) ||
					isXmlPath(entry.remotePath) ||
					isXmlPath(entry.sourcePath)
			}.also { keptEntries ->
				result.entries.asSequence()
					.filterNot { entry -> entry in keptEntries }
					.mapTo(ignoredXmlPaths) { entry -> entry.sourcePath.ifBlank { entry.localPath.toString() } }
			}
			val deletePaths = result.deletePaths.filterNot { remotePath ->
				isXmlPath(remotePath)
			}.also { keptPaths ->
				result.deletePaths.asSequence()
					.filterNot { remotePath -> remotePath in keptPaths }
					.mapTo(ignoredXmlPaths) { remotePath -> remotePath }
			}

			return XmlFilteredManifest(
				entries = uploadEntries,
				deletePaths = deletePaths,
				ignoredXmlPaths = ignoredXmlPaths.toList()
			)
		}

		private fun notifyIgnoredXml(project: com.intellij.openapi.project.Project, ignoredXmlPaths: List<String>) {
			if (ignoredXmlPaths.isEmpty()) {
				return
			}
			BuildAndUploadNotifier.info(
				project,
				"已忽略 ${ignoredXmlPaths.size} 个 XML 文件，未自动上传/删除：${ignoredXmlPaths.joinToString("; ")}"
			)
		}

		private fun isXmlPath(pathText: String): Boolean =
			pathText.lowercase(Locale.ROOT).endsWith(".xml")

		private fun resolvePath(baseDir: Path, pathText: String): Path {
			val path = Path.of(pathText)
			return if (path.isAbsolute) path else baseDir.resolve(path).normalize()
		}
	}
}

class BuildAndUploadManifestFromLogAction : AnAction() {
	override fun update(e: AnActionEvent) {
		updateSelectedCommitActionPresentation(e)
	}

	override fun actionPerformed(e: AnActionEvent) {
		runSelectedCommitUpload(e, SelectedCommitUploadMode.SINGLE_COMMIT)
	}
}

class BuildAndUploadManifestFromSelectedCommitToLatestAction : AnAction() {
	override fun update(e: AnActionEvent) {
		updateSelectedCommitActionPresentation(e)
	}

	override fun actionPerformed(e: AnActionEvent) {
		runSelectedCommitUpload(e, SelectedCommitUploadMode.TO_LATEST)
	}
}

private enum class SelectedCommitUploadMode {
	SINGLE_COMMIT,
	TO_LATEST
}

private fun updateSelectedCommitActionPresentation(e: AnActionEvent) {
	val project = e.project
	val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION)
	val selectedCommitCount = selection?.commits?.size ?: 0
	e.presentation.isVisible = project != null
	e.presentation.isEnabled = project != null && selectedCommitCount == 1
}

private fun runSelectedCommitUpload(e: AnActionEvent, mode: SelectedCommitUploadMode) {
	val project = e.project ?: return
	val selection = e.getData(VcsLogDataKeys.VCS_LOG_COMMIT_SELECTION) ?: return
	val commit: CommitId = selection.commits.singleOrNull() ?: run {
		BuildAndUploadNotifier.error(project, "请在 Git 日志中只选择一个 commit。")
		return
	}

	val commitHash = commit.hash.toString()
	try {
		val baseCommit = DeployManifestService.resolveSingleCommitBase(project, commitHash)
		val headRef = when (mode) {
			SelectedCommitUploadMode.SINGLE_COMMIT -> commitHash
			SelectedCommitUploadMode.TO_LATEST -> null
		}
		BuildAndUploadManifestAction.runManifestUpload(
			project = project,
			baseCommit = baseCommit,
			headRef = headRef,
			markDeployedAfterSuccess = false
		)
	} catch (ex: Exception) {
		val defaultMessage = when (mode) {
			SelectedCommitUploadMode.SINGLE_COMMIT -> "执行基于选中 commit 的部署清单上传失败"
			SelectedCommitUploadMode.TO_LATEST -> "执行基于选中 commit 到最新提交的部署清单上传失败"
		}
		BuildAndUploadNotifier.error(project, ex.message ?: defaultMessage)
	}
}
