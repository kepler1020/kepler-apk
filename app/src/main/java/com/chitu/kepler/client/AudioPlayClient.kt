package com.chitu.kepler.client

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log

class AudioPlayClient(private val context: Context) {
    private lateinit var player: AudioTrack

    fun init() {
        val sampleRateInHz = 24054
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT

        val buffer = AudioTrack.getMinBufferSize(
            sampleRateInHz,
            channelConfig,
            audioFormat
        )

        player = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRateInHz, channelConfig, audioFormat,
            buffer, AudioTrack.MODE_STREAM
        )

    }

    fun play(buffer: ByteArray) {
        Log.i(TAG, "play pcm data size: ${buffer.size}")

        player.play()
        player.write(buffer, 0, buffer.size)
        player.stop()
    }

    fun release() {
        player.release()
    }

    companion object {
        private const val TAG = "RecorderClient"
    }
}