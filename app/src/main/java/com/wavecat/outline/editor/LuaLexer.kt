package com.wavecat.outline.editor

enum class TokenType { KEYWORD, IDENTIFIER, NUMBER, STRING, COMMENT, OPEN, CLOSE, COMMA, DOT, COLON }

class Token(val type: TokenType, val start: Int, val end: Int, val closed: Boolean = true) {
    fun contains(position: Int): Boolean =
        position in (start + 1) until end || (position == end && !closed)
}

object LuaLexer {
    val keywords = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function", "goto", "if",
        "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while",
    )

    fun tokenize(text: CharSequence): List<Token> {
        val tokens = ArrayList<Token>()
        val length = text.length
        var i = 0

        while (i < length) {
            val c = text[i]
            val start = i

            when {
                c == '-' && text.at(i + 1) == '-' -> {
                    i += 2
                    val level = longBracketLevel(text, i)

                    if (level >= 0) {
                        i = skipLongBracket(text, i, level)
                        tokens += Token(TokenType.COMMENT, start, i, text.endsLongBracket(start + 2, i, level))
                    } else {
                        while (i < length && text[i] != '\n') i++
                        tokens += Token(TokenType.COMMENT, start, i, closed = false)
                    }
                }

                c == '"' || c == '\'' -> {
                    i++
                    var closed = false

                    while (i < length && text[i] != '\n') {
                        val ch = text[i++]

                        if (ch == '\\' && i < length) {
                            i++
                        } else if (ch == c) {
                            closed = true
                            break
                        }
                    }

                    tokens += Token(TokenType.STRING, start, i, closed)
                }

                c == '[' && longBracketLevel(text, i) >= 0 -> {
                    val level = longBracketLevel(text, i)
                    i = skipLongBracket(text, i, level)
                    tokens += Token(TokenType.STRING, start, i, text.endsLongBracket(start, i, level))
                }

                c.isDigit() || (c == '.' && text.at(i + 1).isDigit()) -> {
                    i = skipNumber(text, i)
                    tokens += Token(TokenType.NUMBER, start, i)
                }

                isIdentifierStart(c) -> {
                    while (i < length && isIdentifierPart(text[i])) i++

                    val type =
                        if (text.subSequence(start, i).toString() in keywords) TokenType.KEYWORD
                        else TokenType.IDENTIFIER

                    tokens += Token(type, start, i)
                }

                else -> {
                    i++

                    val type = when (c) {
                        '(', '{', '[' -> TokenType.OPEN
                        ')', '}', ']' -> TokenType.CLOSE
                        ',' -> TokenType.COMMA
                        '.' -> TokenType.DOT
                        ':' -> TokenType.COLON
                        else -> null
                    }

                    if (type != null)
                        tokens += Token(type, start, i)
                }
            }
        }

        return tokens
    }

    fun isIdentifierStart(c: Char) = c == '_' || c.isLetter()

    fun isIdentifierPart(c: Char) = c == '_' || c.isLetterOrDigit()

    fun isIdentifier(name: String) =
        name.isNotEmpty() && isIdentifierStart(name[0]) && name.all(::isIdentifierPart)

    private fun CharSequence.at(index: Int): Char = if (index < length) this[index] else '\u0000'

    private fun longBracketLevel(text: CharSequence, index: Int): Int {
        if (text.at(index) != '[') return -1

        var i = index + 1
        while (text.at(i) == '=') i++

        return if (text.at(i) == '[') i - index - 1 else -1
    }

    private fun skipLongBracket(text: CharSequence, index: Int, level: Int): Int {
        val close = "]" + "=".repeat(level) + "]"
        val found = text.indexOf(close, index + level + 2)

        return if (found < 0) text.length else found + close.length
    }

    private fun CharSequence.endsLongBracket(start: Int, end: Int, level: Int): Boolean =
        end - start >= 2 * level + 4 && this[end - 1] == ']'

    private fun skipNumber(text: CharSequence, index: Int): Int {
        var i = index

        if (text[i] == '0' && (text.at(i + 1) == 'x' || text.at(i + 1) == 'X')) {
            i += 2
            while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '.')) i++
            return i
        }

        while (i < text.length) {
            val c = text[i]

            when {
                c.isDigit() || c == '.' -> i++

                c == 'e' || c == 'E' -> {
                    i++
                    if (text.at(i) == '+' || text.at(i) == '-') i++
                }

                else -> break
            }
        }

        return i
    }
}
