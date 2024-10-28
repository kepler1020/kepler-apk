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

class CameraImageClient(private val context: Context) {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var scheduledExecutorService: ScheduledExecutorService

    private var imageCapture: ImageCapture? = null
    private val imageFile = File(context.filesDir, "kepler.jpg")
    private val imageByte = byteArrayOf()

    fun init() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    }

    fun start(imageCapture: ImageCapture) {
        scheduledExecutorService.scheduleWithFixedDelay({
            captureImage()
        }, 0, 2, TimeUnit.SECONDS)

        imageFile.deleteOnExit()
        this.imageCapture = imageCapture
    }

    fun stop() {
        scheduledExecutorService.shutdown()
    }

    private fun captureImage() {
        if (scheduledExecutorService.isShutdown) return

        val outputOptions = ImageCapture.OutputFileOptions.Builder(imageFile).build()

        imageCapture?.takePicture(outputOptions, cameraExecutor, object :
            ImageCapture.OnImageSavedCallback {
            override fun onError(exception: ImageCaptureException) {
                Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
              Log.i(TAG, "Photo capture succeeded: ${output.savedUri}")
                // val image = output.savedUri?.toFile()
                // val stream = FileInputStream(image).use { inputStream ->
                //     inputStream.readBytes()
                // }
            }
        })
    }

    fun takeImage(): ByteArray {
        val stream = FileInputStream(imageFile).use { inputStream ->
            inputStream.readBytes()
        }
        return stream
    }

    companion object {
        private const val TAG = "CameraImageClient"
        private const val PHOTO_TYPE = "image/jpeg"
    }
}