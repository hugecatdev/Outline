package com.wavecat.outline.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenAiChatModel(private val settings: AiSettings) : ChatModel {
    override suspend fun chat(messages: List<ChatMessage>): String {
        val body = JSONObject()
            .put("model", settings.model.trim())
            .put("messages", JSONArray(messages.map { JSONObject().put("role", it.role).put("content", it.content) }))

        val response = request("chat/completions", body.toString())

        return parse(response) {
            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .optString("content")
                .ifBlank { throw AiException("Empty response") }
        }
    }

    suspend fun listModels(): List<String> {
        val response = request("models", null)

        return parse(response) {
            val data = JSONObject(response).getJSONArray("data")

            (0 until data.length())
                .map { data.getJSONObject(it).getString("id") }
                .sorted()
        }
    }

    private suspend fun request(path: String, body: String?): String = withContext(Dispatchers.IO) {
        val connection = URL(settings.baseUrl.trim().trimEnd('/') + "/" + path)
            .openConnection() as HttpURLConnection

        val cancellation = coroutineContext.job.invokeOnCompletion { connection.disconnect() }

        try {
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            if (settings.apiKey.isNotBlank())
                connection.setRequestProperty("Authorization", "Bearer ${settings.apiKey.trim()}")

            if (body != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299)
                throw AiException(errorMessage(response) ?: "HTTP $code")

            response
        } finally {
            cancellation.dispose()
            connection.disconnect()
        }
    }

    private inline fun <T> parse(response: String, block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw AiException("Unexpected response: ${response.take(200)}")
    }

    private fun errorMessage(response: String): String? = try {
        val error = JSONObject(response).opt("error")

        if (error is JSONObject) error.optString("message").ifBlank { null } else error?.toString()
    } catch (e: JSONException) {
        response.take(200).ifBlank { null }
    }

    private companion object {
        const val CONNECT_TIMEOUT = 15_000
        const val READ_TIMEOUT = 180_000
    }
}
