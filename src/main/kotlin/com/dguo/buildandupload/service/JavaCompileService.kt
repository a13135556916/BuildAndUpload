package com.dguo.buildandupload.service

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompileStatusNotification
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object JavaCompileService {
	fun compile(
		project: Project,
		javaFiles: Collection<VirtualFile>,
		startMessage: String,
		successMessage: String,
		onSuccess: (() -> Unit)? = null
	) {
		val files = javaFiles.asSequence()
			.filter { file -> file.isValid && file.isInLocalFileSystem && file.extension.equals("java", ignoreCase = true) }
			.distinctBy(VirtualFile::getPath)
			.toList()
		if (files.isEmpty()) {
			onSuccess?.invoke()
			return
		}

		BuildAndUploadNotifier.info(project, startMessage)
		CompilerManager.getInstance(project)
			.compile(files.toTypedArray(), CompileCallback(project, successMessage, onSuccess))
	}

	private class CompileCallback(
		private val project: Project,
		private val successMessage: String,
		private val onSuccess: (() -> Unit)?
	) : CompileStatusNotification {
		override fun finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
			when {
				aborted -> BuildAndUploadNotifier.error(project, "编译被取消。")
				errors > 0 -> BuildAndUploadNotifier.error(project, "编译失败，请先修复错误。")
				else -> {
					BuildAndUploadNotifier.info(
						project,
						if (warnings > 0) "$successMessage（警告 $warnings 个）" else successMessage
					)
					onSuccess?.invoke()
				}
			}
		}
	}
}
