package com.wavecat.outline.services

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.wavecat.outline.api.locks.AccessibilityButtonEvent

class AccessibilityButtonService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        OutlineService.instance?.dispatchEvent(AccessibilityButtonEvent)
                    }
                }
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}
}
