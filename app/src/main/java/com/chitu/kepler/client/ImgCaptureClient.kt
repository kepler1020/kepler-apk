package com.chitu.kepler.client

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class ImgCaptureClient(private val context: Context) {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scheduledExecutorService: ScheduledExecutorService

    private var imageCapture: ImageCapture? = null

    fun init() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    }

    fun start() {
        scheduledExecutorService.scheduleWithFixedDelay({
            captureImage()
        }, 0, 2, TimeUnit.SECONDS)
    }

    fun stop() {
        scheduledExecutorService.shutdown()
    }

    fun setImageCapture(imageCapture: ImageCapture?) {
        this.imageCapture = imageCapture
    }

    private fun captureImage() {
        val file = File(context.filesDir, "kepler.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

        imageCapture?.takePicture(outputOptions, cameraExecutor, object :
            ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
              Log.d(TAG, "Photo capture succeeded: ${output.savedUri}")
                // val image = output.savedUri?.toFile()
                // val stream = FileInputStream(image).use { inputStream ->
                //     inputStream.readBytes()
                // }
            }
        })
    }

    fun takeImage(): ByteArray {
        val file = File(context.filesDir, "kepler.jpg")
        val stream = FileInputStream(file).use { inputStream ->
            inputStream.readBytes()
        }
        return stream
    }

    companion object {
        private const val TAG = "RecorderClient"
        private const val PHOTO_TYPE = "image/jpeg"
    }
}