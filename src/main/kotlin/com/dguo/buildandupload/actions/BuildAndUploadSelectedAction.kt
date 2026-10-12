package com.dguo.buildandupload.actions

import com.dguo.buildandupload.service.BuildAndUploadService
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile

class BuildAndUploadSelectedAction : AnAction() {
	override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

	override fun update(e: AnActionEvent) {
		val javaCount = if (e.project != null) selectedJavaFiles(e).size else 0
		e.presentation.isEnabledAndVisible = javaCount > 0
		e.presentation.text = if (javaCount > 1) "编译所选 $javaCount 个 Java 文件并上传" else "编译该 Java 文件并上传"
	}

	override fun actionPerformed(e: AnActionEvent) {
		val project = e.project ?: return
		val javaFiles = selectedJavaFiles(e)
		if (javaFiles.isEmpty()) {
			return
		}

		FileDocumentManager.getInstance().saveAllDocuments()
		BuildAndUploadService.getInstance(project).compileAndUpload(javaFiles)
	}

	private fun selectedJavaFiles(e: AnActionEvent): List<VirtualFile> {
		return e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty()
			.filter { file ->
				file.isValid && file.isInLocalFileSystem && !file.isDirectory && file.extension.equals("java", ignoreCase = true)
			}
	}
}
