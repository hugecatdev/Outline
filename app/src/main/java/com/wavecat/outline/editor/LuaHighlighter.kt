package com.wavecat.outline.editor

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import androidx.core.content.ContextCompat
import com.wavecat.outline.R

class LuaHighlighter(context: Context) {
    val keyword = ContextCompat.getColor(context, R.color.code_keyword)
    val string = ContextCompat.getColor(context, R.color.code_string)
    val number = ContextCompat.getColor(context, R.color.code_number)
    val comment = ContextCompat.getColor(context, R.color.code_comment)
    val function = ContextCompat.getColor(context, R.color.code_function)
    val constant = ContextCompat.getColor(context, R.color.code_constant)
    val module = ContextCompat.getColor(context, R.color.code_module)

    fun colorFor(kind: EntryKind): Int? = when (kind) {
        EntryKind.KEYWORD -> keyword
        EntryKind.FUNCTION -> function
        EntryKind.CONSTANT -> constant
        EntryKind.MODULE -> module
        EntryKind.VARIABLE -> null
    }

    fun apply(text: Editable, tokens: List<Token>) {
        for (span in text.getSpans(0, text.length, CodeSpan::class.java))
            text.removeSpan(span)

        val declared = declaredFunctions(text, tokens)

        tokens.forEachIndexed { index, token ->
            val color = colorOf(text, tokens, index, declared) ?: return@forEachIndexed

            if (token.end <= text.length)
                text.setSpan(CodeSpan(color), token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private fun colorOf(text: CharSequence, tokens: List<Token>, index: Int, declared: Set<String>): Int? {
        val token = tokens[index]

        return when (token.type) {
            TokenType.KEYWORD -> when (text.substring(token.start, token.end)) {
                "true", "false", "nil" -> constant
                else -> keyword
            }

            TokenType.STRING -> string
            TokenType.COMMENT -> comment
            TokenType.NUMBER -> number

            TokenType.IDENTIFIER -> {
                val name = text.substring(token.start, token.end)
                val separator = tokens.getOrNull(index - 1)?.type
                val owner = tokens.getOrNull(index - 2)

                if ((separator == TokenType.DOT || separator == TokenType.COLON) && owner?.type == TokenType.IDENTIFIER) {
                    LuaApi.member(text.substring(owner.start, owner.end), name)?.let { colorFor(it.kind) }
                } else {
                    LuaApi.globals[name]?.let { colorFor(it.kind) } ?: if (name in declared) function else null
                }
            }

            else -> null
        }
    }

    private fun declaredFunctions(text: CharSequence, tokens: List<Token>): Set<String> {
        val names = HashSet<String>()

        tokens.forEachIndexed { index, token ->
            if (token.type == TokenType.KEYWORD && text.substring(token.start, token.end) == "function") {
                val name = tokens.getOrNull(index + 1)

                if (name?.type == TokenType.IDENTIFIER)
                    names += text.substring(name.start, name.end)
            }
        }

        return names
    }

    private class CodeSpan(private val color: Int) : CharacterStyle(), UpdateAppearance {
        override fun updateDrawState(paint: TextPaint) {
            paint.color = color
        }
    }
}
