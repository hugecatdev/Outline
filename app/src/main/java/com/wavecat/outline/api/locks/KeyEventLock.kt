package com.wavecat.outline.api.locks

import android.view.KeyEvent
import com.wavecat.outline.api.locks.utils.Lock

class KeyEventLock(
    private val nextKeyCode: Int?,
    private val nextAction: Int?,
) : Lock() {
    override fun tryUnlock(event: Any): Boolean? =
        if (event !is KeyEvent) null
        else (nextAction == null || event.action == nextAction) && (nextKeyCode == null || event.keyCode == nextKeyCode)
}