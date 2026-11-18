package com.xaviourzone.focuslauncher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import kotlin.concurrent.thread

/** Lists every widget your installed apps offer (e.g. ChatGPT's widgets). */
class WidgetPickerActivity : Activity() {

    private data class Item(val info: AppWidgetProviderInfo, val appLabel: String, val label: String)

    private var items: List<Item> = emptyList()
    private val previews = HashMap<Int, Drawable?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.vertical(this).apply { setPadding(dp(16), dp(40), dp(16), 0) }
        root.addView(Ui.text(this, "Add a widget", 24f, Ui.TEXT, true))
        root.addView(Ui.text(this, "Tap a widget to put it on your home screen.", 13f, Ui.SUB).apply {
            setPadding(0, dp(6), 0, dp(10))
        })

        val list = ListView(this).apply { divider = android.graphics.drawable.ColorDrawable(0); dividerHeight = dp(8) }
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val row = (convert as? LinearLayout) ?: makeRow()
                val item = items[p]
                val icon = row.getChildAt(0) as ImageView
                val texts = row.getChildAt(1) as LinearLayout
                val preview = texts.getChildAt(2) as ImageView
                icon.setImageDrawable(Apps.icon(this@WidgetPickerActivity, item.info.provider.packageName))
                (texts.getChildAt(0) as TextView).text = item.appLabel
                val d = resources.displayMetrics.density
                (texts.getChildAt(1) as TextView).text =
                    "${item.label}  ·  ${(item.info.minWidth / d).toInt()}×${(item.info.minHeight / d).toInt()} dp"
                val pv = previews.getOrPut(p) {
                    try { item.info.loadPreviewImage(this@WidgetPickerActivity, 0) } catch (e: Exception) { null }
                }
                preview.setImageDrawable(pv)
                preview.visibility = if (pv != null) View.VISIBLE else View.GONE
                return row
            }
        }
        list.adapter = adapter
        list.setOnItemClickListener { _, _, pos, _ ->
            val info = items[pos].info
            setResult(RESULT_OK, Intent()
                .putExtra("provider", info.provider.flattenToString())
                .putExtra("profile", info.profile))
            finish()
        }
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        thread {
            val pm = packageManager
            val awm = AppWidgetManager.getInstance(this)
            val found = awm.installedProviders.map {
                Item(it, Apps.label(this, it.provider.packageName), it.loadLabel(pm) ?: it.provider.shortClassName)
            }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
            Handler(Looper.getMainLooper()).post {
                items = found
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun makeRow(): LinearLayout = Ui.horizontal(this).apply {
        gravity = Gravity.TOP
        setPadding(dp(10), dp(10), dp(10), dp(10))
        background = Ui.rounded(Ui.CARD, dp(14).toFloat())
        layoutParams = android.widget.AbsListView.LayoutParams(-1, -2)
        addView(ImageView(context), LinearLayout.LayoutParams(dp(36), dp(36)))
        val texts = Ui.vertical(context).apply { setPadding(dp(12), 0, 0, 0) }
        texts.addView(Ui.text(context, "", 15f, Ui.TEXT, true))
        texts.addView(Ui.text(context, "", 13f, Ui.SUB))
        texts.addView(ImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_START
        }, LinearLayout.LayoutParams(-1, dp(90)).apply { topMargin = dp(8) })
        addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
    }
}
