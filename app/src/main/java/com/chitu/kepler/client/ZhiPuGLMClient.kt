package com.chitu.kepler.client

import android.content.Context
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.chitu.kepler.model.XRequest
import com.chitu.kepler.model.XResponse
import com.google.gson.Gson
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.TimeUnit


class ZhiPuGLMClient(private val context: Context, private val callback:(String)->Unit): WebSocketListener() {
    private var wsClient: XWebSocket? = null

    override fun onOpen(webSocket: WebSocket, response: Response) {
        Log.i(TAG, "onOpen")
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.i(TAG, "onMessage, byte")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.i(TAG, "onMessage, string -> ${text.length}")

        val gson = Gson()
        val response = gson.fromJson(text, XResponse::class.java)
        val message = response.message

        when(message.type) {
            "error" -> {
                Log.e(TAG, "response content: ${response.error}")
                // Toast.makeText(context, "ZhiPu-GLM message error: ${response.error}", Toast.LENGTH_SHORT).show()
            }
            "event" -> {
                Log.i(TAG, "response content: $response")
                if (message.content == "finish") {
                    callback(message.content)
                }
            }
            "audio" -> {
                callback(message.content)
                Log.i(TAG, "message content: ${message.type}")
            }
            else -> {
                Log.w(TAG, "response: $response")
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "onClosing")
        // Toast.makeText(context, "ZhiPu-GLM closing", Toast.LENGTH_SHORT).show()
        wsClient = null
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.i(TAG, "onClosed")
        // Toast.makeText(context, "ZhiPu-GLM closed", Toast.LENGTH_SHORT).show()

        wsClient = null
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.i(TAG, "onFailure")
        // Toast.makeText(context, "ZhiPu-GLM onFailure; $response", Toast.LENGTH_SHORT).show()

        t.printStackTrace()
        Log.i(TAG, "onFailure, $response")

    }

    fun connect() {
        if (wsClient == null) {
            wsClient = XWebSocket(this)
            wsClient?.connect()
        } else {
            Log.e(TAG, "wsclient is connected")
        }
    }

    fun sendImage(image: String, audio: String) {
        if (wsClient == null) connect()

        val request = XRequest().apply {
            picChunk = image
            audioChunk = audio
            control.responseType = "audio"
        }

        val message = Gson().toJson(request, XRequest::class.java)
        wsClient?.sendMessage(message)

        Log.e(TAG, "send image to zhipu: ${message.length}, ${image.length}, ${audio.length}")
    }

    fun sendVideo(video: String, audio: String) {
        if (wsClient == null) connect()

        val request = XRequest().apply {
            videoChunk = video
            audioChunk = audio
            control.responseType = "audio"
        }

        val message = Gson().toJson(request, XRequest::class.java)
        wsClient?.sendMessage(message)

        Log.e(TAG, "send video to zhipu: ${message.length}, ${video.length}, ${audio.length}")
    }


    fun disconnect() {
        wsClient?.disconnect()
    }

    companion object {
        private const val TAG = "ZhiPuAiClient"
    }

    class XWebSocket(private val listener: WebSocketListener) {
        private val client = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // 设置读取超时时间为无限
            .writeTimeout(0, TimeUnit.MILLISECONDS) // 设置写入超时时间为无限
            .build()

        private var wsClient: WebSocket? = null

        fun connect() {
            val headers = Headers
                .headersOf("Authorization", "Bearer $KEY")
                .newBuilder()
                .build()

            val request = Request.Builder().url(URI).headers(headers).build()
            wsClient = client.newWebSocket(request, listener)
        }

        fun sendMessage(text: String) {
            wsClient?.send(text)
        }

        fun disconnect() {
            client.dispatcher.executorService.shutdown()
            wsClient?.close(1000, "close app")
        }

        companion object {
            private const val URI = "wss://open.bigmodel.cn/api/paas/ws/chat"
            private const val KEY = "****"
        }
    }
}