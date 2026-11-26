package com.saku.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var search: EditText
    private lateinit var grid: GridView
    private lateinit var dock: LinearLayout
    private lateinit var adapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.dateText)
        search = findViewById(R.id.search)
        grid = findViewById(R.id.grid)
        dock = findViewById(R.id.dock)

        allApps = loadApps()
        adapter = AppAdapter(this, allApps) { launch(it) }
        grid.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                adapter.submit(
                    if (query.isEmpty()) allApps
                    else allApps.filter {
                        it.label.lowercase(Locale.getDefault()).contains(query) ||
                            it.packageName.contains(query, ignoreCase = true)
                    }
                )
            }
        })

        findViewById<Button>(R.id.settingsBtn).setOnClickListener { showHomeHint() }
        buildDock()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    @Deprecated("Home launcher menangani tombol back sendiri")
    override fun onBackPressed() {
        if (search.text.isNotEmpty()) {
            search.text.clear()
            return
        }
    }

    private fun updateTime() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateText.text = SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID")).format(now)
    }

    private fun loadApps(): List<AppInfo> {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }

        return resolved
            .map { info -> AppInfo(info.loadLabel(pm).toString(), info.activityInfo.packageName, info.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    private fun launch(app: AppInfo) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(this, "${app.label} tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun buildDock() {
        val favourites = listOf(
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.camera2",
            "com.android.chrome"
        )

        dock.removeAllViews()
        favourites.forEach { packageName ->
            val app = allApps.firstOrNull { it.packageName == packageName } ?: return@forEach
            val icon = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
                setImageDrawable(app.icon)
                contentDescription = app.label
                background = roundedBackground()
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener { launch(app) }
            }
            dock.addView(icon)
        }

        if (dock.childCount == 0) {
            dock.addView(TextView(this).apply {
                text = "Belum ada pintasan yang cocok."
                setTextColor(Color.parseColor("#A9B9B4"))
                textSize = 12f
            })
        }
    }

    private fun roundedBackground(): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        setColor(Color.parseColor("#173437"))
        setStroke(dp(1), Color.parseColor("#22F4EFE3"))
    }

    private fun showHomeHint() {
        AlertDialog.Builder(this)
            .setTitle("Jadikan Saku sebagai beranda")
            .setMessage("Buka Settings → Home app (atau App default), lalu pilih Saku. Tekan Home untuk kembali ke launcher ini.")
            .setPositiveButton("Mengerti", null)
            .show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class AppAdapter(
        context: Context,
        source: List<AppInfo>,
        private val onClick: (AppInfo) -> Unit
    ) : BaseAdapter() {

        private val inflater = LayoutInflater.from(context)
        private val items = ArrayList(source)

        fun submit(next: List<AppInfo>) {
            items.clear()
            items.addAll(next)
            notifyDataSetChanged()
        }

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): AppInfo = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: inflater.inflate(R.layout.item_app, parent, false).also {
                it.tag = Holder(it.findViewById(R.id.appIcon), it.findViewById(R.id.appLabel))
            }
            val holder = view.tag as Holder
            val app = items[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label
            view.setOnClickListener { onClick(app) }
            return view
        }

        private data class Holder(val icon: ImageView, val label: TextView)
    }
}
