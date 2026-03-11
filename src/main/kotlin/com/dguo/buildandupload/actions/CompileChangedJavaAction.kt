package com.dguo.buildandupload.actions

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

class CompileChangedJavaAction : AnAction() {
	override fun update(e: AnActionEvent) {
		val project = e.project
		val hasJavaChanges = project != null && collectJavaChanges(project).isNotEmpty()
		e.presentation.isEnabledAndVisible = project != null && hasJavaChanges
	}

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return
		val javaFiles = collectJavaChanges(project)
		if (javaFiles.isEmpty()) {
			BuildAndUploadNotifier.info(project, "未检测到需要编译的 Java 变更。")
			return
		}

		FileDocumentManager.getInstance().saveAllDocuments()
		BuildAndUploadNotifier.info(project, "开始编译 ${javaFiles.size} 个 Java 文件 ...")
		CompilerManager.getInstance(project)
			.compile(javaFiles.toTypedArray(), CompileCallback(project, javaFiles.size))
	}

	private fun collectJavaChanges(project: Project): List<VirtualFile> {
		return ChangeListManager.getInstance(project)
			.allChanges
			.mapNotNull(::resolveVirtualFile)
			.filter { file -> file.isValid && file.isInLocalFileSystem && file.extension.equals("java", ignoreCase = true) }
	}

	private fun resolveVirtualFile(change: Change): VirtualFile? {
		change.virtualFile?.let { return it }
		val afterPath = change.afterRevision?.file?.path ?: return null
		return LocalFileSystem.getInstance().findFileByPath(afterPath)
	}

	private class CompileCallback(
		private val project: Project,
		private val count: Int
	) : CompileStatusNotification {
		override fun finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
			when {
				aborted -> BuildAndUploadNotifier.error(project, "编译被取消。")
				errors > 0 -> BuildAndUploadNotifier.error(project, "编译失败，请先修复错误。")
				else -> BuildAndUploadNotifier.info(project, "Java 编译完成，共 $count 个文件。")
			}
		}
	}
}
