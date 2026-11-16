package com.wavecat.outline.ai

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.mlkit.common.MlKit
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import com.wavecat.outline.R
import kotlinx.coroutines.flow.takeWhile

@RequiresApi(Build.VERSION_CODES.O)
class GeminiNanoChatModel(context: Context, private val preview: Boolean) : ChatModel {
    private val context = context.applicationContext
    private var client: GenerativeModel? = null

    suspend fun status(): Int = guarded { client().checkStatus() }

    override suspend fun prepare(onProgress: (String) -> Unit) = guarded {
        val model = client()

        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> {}
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> download(model, onProgress)
            else -> throw AiException(context.getString(R.string.ai_nano_unavailable))
        }
    }

    override suspend fun chat(messages: List<ChatMessage>): String = guarded {
        val model = client()
        val system = messages.filter { it.role == SYSTEM }.joinToString("\n\n") { it.content }
        val dialog = messages.filter { it.role != SYSTEM }

        val prompt =
            if (dialog.size == 1) dialog.single().content
            else dialog.joinToString("\n\n") { "${it.role.replaceFirstChar(Char::uppercaseChar)}:\n${it.content}" } + "\n\nAssistant:"

        val separateSystem = system.isNotEmpty() && model.isSystemPromptAvailable()
        val text = TextPart(if (separateSystem || system.isEmpty()) prompt else "$system\n\n$prompt")

        val request =
            if (separateSystem) generateContentRequest(SystemInstruction(system), text) { temperature = TEMPERATURE }
            else generateContentRequest(text) { temperature = TEMPERATURE }

        model.generateContent(request).candidates.firstOrNull()?.text?.ifBlank { null }
            ?: throw AiException(context.getString(R.string.ai_empty_response))
    }

    override fun close() {
        client?.close()
        client = null
    }

    private suspend fun client(): GenerativeModel {
        client?.let { return it }

        initializeMlKit(context)

        if (preview) {
            val previewClient = Generation.getClient(config(ModelReleaseStage.PREVIEW))

            if (previewClient.checkStatus() != FeatureStatus.UNAVAILABLE)
                return previewClient.also { client = it }

            previewClient.close()
        }

        return Generation.getClient(config(ModelReleaseStage.STABLE)).also { client = it }
    }

    private fun config(stage: Int) = generationConfig {
        modelConfig = modelConfig {
            releaseStage = stage
            preference = ModelPreference.FULL
        }
    }

    private suspend fun download(model: GenerativeModel, onProgress: (String) -> Unit) {
        var total = 0L

        onProgress(context.getString(R.string.ai_nano_downloading_started))

        model.download()
            .takeWhile { it !is DownloadStatus.DownloadCompleted }
            .collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> total = status.bytesToDownload

                    is DownloadStatus.DownloadProgress -> if (total > 0) {
                        val percent = (status.totalBytesDownloaded * 100 / total).toInt().coerceIn(0, 100)
                        onProgress(context.getString(R.string.ai_nano_downloading, percent))
                    }

                    is DownloadStatus.DownloadFailed -> throw AiException(describe(status.e))

                    else -> {}
                }
            }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: GenAiException) {
        throw AiException(describe(e))
    }

    private fun describe(e: GenAiException): String = when (e.errorCode) {
        GenAiException.ErrorCode.BUSY -> context.getString(R.string.ai_nano_busy)
        GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED -> context.getString(R.string.ai_nano_quota)
        GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED -> context.getString(R.string.ai_nano_background)
        GenAiException.ErrorCode.REQUEST_TOO_LARGE -> context.getString(R.string.ai_nano_too_large)
        GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> context.getString(R.string.ai_nano_disk)
        GenAiException.ErrorCode.NOT_AVAILABLE,
        GenAiException.ErrorCode.NOT_SUPPORTED,
        GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
        GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE -> context.getString(R.string.ai_nano_unavailable)

        else -> e.message ?: "AICore error ${e.errorCode}"
    }

    private companion object {
        const val SYSTEM = "system"
        const val TEMPERATURE = 0.2f

        private var mlKitInitialized = false

        @Synchronized
        fun initializeMlKit(context: Context) {
            if (!mlKitInitialized) {
                MlKit.initialize(context)
                mlKitInitialized = true
            }
        }
    }
}
