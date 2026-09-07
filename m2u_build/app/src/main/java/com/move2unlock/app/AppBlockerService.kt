package com.move2unlock.app

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class AppBlockerService : AccessibilityService() {

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.facebook.katana"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName in blockedApps) {
            Toast.makeText(
                this,
                "🔒 Complete your reps in Move2Unlock first!",
                Toast.LENGTH_SHORT
            ).show()

            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onInterrupt() {
    }
}
