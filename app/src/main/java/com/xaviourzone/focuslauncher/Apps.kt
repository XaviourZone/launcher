package com.xaviourzone.focuslauncher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.telecom.TelecomManager
import android.util.TypedValue
import android.widget.Toast
import kotlin.concurrent.thread

data class AppEntry(val pkg: String, val label: String)

fun Context.dp(v: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

object Apps {
    private val iconCache = HashMap<String, Drawable>()
    private val labelCache = HashMap<String, String>()

    /** All apps that have a launcher icon, sorted by name. */
    fun launchable(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != ctx.packageName }
            .distinctBy { it.first }
            .map { AppEntry(it.first, it.second) }
            .sortedBy { it.label.lowercase() }
    }

    fun label(ctx: Context, pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val pm = ctx.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun icon(ctx: Context, pkg: String): Drawable? {
        iconCache[pkg]?.let { return it }
        return try {
            ctx.packageManager.getApplicationIcon(pkg).also { iconCache[pkg] = it }
        } catch (e: Exception) {
            null
        }
    }

    fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Opens an app and (optionally) closes other non-pinned background apps. */
    fun launch(ctx: Context, pkg: String) {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (intent == null) {
            Toast.makeText(ctx, "Can't open this app", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Can't open this app", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = Prefs(ctx)
        if (prefs.killOnLaunch) closeBackgroundApps(ctx, keep = setOf(pkg))
    }

    /**
     * Asks Android to close the background processes of every non-pinned,
     * non-system app. Android only lets launchers stop apps that are idle in
     * the background, so apps playing music or in a call keep running.
     */
    fun closeBackgroundApps(ctx: Context, keep: Set<String> = emptySet(), report: Boolean = false) {
        val app = ctx.applicationContext
        thread(name = "focus-cleaner") {
            val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = app.packageManager
            val protect = HashSet<String>().apply {
                addAll(Prefs(app).pinned)
                addAll(keep)
                add(app.packageName)
                addAll(essentialPackages(app))
            }
            var count = 0
            @Suppress("DEPRECATION")
            val installed = pm.getInstalledApplications(0)
            for (ai in installed) {
                if (ai.packageName in protect) continue
                if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) continue
                try {
                    am.killBackgroundProcesses(ai.packageName); count++
                } catch (_: Exception) {
                }
            }
            if (report) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(app, "Asked $count background apps to close", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Apps that must never be blocked or closed (keyboard, dialer, system UI). */
    fun essentialPackages(ctx: Context): Set<String> {
        val s = HashSet<String>()
        s += listOf("android", "com.android.systemui", "com.android.phone", "com.android.server.telecom")
        try {
            val tm = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            tm.defaultDialerPackage?.let { s += it }
        } catch (_: Exception) {
        }
        try {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.substringBefore('/')?.let { s += it }
        } catch (_: Exception) {
        }
        return s
    }
}
