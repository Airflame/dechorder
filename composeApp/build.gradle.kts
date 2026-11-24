import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// The Android target needs an Android SDK. Without one (no sdk.dir in local.properties and no
// ANDROID_HOME), only the desktop app is built, so the project still works on machines without it.
val androidSdkConfigured: Boolean = run {
    val local = Properties().apply {
        rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
    }
    val dir = local.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    dir != null && file(dir).isDirectory
}
val buildAndroid = androidSdkConfigured && (findProperty("dechorder.android")?.toString() != "false")
if (buildAndroid) {
    apply(plugin = "com.android.application")
} else {
    logger.lifecycle("Dechorder: no Android SDK configured (local.properties sdk.dir / ANDROID_HOME) - building the desktop app only.")
}

kotlin {
    if (buildAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
            }
        }
        if (buildAndroid) {
            getByName("androidMain").dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
    }
}

if (buildAndroid) {
    extensions.configure<com.android.build.api.dsl.ApplicationExtension>("android") {
        namespace = "app.dechorder"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        defaultConfig {
            applicationId = "app.dechorder"
            minSdk = libs.versions.android.minSdk.get().toInt()
            targetSdk = libs.versions.android.targetSdk.get().toInt()
            versionCode = 1
            versionName = "1.0.1"
        }
        packaging {
            resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        }
        buildTypes {
            getByName("release") {
                isMinifyEnabled = false
                // Signed with the debug key so a release APK can be side-loaded straight away.
                // Replace with your own signing config before publishing.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.dechorder.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "Dechorder"
            packageVersion = "1.0.1"
            description = "Listens to music and names the chords being played"
            modules("java.desktop", "java.prefs")
            windows {
                menuGroup = "Dechorder"
                upgradeUuid = "6f3e0a7e-2b1c-4f4e-9a52-6d1c2f0b8e41"
            }
            macOS {
                bundleID = "app.dechorder"
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Dechorder listens to the music to recognise the chords.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
