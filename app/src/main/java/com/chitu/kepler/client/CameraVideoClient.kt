package com.chitu.kepler.client

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.chitu.kepler.utils.H264Encoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock

class CameraVideoClient(private val context: Context) {
    private lateinit var cameraExecutor: ExecutorService
    private val originQueue = ArrayBlockingQueue<ByteArray>(100, true)

    private var isStop = false
    private var isTake = false

    private var videoFile = File(context.filesDir, "kepler.h264")

    fun init() {
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    fun start(imageAnalysis: ImageAnalysis) {
        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
            setImage(imageProxy)
            imageProxy.close()
        }
    }

    fun stop() {
        isStop = true
        originQueue.clear()
        Log.i(TAG, "video recording stop")
    }

    private fun setImage(imageProxy: ImageProxy) {
        if (isTake) return

        Log.e(TAG, "insert video image")
        val data = H264Encoder.getYUVDataFromImageProxy(imageProxy)
        originQueue.offer(data)
    }

    fun takeVideo(): ByteArray {
        isTake = true
        var data = byteArrayOf()

        Log.d(TAG, "start take video")
        val h264Encoder = H264Encoder().apply { init() }
        while (originQueue.isNotEmpty()) {
            Log.d(TAG, ".queue size: ${originQueue.size}, ${data.size}")
            val d = originQueue.poll(100, TimeUnit.MILLISECONDS) ?: break
            Log.d(TAG, "..queue size: ${originQueue.size}, ${data.size}")
            data += h264Encoder.encode(d)

            Log.d(TAG, "...queue size: ${originQueue.size}, ${data.size}")
            if (data.size > 500 * 1024) break
        }
        isTake = false

        // 测试验证
        if (data.isEmpty()) {
            Log.e(TAG, "take empty video")
            return data
        }

        val time = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"))
        videoFile = File(context.filesDir, "kepler-${time}.h264")
        FileOutputStream(videoFile).use { stream ->
            stream.write(data)
            stream.close()
        }

        Log.e(TAG, "take video size: ${data.size}")
        return data
    }

    companion object {
        private const val TAG = "CameraVideoClient"
        private const val PHOTO_TYPE = "image/jpeg"
    }
}