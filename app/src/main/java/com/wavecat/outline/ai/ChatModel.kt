package com.wavecat.outline.ai

data class ChatMessage(val role: String, val content: String)

interface ChatModel : AutoCloseable {
    suspend fun prepare(onProgress: (String) -> Unit) {}

    suspend fun chat(messages: List<ChatMessage>): String

    override fun close() {}
}

class AiException(message: String) : Exception(message)
