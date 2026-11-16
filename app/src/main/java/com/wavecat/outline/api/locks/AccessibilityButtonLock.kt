package com.wavecat.outline.api.locks

import com.wavecat.outline.api.locks.utils.Lock

object AccessibilityButtonEvent {
    override fun toString() = "AccessibilityButton"
}

class AccessibilityButtonLock : Lock() {
    override fun tryUnlock(event: Any): Boolean? = if (event === AccessibilityButtonEvent) true else null
}
