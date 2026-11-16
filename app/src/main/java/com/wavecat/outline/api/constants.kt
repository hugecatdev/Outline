package com.wavecat.outline.api

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

class ConstantGroup(
    private val owner: Class<*>,
    private val prefix: String,
    private val alias: String = "",
) {
    val constants: Map<String, Int>
        get() = owner.declaredFields
            .filter { it.name.startsWith(prefix) }
            .associate { alias + it.name.substring(prefix.length) to it.getInt(null) }

    fun sourceOf(name: String) = "${owner.simpleName}.$prefix${name.removePrefix(alias)}"
}

object LuaConstants {
    val globalActions = ConstantGroup(AccessibilityService::class.java, "GLOBAL_ACTION_", "GLOBAL_")
    val eventTypes = ConstantGroup(AccessibilityEvent::class.java, "TYPE_")
    val keyCodes = ConstantGroup(KeyEvent::class.java, "KEYCODE_")

    val groups = listOf(globalActions, eventTypes, keyCodes)
}
