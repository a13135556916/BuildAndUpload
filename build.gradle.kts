plugins {
	kotlin("jvm") version "2.4.0"
	id("org.jetbrains.intellij.platform") version "2.10.2"
}

group = "com.dguo"
version = "1.1-SNAPSHOT"

val localIdePath = providers
	.gradleProperty("localIdePath")
	.orElse("/Applications/IntelliJ IDEA.app")

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

kotlin {
	jvmToolchain(21)
}

dependencies {
	intellijPlatform {
		local(localIdePath.get())
		bundledPlugin("com.intellij.java")
		bundledModule("intellij.platform.vcs.log")
	}
}

intellijPlatform {
	pluginConfiguration {
		ideaVersion {
			sinceBuild = "253"
		}

		changeNotes.set("Recovered from BuildAndUpload plugin distribution jar.")
	}
}

tasks {
	withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
		compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
	}
}
