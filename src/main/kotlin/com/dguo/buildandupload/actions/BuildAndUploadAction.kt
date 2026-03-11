package com.dguo.buildandupload.actions

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.dguo.buildandupload.service.BuildAndUploadService
import com.dguo.buildandupload.util.ClassFileLocator
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import java.nio.file.Paths
import java.util.LinkedHashSet
import java.util.Locale

class BuildAndUploadAction : AnAction() {
	override fun update(e: AnActionEvent) {
		val project = e.project
		val hasChanges = project != null && ChangeListManager.getInstance(project).allChanges.isNotEmpty()
		e.presentation.isEnabledAndVisible = project != null && hasChanges
	}

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return
		FileDocumentManager.getInstance().saveAllDocuments()
		val changes = ChangeListManager.getInstance(project).allChanges
		if (changes.isEmpty()) {
			BuildAndUploadNotifier.info(project, "未检测到 Git 修改文件，无需上传。")
			return
		}

		val (filesToUpload, skipped) = collectFiles(project, changes)
		if (filesToUpload.isEmpty()) {
			val reason = if (skipped.isNotEmpty()) skipped.joinToString("; ") else "无可处理的文件。"
			BuildAndUploadNotifier.error(project, "没有可上传的文件：$reason")
			return
		}

		if (skipped.isNotEmpty()) {
			BuildAndUploadNotifier.info(project, "部分文件已跳过：${skipped.joinToString("; ")}")
		}

		BuildAndUploadService.getInstance(project).upload(filesToUpload.toList())
	}

	private fun collectFiles(
		project: Project,
		changes: Collection<Change>
	): Pair<Set<Path>, List<String>> {
		val fileIndex = ProjectFileIndex.getInstance(project)
		val files = LinkedHashSet<Path>()
		val skipped = mutableListOf<String>()

		for (change in changes) {
			val virtualFile = resolveVirtualFile(change) ?: continue
			if (!virtualFile.isValid || !virtualFile.isInLocalFileSystem) {
				continue
			}

			when (virtualFile.extension?.lowercase(Locale.ROOT)) {
				"java" -> files.addAll(locateClassFiles(project, fileIndex, virtualFile, skipped))
				"js" -> files.add(Paths.get(virtualFile.path))
			}
		}

		return files to skipped
	}

	private fun resolveVirtualFile(change: Change): VirtualFile? {
		change.virtualFile?.let { return it }
		val afterPath = change.afterRevision?.file?.path ?: return null
		return LocalFileSystem.getInstance().findFileByPath(afterPath)
	}

	private fun locateClassFiles(
		project: Project,
		fileIndex: ProjectFileIndex,
		virtualFile: VirtualFile,
		skipped: MutableList<String>
	): List<Path> {
		val module: Module = fileIndex.getModuleForFile(virtualFile)
			?: return emptyList<Path>().also { skipped += "${virtualFile.name} 未找到所属 Module" }
		val sourceRoot = fileIndex.getSourceRootForFile(virtualFile)
			?: return emptyList<Path>().also { skipped += "${virtualFile.name} 不在 Source Root 中" }
		val moduleOutputPath = CompilerPaths.getModuleOutputDirectory(module, false)?.path
			?: return emptyList<Path>().also { skipped += "${virtualFile.name} 未配置输出目录" }
		val relativeSourcePath = VfsUtilCore.getRelativePath(virtualFile, sourceRoot)
			?: return emptyList<Path>().also { skipped += "${virtualFile.name} 无法计算相对路径" }

		val classFiles = ClassFileLocator.locate(moduleOutputPath, relativeSourcePath)
		if (classFiles.isEmpty()) {
			skipped += "${virtualFile.name} 未找到对应 .class"
		}
		return classFiles
	}
}
