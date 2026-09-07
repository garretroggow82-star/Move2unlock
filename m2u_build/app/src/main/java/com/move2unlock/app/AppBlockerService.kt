package com.move2unlock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.facebook.katana"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        if (packageName !in blockedApps) return

        val prefs = getSharedPreferences("move2unlock", MODE_PRIVATE)
        val unlockUntil = prefs.getLong("unlock_$packageName", 0L)

        if (System.currentTimeMillis() < unlockUntil) {
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("blocked_package", packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        startActivity(intent)
    }

    override fun onInterrupt() {
    }
}
