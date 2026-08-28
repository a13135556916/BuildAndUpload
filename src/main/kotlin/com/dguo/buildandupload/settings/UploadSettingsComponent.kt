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
	private val deleteExecutableField = TextFieldWithBrowseButton()
	private val deleteArgsField = JBTextField()
	private val manifestScriptField = TextFieldWithBrowseButton()
	private val manifestWorkDirField = TextFieldWithBrowseButton()
	private val manifestBaseField = JBTextField()
	private val manifestHeadField = JBTextField()
	private val deployedCommitFileField = TextFieldWithBrowseButton()

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
		deleteExecutableField.addBrowseFolderListener(
			"选择删除脚本",
			"请选择用于处理 delete-files.txt 的删除脚本或可执行文件",
			null as Project?,
			FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
		)
		manifestScriptField.addBrowseFolderListener(
			"选择部署清单脚本",
			"请选择 collect_deploy_manifest.sh 脚本",
			null as Project?,
			FileChooserDescriptorFactory.createSingleFileOrExecutableAppDescriptor()
		)
		manifestWorkDirField.addBrowseFolderListener(
			"选择清单工作目录",
			"通常为业务工程根目录，留空时默认当前打开项目根目录",
			null as Project?,
			FileChooserDescriptorFactory.createSingleFolderDescriptor()
		)
		deployedCommitFileField.addBrowseFolderListener(
			"选择已部署 commit 记录文件",
			"上传成功后会把当前 head commit 写回这个文件",
			null as Project?,
			FileChooserDescriptorFactory.createSingleFileDescriptor()
		)
		panel = FormBuilder.createFormBuilder()
			.addLabeledComponent("上传脚本路径（可选，留空时调用 IDE Deployment）：", executableField)
			.addLabeledComponent("额外参数（使用空格分隔，可包含变量，如 --user xxx）：", argsField)
			.addLabeledComponent("工作目录（可选）：", workDirField)
			.addLabeledComponent("删除脚本路径（可选，用于处理 delete-files.txt）：", deleteExecutableField)
			.addLabeledComponent("删除额外参数（使用空格分隔）：", deleteArgsField)
			.addSeparator()
			.addLabeledComponent("部署清单脚本路径：", manifestScriptField)
			.addLabeledComponent("清单工作目录（留空默认当前项目根目录）：", manifestWorkDirField)
			.addLabeledComponent("默认基线 commit（首次部署时使用）：", manifestBaseField)
			.addLabeledComponent("Head 引用（默认 HEAD，可填 test-integration）：", manifestHeadField)
			.addLabeledComponent("已部署 commit 记录文件（可选，存在时优先读取）：", deployedCommitFileField)
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

	var deleteExecutablePath: String
		get() = deleteExecutableField.text
		set(value) {
			deleteExecutableField.text = value
		}

	var deleteAdditionalArgs: String
		get() = deleteArgsField.text
		set(value) {
			deleteArgsField.text = value
		}

	var manifestScriptPath: String
		get() = manifestScriptField.text
		set(value) {
			manifestScriptField.text = value
		}

	var manifestWorkingDirectory: String
		get() = manifestWorkDirField.text
		set(value) {
			manifestWorkDirField.text = value
		}

	var manifestBaseCommit: String
		get() = manifestBaseField.text
		set(value) {
			manifestBaseField.text = value
		}

	var manifestHeadRef: String
		get() = manifestHeadField.text
		set(value) {
			manifestHeadField.text = value
		}

	var deployedCommitFilePath: String
		get() = deployedCommitFileField.text
		set(value) {
			deployedCommitFileField.text = value
		}
}
