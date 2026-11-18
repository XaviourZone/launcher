package com.xaviourzone.focuslauncher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * When Focus mode is on, removes notifications from every app that is not
 * pinned (or on the extra "allowed" list). Calls, alarms and system
 * notifications always get through.
 */
class FocusNotificationListener : NotificationListenerService() {

    companion object {
        fun isEnabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(ctx, FocusNotificationListener::class.java).flattenToString()
            return flat.split(':').any { it == me }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        sweep()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (shouldBlock(sbn)) mute(sbn)
    }

    private fun sweep() {
        val prefs = Prefs(this)
        if (!prefs.focusMode) return
        try {
            activeNotifications?.forEach { if (shouldBlock(it)) mute(it) }
        } catch (_: Exception) {}
    }

    private fun shouldBlock(sbn: StatusBarNotification): Boolean {
        val prefs = Prefs(this)
        if (!prefs.focusMode) return false
        val pkg = sbn.packageName
        if (pkg == packageName) return false
        if (pkg in prefs.pinned || pkg in prefs.allowedNotif) return false
        if (pkg in Apps.essentialPackages(this)) return false
        if (!sbn.isClearable) return false // ongoing (music, navigation, downloads)
        val cat = sbn.notification.category
        if (cat == Notification.CATEGORY_CALL || cat == Notification.CATEGORY_ALARM) return false
        return true
    }

    private fun mute(sbn: StatusBarNotification) {
        try {
            cancelNotification(sbn.key)
            val prefs = Prefs(this)
            prefs.blockedCount = prefs.blockedCount + 1
        } catch (_: Exception) {}
    }
}
