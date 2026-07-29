package com.care.voice.platform.android.piper

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PiperVoicePackInstaller(
    private val context: Context,
) {
    private val filesDir: File = context.filesDir

    suspend fun ensureInstalled(): Result<PiperInstalledVoice> {
        val manifest = readManifestFromAssets()
            ?: return Result.failure(IllegalStateException("manifest missing"))

        val targetDir = PiperVoicePackPaths.voiceVersionDir(
            filesDir,
            manifest.voiceId,
            manifest.version,
        )
        if (PiperVoicePackValidator.validateInstalled(manifest, targetDir)) {
            cleanupLegacyVoicePacks(manifest.voiceId)
            return Result.success(toInstalled(manifest, targetDir))
        }

        val staging = PiperVoicePackPaths.stagingDir(
            filesDir,
            manifest.voiceId,
            manifest.version,
        )
        if (staging.exists()) staging.deleteRecursively()

        return runCatching {
            copyVoiceAssets(manifest, staging)
            validateStaging(manifest, staging)

            val backupDir = File(targetDir.parentFile, "${targetDir.name}.backup")
            if (targetDir.exists()) {
                backupDir.deleteRecursively()
                check(targetDir.renameTo(backupDir)) { "Failed to backup previous voice pack" }
            }

            try {
                targetDir.parentFile?.mkdirs()
                if (!staging.renameTo(targetDir)) {
                    copyDirectory(staging, targetDir)
                    staging.deleteRecursively()
                }
                File(targetDir, ".installed").writeText(manifest.version)
                val installed = toInstalled(manifest, targetDir)
                backupDir.takeIf { it.exists() }?.deleteRecursively()
                cleanupLegacyVoicePacks(manifest.voiceId)
                installed
            } catch (error: Exception) {
                if (backupDir.exists()) {
                    targetDir.takeIf { it.exists() }?.deleteRecursively()
                    backupDir.renameTo(targetDir)
                }
                throw error
            } finally {
                staging.takeIf { it.exists() }?.deleteRecursively()
            }
        }.recoverCatching {
            staging.deleteRecursively()
            throw it
        }
    }

    private fun readManifestFromAssets(): PiperVoicePackManifest? = try {
        val path = "${PiperVoicePackPaths.ASSET_VOICE_ROOT}/manifest.json"
        val raw = context.assets.open(path).bufferedReader().use { it.readText() }
        PiperVoicePackManifest.parse(raw)
    } catch (_: IOException) {
        null
    }

    private fun copyVoiceAssets(manifest: PiperVoicePackManifest, staging: File) {
        staging.mkdirs()
        val assetRoot = PiperVoicePackPaths.ASSET_VOICE_ROOT
        context.assets.list(assetRoot).orEmpty().forEach { child ->
            copyAssetFolder(context.assets, "$assetRoot/$child", File(staging, child))
        }
    }

    private fun copyAssetFolder(assets: AssetManager, assetPath: String, destination: File) {
        val children = assets.list(assetPath).orEmpty()
        if (children.isEmpty()) {
            destination.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                FileOutputStream(destination).use { output -> input.copyTo(output) }
            }
            return
        }
        destination.mkdirs()
        children.forEach { child ->
            copyAssetFolder(assets, "$assetPath/$child", File(destination, child))
        }
    }

    private fun validateStaging(manifest: PiperVoicePackManifest, staging: File) {
        manifest.sha256.forEach { (name, expected) ->
            val file = File(staging, name)
            if (!file.isFile) {
                throw IOException("Missing asset file: $name")
            }
            val actual = PiperVoicePackValidator.sha256(file)
            if (actual != expected.lowercase()) {
                throw IOException("Checksum mismatch for $name")
            }
        }
        if (!File(staging, manifest.modelFile).isFile) {
            throw IOException("Model file missing")
        }
        if (!File(staging, manifest.configFile).isFile) {
            throw IOException("Config file missing")
        }
        if (!File(staging, manifest.tokensFile).isFile) {
            throw IOException("Tokens file missing")
        }
    }

    private fun cleanupLegacyVoicePacks(currentVoiceId: String) {
        val voicesRoot = File(filesDir, "piper/voices")
        voicesRoot.listFiles()?.forEach { voiceDir ->
            if (voiceDir.isDirectory && voiceDir.name != currentVoiceId) {
                voiceDir.deleteRecursively()
            }
        }
    }

    private fun copyDirectory(source: File, target: File) {
        source.walkTopDown().forEach { file ->
            val relative = file.relativeTo(source).path
            val out = File(target, relative)
            if (file.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                file.copyTo(out, overwrite = true)
            }
        }
    }

    private fun toInstalled(manifest: PiperVoicePackManifest, dir: File): PiperInstalledVoice {
        val espeak = File(dir, "espeak-ng-data")
        return PiperInstalledVoice(
            voiceId = manifest.voiceId,
            version = manifest.version,
            modelPath = File(dir, manifest.modelFile).absolutePath,
            configPath = File(dir, manifest.configFile).absolutePath,
            tokensPath = File(dir, manifest.tokensFile).absolutePath,
            espeakDataPath = espeak.takeIf { it.isDirectory }?.absolutePath,
            sampleRateHz = manifest.sampleRateHz,
            speakerId = manifest.speakerId,
        )
    }
}

data class PiperInstalledVoice(
    val voiceId: String,
    val version: String,
    val modelPath: String,
    val configPath: String,
    val tokensPath: String,
    val espeakDataPath: String?,
    val sampleRateHz: Int,
    val speakerId: Int,
)
