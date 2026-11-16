# Outline

Outline runs Lua scripts inside an Android accessibility service. A script waits for an event, for example a double click of a volume key or a button appearing on the screen, and then does something with the phone.

```lua
while true do
    waitFor(DoubleClick(VOLUME_UP))
    mediaNext()
end
```

This script switches to the next track when volume up is clicked twice.

## Contents

- [Features](#features)
- [Getting started](#getting-started)
- [How scripts work](#how-scripts-work)
- [Examples](#examples)
- [Locks and combinators](#locks-and-combinators)
- [Key events](#key-events)
- [API reference](#api-reference)
- [Editor](#editor)
- [AI script generation](#ai-script-generation)
- [Building](#building)
- [Project structure](#project-structure)
- [License](#license)

## Features

- Scripts in Lua 5.2 on LuaJ. Each script is a coroutine.
- Triggers built from locks: key clicks and sequences, several keys at once, screen events, the accessibility button. Locks can be combined with each other.
- Screen automation through the accessibility API: finding nodes, clicks, scrolling, text input, global actions like Back and Home.
- Media control, text to speech, vibration, toasts, clipboard and a built-in WebView.
- An editor with syntax highlighting, completion and hints for function arguments.
- Script generation from a description, using an OpenAI-compatible API or Gemini Nano on the device.

## Getting started

1. Install the app. It needs Android 5.0 or newer.
2. In Settings > Accessibility, enable Main service. It runs the scripts and receives key events.
3. The other two services are optional. Window content service delivers screen events and is needed for `Node`, `ViewClicked` and other screen locks. Outline button lets scripts react to the accessibility button: assign it to the button in the accessibility settings. It needs Android 8.0 or newer.
4. Write a script in the editor and tap the save button. Scripts start right away and again every time the service starts.

## How scripts work

Each script is a coroutine. It runs once from top to bottom, so anything that should repeat goes into `while true do ... end`. When the function returns, the script ends.

`waitFor(lock)` and `delay(ms)` pause only the script that called them. There are no callbacks: when the lock unlocks, execution continues on the next line.

All scripts share one thread. The code between two waits runs without interruption, and events are not delivered while it runs. A loop without `waitFor` or `delay` blocks every script, so scripts should never poll.

To run several scripts at once, separate them in the editor with a line that contains only `-----`. Errors name scripts by position, `#0`, `#1` and so on, and count lines from the start of each script. A running script can also start another one with `runScript(function() ... end)`.

## Examples

### Two independent gestures

Each part is a separate coroutine, so both gestures work at the same time.

```lua
while true do
    waitFor(DoubleClick(VOLUME_UP))
    mediaNext()
end
-----
while true do
    waitFor(DoubleClick(VOLUME_DOWN))
    mediaPrevious()
end
```

### Key sequence

Down, up, down, up with at most two seconds between presses speaks the time. `speakAndWait` pauses the script until the phrase is finished; other scripts keep working meanwhile.

```lua
while true do
    waitFor(Steps(
        SingleClick(VOLUME_DOWN),
        SingleClick(VOLUME_UP),
        SingleClick(VOLUME_DOWN),
        SingleClick(VOLUME_UP),
        2000
    ))

    speakAndWait("It is " .. os.date("%H:%M"))
end
```

### Both volume keys at once

`Combined` unlocks when all locks fire within the window, in any order.

```lua
while true do
    waitFor(Combined(KeyEvent(VOLUME_UP, ACTION_DOWN), KeyEvent(VOLUME_DOWN, ACTION_DOWN), 300))

    if isHeadphonesConnected() then
        takeScreenshot()
    end
end
```

### Choosing between triggers

`Any` unlocks on the first lock that fires. `getKeyEvent()` shows which key it was.

```lua
while true do
    waitFor(Any(DoubleClick(VOLUME_UP), DoubleClick(VOLUME_DOWN)))

    if getKeyEvent():getKeyCode() == VOLUME_UP then
        mediaNext()
    else
        mediaPrevious()
    end
end
```

### Volume up skips tracks only while music plays

A lock on `ACTION_DOWN` hides the key press from the system. `ignoreKey()` lets it through, so the volume key keeps working when nothing is playing.

```lua
while true do
    waitFor(KeyEvent(VOLUME_UP, ACTION_DOWN))

    if isMusicActive() then
        mediaNext()
    else
        ignoreKey()
    end
end
```

### Double press of the accessibility button

Needs the Outline button service.

```lua
while true do
    waitFor(Steps(AccessibilityButton(), AccessibilityButton(), 500))
    perform(GLOBAL_NOTIFICATIONS)
end
```

### Press a button whenever it appears

Needs Window content service. With `fromRoot = true`, `Node` searches the whole window instead of the view that sent the event.

```lua
while true do
    waitFor(All(PackageName("com.google.android.youtube"), Node({ containsText = "Skip" }, true)))

    local button = findNode(findRoot(), { containsText = "Skip" })

    if button then
        click(button)
    end
end
```

### A timer

Three clicks of volume down start a five minute timer.

```lua
while true do
    waitFor(TripleClick(VOLUME_DOWN))
    toast("Timer started")
    delay(5 * 60 * 1000)
    speakAndWait("Five minutes passed")
end
```

### Starting a script from a script

`runScriptUnique` does not start a second copy while one with the same name is running.

```lua
waitFor(TripleClick(VOLUME_UP))

runScriptUnique(function()
    while true do
        waitFor(DoubleClick(VOLUME_DOWN))
        mediaPrevious()
    end
end, "previous")

speak("Previous track gesture enabled")
```

## Locks and combinators

A lock describes what a script is waiting for. A simple lock matches one event, and combinators build gestures out of other locks. Combinators can be nested.

| Combinator | Unlocks when |
| --- | --- |
| `Steps(a, b, ..., interval)` | the locks fire in this order, at most `interval` ms between steps |
| `Combined(a, b, ..., interval)` | all locks fire within `interval` ms, in any order |
| `Any(a, b, ...)` | any of the locks fires |
| `All(a, b, ...)` | all locks match the same event |

`waitFor(a, b)` is a shortcut for `waitFor(All(a, b))`.

A lock ignores events of other kinds, so locks of different kinds can be combined, for example `Steps(AccessibilityButton(), DoubleClick(VOLUME_UP), 2000)`.

Each time a script starts waiting for a lock, the lock is reset. One lock object can therefore be created once and reused in a loop.

Combinators already handle timeouts and partially entered gestures, so there is no need to build gestures out of `delay`, counters or several `waitFor` calls in a row.

## Key events

Android delivers each key press as `ACTION_DOWN` followed by `ACTION_UP`. Outline keeps the pair consistent:

- A press is hidden from the system only when a lock unlocks on its `ACTION_DOWN`. Its `ACTION_UP` and key repeats are hidden as well.
- Click locks such as `SingleClick` and `DoubleClick` unlock on `ACTION_UP`, so the presses themselves still reach the system. On the volume rocker this means the volume changes with every press of a gesture.
- `ignoreKey()` lets the event that unlocked the script pass through anyway.

## API reference

Optional arguments are marked with `?`, defaults with `=`. Constructors that return locks start with a capital letter.

### Scripts

| Function | Description |
| --- | --- |
| `waitFor(lock...)` | Suspends the script until the locks unlock. Without arguments waits forever |
| `delay(ms)` | Suspends the script for the given time |
| `runScript(function, name?)` | Starts the function as a separate script |
| `runScriptUnique(function, name)` | Starts the script unless one with this name is running |
| `ignoreKey()` | Lets the key event that resumed the script pass through to the system |
| `getKeyEvent()` | Key event that unlocked the script |
| `getAccessibilityEvent()` | Accessibility event that unlocked the script |
| `getPreferences()` | App `SharedPreferences` |

### Key locks

| Function | Description |
| --- | --- |
| `SingleClick(keyCode, interval = 700)` | A click of the key |
| `DoubleClick(keyCode, interval = 700)` | Two clicks |
| `TripleClick(keyCode, interval = 700)` | Three clicks |
| `QuadrupleClick(keyCode, interval = 700)` | Four clicks |
| `KeyEvent(keyCode?, action?)` | A key event, optionally of the given key and action |

### Screen and button locks

| Function | Description |
| --- | --- |
| `Node(query, fromRoot = false)` | A node matching the query appears in the event source, or in the whole window with `fromRoot` |
| `Text(text)` | A node with exactly this text |
| `ContainsText(text)` | A node containing this text |
| `PackageName(packageName)` | An event from the app |
| `EventType(type)` | An accessibility event of the type, for example `VIEW_CLICKED` |
| `WindowContentChanged()` | Window content changed |
| `ViewClicked()`, `ViewLongClicked()` | A view was clicked or long clicked |
| `ViewFocused()`, `ViewSelected()`, `ViewTextChanged()` | A view was focused, selected or edited |
| `AccessibilityButton()` | The accessibility button was pressed |
| `WebViewReady()`, `PageLoaded()` | The built-in WebView is ready or finished loading a page |
| `IsChrome()` | An event from Chrome |

A node query is a table with any of the fields `text`, `containsText`, `resourceName`, `className`, `contentDescription`, `hintText`, `isFocused`, `isFocusable`.

### Nodes

| Function | Description |
| --- | --- |
| `findRoot(event?)` | Root node of the event window, by default of the last event |
| `findNode(node, query)` | First descendant matching the query |
| `findAllNodes(node, query)` | All descendants matching the query |
| `iterNodes(node, query, callback)` | Calls the callback for every matching descendant |
| `click(node)`, `longClick(node)` | Clicks the node |
| `focus(node)`, `clearFocus(node)` | Changes input focus |
| `select(node)`, `clearSelection(node)` | Changes selection |
| `copy(node)`, `cut(node)`, `paste(node)` | Clipboard actions on the node |
| `scrollForward(node)`, `scrollBackward(node)` | Scrolls the node |
| `setText(node, text)` | Replaces the text of an editable node |
| `nextHtmlElement(node, element)`, `previousHtmlElement(node, element)` | Moves between HTML elements in web content |

### System

| Function | Description |
| --- | --- |
| `perform(globalAction)` | Performs a global action such as `GLOBAL_BACK` or `GLOBAL_HOME` |
| `takeScreenshot()` | Takes a screenshot, Android 9.0 or newer |
| `vibrate(ms)` | Vibrates |
| `toast(text)` | Shows a toast |
| `speak(text, mode = "add")` | Speaks the text without waiting. Mode `"flush"` interrupts current speech |
| `speakAndWait(text, mode = "add")` | Speaks the text and suspends the script until it is finished |
| `clip(text)` | Copies the text to the clipboard |

### Media

| Function | Description |
| --- | --- |
| `mediaNext()`, `mediaPrevious()` | Switches tracks |
| `mediaPlay()`, `mediaPause()` | Controls playback |
| `mediaKeyEvent(action, keyCode)` | Dispatches any media key event |
| `volumeUp()`, `volumeDown()` | Changes the music volume |
| `isMusicActive()` | Whether music is playing |
| `isHeadphonesConnected()` | Whether wired, USB or Bluetooth headphones are connected. Bluetooth speakers count too |

### Web

| Function | Description |
| --- | --- |
| `openInChrome(url)` | Opens the URL in Chrome or the default browser |
| `openWebView(url?)` | Opens the built-in WebView and returns a `WebViewReady` lock |
| `openUrl(url)` | Loads the URL in the built-in WebView |
| `setWebViewSize(width, height?)` | Resizes the built-in WebView |
| `webViewClick(x, y)` | Clicks inside the built-in WebView |
| `clearWebViewData()` | Clears cookies, storage, cache and history of the built-in WebView |

### Globals and constants

| Name | Description |
| --- | --- |
| `VOLUME_UP`, `VOLUME_DOWN`, ... | Key codes: Android `KEYCODE_*` constants without the prefix |
| `VIEW_CLICKED`, `WINDOW_CONTENT_CHANGED`, ... | Accessibility event types: `TYPE_*` constants without the prefix |
| `GLOBAL_BACK`, `GLOBAL_HOME`, ... | Global actions for `perform`: `GLOBAL_ACTION_*` with the `GLOBAL_` prefix |
| `ACTION_DOWN`, `ACTION_UP` | Key actions |
| `service` | The accessibility service instance |
| `AccessibilityNodeInfo` | The `AccessibilityNodeInfo` class |

The Lua standard library is available as well: `string`, `table`, `math`, `os`, `coroutine` and the base functions.

## Editor

- Outline functions and constants are highlighted in their own colors, so a typo in an API name stays uncolored.
- The bar above the keyboard suggests Outline functions, constants, the Lua standard library and names from the script.
- When the cursor is inside a call, the bar shows the signature of the function with the current argument in bold.
- Line numbers restart after every `-----`, the same way error messages count them.
- When there is nothing to suggest, the bar shows symbols that are awkward to type on a phone keyboard and a key that inserts a script separator. New lines keep the indentation of the previous one.

## AI script generation

The AI button writes a script from a description. If some code is selected, the model rewrites the selection instead.

There are two providers. The OpenAI-compatible provider works with any service that implements the OpenAI chat completions API. Enter the base URL, the API key and the model; the list of models is loaded from `/models`. The key stays on the device and is excluded from backups.

Gemini Nano runs on the device through Android AICore and does not need a network connection or a key. It needs Android 8.0 or newer and a device with AICore. The model is downloaded on first use. The Preview model switch selects AICore preview models on devices enrolled in the preview program.

Before the code is inserted, Outline compiles it and checks that every called function exists. If something is wrong, the model gets one attempt to fix it. After the code is inserted, Reply continues the conversation, and changes you made to the code are sent along with your message. Generated code is never started automatically, it runs only after you save it.

## Building

Requirements: JDK 17 and the Android SDK with platform 36.

```sh
./gradlew assembleDebug
```

Release builds are signed when a `keystore.properties` file is present in the project root:

```properties
storeFile=outline-release.jks
storePassword=your-store-password
keyAlias=outline
keyPassword=your-key-password
```

```sh
./gradlew assembleRelease
```

The keystore and `keystore.properties` are ignored by git. Without them the release build is produced unsigned.

## Project structure

| Path | Contents |
| --- | --- |
| `app/src/main/java/com/wavecat/outline/services` | Accessibility services that run scripts and forward events |
| `app/src/main/java/com/wavecat/outline/api` | Script runtime and the Lua libraries |
| `app/src/main/java/com/wavecat/outline/api/locks` | Locks and combinators |
| `app/src/main/java/com/wavecat/outline/editor` | Lua lexer, highlighting, completion and the editor views |
| `app/src/main/java/com/wavecat/outline/ai` | Model providers, prompt, validation and the generation flow |

## License

MIT, see [LICENSE](LICENSE).
