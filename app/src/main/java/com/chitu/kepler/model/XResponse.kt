package com.chitu.kepler.model

import com.google.gson.annotations.SerializedName

data class Message(
    val content: String,
    val role: String,
    val type: String,
)

data class Error (
    val code: String,
    val message: String,
)

data class Usage (
    @SerializedName("prompt_tokens")
    val promptTokens: Int,

    @SerializedName("completion_tokens")
    val completionTokens: Int,

    @SerializedName("total_tokens")
    val totalTokens: Int,
)

data class XResponse(
    @SerializedName("conversation_id")
    val conversationId: String,

    @SerializedName("message_id")
    val messageId: String,

    @SerializedName("created")
    val created: Long?,

    @SerializedName("message")
    val message: Message,

    @SerializedName("usage")
    val usage: Usage,

    @SerializedName("error")
    val error: Error?
)
