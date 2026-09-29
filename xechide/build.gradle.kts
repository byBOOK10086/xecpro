import com.android.build.api.dsl.ApplicationExtension
import com.android.build.gradle.BaseExtension

plugins {
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.zygoteloader) apply false
}

// 模块目录名（/data/adb/modules/<id>）用伪装名，不出现 xechide/hide 之类字样
val moduleId by extra("sysfwk")
val appPackageName by extra("com.xecpro.xechide")

val minSdkVer by extra(29)
val targetSdkVer by extra(37)

// 版本号与 XECKernel Pro 主工程保持同一套规则：30000 + git 提交数
val gitCommitCount: Int = runCatching {
    providers.exec {
        commandLine("git", "rev-list", "--count", "HEAD")
    }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(0)

val appVerCode by extra(30000 + gitCommitCount)
val appVerName by extra("v$appVerCode")

val androidSourceCompatibility = JavaVersion.VERSION_21
val androidTargetCompatibility = JavaVersion.VERSION_21

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    extensions.findByType<BaseExtension>()?.run {
        compileSdkVersion(targetSdkVer)

        defaultConfig {
            minSdk = minSdkVer
            targetSdk = targetSdkVer
            versionCode = appVerCode
            versionName = appVerName
        }

        buildTypes {
            named("release") {
                isMinifyEnabled = true
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-rules.pro",
                )
            }
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    extensions.findByType<ApplicationExtension>()?.run {
        dependenciesInfo {
            includeInApk = false
            includeInBundle = false
        }
    }
}

subprojects {
    plugins.withId("com.android.application") { configureBaseExtension() }
    plugins.withId("com.android.library") { configureBaseExtension() }
}