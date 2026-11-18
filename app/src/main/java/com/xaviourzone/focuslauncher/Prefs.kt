package com.xaviourzone.focuslauncher

import android.content.Context
import android.content.SharedPreferences

/** All user settings in one place. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("focus_launcher", Context.MODE_PRIVATE)

    private fun list(key: String): List<String> =
        (sp.getString(key, "") ?: "").split(',').filter { it.isNotBlank() }

    private fun setList(key: String, v: List<String>) =
        sp.edit().putString(key, v.distinct().joinToString(",")).apply()

    /** Pinned apps, in display order. Shown on home + in the side tab. */
    var pinned: List<String>
        get() = list("pinned")
        set(v) = setList("pinned", v)

    /** Extra apps whose notifications are allowed in focus mode (not shown in the tab). */
    var allowedNotif: List<String>
        get() = list("allowed_notif")
        set(v) = setList("allowed_notif", v)

    var widgetIds: List<Int>
        get() = list("widgets").mapNotNull { it.toIntOrNull() }
        set(v) = setList("widgets", v.map { it.toString() })

    fun widgetHeightDp(id: Int, default: Int): Int = sp.getInt("wh_$id", default)
    fun setWidgetHeightDp(id: Int, h: Int) = sp.edit().putInt("wh_$id", h).apply()
    fun removeWidgetHeight(id: Int) = sp.edit().remove("wh_$id").apply()

    var overlayEnabled: Boolean
        get() = sp.getBoolean("overlay", true)
        set(v) = sp.edit().putBoolean("overlay", v).apply()

    /** true = right edge, false = left edge */
    var rightSide: Boolean
        get() = sp.getBoolean("right", true)
        set(v) = sp.edit().putBoolean("right", v).apply()

    /** Vertical position of the tab, 0..1 of screen height */
    var handleY: Float
        get() = sp.getFloat("handle_y", 0.35f)
        set(v) = sp.edit().putFloat("handle_y", v.coerceIn(0.05f, 0.9f)).apply()

    /** Tab height in dp */
    var handleHeightDp: Int
        get() = sp.getInt("handle_h", 110)
        set(v) = sp.edit().putInt("handle_h", v).apply()

    /** Tab opacity 0..100 */
    var handleAlpha: Int
        get() = sp.getInt("handle_a", 55)
        set(v) = sp.edit().putInt("handle_a", v).apply()

    /** Block notifications from apps that are not pinned / allowed */
    var focusMode: Boolean
        get() = sp.getBoolean("focus", false)
        set(v) = sp.edit().putBoolean("focus", v).apply()

    /** Close non-pinned background apps when a pinned app is opened */
    var killOnLaunch: Boolean
        get() = sp.getBoolean("kill", true)
        set(v) = sp.edit().putBoolean("kill", v).apply()

    var blockedCount: Int
        get() = sp.getInt("blocked_count", 0)
        set(v) = sp.edit().putInt("blocked_count", v).apply()

    fun registerListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.registerOnSharedPreferenceChangeListener(l)

    fun unregisterListener(l: SharedPreferences.OnSharedPreferenceChangeListener) =
        sp.unregisterOnSharedPreferenceChangeListener(l)
}
