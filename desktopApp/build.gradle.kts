import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.jna)
}

compose.desktop {
    application {
        mainClass = "com.lin0721.linmusic.desktop.MainKt"
        // 开发运行时从本地 native 目录加载 libmpv；安装包的放置方式在打包阶段处理
        jvmArgs("-Djna.library.path=${project.file("native").absolutePath}")
        // 中文系统默认 GBK，统一日志输出编码
        jvmArgs("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Melodia"
            packageVersion = "1.0.0"
        }
    }
}
