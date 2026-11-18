package com.xaviourzone.focuslauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlin.math.abs

/**
 * Draws a thin translucent tab on the screen edge over every app (like the
 * Play Games side tab). Swipe or tap it to slide out a panel with the pinned
 * apps. Long-press and drag the tab to move it up or down.
 */
class EdgeOverlayService : Service() {

    companion object {
        private const val CHANNEL = "edge_tab"
        private const val NOTIF_ID = 7
        const val ACTION_STOP = "com.xaviourzone.focuslauncher.STOP_TAB"

        fun startIfAllowed(ctx: Context) {
            val prefs = Prefs(ctx)
            if (!prefs.overlayEnabled || !Settings.canDrawOverlays(ctx)) return
            try {
                val i = Intent(ctx, EdgeOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ctx.startForegroundService(i)
                else ctx.startService(i)
            } catch (_: Exception) {
                // Background-start restrictions; it will start next time home opens.
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, EdgeOverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private var handle: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "right", "handle_h", "handle_a" -> { removeHandle(); addHandle() }
            "handle_y" -> {} // updated live while dragging
            "pinned" -> if (panel != null) closePanel()
            "overlay" -> if (!prefs.overlayEnabled) stopSelf()
            else -> {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        goForeground()
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return }
        addHandle()
        prefs.registerListener(prefListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            prefs.overlayEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        goForeground()
        if (handle == null && Settings.canDrawOverlays(this)) addHandle()
        return START_STICKY
    }

    override fun onDestroy() {
        try { prefs.unregisterListener(prefListener) } catch (_: Exception) {}
        closePanel()
        removeHandle()
        super.onDestroy()
    }

    // ------------------------------------------------------------ foreground

    private fun goForeground() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Side tab", NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                    description = "Keeps the side tab with your pinned apps on screen"
                }
            )
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(this, 0, Intent(this, SettingsActivity::class.java), flags)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, EdgeOverlayService::class.java).setAction(ACTION_STOP), flags
        )
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL)
        else @Suppress("DEPRECATION") Notification.Builder(this)
        val n = b.setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Side tab is on")
            .setContentText("Swipe the edge tab to open your pinned apps")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Turn off", stop).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------ the tab

    private fun overlayType() =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun screenH() = resources.displayMetrics.heightPixels

    @SuppressLint("ClickableViewAccessibility")
    private fun addHandle() {
        if (handle != null) return
        val right = prefs.rightSide
        val touchW = dp(22)
        val barW = dp(6)

        val container = FrameLayout(this)
        val bar = View(this).apply {
            background = Ui.rounded(
                ((prefs.handleAlpha * 255 / 100) shl 24) or 0xFFFFFF,
                dp(4).toFloat(), dp(1), 0x33000000
            )
        }
        container.addView(bar, FrameLayout.LayoutParams(barW, -1).apply {
            gravity = if (right) Gravity.END else Gravity.START
            setMargins(dp(2), 0, dp(2), 0)
        })

        val lp = WindowManager.LayoutParams(
            touchW, dp(prefs.handleHeightDp), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or if (right) Gravity.END else Gravity.START
            x = 0
            y = (screenH() * prefs.handleY).toInt()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        val longPress = ViewConfiguration.getLongPressTimeout().toLong()
        var downX = 0f
        var downY = 0f
        var startY = 0
        var moving = false
        var dragging = false
        val longRunnable = Runnable {
            moving = true
            buzz()
            bar.alpha = 1f
            bar.scaleX = 1.6f
        }

        container.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startY = lp.y
                    moving = false; dragging = false
                    v.postDelayed(longRunnable, longPress)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (moving) {
                        lp.y = (startY + dy).toInt().coerceIn(0, screenH() - lp.height)
                        try { wm.updateViewLayout(container, lp) } catch (_: Exception) {}
                    } else if (abs(dx) > slop || abs(dy) > slop) {
                        v.removeCallbacks(longRunnable)
                        val inward = if (right) -dx else dx
                        if (!dragging && inward > dp(18)) {
                            dragging = true
                            openPanel()
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.removeCallbacks(longRunnable)
                    if (moving) {
                        prefs.handleY = lp.y.toFloat() / screenH()
                        bar.scaleX = 1f
                    } else if (!dragging && e.actionMasked == MotionEvent.ACTION_UP &&
                        abs(e.rawX - downX) < slop && abs(e.rawY - downY) < slop
                    ) {
                        openPanel()   // simple tap also opens
                    }
                    moving = false; dragging = false
                }
            }
            true
        }

        try {
            wm.addView(container, lp)
            handle = container
            handleParams = lp
        } catch (_: Exception) {
            handle = null
        }
    }

    private fun removeHandle() {
        handle?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        handle = null
        handleParams = null
    }

    // ------------------------------------------------------------ the panel

    /** Root that closes the panel on Back. */
    private inner class PanelRoot(ctx: Context) : FrameLayout(ctx) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) closePanel()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun openPanel() {
        if (panel != null) return
        buzz()
        val right = prefs.rightSide
        val root = PanelRoot(this).apply {
            setBackgroundColor(0x55000000)
            isFocusableInTouchMode = true
            setOnClickListener { closePanel() }
        }

        val card = Ui.vertical(this).apply {
            background = Ui.rounded(0xEE1C1E23.toInt(), dp(24).toFloat())
            setPadding(dp(10), dp(14), dp(10), dp(14))
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true // don't close when tapping the card itself
            elevation = dp(8).toFloat()
        }

        val list = Ui.vertical(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        val pinned = prefs.pinned.filter { Apps.isInstalled(this, it) }
        if (pinned.isEmpty()) {
            list.addView(Ui.text(this, "No pinned apps.\nOpen Focus Launcher\nsettings to pin some.", 13f, Ui.SUB).apply {
                gravity = Gravity.CENTER
                setPadding(dp(6), dp(6), dp(6), dp(6))
            })
        }
        for (pkg in pinned) {
            val tile = Ui.appTile(this, pkg, 46, true)
            tile.setPadding(dp(4), dp(8), dp(4), dp(8))
            attachLaunchGesture(tile, pkg, right)
            list.addView(tile)
        }
        val sc = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(list) }
        card.addView(sc, LinearLayout.LayoutParams(-2, 0, 1f))

        // Divider + home / settings shortcuts
        card.addView(View(this).apply { setBackgroundColor(0x22FFFFFF) },
            LinearLayout.LayoutParams(dp(48), dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
        card.addView(Ui.text(this, "⌂", 22f, Ui.TEXT).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                closePanel()
                startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })
        card.addView(Ui.text(this, "⚙", 20f, Ui.SUB).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                closePanel()
                startActivity(Intent(this@EdgeOverlayService, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        })

        val maxH = (screenH() * 0.7f).toInt()
        val cardLp = FrameLayout.LayoutParams(dp(92), -2).apply {
            gravity = Gravity.CENTER_VERTICAL or if (right) Gravity.END else Gravity.START
            setMargins(dp(10), 0, dp(10), 0)
        }
        root.addView(card, cardLp)
        card.post {
            if (card.height > maxH) {
                card.layoutParams = cardLp.apply { height = maxH }
            }
        }

        val lp = WindowManager.LayoutParams(
            -1, -1, overlayType(),
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        try {
            wm.addView(root, lp)
            panel = root
            root.requestFocus()
            val off = dp(130).toFloat() * if (right) 1 else -1
            card.translationX = off
            root.alpha = 0f
            root.animate().alpha(1f).setDuration(140).start()
            card.animate().translationX(0f).setDuration(200).start()
        } catch (_: Exception) {
            panel = null
        }
    }

    /** Tap to open, or drag the icon out of the panel toward the middle. */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachLaunchGesture(tile: View, pkg: String, right: Boolean) {
        var downX = 0f
        var downY = 0f
        tile.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start()
                    v.parent.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val inward = if (right) -dx else dx
                    if (inward > 0 && abs(dx) > abs(e.rawY - downY)) {
                        v.parent.requestDisallowInterceptTouchEvent(true)
                        v.translationX = dx
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.rawX - downX
                    val inward = if (right) -dx else dx
                    val moved = abs(dx) > dp(10) || abs(e.rawY - downY) > dp(10)
                    v.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(120).start()
                    if (inward > dp(60) || !moved) {
                        closePanel()
                        Apps.launch(this, pkg)
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).translationX(0f).setDuration(120).start()
                }
            }
            true
        }
    }

    private fun closePanel() {
        val p = panel ?: return
        panel = null
        try { wm.removeView(p) } catch (_: Exception) {}
    }

    // ------------------------------------------------------------ utils

    private fun buzz() {
        try {
            val v = getSystemService(VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) v.vibrate(VibrationEffect.createOneShot(15, 60))
            else @Suppress("DEPRECATION") v.vibrate(15)
        } catch (_: Exception) {}
    }
}
