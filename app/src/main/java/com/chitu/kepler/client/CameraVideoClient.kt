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
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock

class CameraVideoClient(private val context: Context) {
    private lateinit var cameraExecutor: ExecutorService
    private val originQueue = ArrayBlockingQueue<ByteArray>(1000, true)
    private val encodeQueue = ArrayBlockingQueue<ByteArray>(1000, true)
    private lateinit var h264Encoder: H264Encoder

    private var isStop = false
    private val lock = ReentrantReadWriteLock()
    private val readLock = lock.readLock()
    private val writeLock = lock.writeLock()

    private var videoFile = File(context.filesDir, "kepler.h264")

    fun init() {
        cameraExecutor = Executors.newSingleThreadExecutor()
        h264Encoder = H264Encoder().apply { init() }
    }

    fun start(imageAnalysis: ImageAnalysis) {
        CoroutineScope(Dispatchers.IO).launch {
            encodeTask()
        }

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

    fun setImage(imageProxy: ImageProxy) {
        val data = H264Encoder.getYUVDataFromImageProxy(imageProxy)
        originQueue.offer(data)
    }

    fun takeVideo(): ByteArray {
        var data = byteArrayOf()

        while (encodeQueue.isNotEmpty()) {
            data += encodeQueue.poll()!!
        }
        h264Encoder = H264Encoder().apply { init() }

        // 测试验证
        // videoFile = File(context.filesDir, "kepler." + System.currentTimeMillis() + ".h264")
        // FileOutputStream(videoFile).use { stream ->
        //     stream.write(data)
        //     stream.close()
        // }

        return data
    }

    private fun encodeTask() {
        while (!isStop) {
            val data = originQueue.poll()
            if (data != null && data.isNotEmpty()) {
                val encode = h264Encoder.encode(data)
                if (encode.isNotEmpty()) {
                    encodeQueue.offer(encode)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CameraVideoClient"
        private const val PHOTO_TYPE = "image/jpeg"
    }
}