package com.wavecat.outline.ai

class GenerationResult(val code: String, val problems: List<String>)

class ScriptAssistant(private val model: ChatModel) {
    suspend fun generate(
        task: String,
        currentCode: String? = null,
        onProgress: (String) -> Unit = {},
    ): GenerationResult = reply(start(task, currentCode), onProgress)

    suspend fun reply(
        conversation: MutableList<ChatMessage>,
        onProgress: (String) -> Unit = {},
    ): GenerationResult {
        model.prepare(onProgress)

        var code = extractCode(ask(conversation))
        var problems = ScriptValidator.validate(code)

        if (problems.isNotEmpty()) {
            conversation += ChatMessage(USER, LuaPrompt.repair(problems))
            code = extractCode(ask(conversation))
            problems = ScriptValidator.validate(code)
        }

        return GenerationResult(code, problems)
    }

    private suspend fun ask(conversation: MutableList<ChatMessage>): String =
        model.chat(conversation).also { conversation += ChatMessage(ASSISTANT, it) }

    companion object {
        const val SYSTEM = "system"
        const val USER = "user"
        const val ASSISTANT = "assistant"

        private val thinking = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)
        private val fence = Regex("```[a-zA-Z]*[ \\t]*\\r?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

        fun start(task: String, currentCode: String? = null): MutableList<ChatMessage> = mutableListOf(
            ChatMessage(SYSTEM, LuaPrompt.system),
            ChatMessage(USER, LuaPrompt.task(task, currentCode)),
        )

        fun extractCode(reply: String): String {
            val text = reply.replace(thinking, "")

            return (fence.find(text)?.groupValues?.get(1) ?: text).trimEnd().trimStart('\n', '\r')
        }
    }
}
