package com.dguo.buildandupload.util

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern

object ClassFileLocator {
	fun locate(moduleOutputPath: String, relativeSourcePath: String): List<Path> {
		val relative = Paths.get(relativeSourcePath)
		val className = relative.fileName.toString().removeSuffix(".java")
		val classDir = relative.parent?.let { Paths.get(moduleOutputPath).resolve(it) } ?: Paths.get(moduleOutputPath)
		if (!Files.exists(classDir)) {
			return emptyList()
		}

		val pattern = Pattern.compile(Pattern.quote(className) + "(\\$.*)?\\.class")
		Files.list(classDir).use { stream ->
			return stream
				.filter { path -> Files.isRegularFile(path) && pattern.matcher(path.fileName.toString()).matches() }
				.sorted()
				.toList()
		}
	}
}
