package com.chitu.kepler.client

import android.content.Context
import android.util.Log
import tech.oom.idealrecorder.IdealRecorder
import tech.oom.idealrecorder.StatusListener
import tech.oom.idealrecorder.utils.BytesTransUtil
import java.io.File
import java.io.FileInputStream
import kotlin.math.abs

class AudioRecordClient(private val context: Context) : StatusListener() {
    private val recorder: IdealRecorder = IdealRecorder.getInstance()
    private var saveFile = File(context.filesDir, "kepler.wav")


    private var recordData: ByteArray = byteArrayOf()

    var recording: Boolean = false

    fun init() {
        recorder.init(context)
    }

    fun start() {
        if (recording) {
            Log.w(TAG, "recorder is running")
            return
        }

        val config = IdealRecorder.RecordConfig().apply {
            sampleRate = 48000
        }
        recorder.apply {
            saveFile = File(context.filesDir, "kepler.wav")
            setRecordFilePath(saveFile.absolutePath)
            setRecordConfig(config)
            setMaxRecordTime(Long.MAX_VALUE)
            setVolumeInterval(1000)
            setStatusListener(this@AudioRecordClient)
            start()
        }
    }

    fun stop() {
        if (!recording) {
            Log.w(TAG, "recorder is stop")
            return
        }
        recorder.stop()
    }

    fun takeAudio(): ByteArray {
        fun takeFile(): ByteArray {
            var stream: ByteArray = byteArrayOf()
            if (saveFile.exists()) {
                stream = FileInputStream(saveFile).use { inputStream ->
                    inputStream.readBytes()
                }
                saveFile.delete()
            }
            return stream
        }

        fun takeByte(): ByteArray {
            var stream: ByteArray = byteArrayOf()
            stream = recordData
            recordData = byteArrayOf()
            return stream
        }

        val stream = takeFile()
        // val stream = takeByte()

        return stream
    }

    private fun calculateVolume(buffer: ShortArray, size: Int): Int {
        var sum = 0
        for (i in 0 until size) {
            sum += abs(buffer[i].toInt())
        }
        return sum / size
    }

    override fun onStartRecording() {
        Log.i(TAG, "recorder start")
        recording = true
    }

    override fun onStopRecording() {
        Log.i(TAG, "recorder stop")
        recording = false
    }

    override fun onRecordData(data: ShortArray?, length: Int) {
        // Log.i(TAG, "recorder data1, $length")

        // recordData += BytesTransUtil.getInstance().Shorts2Bytes(data)
        val v = calculateVolume(data!!, length)
        if (v > MAX_VOLUME) {
            // Log.i(TAG, "volume value: $v")
        }
    }

    override fun onRecordDataOnWorkerThread(data: ShortArray?, length: Int) {
        // Log.i(TAG, "recorder data2, $length")
    }

    override fun onRecordedAllData(arr: ByteArray?) {
        Log.i(TAG, "recorder all data2, ${arr?.size}")
    }

    override fun onVoiceVolume(volume: Int) {
        // Log.i(TAG, "recorder voice volume, $volume")
    }

    override fun onRecordError(code: Int, errorMsg: String?) {
        Log.e(TAG, "recorder fail, $code, $errorMsg")
    }

    override fun onFileSaveFailed(error: String?) {
        Log.e(TAG, "recorder file save failed, $error")
    }

    override fun onFileSaveSuccess(fileUri: String?) {
        Log.i(TAG, "recorder file save success, $fileUri")
    }

    companion object {
        private const val TAG = "RecorderClient"
        private const val MAX_VOLUME = 500
    }
}