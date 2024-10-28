package com.chitu.kepler.utils

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import androidx.camera.core.ImageProxy


class H264Encoder(val width: Int = 480, val height: Int = 640) {

    // The name of the codec to be instantiated.
    private val mimeType = MediaFormat.MIMETYPE_VIDEO_AVC
    private lateinit var mediaCodec: MediaCodec
    private var isStopHardcode = false

    fun init() {
        isStopHardcode = true

        mediaCodec = MediaCodec.createEncoderByType(mimeType)
        val mediaFormat = MediaFormat.createVideoFormat(mimeType, height, width)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 500)
        mediaFormat.setInteger(MediaFormat.KEY_DURATION, 6 * 1000 * 1000)
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 25)
        // mediaFormat.setInteger(MediaFormat.KEY_CAPTURE_RATE,30)
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        mediaFormat.setInteger(
            MediaFormat.KEY_COLOR_FORMAT,
            MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
        )
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        mediaCodec.start()
    }

    fun encode(data: ByteArray): ByteArray {
        val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1)
        if (inputBufferIndex >= 0) {
            val inputBuffer = mediaCodec.getInputBuffer(inputBufferIndex)
            inputBuffer!!.put(data)
            mediaCodec.queueInputBuffer(inputBufferIndex, 0, data.size, 1000000, 0)
        }

        var h264Data = byteArrayOf()
        val bufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
        val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 1000)
        if (outputBufferIndex >= 0) {
            val outputBuffer = mediaCodec.getOutputBuffer(outputBufferIndex)
            val remaining = outputBuffer!!.remaining()
            h264Data = ByteArray(remaining)
            outputBuffer.get(h264Data)
            mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
        }
        return h264Data
    }

    companion object {
        fun getYUVDataFromImageProxy(imageProxy: ImageProxy): ByteArray {
            // 获取 ImageProxy 的宽度和高度
            val width = imageProxy.width
            val height = imageProxy.height

            // 创建一个足够大的数组来存储 YUV 数据
            val yuvSize = width * height * 3 / 2
            val yuvBytes = ByteArray(yuvSize)

            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val remainingY = yBuffer.remaining()
            val remainingU = uBuffer.remaining()
            val remainingV = vBuffer.remaining()

            yBuffer[yuvBytes, 0, remainingY]
            vBuffer[yuvBytes, remainingY, remainingU]

            yuvBytes[remainingY + remainingV] = uBuffer[remainingU - 1]
            return yuvBytes
        }
    }
}

