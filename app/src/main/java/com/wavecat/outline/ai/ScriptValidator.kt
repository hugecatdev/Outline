package com.wavecat.outline.ai

import com.wavecat.outline.editor.LuaApi
import com.wavecat.outline.editor.LuaLexer
import com.wavecat.outline.editor.Token
import com.wavecat.outline.editor.TokenType
import org.luaj.vm2.Globals
import org.luaj.vm2.LoadState
import org.luaj.vm2.LuaError
import org.luaj.vm2.compiler.LuaC

object ScriptValidator {
    private const val SEPARATOR = "-----\n"

    fun validate(code: String): List<String> =
        listOfNotNull(syntaxError(code)) + unknownFunctions(code).map { "Unknown function: $it" }

    private fun syntaxError(code: String): String? {
        val compiler = Globals().apply {
            LoadState.install(this)
            LuaC.install(this)
        }

        code.split(SEPARATOR).forEachIndexed { index, chunk ->
            try {
                compiler.load(chunk, "#$index")
            } catch (e: LuaError) {
                return "Syntax error: ${e.message}"
            }
        }

        return null
    }

    fun unknownFunctions(code: String): List<String> {
        val tokens = LuaLexer.tokenize(code)
        val declared = declaredNames(code, tokens)

        return tokens.indices
            .filter { index -> isGlobalCall(code, tokens, index) }
            .map { index -> code.substring(tokens[index].start, tokens[index].end) }
            .filter { it !in LuaApi.globals && it !in declared }
            .distinct()
    }

    private fun isGlobalCall(code: String, tokens: List<Token>, index: Int): Boolean {
        val token = tokens[index]
        val next = tokens.getOrNull(index + 1)
        val previous = tokens.getOrNull(index - 1)?.type

        return token.type == TokenType.IDENTIFIER &&
                next?.type == TokenType.OPEN && code[next.start] == '(' &&
                previous != TokenType.DOT && previous != TokenType.COLON
    }

    private fun declaredNames(code: String, tokens: List<Token>): Set<String> {
        val names = HashSet<String>()

        fun word(token: Token) = code.substring(token.start, token.end)

        tokens.forEachIndexed { index, token ->
            when {
                token.type == TokenType.KEYWORD && word(token) in setOf("local", "for") -> {
                    var i = index + 1

                    while (tokens.getOrNull(i)?.type == TokenType.IDENTIFIER) {
                        names += word(tokens[i])
                        if (tokens.getOrNull(i + 1)?.type != TokenType.COMMA) break
                        i += 2
                    }
                }

                token.type == TokenType.KEYWORD && word(token) == "function" -> {
                    var i = index + 1

                    while (i < tokens.size && !(tokens[i].type == TokenType.OPEN && code[tokens[i].start] == '(')) {
                        if (tokens[i].type == TokenType.IDENTIFIER && tokens.getOrNull(i - 1)?.type != TokenType.DOT && tokens.getOrNull(i - 1)?.type != TokenType.COLON)
                            names += word(tokens[i])
                        i++
                    }

                    i++

                    while (i < tokens.size && tokens[i].type != TokenType.CLOSE) {
                        if (tokens[i].type == TokenType.IDENTIFIER)
                            names += word(tokens[i])
                        i++
                    }
                }

                token.type == TokenType.IDENTIFIER && isAssigned(code, token) -> names += word(token)
            }
        }

        return names
    }

    private fun isAssigned(code: String, token: Token): Boolean {
        var i = token.end
        while (i < code.length && (code[i] == ' ' || code[i] == '\t')) i++

        return code.getOrNull(i) == '=' && code.getOrNull(i + 1) != '='
    }
}
