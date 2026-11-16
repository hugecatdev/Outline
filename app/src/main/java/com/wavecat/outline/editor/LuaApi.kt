package com.wavecat.outline.editor

import com.wavecat.outline.api.LuaConstants

enum class EntryKind { KEYWORD, FUNCTION, CONSTANT, MODULE, VARIABLE }

data class ApiEntry(
    val name: String,
    val kind: EntryKind,
    val params: List<String> = emptyList(),
    val description: String = "",
) {
    val signature: String
        get() = if (kind == EntryKind.FUNCTION) "$name(${params.joinToString(", ")})" else name
}

object LuaApi {
    private fun fn(name: String, vararg params: String, doc: String = "") =
        ApiEntry(name, EntryKind.FUNCTION, params.toList(), doc)

    private fun value(name: String, doc: String) = ApiEntry(name, EntryKind.VARIABLE, description = doc)

    val outline = listOf(
        fn("waitFor", "lock...", doc = "Suspends the script until all locks unlock. Without arguments waits forever"),
        fn("delay", "ms", doc = "Pauses the script"),
        fn("runScript", "function", "name?", doc = "Starts the function as a separate script"),
        fn("runScriptUnique", "function", "name", doc = "Starts the script unless one with this name is running"),
        fn("ignoreKey", doc = "Lets the key event that resumed the script pass through to the system"),
        fn("getKeyEvent", doc = "Key event that unlocked the script"),
        fn("getAccessibilityEvent", doc = "Accessibility event that unlocked the script"),
        fn("getPreferences", doc = "App SharedPreferences"),

        fn("SingleClick", "keyCode", "interval = 700", doc = "Lock: key click"),
        fn("DoubleClick", "keyCode", "interval = 700", doc = "Lock: two key clicks"),
        fn("TripleClick", "keyCode", "interval = 700", doc = "Lock: three key clicks"),
        fn("QuadrupleClick", "keyCode", "interval = 700", doc = "Lock: four key clicks"),
        fn("KeyEvent", "keyCode?", "action?", doc = "Lock: key event, ACTION_DOWN or ACTION_UP"),

        fn("Steps", "lock...", "interval", doc = "Lock: locks one after another, at most interval ms between steps"),
        fn("Combined", "lock...", "interval", doc = "Lock: all locks in any order within interval ms"),
        fn("Any", "lock...", doc = "Lock: any of the locks"),
        fn("All", "lock...", doc = "Lock: all locks on the same event"),

        fn(
            "Node", "query", "fromRoot = false",
            doc = "Lock: node matching the query. Query fields: text, containsText, resourceName, " +
                    "className, contentDescription, hintText, isFocused, isFocusable"
        ),
        fn("Text", "text", doc = "Lock: node with exactly this text"),
        fn("ContainsText", "text", doc = "Lock: node containing this text"),
        fn("PackageName", "packageName", doc = "Lock: event from the app"),
        fn("EventType", "type", doc = "Lock: accessibility event of the type, e.g. VIEW_CLICKED"),
        fn("AccessibilityButton", doc = "Lock: the accessibility button is pressed, the optional Outline button service must be assigned to it. Steps(AccessibilityButton(), AccessibilityButton(), 500) is a double press"),
        fn("WindowContentChanged", doc = "Lock: window content changed"),
        fn("ViewClicked", doc = "Lock: view clicked"),
        fn("ViewLongClicked", doc = "Lock: view long clicked"),
        fn("ViewFocused", doc = "Lock: view focused"),
        fn("ViewTextChanged", doc = "Lock: view text changed"),
        fn("ViewSelected", doc = "Lock: view selected"),
        fn("WebViewReady", doc = "Lock: WebView is ready"),
        fn("PageLoaded", doc = "Lock: WebView page loaded"),
        fn("IsChrome", doc = "Lock: event from Chrome"),

        fn("findRoot", "event?", doc = "Root node of the event window, by default of the last event"),
        fn("findNode", "node", "query", doc = "First descendant node matching the query"),
        fn("findAllNodes", "node", "query", doc = "All descendant nodes matching the query"),
        fn("iterNodes", "node", "query", "callback", doc = "Calls callback for every matching descendant node"),
        fn("click", "node"),
        fn("longClick", "node"),
        fn("focus", "node"),
        fn("clearFocus", "node"),
        fn("select", "node"),
        fn("clearSelection", "node"),
        fn("copy", "node"),
        fn("cut", "node"),
        fn("paste", "node"),
        fn("scrollForward", "node"),
        fn("scrollBackward", "node"),
        fn("setText", "node", "text"),
        fn("nextHtmlElement", "node", "element"),
        fn("previousHtmlElement", "node", "element"),

        fn("perform", "globalAction", doc = "Performs a global action: GLOBAL_BACK, GLOBAL_HOME, GLOBAL_RECENTS, GLOBAL_NOTIFICATIONS, GLOBAL_LOCK_SCREEN"),
        fn("takeScreenshot", doc = "Android 9+"),
        fn("vibrate", "ms"),
        fn("toast", "text"),
        fn("speak", "text", "mode = \"add\"", doc = "Text to speech without waiting, mode \"flush\" interrupts current speech"),
        fn("speakAndWait", "text", "mode = \"add\"", doc = "Speaks the text and suspends the script until it is spoken"),
        fn("clip", "text", doc = "Copies text to the clipboard"),

        fn("mediaKeyEvent", "action", "keyCode", doc = "Dispatches a media key event"),
        fn("mediaNext"),
        fn("mediaPrevious"),
        fn("mediaPlay"),
        fn("mediaPause"),
        fn("volumeUp"),
        fn("volumeDown"),
        fn("isMusicActive"),
        fn("isHeadphonesConnected", doc = "Wired, USB or Bluetooth headphones are connected. Bluetooth speakers count too"),

        fn("openInChrome", "url"),
        fn("openWebView", "url?", doc = "Opens the built-in WebView, returns WebViewReady lock"),
        fn("openUrl", "url", doc = "Opens the url in the built-in WebView"),
        fn("clearWebViewData"),
        fn("setWebViewSize", "width", "height?"),
        fn("webViewClick", "x", "y"),

        value("service", "Accessibility service instance"),
        value("AccessibilityNodeInfo", "AccessibilityNodeInfo class"),
    )

    private val builtins = listOf(
        fn("print", "..."),
        fn("pairs", "table"),
        fn("ipairs", "table"),
        fn("next", "table", "key?"),
        fn("type", "value"),
        fn("tostring", "value"),
        fn("tonumber", "value", "base?"),
        fn("pcall", "function", "..."),
        fn("xpcall", "function", "handler", "..."),
        fn("error", "message", "level?"),
        fn("assert", "value", "message?"),
        fn("select", "index", "..."),
        fn("rawget", "table", "key"),
        fn("rawset", "table", "key", "value"),
        fn("rawequal", "a", "b"),
        fn("rawlen", "value"),
        fn("setmetatable", "table", "metatable"),
        fn("getmetatable", "value"),
        fn("load", "chunk", "name?"),
        fn("require", "name"),
        fn("collectgarbage", "option?"),
    )

    val modules: Map<String, List<ApiEntry>> = mapOf(
        "string" to listOf(
            fn("byte", "s", "i?", "j?"),
            fn("char", "..."),
            fn("find", "s", "pattern", "init?", "plain?"),
            fn("format", "format", "..."),
            fn("gmatch", "s", "pattern"),
            fn("gsub", "s", "pattern", "replacement", "n?"),
            fn("len", "s"),
            fn("lower", "s"),
            fn("match", "s", "pattern", "init?"),
            fn("rep", "s", "n", "separator?"),
            fn("reverse", "s"),
            fn("sub", "s", "i", "j?"),
            fn("upper", "s"),
        ),
        "table" to listOf(
            fn("concat", "table", "separator?", "i?", "j?"),
            fn("insert", "table", "position?", "value"),
            fn("remove", "table", "position?"),
            fn("sort", "table", "comparator?"),
            fn("pack", "..."),
            fn("unpack", "table", "i?", "j?"),
        ),
        "math" to listOf(
            fn("abs", "x"),
            fn("ceil", "x"),
            fn("floor", "x"),
            fn("max", "x", "..."),
            fn("min", "x", "..."),
            fn("random", "m?", "n?"),
            fn("randomseed", "x"),
            fn("sqrt", "x"),
            fn("fmod", "x", "y"),
            fn("modf", "x"),
            fn("exp", "x"),
            fn("log", "x", "base?"),
            fn("pow", "x", "y"),
            fn("sin", "x"),
            fn("cos", "x"),
            fn("tan", "x"),
            ApiEntry("pi", EntryKind.CONSTANT),
            ApiEntry("huge", EntryKind.CONSTANT),
        ),
        "os" to listOf(
            fn("time", "table?"),
            fn("clock"),
            fn("date", "format?", "time?"),
            fn("getenv", "name"),
        ),
        "coroutine" to listOf(
            fn("create", "function"),
            fn("resume", "coroutine", "..."),
            fn("yield", "..."),
            fn("status", "coroutine"),
            fn("wrap", "function"),
            fn("running"),
        ),
    )

    private val constants: List<ApiEntry> by lazy {
        val sources = linkedMapOf<String, String>()

        for (group in LuaConstants.groups)
            for (name in runCatching { group.constants.keys }.getOrDefault(emptySet()))
                if (LuaLexer.isIdentifier(name))
                    sources[name] = group.sourceOf(name)

        sources["ACTION_DOWN"] = "KeyEvent.ACTION_DOWN"
        sources["ACTION_UP"] = "KeyEvent.ACTION_UP"

        sources.map { (name, source) -> ApiEntry(name, EntryKind.CONSTANT, description = source) }
    }

    val globals: Map<String, ApiEntry> by lazy {
        buildMap {
            constants.forEach { put(it.name, it) }
            modules.keys.forEach { put(it, ApiEntry(it, EntryKind.MODULE)) }
            builtins.forEach { put(it.name, it) }
            outline.forEach { put(it.name, it) }
            LuaLexer.keywords.forEach { put(it, ApiEntry(it, EntryKind.KEYWORD)) }
        }
    }

    fun member(module: String, name: String): ApiEntry? = modules[module]?.find { it.name == name }
}
