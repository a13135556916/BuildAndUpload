package com.dguo.buildandupload.service

import com.dguo.buildandupload.settings.UploadSettingsState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.project.Project
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

data class DeployManifestEntry(
	val type: String,
	val localPath: Path,
	val remotePath: String,
	val sourcePath: String
)

data class DeployManifestResult(
	val workingDir: Path,
	val outputDir: Path,
	val baseCommit: String,
	val headRef: String,
	val headCommit: String,
	val entries: List<DeployManifestEntry>,
	val deletePaths: List<String>,
	val skippedPaths: List<String>,
	val missingClassPaths: List<String>,
	val summary: Map<String, String>
)

object DeployManifestService {
	private val outputTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
	private const val EMPTY_TREE_HASH = "4b825dc642cb6eb9a060e54bf8d69288fbee4904"

	fun suggestBaseCommit(project: Project): String {
		val settings = UploadSettingsState.getInstance()
		val workingDir = resolveWorkingDirectory(project, settings)
		return resolveBaseCommit(workingDir, settings)
	}

	fun generate(
		project: Project,
		baseCommitOverride: String? = null,
		headRefOverride: String? = null
	): DeployManifestResult {
		val settings = UploadSettingsState.getInstance()
		val workingDir = resolveWorkingDirectory(project, settings)
		val scriptPath = resolvePath(workingDir, settings.manifestScriptPath)
		check(scriptPath.exists() && scriptPath.isRegularFile()) { "部署清单脚本不存在: ${scriptPath.pathString}" }
		val headRef = headRefOverride?.trim().orEmpty().ifEmpty {
			settings.manifestHeadRef.ifBlank { "HEAD" }
		}
		val baseCommit = baseCommitOverride?.trim().orEmpty().ifEmpty { resolveBaseCommit(workingDir, settings) }
		val headCommit = resolveHeadCommit(workingDir, headRef)
		val outputDir = workingDir.resolve("tasks/deploy-manifests/plugin_${LocalDateTime.now().format(outputTimeFormatter)}")
		outputDir.createDirectories()

		runManifestScript(scriptPath, workingDir, baseCommit, headRef, outputDir)

		val summary = readSummary(outputDir.resolve("summary.txt"))
		val entries = readUploadManifest(workingDir, outputDir.resolve("upload-manifest.tsv"))
		val deletePaths = readSimpleList(outputDir.resolve("delete-files.txt"))
		val skippedPaths = readSimpleList(outputDir.resolve("skipped-files.txt"))
		val missingClassPaths = readSimpleList(outputDir.resolve("missing-class-files.txt"))

		return DeployManifestResult(
			workingDir = workingDir,
			outputDir = outputDir,
			baseCommit = baseCommit,
			headRef = headRef,
			headCommit = headCommit,
			entries = entries,
			deletePaths = deletePaths,
			skippedPaths = skippedPaths,
			missingClassPaths = missingClassPaths,
			summary = summary
		)
	}

	fun markDeployed(result: DeployManifestResult) {
		val settings = UploadSettingsState.getInstance()
		val recordPathText = settings.deployedCommitFilePath.trim()
		if (recordPathText.isEmpty()) {
			return
		}

		val recordPath = resolvePath(result.workingDir, recordPathText)
		recordPath.parent?.createDirectories()
		recordPath.writeText(result.headCommit + System.lineSeparator(), StandardCharsets.UTF_8)
	}

	fun resolveSingleCommitBase(project: Project, commitHash: String): String {
		val settings = UploadSettingsState.getInstance()
		val workingDir = resolveWorkingDirectory(project, settings)
		val commandLine = GeneralCommandLine("git", "rev-list", "--parents", "-n", "1", commitHash)
			.withWorkDirectory(workingDir.toFile())
		val result = CapturingProcessHandler(commandLine).runProcess(UploadSettingsState.PROCESS_TIMEOUT_MS)
		check(result.exitCode == 0) { "读取 commit 父提交失败: ${result.stderr.ifBlank { result.stdout }}" }

		val hashes = result.stdout.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
		check(hashes.isNotEmpty()) { "未读取到 commit 信息: $commitHash" }
		return hashes.getOrNull(1) ?: EMPTY_TREE_HASH
	}

	private fun resolveWorkingDirectory(project: Project?, settings: UploadSettingsState): Path {
		val configured = settings.manifestWorkingDirectory.trim()
		return when {
			configured.isNotEmpty() -> Path.of(configured)
			project?.basePath != null -> Path.of(project.basePath!!)
			else -> throw IllegalStateException("未配置清单工作目录，且当前项目没有可用的 basePath")
		}
	}

	private fun resolveBaseCommit(workingDir: Path, settings: UploadSettingsState): String {
		val deployedCommitFilePath = settings.deployedCommitFilePath.trim()
		if (deployedCommitFilePath.isNotEmpty()) {
			val recordPath = resolvePath(workingDir, deployedCommitFilePath)
			if (recordPath.exists() && recordPath.isRegularFile()) {
				val content = recordPath.readText(StandardCharsets.UTF_8).trim()
				if (content.isNotEmpty()) {
					return content
				}
			}
		}

		return settings.manifestBaseCommit.trim().ifEmpty {
			throw IllegalStateException("未配置默认基线 commit，且未找到已部署 commit 记录文件")
		}
	}

	private fun resolveHeadCommit(workingDir: Path, headRef: String): String {
		val commandLine = GeneralCommandLine("git", "rev-parse", headRef)
			.withWorkDirectory(workingDir.toFile())
		val result = CapturingProcessHandler(commandLine).runProcess(UploadSettingsState.PROCESS_TIMEOUT_MS)
		check(result.exitCode == 0) { "读取 head commit 失败: ${result.stderr.ifBlank { result.stdout }}" }
		return result.stdout.trim()
	}

	private fun runManifestScript(scriptPath: Path, workingDir: Path, baseCommit: String, headRef: String, outputDir: Path) {
		val commandLine = GeneralCommandLine(
			"bash",
			scriptPath.pathString,
			"--base",
			baseCommit,
			"--head",
			headRef,
			"--output",
			outputDir.pathString
		).withWorkDirectory(workingDir.toFile())
		val result = CapturingProcessHandler(commandLine).runProcess(UploadSettingsState.PROCESS_TIMEOUT_MS)
		check(result.exitCode == 0) {
			"生成部署清单失败: ${result.stderr.ifBlank { result.stdout }}"
		}
	}

	private fun readSummary(path: Path): Map<String, String> {
		if (!path.exists()) {
			return emptyMap()
		}
		return path.readLines(StandardCharsets.UTF_8)
			.mapNotNull { line ->
				val index = line.indexOf('=')
				if (index <= 0) {
					null
				} else {
					line.substring(0, index) to line.substring(index + 1)
				}
			}
			.toMap()
	}

	private fun readUploadManifest(workingDir: Path, path: Path): List<DeployManifestEntry> {
		if (!path.exists()) {
			return emptyList()
		}
		return path.readLines(StandardCharsets.UTF_8)
			.filter { it.isNotBlank() }
			.map { line ->
				val parts = line.split('\t')
				require(parts.size >= 4) { "upload-manifest.tsv 格式非法: $line" }
				DeployManifestEntry(
					type = parts[0],
					localPath = resolvePath(workingDir, parts[1]).absolute(),
					remotePath = parts[2],
					sourcePath = parts[3]
				)
			}
	}

	private fun readSimpleList(path: Path): List<String> {
		if (!path.exists()) {
			return emptyList()
		}
		return path.readLines(StandardCharsets.UTF_8).filter { it.isNotBlank() }
	}

	private fun resolvePath(workingDir: Path, pathText: String): Path {
		val path = Path.of(pathText)
		return if (path.isAbsolute) path else workingDir.resolve(path).normalize()
	}
}
