package com.wavecat.outline.ai

import android.os.Build
import android.text.NoCopySpan
import android.text.Spanned
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.mlkit.genai.common.FeatureStatus
import com.wavecat.outline.R
import com.wavecat.outline.databinding.DialogAiPromptBinding
import com.wavecat.outline.databinding.DialogAiSettingsBinding
import com.wavecat.outline.editor.CodeEditText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class AiController(
    private val activity: AppCompatActivity,
    private val editor: CodeEditText,
    private val anchor: View,
) {
    private val generatedCode = object : NoCopySpan {}

    private var lastTask = ""
    private var conversation: MutableList<ChatMessage>? = null
    private var lastCode: String? = null
    private var generation: Job? = null

    init {
        editor.customSelectionActionModeCallback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, MENU_EDIT, Menu.NONE, R.string.ai_generate)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (item.itemId != MENU_EDIT)
                    return false

                showGenerateDialog()
                mode.finish()
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {}
        }
    }

    fun showGenerateDialog() {
        val settings = AiSettings.load(activity)

        if (!settings.isComplete) {
            showSettingsDialog(thenGenerate = true)
            return
        }

        val content = editor.text ?: return
        val start = min(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val end = max(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
        val selection = if (end > start) content.substring(start, end) else null

        showPrompt(
            title = if (selection == null) R.string.ai_prompt_title else R.string.ai_edit_title,
            hint = R.string.ai_prompt_hint,
            initial = lastTask,
        ) { task ->
            lastTask = task
            conversation = ScriptAssistant.start(task, selection)
            generate(settings, start, end, replace = selection != null)
        }
    }

    fun reply(message: String? = null) {
        val settings = AiSettings.load(activity)
        val content = editor.text ?: return
        val start = content.getSpanStart(generatedCode)
        val end = content.getSpanEnd(generatedCode)
        val history = conversation

        if (!settings.isComplete || history == null || start < 0 || end <= start) {
            showGenerateDialog()
            return
        }

        if (message == null) {
            showPrompt(R.string.ai_reply_title, R.string.ai_reply_hint, initial = "") { reply(it) }
            return
        }

        val current = content.substring(start, end)
        val followUp = ChatMessage(ScriptAssistant.USER, LuaPrompt.followUp(message, current.takeIf { it != lastCode }))
        generate(settings, start, end, replace = true, followUp)
    }

    fun showSettingsDialog(thenGenerate: Boolean = false) {
        val settings = AiSettings.load(activity)
        val binding = DialogAiSettingsBinding.inflate(activity.layoutInflater)
        var loading: Job? = null
        var checking: Job? = null
        var modelsRequested = false

        binding.baseUrl.setText(settings.baseUrl)
        binding.apiKey.setText(settings.apiKey)
        binding.model.setText(settings.model, false)
        binding.nanoPreview.isChecked = settings.nanoPreview
        binding.providerNano.isVisible = AiSettings.isNanoSupported

        fun loadModels(show: Boolean) {
            val current = AiSettings(binding.baseUrl.text.toString(), binding.apiKey.text.toString(), "")

            if (current.baseUrl.isBlank())
                return

            modelsRequested = true
            loading?.cancel()
            binding.modelLayout.helperText = activity.getString(R.string.ai_models_loading)

            loading = activity.lifecycleScope.launch {
                try {
                    val models = OpenAiChatModel(current).listModels()

                    binding.model.setSimpleItems(models.toTypedArray())
                    binding.modelLayout.helperText = activity.getString(R.string.ai_models_loaded, models.size)

                    if (show)
                        binding.model.showDropDown()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    binding.modelLayout.helperText =
                        activity.getString(R.string.ai_models_failed, e.message ?: e.javaClass.simpleName)
                }
            }
        }

        fun checkNano() {
            checking?.cancel()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                binding.nanoStatus.setText(R.string.ai_nano_unavailable)
                return
            }

            binding.nanoStatus.setText(R.string.ai_nano_checking)

            checking = activity.lifecycleScope.launch {
                val model = GeminiNanoChatModel(activity, binding.nanoPreview.isChecked)

                try {
                    binding.nanoStatus.setText(nanoStatusText(model.status()))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    binding.nanoStatus.text = e.message ?: e.javaClass.simpleName
                } finally {
                    model.close()
                }
            }
        }

        fun showProvider(nano: Boolean) {
            binding.openAiGroup.isVisible = !nano
            binding.nanoGroup.isVisible = nano

            if (nano) checkNano()
            else if (!modelsRequested) loadModels(show = false)
        }

        val nanoSelected = settings.provider == AiSettings.Provider.GEMINI_NANO && AiSettings.isNanoSupported

        binding.provider.check(if (nanoSelected) R.id.providerNano else R.id.providerOpenAi)
        binding.provider.addOnButtonCheckedListener { _, id, checked ->
            if (checked) showProvider(nano = id == R.id.providerNano)
        }

        binding.nanoPreview.setOnCheckedChangeListener { _, _ -> checkNano() }
        binding.modelLayout.setEndIconOnClickListener { loadModels(show = true) }
        showProvider(nanoSelected)

        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ai_settings)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val updated = AiSettings(
                    baseUrl = binding.baseUrl.text.toString().trim(),
                    apiKey = binding.apiKey.text.toString().trim(),
                    model = binding.model.text.toString().trim(),
                    provider =
                        if (binding.provider.checkedButtonId == R.id.providerNano) AiSettings.Provider.GEMINI_NANO
                        else AiSettings.Provider.OPENAI,
                    nanoPreview = binding.nanoPreview.isChecked,
                )

                updated.save(activity)

                if (thenGenerate && updated.isComplete)
                    showGenerateDialog()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setOnDismissListener {
                loading?.cancel()
                checking?.cancel()
            }
            .show()
    }

    private fun showPrompt(title: Int, hint: Int, initial: String, onSubmit: (String) -> Unit) {
        val binding = DialogAiPromptBinding.inflate(activity.layoutInflater)

        binding.taskLayout.setHint(hint)
        binding.task.setText(initial)

        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setView(binding.root)
            .setPositiveButton(R.string.ai_generate_button) { _, _ ->
                val task = binding.task.text.toString().trim()

                if (task.isNotEmpty())
                    onSubmit(task)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        binding.task.requestFocus()
    }

    private fun nanoStatusText(status: Int) = when (status) {
        FeatureStatus.AVAILABLE -> R.string.ai_nano_ready
        FeatureStatus.DOWNLOADABLE -> R.string.ai_nano_downloadable
        FeatureStatus.DOWNLOADING -> R.string.ai_nano_downloading_now
        else -> R.string.ai_nano_unavailable
    }

    private fun generate(
        settings: AiSettings,
        start: Int,
        end: Int,
        replace: Boolean,
        followUp: ChatMessage? = null,
    ) {
        val history = conversation ?: return
        val snapshot = history.toList()
        followUp?.let { history += it }
        val density = activity.resources.displayMetrics.density

        val statusView = TextView(activity).apply {
            isVisible = false
            setPadding(0, 0, 0, (8 * density).toInt())
        }

        val progressView = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(statusView)
            addView(LinearProgressIndicator(activity).apply { isIndeterminate = true })
        }

        val progress = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ai_generating)
            .setView(progressView)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> generation?.cancel() }
            .show()

        generation?.cancel()
        generation = activity.lifecycleScope.launch {
            try {
                val result = settings.createModel(activity).use { model ->
                    ScriptAssistant(model).reply(history) { status ->
                        statusView.text = status
                        statusView.isVisible = true
                    }
                }

                if (result.code.isBlank())
                    throw AiException(activity.getString(R.string.ai_empty_response))

                insert(result.code, start, end, replace)
                lastCode = result.code

                if (result.problems.isEmpty())
                    showInserted()
                else
                    showProblems(result.problems)
            } catch (e: CancellationException) {
                history.clear()
                history.addAll(snapshot)
                throw e
            } catch (e: Exception) {
                history.clear()
                history.addAll(snapshot)
                showMessage(R.string.ai_error_title, e.message ?: e.javaClass.simpleName)
            } finally {
                progress.dismiss()
            }
        }
    }

    private fun insert(code: String, start: Int, end: Int, replace: Boolean) {
        val content = editor.text ?: return
        val from = start.coerceIn(0, content.length)
        val to = end.coerceIn(from, content.length)

        val prefix = if (!replace && from > 0 && content[from - 1] != '\n') "\n" else ""
        val suffix = if (!replace && to < content.length && content[to] != '\n') "\n" else ""
        val codeStart = from + prefix.length
        val codeEnd = codeStart + code.length

        editor.replaceText(from, to, prefix + code + suffix, codeEnd)
        editor.text?.setSpan(generatedCode, codeStart, codeEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun showInserted() {
        Snackbar.make(editor, R.string.ai_inserted, SNACKBAR_DURATION)
            .setAnchorView(anchor)
            .setAction(R.string.ai_reply) { reply() }
            .show()
    }

    private fun showProblems(problems: List<String>) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.ai_problems_title)
            .setMessage(problems.joinToString("\n"))
            .setPositiveButton(R.string.ai_fix) { _, _ -> reply(LuaPrompt.repair(problems)) }
            .setNeutralButton(R.string.ai_reply) { _, _ -> reply() }
            .setNegativeButton(android.R.string.ok, null)
            .show()
    }

    private fun showMessage(title: Int, message: String) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private companion object {
        const val MENU_EDIT = 0x0A1
        const val SNACKBAR_DURATION = 10_000
    }
}
