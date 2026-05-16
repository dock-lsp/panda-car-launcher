package com.pandora.carlauncher

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val PREF_NAME = "panda_launcher_prefs"
        const val KEY_CUSTOM_APPS = "custom_apps"
        const val MAX_CUSTOM_APPS = 5
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault())

    private lateinit var audioManager: AudioManager
    private var customApps = mutableListOf<CustomApp>()
    private lateinit var gridAdapter: AppGridAdapter

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            // 应用壁纸和主题背景
            applyAppBackground()
            setupFullScreen()

            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

            updateTime()
            handler.post(updateTimeRunnable)

            requestPermissions()
            loadCustomApps()
            setupAppGrid()
            setupBottomNavigation()
            setupCardClicks()
        } catch (e: Exception) {
            Log.e(TAG, "onCreate 初始化失败", e)
            Toast.makeText(this, "初始化异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 应用壁纸或主题背景到壁纸层 ImageView
     */
    private fun applyAppBackground() {
        val ivWallpaper = findViewById<ImageView>(R.id.iv_wallpaper)
        if (ivWallpaper != null) {
            val wallpaperDrawable = WallpaperManager.getWallpaperDrawable(this)
            if (wallpaperDrawable != null) {
                ivWallpaper.setImageDrawable(wallpaperDrawable)
                ivWallpaper.visibility = View.VISIBLE
            } else {
                // 没有自定义壁纸，使用主题背景色
                ivWallpaper.setImageDrawable(null)
                ivWallpaper.setBackgroundColor(ThemeManager.getBackgroundColor(this))
                ivWallpaper.visibility = View.VISIBLE
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupFullScreen()
        applyAppBackground()
        loadCustomApps()
        gridAdapter.notifyDataSetChanged()
        // 刷新底部应用列表
        setupBottomAppsRecyclerView()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupFullScreen()
    }

    /**
     * 全屏沉浸式显示
     */
    private fun setupFullScreen() {
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.let {
                    it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                    it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupFullScreen 失败", e)
        }
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        findViewById<TextView>(R.id.tv_time)?.text = timeFormat.format(now.time)
        findViewById<TextView>(R.id.tv_date)?.text = dateFormat.format(now.time)
    }

    /**
     * 设置应用网格（动态加载已安装应用）
     */
    private fun setupAppGrid() {
        val rvGrid = findViewById<RecyclerView>(R.id.rv_app_grid)
        rvGrid?.layoutManager = GridLayoutManager(this, 6)
        gridAdapter = AppGridAdapter(getGridApps())
        rvGrid?.adapter = gridAdapter
    }

    /**
     * 获取网格显示的应用列表（常用应用）
     */
    private fun getGridApps(): List<GridApp> {
        val apps = mutableListOf<GridApp>()
        
        try {
            // 固定快捷入口
            apps.add(GridApp(appName = "应用管理", iconRes = R.drawable.ic_apps, iconBg = R.drawable.bg_icon_blue) {
                startActivity(Intent(this, AppManagerActivity::class.java))
            })
            apps.add(GridApp(appName = "系统设置", iconRes = R.drawable.ic_settings, iconBg = R.drawable.bg_icon_orange) {
                startActivity(Intent(this, SettingsActivity::class.java))
            })
            apps.add(GridApp(appName = "主题中心", iconRes = R.drawable.ic_music, iconBg = R.drawable.bg_icon_cyan) {
                Toast.makeText(this, "主题中心开发中", Toast.LENGTH_SHORT).show()
            })

            // 动态检测音乐应用
            val musicApps = AppRecognizer.getInstalledMusicApps(this)
            if (musicApps.isNotEmpty()) {
                val musicApp = musicApps[0]
                apps.add(GridApp(appName = musicApp.appName, icon = musicApp.icon, iconBg = R.drawable.bg_icon_orange) {
                    openApp(musicApp.packageName, musicApp.appName)
                })
            }

            // 动态检测导航应用
            val navApps = AppRecognizer.getInstalledNavigationApps(this)
            if (navApps.isNotEmpty()) {
                val navApp = navApps[0]
                apps.add(GridApp(appName = navApp.appName, icon = navApp.icon, iconBg = R.drawable.bg_icon_green) {
                    openApp(navApp.packageName, navApp.appName)
                })
            }

            // 文件管理
            apps.add(GridApp(appName = "文件管理", iconRes = R.drawable.ic_file, iconBg = R.drawable.bg_icon_blue) {
                openFileManager()
            })
        } catch (e: Exception) {
            Log.e(TAG, "获取应用列表失败", e)
            // 至少返回固定入口
            apps.add(GridApp(appName = "应用管理", iconRes = R.drawable.ic_apps, iconBg = R.drawable.bg_icon_blue) {
                startActivity(Intent(this, AppManagerActivity::class.java))
            })
            apps.add(GridApp(appName = "系统设置", iconRes = R.drawable.ic_settings, iconBg = R.drawable.bg_icon_orange) {
                startActivity(Intent(this, SettingsActivity::class.java))
            })
        }

        return apps
    }

    private fun setupBottomNavigation() {
        // 固定功能按钮
        findViewById<LinearLayout>(R.id.nav_home)?.setOnClickListener {
            Toast.makeText(this, "主页", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.nav_navigation)?.setOnClickListener {
            openFirstAvailableNavigation()
        }
        findViewById<LinearLayout>(R.id.nav_music)?.setOnClickListener {
            openFirstAvailableMusic()
        }
        findViewById<LinearLayout>(R.id.nav_theme)?.setOnClickListener {
            showThemeCenterDialog()
        }
        findViewById<LinearLayout>(R.id.nav_add)?.setOnClickListener {
            showAddAppDialog()
        }
        
        // 设置可滑动的底部应用列表
        setupBottomAppsRecyclerView()
    }
    
    private fun setupBottomAppsRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.rv_bottom_apps) ?: return
        val layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)
        recyclerView.layoutManager = layoutManager
        
        val bottomApps = getBottomApps()
        val adapter = BottomAppsAdapter(bottomApps)
        recyclerView.adapter = adapter
    }
    
    private fun getBottomApps(): List<BottomApp> {
        val apps = mutableListOf<BottomApp>()
        
        // 添加固定功能：应用管理
        apps.add(BottomApp("应用管理", R.drawable.ic_apps, null) {
            startActivity(Intent(this, AppManagerActivity::class.java))
        })
        
        // 添加固定功能：文件管理
        apps.add(BottomApp("文件管理", R.drawable.ic_file, null) {
            openFileManager()
        })
        
        // 添加固定功能：音量
        apps.add(BottomApp("音量", R.drawable.ic_volume, null) {
            showVolumeDialog()
        })
        
        // 添加固定功能：设置
        apps.add(BottomApp("设置", R.drawable.ic_settings, null) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        
        // 添加自定义应用
        for (app in customApps) {
            val icon = try {
                packageManager.getApplicationIcon(app.packageName)
            } catch (e: Exception) {
                null
            }
            apps.add(BottomApp(app.appName, 0, icon) {
                openApp(app.packageName, app.appName)
            })
        }
        
        return apps
    }

    private fun setupCardClicks() {
        findViewById<View>(R.id.card_map)?.setOnClickListener { openFirstAvailableNavigation() }
        findViewById<View>(R.id.card_music)?.setOnClickListener { openFirstAvailableMusic() }
        
        // 地图卡片悬浮按钮 - 选择地图后启动画中画
        findViewById<View>(R.id.btn_float_map)?.setOnClickListener {
            showMapPickerForFloating()
        }
    }

    /**
     * 显示地图选择器，选择后启动画中画
     */
    private fun showMapPickerForFloating() {
        val mapApps = getInstalledMapApps()
        if (mapApps.isEmpty()) {
            Toast.makeText(this, "未检测到地图应用", Toast.LENGTH_SHORT).show()
            return
        }

        val names = mapApps.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择导航应用")
            .setItems(names) { _, which ->
                val (pkg, name) = mapApps[which]
                requestScreenCaptureAndStartFloating(pkg, name)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 获取已安装的地图应用列表
     */
    private fun getInstalledMapApps(): List<Pair<String, String>> {
        val apps = mutableListOf<Pair<String, String>>()
        val mapPackages = mapOf(
            "com.autonavi.amapauto" to "高德地图(车机版)",
            "com.autonavi.amapauto.lite" to "高德地图(车机精简版)",
            "com.autonavi.minimap" to "高德地图(手机版)",
            "com.baidu.BaiduMap" to "百度地图",
            "com.baidu.naviauto" to "百度地图(车机版)",
            "com.tencent.map" to "腾讯地图"
        )
        for ((pkg, name) in mapPackages) {
            try {
                if (packageManager.getPackageInfo(pkg, 0) != null) {
                    apps.add(pkg to name)
                }
            } catch (_: Exception) {}
        }
        // 检测双开/共存版
        try {
            val installed = packageManager.getInstalledPackages(0)
            for (info in installed) {
                val pkgName = info.packageName
                if (pkgName.contains("autonavi") || pkgName.contains("baidu.BaiduMap") || pkgName.contains("tencent.map")) {
                    if (apps.none { it.first == pkgName }) {
                        val label = info.applicationInfo?.loadLabel(packageManager)?.toString() ?: pkgName
                        apps.add(pkgName to label)
                    }
                }
            }
        } catch (_: Exception) {}
        return apps
    }

    private var pendingMapPackage: String? = null
    private var pendingMapName: String? = null

    private fun requestScreenCaptureAndStartFloating(pkg: String, name: String) {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            return
        }
        // 请求截屏权限
        pendingMapPackage = pkg
        pendingMapName = name
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), 2001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2001) {
            if (resultCode == RESULT_OK && data != null && pendingMapPackage != null) {
                val intent = Intent(this, FloatingMapService::class.java).apply {
                    putExtra(FloatingMapService.EXTRA_MAP_PACKAGE, pendingMapPackage)
                    putExtra(FloatingMapService.EXTRA_MAP_NAME, pendingMapName)
                    putExtra(FloatingMapService.EXTRA_CAPTURE_RESULT, resultCode)
                    putExtra(FloatingMapService.EXTRA_CAPTURE_DATA, data)
                }
                startService(intent)
                Toast.makeText(this, "${pendingMapName} 画中画已启动", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "需要截屏权限才能使用画中画", Toast.LENGTH_SHORT).show()
            }
            pendingMapPackage = null
            pendingMapName = null
        }
    }

    private fun openFirstAvailableNavigation() {
        val navApps = AppRecognizer.getInstalledNavigationApps(this)
        if (navApps.isEmpty()) {
            Toast.makeText(this, "未检测到导航应用\n请确认已安装导航软件", Toast.LENGTH_LONG).show()
            return
        }
        if (navApps.size == 1) {
            openApp(navApps[0].packageName, navApps[0].appName)
            return
        }
        val names = navApps.map {
            "${it.appName}${if (it.isDualApp) " (共存版)" else ""}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择导航应用")
            .setItems(names) { _, which ->
                openApp(navApps[which].packageName, navApps[which].appName)
            }
            .show()
    }

    private fun openFirstAvailableMusic() {
        val musicApps = AppRecognizer.getInstalledMusicApps(this)
        if (musicApps.isEmpty()) {
            Toast.makeText(this, "未检测到音乐应用\n请确认已安装音乐软件", Toast.LENGTH_LONG).show()
            return
        }
        if (musicApps.size == 1) {
            openApp(musicApps[0].packageName, musicApps[0].appName)
            return
        }
        // 多个音乐应用，弹出选择列表
        val names = musicApps.map { 
            "${it.appName}${if (it.isDualApp) " (共存版)" else ""}" 
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择音乐应用")
            .setItems(names) { _, which ->
                openApp(musicApps[which].packageName, musicApps[which].appName)
            }
            .show()
    }

    private fun openApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "$appName 无法启动", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开应用失败: $packageName", e)
            Toast.makeText(this, "打开 $appName 失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFileManager() {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.android.externalstorage.documents"), "vnd.android.document/root")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (e2: Exception) {
                Toast.makeText(this, "无法打开文件管理", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVolumeDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_volume)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setGravity(Gravity.BOTTOM)

        // 静音按钮
        val ivMute = dialog.findViewById<ImageView>(R.id.iv_mute)
        var isMuted = false
        val savedVolumes = mutableMapOf<Int, Int>()
        ivMute?.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                savedVolumes[AudioManager.STREAM_MUSIC] = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                savedVolumes[AudioManager.STREAM_RING] = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                savedVolumes[AudioManager.STREAM_ALARM] = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                safeSetVolume(AudioManager.STREAM_MUSIC, 0)
                safeSetVolume(AudioManager.STREAM_RING, 0)
                safeSetVolume(AudioManager.STREAM_ALARM, 0)
                ivMute.setImageResource(R.drawable.ic_volume_mute)
                updateVolumeUI(dialog, R.id.seek_media_volume, R.id.tv_media_volume, AudioManager.STREAM_MUSIC)
                updateVolumeUI(dialog, R.id.seek_ring_volume, R.id.tv_ring_volume, AudioManager.STREAM_RING)
                updateVolumeUI(dialog, R.id.seek_alarm_volume, R.id.tv_alarm_volume, AudioManager.STREAM_ALARM)
            } else {
                savedVolumes[AudioManager.STREAM_MUSIC]?.let { safeSetVolume(AudioManager.STREAM_MUSIC, it) }
                savedVolumes[AudioManager.STREAM_RING]?.let { safeSetVolume(AudioManager.STREAM_RING, it) }
                savedVolumes[AudioManager.STREAM_ALARM]?.let { safeSetVolume(AudioManager.STREAM_ALARM, it) }
                ivMute.setImageResource(R.drawable.ic_volume)
                updateVolumeUI(dialog, R.id.seek_media_volume, R.id.tv_media_volume, AudioManager.STREAM_MUSIC)
                updateVolumeUI(dialog, R.id.seek_ring_volume, R.id.tv_ring_volume, AudioManager.STREAM_RING)
                updateVolumeUI(dialog, R.id.seek_alarm_volume, R.id.tv_alarm_volume, AudioManager.STREAM_ALARM)
            }
        }

        setupVolumeControl(dialog, R.id.seek_media_volume, R.id.tv_media_volume, R.id.btn_media_minus, R.id.btn_media_plus, AudioManager.STREAM_MUSIC)
        setupVolumeControl(dialog, R.id.seek_ring_volume, R.id.tv_ring_volume, R.id.btn_ring_minus, R.id.btn_ring_plus, AudioManager.STREAM_RING)
        setupVolumeControl(dialog, R.id.seek_alarm_volume, R.id.tv_alarm_volume, R.id.btn_alarm_minus, R.id.btn_alarm_plus, AudioManager.STREAM_ALARM)

        dialog.show()
    }

    private fun safeSetVolume(streamType: Int, volume: Int) {
        try {
            audioManager.setStreamVolume(streamType, volume, 0)
        } catch (e: SecurityException) {
            Log.e(TAG, "设置音量失败(权限不足): stream=$streamType", e)
        } catch (e: Exception) {
            Log.e(TAG, "设置音量失败: stream=$streamType", e)
        }
    }

    private fun updateVolumeUI(dialog: Dialog, seekId: Int, tvId: Int, streamType: Int) {
        val seek = dialog.findViewById<SeekBar>(seekId)
        val tv = dialog.findViewById<TextView>(tvId)
        seek?.progress = audioManager.getStreamVolume(streamType)
        tv?.text = "${audioManager.getStreamVolume(streamType)}"
    }

    private fun setupVolumeControl(dialog: Dialog, seekId: Int, tvId: Int, minusBtnId: Int, plusBtnId: Int, streamType: Int) {
        val seek = dialog.findViewById<SeekBar>(seekId)
        val tv = dialog.findViewById<TextView>(tvId)
        val max = audioManager.getStreamMaxVolume(streamType)
        seek?.max = max
        val current = audioManager.getStreamVolume(streamType)
        seek?.progress = current
        tv?.text = "$current"

        seek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    safeSetVolume(streamType, progress)
                    tv?.text = "$progress"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        dialog.findViewById<View>(minusBtnId)?.setOnClickListener {
            val newVol = (audioManager.getStreamVolume(streamType) - 1).coerceAtLeast(0)
            safeSetVolume(streamType, newVol)
            seek?.progress = newVol
            tv?.text = "$newVol"
        }

        dialog.findViewById<View>(plusBtnId)?.setOnClickListener {
            val newVol = (audioManager.getStreamVolume(streamType) + 1).coerceAtMost(max)
            safeSetVolume(streamType, newVol)
            seek?.progress = newVol
            tv?.text = "$newVol"
        }
    }

    private fun showAddAppDialog() {
        if (customApps.size >= MAX_CUSTOM_APPS) {
            Toast.makeText(this, R.string.custom_app_max_reached, Toast.LENGTH_SHORT).show()
            return
        }
        val allApps = AppRecognizer.getAllInstalledApps(this)
        if (allApps.isEmpty()) { Toast.makeText(this, "未检测到已安装应用", Toast.LENGTH_SHORT).show(); return }
        val appNames = allApps.map { it.appName }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_app_add_title)
            .setItems(appNames) { _, which ->
                val appInfo = allApps[which]
                customApps.add(CustomApp(appInfo.packageName, appInfo.appName))
                saveCustomApps()
                setupBottomAppsRecyclerView() // 刷新底部应用列表
                Toast.makeText(this, "已添加: ${appInfo.appName}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }



    private fun loadCustomApps() {
        customApps.clear()
        val json = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_CUSTOM_APPS, "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); customApps.add(CustomApp(o.getString("packageName"), o.getString("appName"))) }
        } catch (e: Exception) { Log.e(TAG, "加载自定义应用失败", e) }
    }

    private fun saveCustomApps() {
        val arr = JSONArray()
        for (app in customApps) { val o = JSONObject(); o.put("packageName", app.packageName); o.put("appName", app.appName); arr.put(o) }
        getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit().putString(KEY_CUSTOM_APPS, arr.toString()).apply()
    }

    private fun showThemeCenterDialog() {
        val items = arrayOf("更换壁纸", "切换主题")
        AlertDialog.Builder(this)
            .setTitle("主题中心")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, WallpaperActivity::class.java))
                    1 -> showThemeSwitchDialog()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showThemeSwitchDialog() {
        val themes = arrayOf("深色主题", "浅色主题")
        val current = if (ThemeManager.isDarkTheme(this)) 0 else 1
        AlertDialog.Builder(this)
            .setTitle("切换主题")
            .setSingleChoiceItems(themes, current) { dialog, which ->
                val newTheme = if (which == 0) ThemeManager.THEME_DARK else ThemeManager.THEME_LIGHT
                ThemeManager.setTheme(this, newTheme)
                dialog.dismiss()
                // 实时应用主题背景
                applyAppBackground()
                Toast.makeText(this, "主题已切换，返回主页即可看到效果", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(updateTimeRunnable) }

    data class CustomApp(val packageName: String, val appName: String)

    /**
     * 底部应用数据
     */
    data class BottomApp(
        val appName: String,
        val iconRes: Int = 0,
        val icon: Drawable? = null,
        val onClick: () -> Unit
    )

    /**
     * 底部应用适配器
     */
    inner class BottomAppsAdapter(private val apps: List<BottomApp>) : RecyclerView.Adapter<BottomAppsAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_app_icon)
            val tvName: TextView = view.findViewById(R.id.tv_app_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_bottom_app, parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvName.text = app.appName
            if (app.icon != null) {
                holder.ivIcon.setImageDrawable(app.icon)
            } else {
                holder.ivIcon.setImageResource(app.iconRes)
            }
            holder.itemView.setOnClickListener { app.onClick() }
            // 长按移除自定义应用
            holder.itemView.setOnLongClickListener {
                val customApp = customApps.find { it.appName == app.appName }
                if (customApp != null) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("移除应用")
                        .setMessage("确定要移除 \"${app.appName}\" 吗？")
                        .setPositiveButton("移除") { _, _ ->
                            customApps.remove(customApp)
                            saveCustomApps()
                            setupBottomAppsRecyclerView()
                            Toast.makeText(this@MainActivity, "已移除 ${app.appName}", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                } else {
                    false
                }
            }
        }

        override fun getItemCount() = apps.size
    }

    /**
     * 网格应用数据
     */
    data class GridApp(
        val appName: String,
        val iconRes: Int = 0,
        val icon: Drawable? = null,
        val iconBg: Int = R.drawable.bg_icon_blue,
        val onClick: () -> Unit
    )

    /**
     * 应用网格适配器
     */
    inner class AppGridAdapter(private val apps: List<GridApp>) : RecyclerView.Adapter<AppGridAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_icon)
            val tvName: TextView = view.findViewById(R.id.tv_name)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_app_grid, parent, false))
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvName.text = app.appName
            if (app.icon != null) {
                holder.ivIcon.setImageDrawable(app.icon)
                holder.ivIcon.setBackgroundResource(0)
            } else {
                holder.ivIcon.setImageResource(app.iconRes)
                holder.ivIcon.setBackgroundResource(app.iconBg)
                holder.ivIcon.setPadding(16, 16, 16, 16)
            }
            holder.itemView.setOnClickListener { app.onClick() }
        }
        override fun getItemCount() = apps.size
    }
}
