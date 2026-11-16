package com.wavecat.outline.ai

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import com.wavecat.outline.R

data class AiSettings(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val provider: Provider = Provider.OPENAI,
    val nanoPreview: Boolean = false,
) {
    enum class Provider { OPENAI, GEMINI_NANO }

    val isComplete: Boolean
        get() = when (provider) {
            Provider.OPENAI -> baseUrl.isNotBlank() && model.isNotBlank()
            Provider.GEMINI_NANO -> isNanoSupported
        }

    fun createModel(context: Context): ChatModel = when (provider) {
        Provider.OPENAI -> OpenAiChatModel(this)

        Provider.GEMINI_NANO ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) GeminiNanoChatModel(context, nanoPreview)
            else throw AiException(context.getString(R.string.ai_nano_unavailable))
    }

    fun save(context: Context) = preferences(context).edit {
        putString(BASE_URL, baseUrl)
        putString(API_KEY, apiKey)
        putString(MODEL, model)
        putString(PROVIDER, provider.name)
        putBoolean(NANO_PREVIEW, nanoPreview)
    }

    companion object {
        const val PREFERENCES = "ai"
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"

        private const val BASE_URL = "base_url"
        private const val API_KEY = "api_key"
        private const val MODEL = "model"
        private const val PROVIDER = "provider"
        private const val NANO_PREVIEW = "nano_preview"

        val isNanoSupported: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        fun load(context: Context): AiSettings {
            val preferences = preferences(context)

            return AiSettings(
                baseUrl = preferences.getString(BASE_URL, null) ?: DEFAULT_BASE_URL,
                apiKey = preferences.getString(API_KEY, null).orEmpty(),
                model = preferences.getString(MODEL, null).orEmpty(),
                provider = Provider.entries.find { it.name == preferences.getString(PROVIDER, null) }
                    ?: Provider.OPENAI,
                nanoPreview = preferences.getBoolean(NANO_PREVIEW, false),
            )
        }

        private fun preferences(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    }
}
