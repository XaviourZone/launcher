package com.xaviourzone.focuslauncher

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.Toast

class HomeActivity : Activity() {

    companion object {
        private const val HOST_ID = 1024
        private const val REQ_PICK = 1
        private const val REQ_BIND = 2
        private const val REQ_CONFIG = 3
    }

    private lateinit var prefs: Prefs
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetManager: AppWidgetManager

    private lateinit var scroll: ScrollView
    private lateinit var widgetsBox: LinearLayout
    private lateinit var dock: GridLayout
    private lateinit var focusChip: android.widget.TextView
    private lateinit var editRow: LinearLayout

    private var editMode = false
    private var pendingWidgetId = -1

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "pinned" -> renderDock()
            "focus", "blocked_count" -> renderFocusChip()
            else -> {}
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        widgetManager = AppWidgetManager.getInstance(this)
        widgetHost = AppWidgetHost(applicationContext, HOST_ID)
        pendingWidgetId = savedInstanceState?.getInt("pending", -1) ?: -1
        buildUi()
        renderAll()
        prefs.registerListener(prefListener)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("pending", pendingWidgetId)
    }

    override fun onStart() {
        super.onStart()
        try { widgetHost.startListening() } catch (_: Exception) {}
    }

    override fun onStop() {
        super.onStop()
        try { widgetHost.stopListening() } catch (_: Exception) {}
    }

    override fun onDestroy() {
        prefs.unregisterListener(prefListener)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        renderDock()
        renderFocusChip()
        EdgeOverlayService.startIfAllowed(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home button pressed while already home
        if (editMode) setEditMode(false)
        scroll.smoothScrollTo(0, 0)
    }

    @Deprecated("Launcher ignores back")
    override fun onBackPressed() {
        if (editMode) setEditMode(false)
        // A launcher never closes on back.
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        val root = FrameLayout(this)

        // Soft scrim so text stays readable on any wallpaper
        root.addView(View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x66000000, 0x11000000, 0x22000000, 0x88000000.toInt())
            )
        }, FrameLayout.LayoutParams(-1, -1))

        val column = Ui.vertical(this)
        root.addView(column, FrameLayout.LayoutParams(-1, -1))

        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true }
        column.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val content = Ui.vertical(this).apply {
            setPadding(dp(20), dp(56), dp(20), dp(16))
            setOnLongClickListener { openSettings(); true }
        }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))

        val clock = TextClock(this).apply {
            format12Hour = "h:mm"
            format24Hour = "HH:mm"
            textSize = 64f
            setTextColor(Ui.TEXT)
            typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            setShadowLayer(8f, 0f, 2f, 0x88000000.toInt())
            setOnClickListener { openClockApp() }
            setOnLongClickListener { openSettings(); true }
        }
        content.addView(clock)

        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"
            format24Hour = "EEEE, d MMMM"
            textSize = 17f
            setTextColor(Ui.TEXT)
            setShadowLayer(6f, 0f, 1f, 0x88000000.toInt())
        }
        content.addView(date)

        focusChip = Ui.pill(this, "") {
            prefs.focusMode = !prefs.focusMode
            if (prefs.focusMode && !FocusNotificationListener.isEnabled(this)) {
                Toast.makeText(this, "Allow notification access so Focus can mute other apps", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            if (prefs.focusMode) Apps.closeBackgroundApps(this)
        }
        content.addView(focusChip, Ui.lp(-2).apply { topMargin = dp(14) })

        widgetsBox = Ui.vertical(this)
        content.addView(widgetsBox, Ui.lp().apply { topMargin = dp(18) })

        editRow = Ui.horizontal(this).apply { gravity = Gravity.CENTER }
        editRow.addView(Ui.pill(this, "+ Widget") { startWidgetPick() })
        editRow.addView(Ui.pill(this, "Edit") { setEditMode(!editMode) }.apply { tag = "edit" },
            Ui.lp(-2).apply { leftMargin = dp(10) })
        content.addView(editRow, Ui.lp().apply { topMargin = dp(8) })

        // Dock with pinned apps
        dock = GridLayout(this).apply {
            columnCount = 4
            setPadding(dp(8), dp(12), dp(8), dp(4))
        }
        column.addView(dock, Ui.lp())

        val bottom = Ui.horizontal(this).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(28))
        }
        bottom.addView(Ui.pill(this, "All apps", 0x22FFFFFF) {
            startActivity(Intent(this, AllAppsActivity::class.java))
        })
        bottom.addView(Ui.pill(this, "Settings", 0x22FFFFFF) { openSettings() },
            Ui.lp(-2).apply { leftMargin = dp(12) })
        column.addView(bottom, Ui.lp())

        setContentView(root)
    }

    private fun renderAll() {
        renderDock()
        renderFocusChip()
        renderWidgets()
    }

    private fun renderFocusChip() {
        if (!::focusChip.isInitialized) return
        if (prefs.focusMode) {
            focusChip.text = "● Focus on · ${prefs.blockedCount} muted"
            focusChip.setTextColor(0xFF0E1A15.toInt())
            focusChip.background = Ui.rounded(Ui.ACCENT, dp(20).toFloat())
        } else {
            focusChip.text = "○ Focus off"
            focusChip.setTextColor(Ui.TEXT)
            focusChip.background = Ui.rounded(0x33FFFFFF, dp(20).toFloat())
        }
    }

    private fun renderDock() {
        if (!::dock.isInitialized) return
        dock.removeAllViews()
        val pinned = prefs.pinned.filter { Apps.isInstalled(this, it) }
        if (pinned.isEmpty()) {
            dock.columnCount = 1
            dock.addView(Ui.pill(this, "Pin your apps (Phone, WhatsApp…)") {
                startActivity(Intent(this, AppPickerActivity::class.java))
            }, GridLayout.LayoutParams().apply {
                width = resources.displayMetrics.widthPixels - dp(16)
                setGravity(Gravity.CENTER)
            })
            return
        }
        dock.columnCount = 4
        val cell = (resources.displayMetrics.widthPixels - dp(16)) / 4
        for (pkg in pinned) {
            val tile = Ui.appTile(this, pkg, 52, true).apply {
                setPadding(0, dp(6), 0, dp(6))
                setOnClickListener { Apps.launch(this@HomeActivity, pkg) }
                setOnLongClickListener { pinnedMenu(pkg); true }
            }
            dock.addView(tile, GridLayout.LayoutParams().apply { width = cell })
        }
    }

    private fun pinnedMenu(pkg: String) {
        val items = arrayOf("Move earlier", "Move later", "Unpin", "App info")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(Apps.label(this, pkg))
            .setItems(items) { _, which ->
                val list = prefs.pinned.toMutableList()
                val i = list.indexOf(pkg)
                when (which) {
                    0 -> if (i > 0) { list.removeAt(i); list.add(i - 1, pkg); prefs.pinned = list }
                    1 -> if (i >= 0 && i < list.size - 1) { list.removeAt(i); list.add(i + 1, pkg); prefs.pinned = list }
                    2 -> { list.remove(pkg); prefs.pinned = list }
                    3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                }
            }.show()
    }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun openClockApp() {
        try {
            startActivity(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
    }

    private fun setEditMode(on: Boolean) {
        editMode = on
        (editRow.findViewWithTag<android.widget.TextView>("edit"))?.text = if (on) "Done" else "Edit"
        renderWidgets()
    }

    // ---------------------------------------------------------------- Widgets

    private fun defaultHeightDp(info: AppWidgetProviderInfo): Int {
        val minDp = (info.minHeight / resources.displayMetrics.density).toInt()
        return minDp.coerceIn(90, 400)
    }

    private fun renderWidgets() {
        widgetsBox.removeAllViews()
        val ids = prefs.widgetIds
        val alive = ArrayList<Int>()
        val widthDp = ((resources.displayMetrics.widthPixels - dp(40)) / resources.displayMetrics.density).toInt()
        for (id in ids) {
            val info = widgetManager.getAppWidgetInfo(id) ?: continue
            alive += id
            val hDp = prefs.widgetHeightDp(id, defaultHeightDp(info))
            val hostView = try {
                widgetHost.createView(applicationContext, id, info)
            } catch (e: Exception) {
                continue
            }
            try {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(Bundle(), widthDp, hDp, widthDp, hDp)
            } catch (_: Exception) {}

            widgetsBox.addView(hostView, Ui.lp(-1, dp(hDp)).apply { bottomMargin = dp(10) })

            if (editMode) {
                val ctl = Ui.horizontal(this).apply { gravity = Gravity.CENTER }
                ctl.addView(Ui.pill(this, "Shorter") {
                    prefs.setWidgetHeightDp(id, (hDp - 30).coerceAtLeast(60)); renderWidgets()
                })
                ctl.addView(Ui.pill(this, "Taller") {
                    prefs.setWidgetHeightDp(id, (hDp + 30).coerceAtMost(600)); renderWidgets()
                }, Ui.lp(-2).apply { leftMargin = dp(8) })
                ctl.addView(Ui.pill(this, "Remove", 0x66D04040) { removeWidget(id) },
                    Ui.lp(-2).apply { leftMargin = dp(8) })
                widgetsBox.addView(ctl, Ui.lp().apply { bottomMargin = dp(16) })
            }
        }
        if (alive.size != ids.size) prefs.widgetIds = alive
    }

    private fun removeWidget(id: Int) {
        try { widgetHost.deleteAppWidgetId(id) } catch (_: Exception) {}
        prefs.widgetIds = prefs.widgetIds - id
        prefs.removeWidgetHeight(id)
        renderWidgets()
    }

    private fun startWidgetPick() {
        @Suppress("DEPRECATION")
        startActivityForResult(Intent(this, WidgetPickerActivity::class.java), REQ_PICK)
    }

    private fun bindPicked(provider: ComponentName, profile: UserHandle?) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        val ok = try {
            if (profile != null) widgetManager.bindAppWidgetIdIfAllowed(id, profile, provider, null)
            else widgetManager.bindAppWidgetIdIfAllowed(id, provider)
        } catch (e: Exception) {
            false
        }
        if (ok) {
            configureOrAdd(id)
        } else {
            val i = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                if (profile != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
            }
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(i, REQ_BIND)
            } catch (e: Exception) {
                widgetHost.deleteAppWidgetId(id)
                Toast.makeText(this, "Couldn't add this widget", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun configureOrAdd(id: Int) {
        val info = widgetManager.getAppWidgetInfo(id)
        if (info == null) {
            widgetHost.deleteAppWidgetId(id); return
        }
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        if (info.configure != null && !optional) {
            try {
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
                return
            } catch (e: Exception) {
                // fall through and add without configuring
            }
        }
        addWidget(id)
    }

    private fun addWidget(id: Int) {
        pendingWidgetId = -1
        prefs.widgetIds = prefs.widgetIds + id
        renderWidgets()
    }

    @Deprecated("Uses platform Activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQ_PICK -> if (resultCode == RESULT_OK && data != null) {
                val cn = ComponentName.unflattenFromString(data.getStringExtra("provider") ?: return) ?: return
                @Suppress("DEPRECATION")
                val profile = data.getParcelableExtra<UserHandle>("profile")
                bindPicked(cn, profile)
            }
            REQ_BIND -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                if (resultCode == RESULT_OK && id != -1) configureOrAdd(id)
                else if (id != -1) { widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1 }
            }
            REQ_CONFIG -> {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
                if (id == -1) return
                if (resultCode == RESULT_OK) addWidget(id)
                else { widgetHost.deleteAppWidgetId(id); pendingWidgetId = -1 }
            }
        }
    }
}
