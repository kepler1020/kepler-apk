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
        Log.d(TAG, "onOpen")
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "onMessage, byte")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "onMessage, string -> ${text.length}")

        val gson = Gson()
        val response = gson.fromJson(text, XResponse::class.java)
        val message = response.message

        when(message.type) {
            "error" -> {
                Log.e(TAG, "response content: ${response.error}")
                // Toast.makeText(context, "ZhiPu-GLM message error: ${response.error}", Toast.LENGTH_SHORT).show()
            }
            "event" -> {
                Log.d(TAG, "response content: $response")
                if (message.content == "finish") {
                    callback(message.content)
                }
            }
            "audio" -> {
                callback(message.content)
                Log.d(TAG, "message content: ${message.type}")
            }
            else -> {
                Log.w(TAG, "response: $response")
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "onClosing")
        // Toast.makeText(context, "ZhiPu-GLM closing", Toast.LENGTH_SHORT).show()
        wsClient = null
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        Log.d(TAG, "onClosed")
        // Toast.makeText(context, "ZhiPu-GLM closed", Toast.LENGTH_SHORT).show()

        wsClient = null
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        Log.d(TAG, "onFailure")
        // Toast.makeText(context, "ZhiPu-GLM onFailure; $response", Toast.LENGTH_SHORT).show()

        t.printStackTrace()
        Log.d(TAG, "onFailure, $response")

    }

    fun connect() {
        wsClient = XWebSocket(this)
        wsClient?.connect()
    }

    fun sendMessage(image: String, audio: String) {
        if (wsClient == null) connect()

        val request = XRequest().apply {
            picChunk = image
            audioChunk = audio
            control.responseType = "audio"
        }

        val message = Gson().toJson(request, XRequest::class.java)
        wsClient?.sendMessage(message)

        Log.d(TAG, "send message to zhipu: ${message.length}")
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
            wsClient?.close(0, "close app")
        }

        companion object {
            private const val URI = "wss://open.bigmodel.cn/api/paas/ws/chat"
            private const val KEY = "****"
        }
    }
}