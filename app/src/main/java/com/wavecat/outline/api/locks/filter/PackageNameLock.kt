package com.wavecat.outline.api.locks.filter

import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.utils.Lock

class PackageNameLock(private val pkgName: String) : Lock() {
    override fun tryUnlock(event: Any): Boolean? =
        if (event !is AccessibilityEvent) null else event.packageName == pkgName
}