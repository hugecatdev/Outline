package com.wavecat.outline.editor

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.min

class CompletionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val highlighter = LuaHighlighter(context)
    private val density = resources.displayMetrics.density

    private val secondaryColor =
        MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant)

    private val signatureView = TextView(context).apply {
        typeface = Typeface.MONOSPACE
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setPadding(dp(12), dp(6), dp(12), dp(2))
        maxLines = 4
        visibility = View.GONE
    }

    private val itemsLayout = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    private val itemsScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        addView(itemsLayout, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
    }

    private var editor: CodeEditText? = null
    private var completion: Completion? = null
    private var shownItems: List<String>? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceContainer))

        addView(signatureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(itemsScroll, LayoutParams(LayoutParams.MATCH_PARENT, dp(44)))
    }

    fun attach(editor: CodeEditText) {
        this.editor = editor
        editor.onCodeChanged = ::update
        update()
    }

    private fun update() {
        val editor = editor ?: return
        val content = editor.text ?: return
        val cursor = editor.selectionEnd
        val hasSelection = editor.selectionStart != cursor

        completion = if (hasSelection) null else LuaCompletion.complete(content, editor.tokens, cursor)
        renderItems(completion)
        renderSignature(if (hasSelection) null else LuaCompletion.signature(content, editor.tokens, cursor))
    }

    private fun renderItems(completion: Completion?) {
        val names = completion?.items?.map { it.name } ?: SYMBOLS

        if (names == shownItems)
            return

        shownItems = names
        itemsLayout.removeAllViews()

        if (completion == null) {
            SYMBOLS.forEach { symbol -> itemsLayout.addView(item(symbol, null) { insertSymbol(symbol) }) }
        } else {
            completion.items.forEach { entry ->
                itemsLayout.addView(item(entry.name, highlighter.colorFor(entry.kind)) { complete(entry) })
            }
        }

        itemsScroll.scrollTo(0, 0)
    }

    private fun renderSignature(signature: Signature?) {
        if (signature == null) {
            signatureView.visibility = View.GONE
            return
        }

        val entry = signature.entry
        val active = signature.activeParameter

        val text = SpannableStringBuilder()
            .append(entry.name, ForegroundColorSpan(highlighter.function), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            .append("(")

        entry.params.forEachIndexed { index, param ->
            if (index > 0)
                text.append(", ")

            if (index == active)
                text.append(param, StyleSpan(Typeface.BOLD), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            else
                text.append(param, ForegroundColorSpan(secondaryColor), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        text.append(")")

        if (entry.description.isNotEmpty()) {
            val start = text.length

            text.append("\n").append(entry.description)
            text.setSpan(ForegroundColorSpan(secondaryColor), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(RelativeSizeSpan(0.9f), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        signatureView.text = text
        signatureView.visibility = View.VISIBLE
    }

    private fun complete(entry: ApiEntry) {
        val editor = editor ?: return
        val content = editor.text ?: return
        val completion = completion ?: return

        val call = entry.kind == EntryKind.FUNCTION && content.getOrNull(completion.end) != '('
        val value = if (call) "${entry.name}()" else entry.name

        val cursor = completion.start + entry.name.length + when {
            !call -> 0
            entry.params.isEmpty() -> 2
            else -> 1
        }

        editor.replaceText(completion.start, completion.end, value, cursor)
    }

    private fun insertSymbol(symbol: String) {
        val editor = editor ?: return
        val content = editor.text ?: return
        val start = min(editor.selectionStart, editor.selectionEnd)
        val end = max(editor.selectionStart, editor.selectionEnd)

        if (start < 0)
            return

        val value = when (symbol) {
            TAB -> CodeEditText.INDENT
            CodeEditText.SEPARATOR -> (if (start > 0 && content[start - 1] != '\n') "\n" else "") + symbol + "\n"
            else -> symbol
        }

        editor.replaceText(start, end, value, start + value.length)
    }

    private fun item(label: String, color: Int?, onClick: () -> Unit) = TextView(context).apply {
        text = label
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        minWidth = dp(40)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(12), 0, dp(12), 0)
        color?.let(::setTextColor)
        setBackgroundResource(selectableBackground())
        setOnClickListener { onClick() }
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
    }

    private fun selectableBackground(): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        return value.resourceId
    }

    private fun dp(value: Int) = (value * density).toInt()

    private companion object {
        const val TAB = "⇥"

        val SYMBOLS = listOf(TAB, CodeEditText.SEPARATOR, "(", ")", "\"", ",", ".", ":", "=", "{", "}", "[", "]", "--", "~=", "#", "_")
    }
}
