package com.xaviourzone.focuslauncher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.Toast
import kotlin.concurrent.thread

class SettingsActivity : Activity() {

    companion object {
        private const val REQ_ROLE = 10
        private const val REQ_IMAGE = 11
        private const val REQ_NOTIF = 12
    }

    private lateinit var prefs: Prefs
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val scroll = ScrollView(this)
        content = Ui.vertical(this).apply { setPadding(dp(18), dp(40), dp(18), dp(40)) }
        scroll.addView(content)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ------------------------------------------------------------ building blocks

    private fun section(title: String) {
        content.addView(Ui.text(this, title.uppercase(), 12f, Ui.ACCENT, true),
            Ui.lp().apply { topMargin = dp(26); bottomMargin = dp(8) })
    }

    private fun card(): LinearLayout = Ui.vertical(this).apply {
        background = Ui.rounded(Ui.CARD, dp(16).toFloat())
        setPadding(dp(14), dp(6), dp(14), dp(6))
        content.addView(this, Ui.lp())
    }

    private fun row(parent: LinearLayout, title: String, sub: String?, action: View?, onClick: (() -> Unit)? = null) {
        val r = Ui.horizontal(this).apply {
            setPadding(0, dp(10), 0, dp(10))
            if (onClick != null) setOnClickListener { onClick() }
        }
        val texts = Ui.vertical(this)
        texts.addView(Ui.text(this, title, 16f))
        if (sub != null) texts.addView(Ui.text(this, sub, 13f, Ui.SUB))
        r.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        if (action != null) r.addView(action, Ui.lp(-2).apply { leftMargin = dp(10) })
        parent.addView(r, Ui.lp())
    }

    private fun status(ok: Boolean, fix: () -> Unit): View =
        if (ok) Ui.text(this, "✓ Done", 14f, Ui.ACCENT, true)
        else Ui.pill(this, "Allow", Ui.ACCENT, 0xFF0E1A15.toInt()) { fix() }

    private fun switch(on: Boolean, change: (Boolean) -> Unit): Switch = Switch(this).apply {
        isChecked = on
        setOnCheckedChangeListener { _, v -> change(v) }
    }

    private fun choice(options: List<Pair<String, Boolean>>, pick: (Int) -> Unit): View {
        val box = Ui.horizontal(this)
        options.forEachIndexed { i, (label, selected) ->
            box.addView(Ui.pill(this, label,
                if (selected) Ui.ACCENT else 0x22FFFFFF,
                if (selected) 0xFF0E1A15.toInt() else Ui.TEXT) { pick(i); render() },
                Ui.lp(-2).apply { leftMargin = if (i == 0) 0 else dp(6) })
        }
        return box
    }

    // ------------------------------------------------------------ screen

    private fun render() {
        content.removeAllViews()
        content.addView(Ui.text(this, "Focus Launcher", 28f, Ui.TEXT, true))
        content.addView(Ui.text(this, "Only the apps you choose. Everything else stays quiet.", 14f, Ui.SUB))

        // ---- Setup
        section("Setup")
        val setup = card()
        row(setup, "Default home app", "Make Focus Launcher your home screen", status(isDefaultHome()) { requestHome() })
        row(setup, "Display over other apps", "Needed for the side tab over games and apps",
            status(Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            })
        row(setup, "Notification access", "Lets Focus mode mute non-pinned apps",
            status(FocusNotificationListener.isEnabled(this)) { openNotificationAccess() })
        if (Build.VERSION.SDK_INT >= 33) {
            row(setup, "Show notifications", "For the small 'side tab is on' notice",
                status(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
                })
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        row(setup, "No battery restriction", "Stops Vivo from killing the side tab",
            status(pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            })
        row(setup, "Vivo: allow background / autostart",
            "i Manager → App management → Autostart, and Battery → High background power use → turn on for Focus Launcher",
            Ui.pill(this, "Open") { openVivoBackground() })

        // ---- Pinned apps
        section("Pinned apps")
        val pins = card()
        val names = prefs.pinned.filter { Apps.isInstalled(this, it) }.joinToString(", ") { Apps.label(this, it) }
        row(pins, "Choose pinned apps", if (names.isEmpty()) "None yet — e.g. Phone, WhatsApp" else names,
            Ui.pill(this, "Edit") { openPicker("pinned") }) { openPicker("pinned") }

        // ---- Side tab
        section("Side tab")
        val tab = card()
        row(tab, "Show side tab", "Over every app and game. Swipe or tap it to open pinned apps; long-press and drag to move it.",
            switch(prefs.overlayEnabled) { on ->
                prefs.overlayEnabled = on
                if (on) {
                    if (!Settings.canDrawOverlays(this)) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } else EdgeOverlayService.startIfAllowed(this)
                } else EdgeOverlayService.stop(this)
            })
        row(tab, "Edge", null, choice(listOf("Left" to !prefs.rightSide, "Right" to prefs.rightSide)) {
            prefs.rightSide = it == 1
        })
        val h = prefs.handleHeightDp
        row(tab, "Size", null, choice(listOf("S" to (h == 70), "M" to (h == 110), "L" to (h == 160))) {
            prefs.handleHeightDp = listOf(70, 110, 160)[it]
        })
        val a = prefs.handleAlpha
        row(tab, "Visibility", null, choice(listOf("Faint" to (a == 25), "Normal" to (a == 55), "Bold" to (a == 85))) {
            prefs.handleAlpha = listOf(25, 55, 85)[it]
        })

        // ---- Focus
        section("Focus")
        val focus = card()
        row(focus, "Focus mode", "Mute notifications from apps that aren't pinned. Calls and alarms always come through.",
            switch(prefs.focusMode) { on ->
                prefs.focusMode = on
                if (on && !FocusNotificationListener.isEnabled(this)) openNotificationAccess()
                if (on) Apps.closeBackgroundApps(this)
            })
        row(focus, "Also allow notifications from", "${prefs.allowedNotif.size} extra apps (bank OTP, Messages…)",
            Ui.pill(this, "Edit") { openPicker("allowed") }) { openPicker("allowed") }
        row(focus, "Close other apps on launch", "When you open a pinned app, close background apps that aren't pinned",
            switch(prefs.killOnLaunch) { prefs.killOnLaunch = it })
        row(focus, "Close background apps now", "${prefs.blockedCount} notifications muted so far",
            Ui.pill(this, "Clean") { Apps.closeBackgroundApps(this, report = true) })

        // ---- Look
        section("Home screen")
        val look = card()
        row(look, "Wallpaper", "Pick any image from your phone", Ui.pill(this, "Choose") { pickImage() })
        row(look, "Widgets", "On the home screen tap “+ Widget”. Tap “Edit” to resize or remove.", null)

        content.addView(Ui.text(this,
            "Tip: long-press the clock or empty space on the home screen to come back here.",
            12f, Ui.SUB).apply { gravity = Gravity.CENTER }, Ui.lp().apply { topMargin = dp(24) })
    }

    // ------------------------------------------------------------ actions

    private fun openPicker(mode: String) =
        startActivity(Intent(this, AppPickerActivity::class.java).putExtra("mode", mode))

    private fun isDefaultHome(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val r = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)
        return r?.activityInfo?.packageName == packageName
    }

    private fun requestHome() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val rm = getSystemService(RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                    @Suppress("DEPRECATION")
                    startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_ROLE)
                    return
                }
            } catch (_: Exception) {}
        }
        openHomeSettings()
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openNotificationAccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    ComponentName(this, FocusNotificationListener::class.java).flattenToString()))
                return
            }
        } catch (_: Exception) {}
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openVivoBackground() {
        val candidates = listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        )
        for (c in candidates) {
            try {
                startActivity(Intent().setComponent(c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return
            } catch (_: Exception) {}
        }
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        @Suppress("DEPRECATION")
        startActivityForResult(i, REQ_IMAGE)
    }

    private fun applyWallpaper(uri: Uri) {
        val opts = arrayOf("Home screen", "Lock screen", "Both")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Set wallpaper on")
            .setItems(opts) { _, which ->
                val flags = when (which) {
                    0 -> WallpaperManager.FLAG_SYSTEM
                    1 -> WallpaperManager.FLAG_LOCK
                    else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                Toast.makeText(this, "Setting wallpaper…", Toast.LENGTH_SHORT).show()
                thread {
                    val msg = try {
                        contentResolver.openInputStream(uri)?.use {
                            WallpaperManager.getInstance(this).setStream(it, null, true, flags)
                        }
                        "Wallpaper set"
                    } catch (e: Exception) {
                        "Couldn't set wallpaper: ${e.message}"
                    }
                    Handler(Looper.getMainLooper()).post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
                }
            }.show()
    }

    @Deprecated("Uses platform Activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_ROLE -> if (!isDefaultHome()) openHomeSettings()
            REQ_IMAGE -> if (resultCode == RESULT_OK) data?.data?.let { applyWallpaper(it) }
        }
    }
}
