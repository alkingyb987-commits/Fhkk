package com.example.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sin

class AudioHapticHelper(private val context: Context) {

    private val playScope = CoroutineScope(Dispatchers.IO)

    /**
     * Synthesizes and plays a classic laser sweep audio effect:
     * Sweeps from 1500Hz down to 250Hz rapidly.
     */
    fun playLaserSound() {
        playScope.launch {
            try {
                val sampleRate = 22050
                val durationMs = 180
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    // Descending laser sweep sound
                    val currentFreq = 1600f * (1f - progress) + 250f
                    val value = sin(2.0 * Math.PI * currentFreq * t.toDouble())
                    // Slide down volume slightly at the tail end
                    val envelope = if (progress > 0.8f) (1f - progress) / 0.2f else 1f
                    buffer[i] = (value * 32767f * 0.45f * envelope).toInt().toShort()
                }

                playBuffer(buffer, sampleRate, durationMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Synthesizes and plays an explosion/impact hit sound:
     * Fast sweep up and down of a buzz wave.
     */
    fun playHitSound() {
        playScope.launch {
            try {
                val sampleRate = 22050
                val durationMs = 280
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    // High-interest explosive frequency
                    val currentFreq = 300f + 500f * sin(progress * Math.PI)
                    val arg = 2.0 * Math.PI * currentFreq * t.toDouble()
                    // Use a square wave shape for organic high-impact buzz
                    val baseValue = if (sin(arg) > 0) 1.0 else -1.0
                    val envelope = 1f - progress // Linear fade
                    buffer[i] = (baseValue * 32767f * 0.35f * envelope).toInt().toShort()
                }

                playBuffer(buffer, sampleRate, durationMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Synthesizes a dry clicking error sound when out of ammunition.
     */
    fun playNoAmmoSound() {
        playScope.launch {
            try {
                val sampleRate = 22050
                val durationMs = 50
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    val value = sin(2.0 * Math.PI * 150f * t.toDouble())
                    val envelope = 1f - progress
                    buffer[i] = (value * 32767f * 0.25f * envelope).toInt().toShort()
                }

                playBuffer(buffer, sampleRate, durationMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Synthesizes sound indicating reload start and end.
     */
    fun playReloadSound() {
        playScope.launch {
            try {
                val sampleRate = 22050
                val durationMs = 400
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)

                // Double futuristic reload beep
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / sampleRate
                    val progress = i.toFloat() / numSamples
                    val freq = if (progress < 0.5f) 660f else 880f
                    val value = sin(2.0 * Math.PI * freq * t.toDouble())
                    val envelope = if (progress < 0.5f) {
                        (0.5f - progress) / 0.5f
                    } else {
                        (1f - progress) / 0.5f
                    }
                    buffer[i] = (value * 32767f * 0.3f * envelope).toInt().toShort()
                }

                playBuffer(buffer, sampleRate, durationMs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun playBuffer(buffer: ShortArray, sampleRate: Int, durationMs: Int) {
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        audioTrack.write(buffer, 0, buffer.size)
        audioTrack.play()
        
        playScope.launch {
            delay(durationMs.toLong() + 100)
            try {
                audioTrack.release()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Physical device haptic feedback.
     */
    @Suppress("DEPRECATION")
    fun vibrate(durationMs: Long) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vibrator.vibrate(durationMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
