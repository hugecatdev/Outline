package com.wavecat.outline.editor

class Completion(val start: Int, val end: Int, val items: List<ApiEntry>)

class Signature(val entry: ApiEntry, val argument: Int) {
    val activeParameter: Int
        get() {
            val vararg = entry.params.indexOfFirst { it.endsWith("...") }

            return when {
                vararg in 0..argument -> vararg
                argument < entry.params.size -> argument
                else -> -1
            }
        }
}

object LuaCompletion {
    private const val LIMIT = 40
    private const val SCAN_LIMIT = 4000

    private val blockClosers = setOf("end", "until")
    private val blockOpeners = setOf("function", "if", "do", "repeat")
    private val statementKeywords =
        setOf("local", "return", "while", "for", "then", "else", "elseif", "break", "goto")

    fun complete(text: CharSequence, tokens: List<Token>, cursor: Int): Completion? {
        if (isInsideLiteral(tokens, cursor))
            return null

        var start = cursor
        while (start > 0 && LuaLexer.isIdentifierPart(text[start - 1])) start--

        var end = cursor
        while (end < text.length && LuaLexer.isIdentifierPart(text[end])) end++

        val prefix = text.substring(start, cursor)

        if (prefix.isNotEmpty() && !LuaLexer.isIdentifierStart(prefix[0]))
            return null

        val separator = text.getOrNull(start - 1)

        val candidates = if ((separator == '.' || separator == ':') && text.getOrNull(start - 2) != '.') {
            var moduleStart = start - 1
            while (moduleStart > 0 && LuaLexer.isIdentifierPart(text[moduleStart - 1])) moduleStart--

            LuaApi.modules[text.substring(moduleStart, start - 1)]?.asSequence() ?: return null
        } else {
            if (prefix.isEmpty())
                return null

            LuaApi.globals.values.asSequence() + documentIdentifiers(text, tokens, start)
        }

        val strict = prefix in LuaLexer.keywords

        val items = candidates
            .filter { it.name.startsWith(prefix, ignoreCase = !strict) }
            .filter { it.name != prefix || it.kind == EntryKind.FUNCTION }
            .sortedWith(
                compareBy<ApiEntry>(
                    { !it.name.startsWith(prefix) },
                    { it.kind.ordinal },
                    { it.name.length },
                    { it.name },
                )
            )
            .take(LIMIT)
            .toList()

        return if (items.isEmpty()) null else Completion(start, end, items)
    }

    fun signature(text: CharSequence, tokens: List<Token>, cursor: Int): Signature? {
        var index = lastTokenStartingBefore(tokens, cursor)

        if (index >= 0 && tokens[index].end > cursor)
            index--

        var depth = 0
        var blocks = 0
        var argument = 0
        var scanned = 0

        while (index >= 0 && scanned++ < SCAN_LIMIT) {
            val token = tokens[index]

            when (token.type) {
                TokenType.CLOSE -> depth++

                TokenType.OPEN -> when {
                    depth > 0 -> depth--
                    text[token.start] == '(' -> return resolve(text, tokens, index - 1, argument)
                    else -> argument = 0
                }

                TokenType.COMMA -> if (depth == 0 && blocks == 0) argument++

                TokenType.KEYWORD -> if (depth == 0) {
                    val word = text.substring(token.start, token.end)

                    when {
                        word in blockClosers -> blocks++
                        word in blockOpeners -> if (blocks > 0) blocks-- else return null
                        word in statementKeywords && blocks == 0 -> return null
                    }
                }

                else -> {}
            }

            index--
        }

        return null
    }

    private fun resolve(text: CharSequence, tokens: List<Token>, nameIndex: Int, argument: Int): Signature? {
        val nameToken = tokens.getOrNull(nameIndex)?.takeIf { it.type == TokenType.IDENTIFIER } ?: return null
        val name = text.substring(nameToken.start, nameToken.end)

        val separator = tokens.getOrNull(nameIndex - 1)
        val owner = tokens.getOrNull(nameIndex - 2)

        val entry =
            if ((separator?.type == TokenType.DOT || separator?.type == TokenType.COLON) && owner?.type == TokenType.IDENTIFIER)
                LuaApi.member(text.substring(owner.start, owner.end), name)
            else
                LuaApi.globals[name]

        return entry?.takeIf { it.kind == EntryKind.FUNCTION }?.let { Signature(it, argument) }
    }

    private fun documentIdentifiers(text: CharSequence, tokens: List<Token>, wordStart: Int): Sequence<ApiEntry> {
        val names = LinkedHashSet<String>()

        tokens.forEachIndexed { index, token ->
            if (token.type != TokenType.IDENTIFIER || token.start == wordStart)
                return@forEachIndexed

            val previous = tokens.getOrNull(index - 1)?.type

            if (previous != TokenType.DOT && previous != TokenType.COLON)
                names += text.substring(token.start, token.end)
        }

        return names.asSequence()
            .filter { it !in LuaApi.globals }
            .map { ApiEntry(it, EntryKind.VARIABLE) }
    }

    private fun isInsideLiteral(tokens: List<Token>, cursor: Int): Boolean {
        val token = tokens.getOrNull(lastTokenStartingBefore(tokens, cursor)) ?: return false

        return (token.type == TokenType.STRING || token.type == TokenType.COMMENT) && token.contains(cursor)
    }

    private fun lastTokenStartingBefore(tokens: List<Token>, position: Int): Int {
        var low = 0
        var high = tokens.lastIndex
        var result = -1

        while (low <= high) {
            val middle = (low + high) ushr 1

            if (tokens[middle].start < position) {
                result = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }

        return result
    }
}
