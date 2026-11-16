package com.wavecat.outline.api

import android.view.KeyEvent

class KeyConsumption {
    private val consumedKeys = HashSet<Int>()

    fun decide(keyCode: Int, action: Int, repeatCount: Int, requested: Boolean): Boolean = when (action) {
        KeyEvent.ACTION_DOWN ->
            if (repeatCount > 0) {
                keyCode in consumedKeys
            } else {
                if (requested) consumedKeys += keyCode else consumedKeys -= keyCode
                requested
            }

        KeyEvent.ACTION_UP -> consumedKeys.remove(keyCode)

        else -> requested
    }

    fun clear() = consumedKeys.clear()
}
