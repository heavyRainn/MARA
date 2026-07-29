package com.care.voice.platform.android.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.care.voice.brain.speech.SpeechFailureCode
import com.care.voice.platform.android.piper.PcmAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PcmAudioPlayer {
    suspend fun play(
        pcm: PcmAudio,
        isCancelled: () -> Boolean,
    ): SpeechFailureCode? = withContext(Dispatchers.IO) {
        val bufferSize = AudioTrack.getMinBufferSize(
            pcm.sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (bufferSize <= 0) return@withContext SpeechFailureCode.AUDIO_TRACK_INIT_FAILED

        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setSampleRate(pcm.sampleRateHz)
            .build()

        val track = AudioTrack(
            attributes,
            format,
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            return@withContext SpeechFailureCode.AUDIO_TRACK_INIT_FAILED
        }

        try {
            track.play()
            var offset = 0
            val chunkSize = 4_096
            while (offset < pcm.samples.size) {
                if (isCancelled()) return@withContext null
                val end = minOf(offset + chunkSize, pcm.samples.size)
                val written = track.write(pcm.samples, offset, end - offset)
                if (written <= 0) return@withContext SpeechFailureCode.AUDIO_PLAYBACK_FAILED
                offset += written
            }
            null
        } catch (_: Exception) {
            SpeechFailureCode.AUDIO_PLAYBACK_FAILED
        } finally {
            runCatching {
                track.stop()
                track.release()
            }
        }
    }
}
