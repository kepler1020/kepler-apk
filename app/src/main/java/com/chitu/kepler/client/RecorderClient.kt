package com.chitu.kepler.client

import android.content.Context
import android.util.Log
import tech.oom.idealrecorder.IdealRecorder
import tech.oom.idealrecorder.StatusListener
import java.io.File
import java.io.FileInputStream

class RecorderClient(private val context: Context) : StatusListener() {
    private val recorder: IdealRecorder = IdealRecorder.getInstance()
    private var saveFile = File(context.filesDir, "kepler.wav")

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
            saveFile = File(context.filesDir, "kepler" + System.currentTimeMillis() + ".wav")
            setRecordFilePath(saveFile.absolutePath)
            setRecordConfig(config)
            setMaxRecordTime(Long.MAX_VALUE)
            setVolumeInterval(1000)
            setStatusListener(this@RecorderClient)
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
        stop()
        var stream: ByteArray = byteArrayOf()
        if (saveFile.exists()) {
            stream = FileInputStream(saveFile).use { inputStream ->
                inputStream.readBytes()
            }
            saveFile.delete()
        }
        start()
        return stream
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

        // data?.let {
        //     val byteArray = ByteArray(data.size * 2)
        //     for (i in data.indices) {
        //         val shortValue = data[i].toShort()
        //         val index = i * 2
        //         byteArray[index] = (shortValue.toInt() shr 8).toByte()
        //         byteArray[index + 1] = shortValue.toByte()
        //     }
        //
        //     val outputStream = ByteArrayOutputStream()
        //     outputStream.write(recordData)
        //     outputStream.write(byteArray)
        //     recordData = outputStream.toByteArray()
        // }
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
    }
}