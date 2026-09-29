plugins {
    alias(libs.plugins.agp.lib)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.xecpro.xechide.common"

    defaultConfig {
        consumerProguardFiles("proguard-rules.pro")
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
}