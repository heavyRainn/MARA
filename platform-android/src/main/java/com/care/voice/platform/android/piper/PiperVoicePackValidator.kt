package com.care.voice.platform.android.piper

import java.io.File
import java.security.MessageDigest

object PiperVoicePackValidator {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun validateInstalled(manifest: PiperVoicePackManifest, voiceDir: File): Boolean {
        if (!voiceDir.isDirectory) return false
        val marker = File(voiceDir, ".installed")
        if (!marker.exists()) return false
        if (marker.readText().trim() != manifest.version) return false
        manifest.sha256.forEach { (name, expected) ->
            val file = File(voiceDir, name)
            if (!file.isFile) return false
            if (sha256(file) != expected.lowercase()) return false
        }
        return true
    }
}

object PiperVoicePackPaths {
    const val ASSET_VOICE_ROOT = "piper/voices/ru_RU-irina-medium"
    const val PREFERRED_VOICE_ID = "ru_RU-irina-medium"
    const val LEGACY_VOICE_ID = "ru_RU-dmitri-medium"

    fun voiceVersionDir(filesDir: File, voiceId: String, version: String): File =
        File(File(filesDir, "piper/voices/$voiceId"), version)

    fun stagingDir(filesDir: File, voiceId: String, version: String): File =
        File(File(filesDir, "piper/staging"), "$voiceId-$version")
}
