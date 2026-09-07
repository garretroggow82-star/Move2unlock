package com.move2unlock.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class AppBlockerService : AccessibilityService() {

    private fun getBlockedApps(): Set<String> {
        val prefs = getSharedPreferences("move2unlock", MODE_PRIVATE)

        return prefs.getStringSet(
            "blocked_apps",
            setOf(
                "com.facebook.katana",
                "com.instagram.android",
                "com.zhiliaoapp.musically"
            )
        ) ?: emptySet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (pkg !in getBlockedApps()) return

        val prefs = getSharedPreferences("move2unlock", MODE_PRIVATE)

        val unlockUntil = prefs.getLong("unlock_$pkg", 0L)

        if (System.currentTimeMillis() < unlockUntil) {
            return
        }

        prefs.edit()
            .putString("pending_package", pkg)
            .apply()

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("blocked_package", pkg)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }

        startActivity(intent)
    }

    override fun onInterrupt() {}
}
