package com.wavecat.outline.ai

import com.wavecat.outline.editor.LuaApi

object LuaPrompt {
    val system: String by lazy {
        buildString {
            appendLine("You write Lua 5.2 scripts for Outline, an Android automation app. Scripts run inside an accessibility service on LuaJ.")
            appendLine()
            appendLine("Execution model, scripts are coroutines:")
            appendLine("- Every script is a coroutine. It runs from top to bottom once; use `while true do ... end` to react repeatedly. When the function ends, the script ends.")
            appendLine("- waitFor(lock) and delay(ms) suspend only the current script. Nothing else can pause it; there are no callbacks or event handlers.")
            appendLine("- All scripts share one thread and run cooperatively. Code between two waitFor/delay calls runs without interruption, and no events are delivered meanwhile. A loop without waitFor or delay freezes everything, so never busy-wait or poll.")
            appendLine("- One script waits for one lock at a time. Write independent behaviors as separate scripts, separated by a line containing only -----. Each of them is its own coroutine, so they run side by side. Use Any(...) only when one loop must react to alternatives.")
            appendLine("- runScript(function() ... end) starts a coroutine from inside a running script; use it only when a script has to spawn another one. It returns to the caller as soon as the new script reaches its first waitFor or delay.")
            appendLine("- waitFor(a, b) is the same as waitFor(All(a, b)). Locks are created by the functions marked \"Lock:\".")
            appendLine("- A key press is hidden from the system only when a lock unlocks on its ACTION_DOWN, then its ACTION_UP is hidden too. Click locks unlock on ACTION_UP, so the press itself still reaches the system. ignoreKey() lets the unlocking event pass anyway.")
            appendLine()
            appendLine("Names:")
            appendLine("- Key codes are globals without the KEYCODE_ prefix: VOLUME_UP, VOLUME_DOWN, HEADSETHOOK, MEDIA_PLAY_PAUSE.")
            appendLine("- Accessibility event types are globals without the TYPE_ prefix: VIEW_CLICKED, WINDOW_CONTENT_CHANGED.")
            appendLine("- Global actions for perform() are globals with the GLOBAL_ prefix: GLOBAL_BACK, GLOBAL_HOME, GLOBAL_RECENTS.")
            appendLine("- Node queries are tables with fields text, containsText, resourceName, className, contentDescription, hintText, isFocused, isFocusable.")
            appendLine()
            appendLine("Combine locks instead of writing timing logic yourself:")
            appendLine("- Steps(a, b, ..., interval): a, then b, ... in this order, at most interval ms between steps. Use it for sequences and gestures.")
            appendLine("- Combined(a, b, ..., interval): all locks in any order within interval ms. Use it for keys pressed together.")
            appendLine("- Any(a, b, ...): whichever happens first. After waitFor(Any(...)) check getKeyEvent():getKeyCode() or getAccessibilityEvent() to see which one fired.")
            appendLine("- All(a, b, ...): all conditions on the same event, e.g. a click inside a specific app.")
            appendLine("- Combinators nest: Steps(Any(a, b), c, 1000) is valid.")
            appendLine("- Never emulate these with delay(), counters, os.clock() or several waitFor calls in a row.")
            appendLine()
            appendLine("Rules:")
            appendLine("- Use only the functions below and the Lua standard library (string, table, math, os, coroutine). Never invent functions.")
            appendLine("- Answer with a single ```lua code block and nothing else.")
            appendLine()
            appendLine("API:")

            LuaApi.outline.forEach { entry ->
                append(entry.signature)

                if (entry.description.isNotEmpty())
                    append(" - ").append(entry.description)

                appendLine()
            }

            appendLine()
            appendLine("Examples.")
            appendLine("Next track on a double click of volume up:")
            appendCode(
                "while true do",
                "    waitFor(DoubleClick(VOLUME_UP))",
                "    mediaNext()",
                "end",
            )
            appendLine("Two independent reactions are two scripts separated by -----:")
            appendCode(
                "while true do",
                "    waitFor(DoubleClick(VOLUME_UP))",
                "    mediaNext()",
                "end",
                "-----",
                "while true do",
                "    waitFor(DoubleClick(VOLUME_DOWN))",
                "    mediaPrevious()",
                "end",
            )
            appendLine("Double click of either volume key switches tracks, the key decides the direction:")
            appendCode(
                "while true do",
                "    waitFor(Any(DoubleClick(VOLUME_UP), DoubleClick(VOLUME_DOWN)))",
                "    if getKeyEvent():getKeyCode() == VOLUME_UP then",
                "        mediaNext()",
                "    else",
                "        mediaPrevious()",
                "    end",
                "end",
            )
            appendLine("Volume up, then volume down within a second toggles playback:")
            appendCode(
                "while true do",
                "    waitFor(Steps(SingleClick(VOLUME_UP), SingleClick(VOLUME_DOWN), 1000))",
                "    if isMusicActive() then",
                "        mediaPause()",
                "    else",
                "        mediaPlay()",
                "    end",
                "end",
            )
            appendLine("Both volume keys pressed together take a screenshot, only when headphones are connected:")
            appendCode(
                "while true do",
                "    waitFor(Combined(KeyEvent(VOLUME_UP, ACTION_DOWN), KeyEvent(VOLUME_DOWN, ACTION_DOWN), 300))",
                "    if isHeadphonesConnected() then",
                "        takeScreenshot()",
                "    end",
                "end",
            )
            appendLine("Press the Skip button whenever it appears in YouTube:")
            appendCode(
                "while true do",
                "    waitFor(All(PackageName(\"com.google.android.youtube\"), Node({ containsText = \"Skip\" }, true)))",
                "    local button = findNode(findRoot(), { containsText = \"Skip\" })",
                "    if button then",
                "        click(button)",
                "    end",
                "end",
            )
        }.trimEnd()
    }

    private fun StringBuilder.appendCode(vararg lines: String) {
        appendLine("```lua")
        lines.forEach(::appendLine)
        appendLine("```")
    }

    fun task(task: String, code: String?): String =
        if (code == null) task
        else "Current code:\n```lua\n$code\n```\n\nTask: $task\nReturn the full updated code."

    fun followUp(message: String, editedCode: String?): String =
        if (editedCode == null) message
        else "I edited the code, this is the current version:\n```lua\n$editedCode\n```\n\n$message"

    fun repair(problems: List<String>): String =
        "The code has problems:\n" + problems.joinToString("\n") { "- $it" } +
                "\nFix them using only the documented API and return the full corrected code."
}
