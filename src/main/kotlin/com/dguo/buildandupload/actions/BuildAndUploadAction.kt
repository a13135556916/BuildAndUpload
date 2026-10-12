package com.dguo.buildandupload.actions

import com.dguo.buildandupload.notifications.BuildAndUploadNotifier
import com.dguo.buildandupload.service.BuildAndUploadService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

class BuildAndUploadAction : AnAction() {
	override fun update(e: AnActionEvent) {
		val project = e.project
		val hasChanges = project != null && ChangeListManager.getInstance(project).allChanges.isNotEmpty()
		e.presentation.isVisible = project != null
		e.presentation.isEnabled = project != null && hasChanges
	}

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return
		FileDocumentManager.getInstance().saveAllDocuments()
		val changes = ChangeListManager.getInstance(project).allChanges
		if (changes.isEmpty()) {
			BuildAndUploadNotifier.info(project, "未检测到 Git 修改文件，无需上传。")
			return
		}

		BuildAndUploadService.getInstance(project).compileAndUpload(changes.mapNotNull(::resolveVirtualFile))
	}

	private fun resolveVirtualFile(change: Change): VirtualFile? {
		change.virtualFile?.let { return it }
		val afterPath = change.afterRevision?.file?.path ?: return null
		return LocalFileSystem.getInstance().findFileByPath(afterPath)
	}
}
