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
import androidx.camera.core.ImageAnalysis
import androidx.camera.video.VideoCapture
import androidx.camera.video.Recorder
import kotlinx.coroutines.delay
import java.util.LinkedList

class KeplerClient(private val context: Context, private val messageType: String = MESSAGE_TYPE_IMAGE) {
    // Major components
    private lateinit var zhiPuAiClient: ZhiPuGLMClient

    private lateinit var audioRecordClient: AudioRecordClient
    private lateinit var audioPlayClient: AudioPlayClient

    private lateinit var cameraImageClient: CameraImageClient
    private lateinit var cameraVideoClient: CameraVideoClient

    private lateinit var scheduledExecutorService: ScheduledExecutorService
    private var audios: LinkedList<String> = LinkedList()

    fun init() {
        audioPlayClient = AudioPlayClient(context).apply { init() }
        audioRecordClient = AudioRecordClient(context).apply { init() }

        when(messageType) {
            MESSAGE_TYPE_IMAGE -> cameraImageClient = CameraImageClient(context).apply { init() }
            MESSAGE_TYPE_VIDEO -> cameraVideoClient = CameraVideoClient(context).apply { init() }
        }
        zhiPuAiClient = ZhiPuGLMClient(context) { data ->
            this.audios.add(data)
        }

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
        scheduledExecutorService.scheduleWithFixedDelay({
            if (audios.isEmpty()) collect()
        }, 1, FIXED_INTERVAL, TimeUnit.SECONDS)
    }

    fun start(imageCapture: ImageCapture?) {
        CoroutineScope(Dispatchers.IO).launch {
            zhiPuAiClient.connect()

            delay(1000)
            audioRecordClient.start()
            cameraImageClient.start(imageCapture!!)

            playAudio()
        }
    }

    fun start(imageAnalysis: ImageAnalysis?) {
        CoroutineScope(Dispatchers.IO).launch {
            zhiPuAiClient.connect()

            delay(1000)
            audioRecordClient.start()
            cameraVideoClient.start(imageAnalysis!!)

            playAudio()
        }
    }


    fun stop() {
        zhiPuAiClient.disconnect()

        audioPlayClient.release()
        audioRecordClient.stop()

        when(messageType) {
            MESSAGE_TYPE_IMAGE -> cameraImageClient.stop()
            MESSAGE_TYPE_VIDEO -> cameraVideoClient.stop()
        }

        scheduledExecutorService.shutdown()
    }

    private suspend fun playAudio() {
        while (true) {
            if (audios.isEmpty()) {
                delay(100)
                continue
            }

            val data = audios.removeFirst()
            if (data == "finish") {
                audioRecordClient.start()
            } else {
                if (audioRecordClient.recording) { audioRecordClient.stop() }
                val audio = Base64.decode(data, Base64.NO_WRAP)
                audioPlayClient.play(audio)
            }
        }
    }

    private fun collect() {
        audioRecordClient.stop()

        when(messageType) {
            MESSAGE_TYPE_IMAGE -> sendImage()
            MESSAGE_TYPE_VIDEO -> sendVideo()
        }

        audioRecordClient.start()
    }

    private fun sendImage() {
        val audio = audioRecordClient.takeAudio()
        val image = cameraImageClient.takeImage()

        val b64Audio = Base64.encodeToString(audio, Base64.NO_WRAP)
        val b64Image = Base64.encodeToString(image, Base64.NO_WRAP)

        zhiPuAiClient.sendImage(b64Image, b64Audio)
    }

    private fun sendVideo() {
        val audio = audioRecordClient.takeAudio()
        val video = cameraVideoClient.takeVideo()

        val b64Audio = Base64.encodeToString(audio, Base64.NO_WRAP)
        val b64Video = Base64.encodeToString(video, Base64.NO_WRAP)

        zhiPuAiClient.sendVideo(b64Video, b64Audio)
    }

    companion object {
        private const val FIXED_INTERVAL = 2L

        private const val MESSAGE_TYPE_IMAGE = "image"
        private const val MESSAGE_TYPE_VIDEO = "video"
    }
}