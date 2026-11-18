package com.xaviourzone.focuslauncher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.concurrent.thread

/** Searchable list of installed apps. Subclasses decide what a tap does. */
abstract class AppListBase : Activity() {

    protected var all: List<AppEntry> = emptyList()
    protected var shown: List<AppEntry> = emptyList()
    protected lateinit var adapter: BaseAdapter
    protected lateinit var header: LinearLayout

    abstract val screenTitle: String
    open val showCheckboxes = false
    open fun isChecked(pkg: String) = false
    abstract fun onTap(app: AppEntry)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.vertical(this).apply { setPadding(dp(16), dp(40), dp(16), 0) }
        root.addView(Ui.text(this, screenTitle, 24f, Ui.TEXT, true))
        header = Ui.vertical(this)
        root.addView(header, Ui.lp())

        val search = EditText(this).apply {
            hint = "Search apps"
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.SUB)
            isSingleLine = true
            background = Ui.rounded(Ui.CARD, dp(12).toFloat())
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }
        root.addView(search, Ui.lp().apply { topMargin = dp(12); bottomMargin = dp(8) })

        val list = ListView(this).apply { divider = null; isFastScrollEnabled = true }
        adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val row = (convert as? LinearLayout) ?: makeRow()
                val app = shown[p]
                (row.getChildAt(0) as ImageView).setImageDrawable(Apps.icon(this@AppListBase, app.pkg))
                (row.getChildAt(1) as TextView).text = app.label
                val cb = row.getChildAt(2) as CheckBox
                cb.visibility = if (showCheckboxes) View.VISIBLE else View.GONE
                cb.isChecked = isChecked(app.pkg)
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            onTap(shown[pos])
            adapter.notifyDataSetChanged()
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.trim()?.lowercase() ?: ""
                shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
                adapter.notifyDataSetChanged()
            }
        })

        thread {
            val apps = Apps.launchable(this)
            Handler(Looper.getMainLooper()).post {
                all = sortApps(apps); shown = all; adapter.notifyDataSetChanged()
            }
        }
    }

    open fun sortApps(apps: List<AppEntry>) = apps

    private fun makeRow(): LinearLayout = Ui.horizontal(this).apply {
        setPadding(dp(4), dp(8), dp(4), dp(8))
        addView(ImageView(context), LinearLayout.LayoutParams(dp(40), dp(40)))
        addView(Ui.text(context, "", 16f).apply { setPadding(dp(14), 0, 0, 0); gravity = Gravity.CENTER_VERTICAL },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(CheckBox(context).apply { isClickable = false; isFocusable = false })
    }
}

/** Choose which apps are pinned (home dock + side tab) or allowed to notify. */
class AppPickerActivity : AppListBase() {
    private lateinit var prefs: Prefs
    private val allowMode get() = intent.getStringExtra("mode") == "allowed"

    override val screenTitle: String get() = if (allowMode) "Allowed notifications" else "Pinned apps"
    override val showCheckboxes = true

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        super.onCreate(savedInstanceState)
        header.addView(Ui.text(this,
            if (allowMode) "Pinned apps can always notify. Add other apps here (e.g. bank OTP, Messages) that may notify you in Focus mode."
            else "Tap to pin or unpin. Pinned apps stay on your home screen and in the side tab — everything else stays out of the way.",
            13f, Ui.SUB).apply { setPadding(0, dp(6), 0, 0) })
    }

    private fun current() = if (allowMode) prefs.allowedNotif else prefs.pinned

    override fun isChecked(pkg: String) = pkg in current()

    // Selected apps first so they're easy to see
    override fun sortApps(apps: List<AppEntry>): List<AppEntry> {
        val sel = current()
        return apps.sortedBy { if (it.pkg in sel) sel.indexOf(it.pkg) else 10_000 }
    }

    override fun onTap(app: AppEntry) {
        val list = current().toMutableList()
        if (app.pkg in list) list.remove(app.pkg) else list.add(app.pkg)
        if (allowMode) prefs.allowedNotif = list else prefs.pinned = list
    }
}

/** Full app list, kept one tap away from home on purpose. */
class AllAppsActivity : AppListBase() {
    override val screenTitle = "All apps"
    override fun onTap(app: AppEntry) {
        Apps.launch(this, app.pkg)
        finish()
    }
}
