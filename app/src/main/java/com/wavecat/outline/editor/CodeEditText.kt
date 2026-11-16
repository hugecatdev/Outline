package com.wavecat.outline.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.text.Spanned
import android.util.AttributeSet
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.wavecat.outline.R
import java.util.Arrays
import kotlin.math.ceil
import kotlin.math.max

class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle,
) : AppCompatEditText(context, attrs, defStyleAttr) {

    var tokens: List<Token> = emptyList()
        private set

    var onCodeChanged: (() -> Unit)? = null

    private val highlighter = LuaHighlighter(context)
    private val highlightTask = Runnable { text?.let { highlighter.apply(it, tokens) } }

    private var lineStarts = IntArray(0)
    private var lineLabels = IntArray(0)
    private var lineCount = 0

    private val spacing = (resources.displayMetrics.density * 8).toInt()
    private var gutterWidth = 0

    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.RIGHT
        color = ContextCompat.getColor(context, R.color.code_gutter)
    }

    private val dividerPaint = Paint().apply {
        color = gutterPaint.color
        alpha = 60
    }

    init {
        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
        typeface = Typeface.MONOSPACE
        gravity = Gravity.TOP or Gravity.START
        background = null
        setHorizontallyScrolling(false)
        filters = arrayOf(AutoIndentFilter())
        gutterPaint.textSize = textSize * 0.85f

        doAfterTextChanged { onTextEdited() }
        onTextEdited()
    }

    fun replaceText(start: Int, end: Int, value: CharSequence, cursor: Int) {
        val content = text ?: return

        content.replace(start, end, value)
        setSelection(cursor.coerceIn(0, content.length))
    }

    private fun onTextEdited() {
        val content = text ?: return

        tokens = LuaLexer.tokenize(content)
        countLines(content)

        removeCallbacks(highlightTask)
        postDelayed(highlightTask, HIGHLIGHT_DELAY)

        onCodeChanged?.invoke()
    }

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCodeChanged?.invoke()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        if (h < oldh && hasFocus())
            post { bringPointIntoView(selectionEnd) }
    }

    private fun countLines(content: CharSequence) {
        var starts = IntArray(max(16, lineStarts.size))
        var labels = IntArray(starts.size)
        var count = 0
        var number = 0
        var chunk = 0
        var longestLabel = 2
        var lineStart = 0

        while (true) {
            val lineEnd = content.indexOf('\n', lineStart).let { if (it < 0) content.length else it }
            val separator = lineEnd < content.length && isSeparator(content, lineStart, lineEnd)

            if (count == starts.size) {
                starts = starts.copyOf(count * 2)
                labels = labels.copyOf(count * 2)
            }

            starts[count] = lineStart
            labels[count] = if (separator && lineEnd - lineStart == SEPARATOR.length) -(chunk + 1) else ++number
            longestLabel = max(longestLabel, labelOf(labels[count]).length)
            count++

            if (separator) {
                chunk++
                number = 0
            }

            if (lineEnd >= content.length)
                break

            lineStart = lineEnd + 1
        }

        lineStarts = starts
        lineLabels = labels
        lineCount = count

        val width = ceil(gutterPaint.measureText("0".repeat(longestLabel))).toInt() + spacing * 2

        if (width != gutterWidth) {
            gutterWidth = width
            setPadding(gutterWidth + spacing, spacing, spacing, spacing)
        }
    }

    private fun isSeparator(content: CharSequence, lineStart: Int, lineEnd: Int): Boolean {
        if (lineEnd - lineStart < SEPARATOR.length)
            return false

        return SEPARATOR.indices.all { content[lineEnd - SEPARATOR.length + it] == SEPARATOR[it] }
    }

    private fun labelOf(label: Int) = if (label > 0) label.toString() else "#${-label}"

    override fun onDraw(canvas: Canvas) {
        val layout = layout
        val content = text

        if (layout != null && content != null && lineCount > 0) {
            val top = totalPaddingTop
            val first = layout.getLineForVertical(max(0, scrollY - top))
            val last = layout.getLineForVertical(scrollY + height - top)
            val x = (scrollX + gutterWidth - spacing).toFloat()

            for (line in first..last) {
                val start = layout.getLineStart(line)

                if (start > 0 && content[start - 1] != '\n')
                    continue

                val index = Arrays.binarySearch(lineStarts, 0, lineCount, start)

                if (index >= 0)
                    canvas.drawText(labelOf(lineLabels[index]), x, (layout.getLineBaseline(line) + top).toFloat(), gutterPaint)
            }

            val dividerX = (scrollX + gutterWidth).toFloat()
            canvas.drawLine(dividerX, scrollY.toFloat(), dividerX, (scrollY + height).toFloat(), dividerPaint)
        }

        super.onDraw(canvas)
    }

    private class AutoIndentFilter : InputFilter {
        override fun filter(
            source: CharSequence,
            start: Int,
            end: Int,
            dest: Spanned,
            dstart: Int,
            dend: Int,
        ): CharSequence? {
            if (end - start != 1 || source[start] != '\n')
                return null

            val lineStart = dest.lastIndexOf('\n', dstart - 1) + 1
            var indentEnd = lineStart

            while (indentEnd < dstart && (dest[indentEnd] == ' ' || dest[indentEnd] == '\t'))
                indentEnd++

            val extra = if (opensBlock(dest.subSequence(lineStart, dstart))) INDENT else ""

            return "\n" + dest.subSequence(lineStart, indentEnd) + extra
        }

        private fun opensBlock(line: CharSequence): Boolean {
            val tokens = LuaLexer.tokenize(line).filter { it.type != TokenType.COMMENT }
            val last = tokens.lastOrNull() ?: return false
            val words = tokens.filter { it.type == TokenType.KEYWORD }.map { line.substring(it.start, it.end) }

            return when (last.type) {
                TokenType.KEYWORD -> line.substring(last.start, last.end) in BLOCK_WORDS
                TokenType.OPEN -> true
                TokenType.CLOSE -> line[last.start] == ')' && words.count { it == "function" } > words.count { it == "end" }
                else -> false
            }
        }
    }

    companion object {
        const val INDENT = "    "
        const val SEPARATOR = "-----"

        private const val HIGHLIGHT_DELAY = 120L
        private val BLOCK_WORDS = setOf("then", "do", "else", "repeat")
    }
}
