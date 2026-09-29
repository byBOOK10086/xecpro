import com.v7878.zygisk.gradle.ZygoteLoader
import kotlin.io.path.Path

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.zygoteloader)
}

val moduleId: String by rootProject.extra
val appPackageName: String by rootProject.extra

android {
    namespace = "$appPackageName.zygote"

    defaultConfig {
        applicationId = namespace
    }

    sourceSets {
        getByName("main") {
            java {
                // com.v7878.vmtools（Hooks / HookTransformer / EntryPoints）没有发布到
                // 任何 Maven 仓库，只能像上游那样把源码目录直接编进来。
                // 见 xechide/external/AndroidVMTools（git submodule，MIT）。
                srcDirs(Path(rootDir.path, "external", "AndroidVMTools", "src", "main", "java"))
            }
        }
    }
}

kotlin {
    jvmToolchain(21)
}

zygisk {
    // 只注入 system_server：隐藏必须发生在 PackageManager 的裁决点上，
    // 注入到各 App 进程只能看到已被裁剪的结果，无法拦截。
    packages(ZygoteLoader.PACKAGE_SYSTEM_SERVER)

    // /data/adb/modules/<id> 是设备上肉眼可见的痕迹，所以目录名与展示信息
    // 一律按「系统框架兼容层」伪装，不出现 hide/root/ksu 之类的字样。
    id = "${moduleId}_zygisk"
    name = "System Framework Compat"
    author = "XECKernel Pro"
    description = "Framework-level compatibility shims"
    entrypoint = "$appPackageName.zygote.HideEntry"
    archiveName = "XecHide"
    isAddVariantToArchiveName = true
    // 刻意不设 updateJson：省掉一次联网回连，也少一处可被指认的指纹。
}

dependencies {
    implementation(projects.common)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.r8annotations)
    implementation(libs.sun.cleaner)

    api(libs.panama.core)
    api(libs.panama.unsafe)
    api(libs.panama.llvm)
}