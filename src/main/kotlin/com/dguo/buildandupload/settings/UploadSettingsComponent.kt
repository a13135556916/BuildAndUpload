package com.dguo.buildandupload.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class UploadSettingsComponent {
	private val executableField = TextFieldWithBrowseButton()
	private val argsField = JBTextField()
	private val workDirField = TextFieldWithBrowseButton()

	val panel: JPanel

	init {
		executableField.addBrowseFolderListener(
			"选择上传脚本",
			"请选择需要执行的上传脚本或可执行文件",
			null as Project?,
			FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
		)
		workDirField.addBrowseFolderListener(
			"选择工作目录",
			"上传命令的工作目录（可选）",
			null as Project?,
			FileChooserDescriptorFactory.createSingleFolderDescriptor()
		)
		panel = FormBuilder.createFormBuilder()
			.addLabeledComponent("上传脚本路径（可选，留空时调用 IDE Deployment）：", executableField)
			.addLabeledComponent("额外参数（使用空格分隔，可包含变量，如 --user xxx）：", argsField)
			.addLabeledComponent("工作目录（可选）：", workDirField)
			.addComponentFillVertically(JPanel(), 0)
			.panel
	}

	fun getPreferredFocusedComponent(): JComponent = executableField

	var executablePath: String
		get() = executableField.text
		set(value) {
			executableField.text = value
		}

	var additionalArgs: String
		get() = argsField.text
		set(value) {
			argsField.text = value
		}

	var workingDirectory: String
		get() = workDirField.text
		set(value) {
			workDirField.text = value
		}
}
