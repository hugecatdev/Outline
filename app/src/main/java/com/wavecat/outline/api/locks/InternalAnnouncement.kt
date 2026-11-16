package com.wavecat.outline.api.locks

import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.utils.Lock

class InternalAnnouncement(
    private val announcement: String
) : Lock() {
    override fun tryUnlock(event: Any): Boolean? =
        if (event !is AccessibilityEvent) null
        else event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED && event.text.contains(announcement)
}