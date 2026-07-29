package com.care.voice.platform.android.piper

import org.json.JSONObject

data class PiperVoicePackManifest(
    val voiceId: String,
    val locale: String,
    val version: String,
    val modelFile: String,
    val configFile: String,
    val tokensFile: String,
    val sampleRateHz: Int,
    val speakerId: Int,
    val sha256: Map<String, String>,
    val license: String,
    val datasetLicense: String,
) {
    companion object {
        fun parse(raw: String): PiperVoicePackManifest {
            val json = JSONObject(raw)
            val checksums = linkedMapOf<String, String>()
            val sha = json.getJSONObject("sha256")
            sha.keys().forEach { key -> checksums[key] = sha.getString(key) }
            return PiperVoicePackManifest(
                voiceId = json.getString("voiceId"),
                locale = json.getString("locale"),
                version = json.getString("version"),
                modelFile = json.getString("modelFile"),
                configFile = json.getString("configFile"),
                tokensFile = json.optString("tokensFile", "tokens.txt"),
                sampleRateHz = json.getInt("sampleRateHz"),
                speakerId = json.optInt("speakerId", 0),
                sha256 = checksums,
                license = json.getString("license"),
                datasetLicense = json.optString("datasetLicense", "Unknown"),
            )
        }
    }
}
