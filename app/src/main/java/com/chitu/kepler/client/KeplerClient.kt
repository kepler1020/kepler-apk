package com.chitu.kepler.client

import android.content.Context
import androidx.camera.core.ImageCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import android.util.Base64
import kotlinx.coroutines.delay
import java.util.LinkedList

class KeplerClient(private val context: Context) {
    // Major components
    private lateinit var zhiPuAiClient: ZhiPuGLMClient
    private lateinit var recorderClient: RecorderClient
    private lateinit var audioPlayClient: AudioPlayClient
    private lateinit var imgCaptureClient: ImgCaptureClient

    private lateinit var scheduledExecutorService: ScheduledExecutorService
    private var audios: LinkedList<String> = LinkedList()

    fun init() {
        recorderClient = RecorderClient(context).apply {
            init {
                // imgCaptureClient.captureImage()
            }
            start()
        }
        imgCaptureClient = ImgCaptureClient(context).apply {
            init()
            start()
        }
        audioPlayClient = AudioPlayClient(context).apply {
            init()
        }

        zhiPuAiClient = ZhiPuGLMClient(context) { data ->
            audios.add(data)
        }

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                if (audios.isEmpty()) {
                    delay(100)
                    continue
                }

                val data = audios.removeFirst()
                if (data == "finish") {
                    recorderClient.start()
                } else {
                    if (recorderClient.recording) recorderClient.stop()
                    val audio = Base64.decode(data, Base64.NO_WRAP)
                    audioPlayClient.play(audio)
                }
            }
        }

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutorService.scheduleWithFixedDelay({
            if (audios.isEmpty()) collect()
        }, 1, FIXED_INTERVAL, TimeUnit.SECONDS)
    }

    fun start() {
        CoroutineScope(Dispatchers.IO).launch {
            zhiPuAiClient.connect()
        }
    }

    fun setImageCapture(imageCapture: ImageCapture?) {
        imgCaptureClient.setImageCapture(imageCapture)
    }

    fun stop() {
        zhiPuAiClient.disconnect()
        audioPlayClient.release()
        recorderClient.stop()
        imgCaptureClient.stop()

        scheduledExecutorService.shutdown()
    }

    private fun collect() {
        val audio = recorderClient.takeAudio()
        val image = imgCaptureClient.takeImage()

        val b64Audio = Base64.encodeToString(audio, Base64.NO_WRAP)
        val b64Image = Base64.encodeToString(image, Base64.NO_WRAP)

        zhiPuAiClient.sendMessage(b64Image, b64Audio)
    }

    companion object {
        private const val FIXED_INTERVAL = 3L
    }
}