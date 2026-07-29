plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.care.voice.piper.spike"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

val downloadPiperSpikeAssets = tasks.register<Exec>("downloadPiperSpikeAssets") {
    group = "piper"
    description = "Download sherpa-onnx native libs and Russian Piper model into this module"
    val script = rootProject.file("scripts/download-piper-spike.ps1")
    onlyIf { script.exists() }
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        script.absolutePath,
    )
}

tasks.named("preBuild") {
    dependsOn(downloadPiperSpikeAssets)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
