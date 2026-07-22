package com.example.service

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sin

class SoundPlayer {
    enum class SoundStyle(val displayName: String) {
        PIKO("電子音ピコッ (Synth Beep)"),
        MELODY("メロディ音 (Synth Melody)"),
        BUZZER("レトロブザー (Retro Buzzer)"),
        MUTE("消音（ミュート）")
    }

    suspend fun playCorrect(style: SoundStyle, volumeMultiplier: Float = 1.0f) {
        if (style == SoundStyle.MUTE) return
        withContext(Dispatchers.Default) {
            val volumeCoeff = volumeMultiplier.coerceIn(0f, 1f).toDouble()
            when (style) {
                SoundStyle.PIKO -> {
                    playTone(523.25, 0.08, 0.25 * volumeCoeff) // C5
                    playTone(659.25, 0.12, 0.25 * volumeCoeff) // E5
                }
                SoundStyle.MELODY -> {
                    playTone(523.25, 0.05, 0.2 * volumeCoeff)
                    playTone(659.25, 0.05, 0.2 * volumeCoeff)
                    playTone(783.99, 0.05, 0.2 * volumeCoeff)
                    playTone(1046.50, 0.12, 0.2 * volumeCoeff)
                }
                SoundStyle.BUZZER -> {
                    playTone(880.0, 0.15, 0.3 * volumeCoeff) // A5 beep
                }
                else -> {}
            }
        }
    }

    suspend fun playIncorrect(style: SoundStyle, volumeMultiplier: Float = 1.0f) {
        if (style == SoundStyle.MUTE) return
        withContext(Dispatchers.Default) {
            val volumeCoeff = volumeMultiplier.coerceIn(0f, 1f).toDouble()
            when (style) {
                SoundStyle.PIKO -> {
                    playTone(220.0, 0.25, 0.3 * volumeCoeff) // A3 (sine)
                }
                SoundStyle.MELODY -> {
                    playTone(330.0, 0.10, 0.25 * volumeCoeff)
                    playTone(247.0, 0.10, 0.25 * volumeCoeff)
                    playTone(196.0, 0.20, 0.25 * volumeCoeff)
                }
                SoundStyle.BUZZER -> {
                    playSquareTone(130.0, 0.35, 0.15 * volumeCoeff) // C3 heavy square wave buzz
                }
                else -> {}
            }
        }
    }

    private suspend fun playTone(frequency: Double, durationSeconds: Double, volume: Double) {
        try {
            val sampleRate = 16000
            val numSamples = (durationSeconds * sampleRate).toInt()
            val sample = DoubleArray(numSamples)
            val generatedSnd = ShortArray(numSamples)

            for (i in 0 until numSamples) {
                sample[i] = sin(2.0 * Math.PI * i / (sampleRate / frequency))
                val envelope = if (i > numSamples * 0.8) {
                    (numSamples - i).toDouble() / (numSamples * 0.2)
                } else 1.0
                generatedSnd[i] = (sample[i] * 32767.0 * volume * envelope).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                audioTrack.write(generatedSnd, 0, numSamples)
                audioTrack.play()
                kotlinx.coroutines.delay((durationSeconds * 1000).toLong() + 100)
            } finally {
                audioTrack.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private suspend fun playSquareTone(frequency: Double, durationSeconds: Double, volume: Double) {
        try {
            val sampleRate = 16000
            val numSamples = (durationSeconds * sampleRate).toInt()
            val generatedSnd = ShortArray(numSamples)

            val period = sampleRate / frequency
            for (i in 0 until numSamples) {
                val value = if ((i % period) < (period / 2)) 1.0 else -1.0
                val envelope = if (i > numSamples * 0.8) {
                    (numSamples - i).toDouble() / (numSamples * 0.2)
                } else 1.0
                generatedSnd[i] = (value * 32767.0 * volume * envelope * 0.15).toInt().toShort()
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                audioTrack.write(generatedSnd, 0, numSamples)
                audioTrack.play()
                kotlinx.coroutines.delay((durationSeconds * 1000).toLong() + 100)
            } finally {
                audioTrack.release()
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
