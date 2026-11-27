package com.saku.launcher

import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class MainActivity : Activity() {

    private data class AppInfo(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var root: FrameLayout
    private lateinit var ambient: AmbientBackgroundView
    private lateinit var homeLayer: LinearLayout
    private lateinit var glanceCard: LinearLayout
    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var batteryChip: TextView
    private lateinit var alarmChip: TextView
    private lateinit var settingsBtn: TextView
    private lateinit var searchPill: TextView
    private lateinit var homeGrid: GridView
    private lateinit var dragHandle: TextView
    private lateinit var dock: LinearLayout
    private lateinit var drawerLayer: FrameLayout
    private lateinit var drawerInner: LinearLayout
    private lateinit var drawerSearch: EditText
    private lateinit var closeDrawerBtn: Button
    private lateinit var drawerGrid: GridView

    private lateinit var homeAdapter: AppAdapter
    private lateinit var drawerAdapter: AppAdapter
    private lateinit var gestureDetector: GestureDetector

    private var allApps: List<AppInfo> = emptyList()
    private var accent = Color.parseColor("#FFC857")
    private var secondary = Color.parseColor("#66D9E8")
    private var drawerOpen = false
    private var introDone = false
    private var powerReceiverRegistered = false
    private var notifReceiverRegistered = false
    private val handler = Handler(Looper.getMainLooper())

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            ambient.onHostResume(isPowerSaveMode())
        }
    }

    private val notifReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshBadges()
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateGlance()
            handler.postDelayed(this, 15_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        setContentView(R.layout.activity_main)

        bindViews()
        setupEdgeToEdge()
        applyWindowInsets()
        resolvePalette()
        styleSurfaces()
        applyLayoutPrefs()
        loadAppLists()
        setupInteractions()
        setup3D()
        buildDock()
    }

    private fun bindViews() {
        root = findViewById(R.id.root)
        ambient = findViewById(R.id.ambient)
        homeLayer = findViewById(R.id.homeLayer)
        glanceCard = findViewById(R.id.glanceCard)
        clock = findViewById(R.id.clock)
        dateText = findViewById(R.id.dateText)
        batteryChip = findViewById(R.id.batteryChip)
        alarmChip = findViewById(R.id.alarmChip)
        settingsBtn = findViewById(R.id.settingsBtn)
        searchPill = findViewById(R.id.searchPill)
        homeGrid = findViewById(R.id.homeGrid)
        dragHandle = findViewById(R.id.dragHandle)
        dock = findViewById(R.id.dock)
        drawerLayer = findViewById(R.id.drawerLayer)
        drawerInner = findViewById(R.id.drawerInner)
        drawerSearch = findViewById(R.id.drawerSearch)
        closeDrawerBtn = findViewById(R.id.closeDrawer)
        drawerGrid = findViewById(R.id.drawerGrid)
    }

    private fun setupEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun applyWindowInsets() {
        root.setOnApplyWindowInsetsListener { _, insets ->
            val left = insets.systemWindowInsetLeft
            val top = insets.systemWindowInsetTop + dp(8)
            val right = insets.systemWindowInsetRight
            val bottom = insets.systemWindowInsetBottom + dp(8)
            homeLayer.setPadding(left, top, right, bottom)
            drawerInner.setPadding(left, top, right, bottom)
            insets.consumeSystemWindowInsets()
        }
    }

    private fun resolvePalette() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            accent = getColor(android.R.color.system_accent1_500)
            secondary = getColor(android.R.color.system_accent3_500)
        }
        ambient.setPalette(accent, secondary)
    }

    private fun styleSurfaces() {
        glanceCard.background = rounded(
            fill = Color.argb(96, 8, 28, 31),
            stroke = withAlpha(accent, 110),
            radius = 30
        )
        searchPill.background = rounded(
            fill = Color.argb(150, 23, 52, 55),
            stroke = withAlpha(accent, 130),
            radius = 22
        )
        dragHandle.background = rounded(
            fill = Color.argb(120, 12, 36, 39),
            stroke = withAlpha(secondary, 100),
            radius = 999
        )
        dock.background = rounded(
            fill = Color.argb(120, 6, 18, 20),
            stroke = withAlpha(accent, 70),
            radius = 26
        )
    }

    private fun applyLayoutPrefs() {
        homeGrid.numColumns = Prefs.columns
        drawerGrid.numColumns = if (Prefs.drawerList) 1 else Prefs.columns
    }

    private fun loadAppLists() {
        allApps = loadApps()
        homeAdapter = AppAdapter(this, allApps.take(8), R.layout.item_tile) { launch(it) }
        drawerAdapter = AppAdapter(this, allApps, R.layout.item_app) { launch(it) }
        homeGrid.adapter = homeAdapter
        drawerGrid.adapter = drawerAdapter
    }

    private fun reloadApps() {
        allApps = loadApps()
        if (::homeAdapter.isInitialized) homeAdapter.submit(allApps.take(8))
        if (::drawerAdapter.isInitialized) {
            drawerAdapter.submit(filterApps(drawerSearch.text?.toString().orEmpty().trim().lowercase(Locale.getDefault())))
        }
    }

    private fun setupInteractions() {
        settingsBtn.setOnClickListener { showSettings() }
        searchPill.setOnClickListener { openDrawer() }
        closeDrawerBtn.setOnClickListener { closeDrawer() }

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!Prefs.lockGesture) return false
                if (!LauncherExtras.lockNow(this@MainActivity)) {
                    toast("Aktifkan device admin dulu")
                    LauncherExtras.requestDeviceAdmin(this@MainActivity)
                }
                return true
            }
        })
        homeLayer.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false
        }

        drawerSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
                drawerAdapter.submit(filterApps(query))
            }
        })

        var handleStartY = 0f
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> handleStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (event.y - handleStartY < -dp(28)) {
                        dragHandle.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        openDrawer()
                    }
                }
            }
            false
        }

        var drawerStartY = 0f
        drawerInner.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> drawerStartY = event.y
                MotionEvent.ACTION_UP -> {
                    if (event.y - drawerStartY > dp(90) && !drawerGrid.canScrollVertically(-1)) {
                        closeDrawer()
                    }
                }
            }
            false
        }
    }

    private fun setup3D() {
        attach3D(homeGrid)
        attach3D(drawerGrid)
    }

    private fun attach3D(grid: GridView) {
        grid.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
                if (scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    for (index in 0 until grid.childCount) {
                        reset3D(grid.getChildAt(index) ?: continue)
                    }
                }
            }

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                if (!canUse3D()) return
                val middle = grid.height / 2f
                if (middle <= 0f) return
                for (index in 0 until grid.childCount) {
                    val child = grid.getChildAt(index) ?: continue
                    val childMiddle = (child.top + child.bottom) / 2f
                    val distance = ((childMiddle - middle) / middle).coerceIn(-1f, 1f)
                    child.cameraDistance = dp(1200).toFloat()
                    child.rotationY = distance * -24f
                    child.translationZ = dp(26) * (1f - abs(distance))
                    child.alpha = 0.72f + 0.28f * (1f - abs(distance))
                }
            }
        })
    }

    private fun canUse3D(): Boolean =
        Prefs.effect3d && animationsEnabled() && !isPowerSaveMode()

    private fun reset3D(view: View) {
        view.animate()
            .rotationY(0f)
            .translationZ(0f)
            .alpha(1f)
            .setDuration(180)
            .start()
    }

    private fun showSettings() {
        val items = arrayOf(
            if (Prefs.effect3d) "Efek 3D: AKTIF" else "Efek 3D: mati",
            if (Prefs.notifTiles) "Notifikasi di tile: AKTIF" else "Notifikasi di tile: mati",
            if (Prefs.usageSort) "Urut berdasarkan pemakaian: AKTIF" else "Urut berdasarkan pemakaian: mati",
            if (Prefs.lockGesture) "Double-tap kunci: AKTIF" else "Double-tap kunci: mati",
            if (Prefs.drawerList) "Drawer: mode list" else "Drawer: mode grid",
            "Sembunyikan aplikasi",
            "Jumlah kolom: ${Prefs.columns}",
            "Backup / restore",
            "Tentang Saku"
        )
        AlertDialog.Builder(this)
            .setTitle("Saku Fusion")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        Prefs.effect3d = !Prefs.effect3d
                        toast(if (Prefs.effect3d) "Efek 3D aktif" else "Efek 3D dimatikan")
                    }
                    1 -> {
                        Prefs.notifTiles = !Prefs.notifTiles
                        if (Prefs.notifTiles && !LauncherExtras.isNotificationAccess(this)) {
                            toast("Aktifkan akses notifikasi untuk Saku")
                            LauncherExtras.openNotificationSettings(this)
                        }
                        refreshBadges()
                    }
                    2 -> {
                        Prefs.usageSort = !Prefs.usageSort
                        if (Prefs.usageSort && !LauncherExtras.isUsageAccess(this)) {
                            toast("Aktifkan akses penggunaan untuk Saku")
                            LauncherExtras.openUsageSettings(this)
                        }
                        reloadApps()
                    }
                    3 -> {
                        Prefs.lockGesture = !Prefs.lockGesture
                        if (Prefs.lockGesture && !LauncherExtras.isDeviceAdmin(this)) {
                            LauncherExtras.requestDeviceAdmin(this)
                        }
                        toast(if (Prefs.lockGesture) "Double-tap kunci aktif" else "Double-tap kunci dimatikan")
                    }
                    4 -> {
                        Prefs.drawerList = !Prefs.drawerList
                        applyLayoutPrefs()
                        toast(if (Prefs.drawerList) "Drawer mode list" else "Drawer mode grid")
                    }
                    5 -> showHiddenPicker()
                    6 -> showColumnPicker()
                    7 -> showBackupMenu()
                    8 -> toast("Saku Fusion v3.0 — native Kotlin, ringan & hemat daya")
                }
            }
            .setPositiveButton("Tutup", null)
            .show()
    }

    private fun showHiddenPicker() {
        val labels = allApps.map { it.label }.toTypedArray()
        val checked = BooleanArray(allApps.size) { index -> Prefs.hidden.contains(allApps[index].packageName) }
        AlertDialog.Builder(this)
            .setTitle("Sembunyikan aplikasi")
            .setMultiChoiceItems(labels, checked) { _, _, _ -> }
            .setPositiveButton("Simpan") { _, _ ->
                val hidden = HashSet<String>()
                for (index in labels.indices) {
                    if (checked[index]) hidden.add(allApps[index].packageName)
                }
                Prefs.hidden = hidden
                reloadApps()
                toast("${hidden.size} aplikasi disembunyikan")
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showColumnPicker() {
        AlertDialog.Builder(this)
            .setTitle("Jumlah kolom")
            .setSingleChoiceItems(arrayOf("3 kolom", "4 kolom", "5 kolom"), Prefs.columns - 3) { dialog, which ->
                Prefs.columns = which + 3
                applyLayoutPrefs()
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showBackupMenu() {
        AlertDialog.Builder(this)
            .setTitle("Backup / restore")
            .setItems(arrayOf("Export ke JSON", "Import dari JSON")) { _, which ->
                when (which) {
                    0 -> {
                        val file = LauncherExtras.exportBackup(this)
                        toast("Backup: ${file.name}")
                    }
                    1 -> {
                        val ok = LauncherExtras.importBackup(this)
                        if (ok) {
                            applyLayoutPrefs()
                            reloadApps()
                            toast("Backup dipulihkan")
                        } else {
                            toast("File backup tidak ditemukan")
                        }
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun refreshBadges() {
        if (!::homeAdapter.isInitialized || !::drawerAdapter.isInitialized) return
        homeAdapter.notifyDataSetChanged()
        drawerAdapter.notifyDataSetChanged()
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(ticker)
        ticker.run()
        ambient.onHostResume(isPowerSaveMode())
        if (!powerReceiverRegistered) {
            registerReceiver(powerSaveReceiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
            powerReceiverRegistered = true
        }
        if (!notifReceiverRegistered) {
            registerReceiver(notifReceiver, IntentFilter(SakuNotificationListener.ACTION_NOTIF))
            notifReceiverRegistered = true
        }
        if (!introDone) {
            introDone = true
            playIntro()
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        ambient.onHostPause()
        if (powerReceiverRegistered) {
            unregisterReceiver(powerSaveReceiver)
            powerReceiverRegistered = false
        }
        if (notifReceiverRegistered) {
            unregisterReceiver(notifReceiver)
            notifReceiverRegistered = false
        }
    }

    @Deprecated("Home launcher menangani tombol back sendiri")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer()
    }

    private fun playIntro() {
        if (!animationsEnabled()) return
        listOf(glanceCard, searchPill, homeGrid, dragHandle, dock).forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = dp(26).toFloat()
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(420)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .start()
        }
    }

    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        ambient.onHostPause()
        drawerAdapter.submit(allApps)
        drawerSearch.text.clear()

        if (!animationsEnabled()) {
            homeLayer.visibility = View.GONE
            drawerLayer.visibility = View.VISIBLE
            drawerSearch.requestFocus()
            return
        }

        homeLayer.animate()
            .alpha(0f)
            .translationY(-dp(44).toFloat())
            .scaleX(0.96f)
            .scaleY(0.96f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator(2f))
            .withEndAction {
                homeLayer.visibility = View.GONE
                drawerLayer.visibility = View.VISIBLE
                drawerLayer.alpha = 0f
                drawerLayer.translationY = dp(72).toFloat()
                drawerLayer.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(340)
                    .setInterpolator(OvershootInterpolator(0.9f))
                    .withEndAction {
                        drawerSearch.requestFocus()
                        drawerGrid.post { stagger(drawerGrid) }
                    }
                    .start()
            }
            .start()
    }

    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        ambient.onHostResume(isPowerSaveMode())
        hideKeyboard()

        if (!animationsEnabled()) {
            drawerLayer.visibility = View.GONE
            restoreHome()
            return
        }

        drawerLayer.animate()
            .alpha(0f)
            .translationY(dp(64).toFloat())
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                drawerLayer.visibility = View.GONE
                restoreHome()
            }
            .start()
    }

    private fun restoreHome() {
        homeLayer.visibility = View.VISIBLE
        if (!animationsEnabled()) {
            homeLayer.alpha = 1f
            homeLayer.translationY = 0f
            homeLayer.scaleX = 1f
            homeLayer.scaleY = 1f
            return
        }
        homeLayer.alpha = 0f
        homeLayer.translationY = dp(28).toFloat()
        homeLayer.scaleX = 0.97f
        homeLayer.scaleY = 0.97f
        homeLayer.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(0.8f))
            .start()
    }

    private fun stagger(grid: GridView) {
        if (!animationsEnabled()) return
        for (index in 0 until grid.childCount) {
            val view = grid.getChildAt(index) ?: continue
            view.alpha = 0f
            view.translationY = dp(22).toFloat()
            view.scaleX = 0.92f
            view.scaleY = 0.92f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay((index * 14).toLong())
                .setDuration(280)
                .setInterpolator(OvershootInterpolator(0.7f))
                .start()
        }
    }

    private fun updateGlance() {
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        dateText.text = SimpleDateFormat("EEEE, d MMMM", Locale("id", "ID")).format(now)

        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        batteryChip.text = if (level >= 0 && scale > 0) {
            "▰ ${level * 100 / scale}%"
        } else {
            "▰ Baterai"
        }

        val alarm = (getSystemService(ALARM_SERVICE) as AlarmManager).nextAlarmClock
        alarmChip.text = if (alarm != null) {
            "◷ Alarm " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alarm.triggerTime))
        } else {
            "◷ Tanpa alarm"
        }
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
        val hidden = Prefs.hidden
        val base = resolved
            .map { info -> AppInfo(info.loadLabel(pm).toString(), info.activityInfo.packageName, info.loadIcon(pm)) }
            .distinctBy { it.packageName }
            .filterNot { hidden.contains(it.packageName) }

        return if (Prefs.usageSort) {
            val rank = LauncherExtras.usageRank(this)
            base.sortedByDescending { rank[it.packageName] ?: 0L }
        } else {
            base.sortedBy { it.label.lowercase(Locale.getDefault()) }
        }
    }

    private fun filterApps(query: String): List<AppInfo> {
        if (query.isEmpty()) return allApps
        return allApps.filter {
            it.label.lowercase(Locale.getDefault()).contains(query) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    private fun launch(app: AppInfo) {
        val intent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            toast("${app.label} tidak bisa dibuka")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun buildDock() {
        dock.removeAllViews()
        val favourites = listOf(
            "com.google.android.dialer",
            "com.google.android.apps.messaging",
            "com.android.camera2",
            "com.android.chrome"
        )
        favourites.forEach { packageName ->
            val app = allApps.firstOrNull { it.packageName == packageName } ?: return@forEach
            dock.addView(ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f)
                setImageDrawable(app.icon)
                contentDescription = app.label
                background = rounded(
                    fill = Color.argb(140, 23, 52, 55),
                    stroke = withAlpha(accent, 90),
                    radius = 18
                )
                setPadding(dp(9), dp(9), dp(9), dp(9))
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    launch(app)
                }
                attachPress(this)
            })
        }
    }

    fun attachPress(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> if (animationsEnabled()) {
                    target.animate().scaleX(0.88f).scaleY(0.88f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (animationsEnabled()) {
                    target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(260)
                        .setInterpolator(OvershootInterpolator(1.1f))
                        .start()
                }
            }
            false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(drawerSearch.windowToken, 0)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun animationsEnabled(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    private fun isPowerSaveMode(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isPowerSaveMode

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radius).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class AppAdapter(
        context: Context,
        source: List<AppInfo>,
        private val layoutRes: Int,
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
            val view = convertView ?: inflater.inflate(layoutRes, parent, false).also {
                it.tag = Holder(
                    it.findViewById(R.id.appIcon),
                    it.findViewById(R.id.appLabel),
                    it.findViewById(R.id.notifBadge)
                )
            }
            val holder = view.tag as Holder
            val app = items[position]
            holder.icon.setImageDrawable(app.icon)
            holder.label.text = app.label

            val count = Prefs.notifCounts()[app.packageName] ?: 0
            if (Prefs.notifTiles && count > 0) {
                holder.badge?.text = count.toString()
                holder.badge?.visibility = View.VISIBLE
            } else {
                holder.badge?.visibility = View.GONE
            }

            view.setOnClickListener {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick(app)
            }
            (view.context as? MainActivity)?.attachPress(view)
            return view
        }

        private data class Holder(val icon: ImageView, val label: TextView, val badge: TextView?)
    }
}
