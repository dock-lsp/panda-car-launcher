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

    override fun onResume() {
        super.onResume()
        setupFullScreen()
        loadCustomApps()
        gridAdapter.notifyDataSetChanged()
        renderCustomApps()
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
        findViewById<LinearLayout>(R.id.nav_home)?.setOnClickListener {
            Toast.makeText(this, "主页", Toast.LENGTH_SHORT).show()
        }
        findViewById<LinearLayout>(R.id.nav_navigation)?.setOnClickListener {
            openFirstAvailableNavigation()
        }
        findViewById<LinearLayout>(R.id.nav_music)?.setOnClickListener {
            openFirstAvailableMusic()
        }
        findViewById<LinearLayout>(R.id.nav_app_store)?.setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.nav_file_manager)?.setOnClickListener {
            openFileManager()
        }
        findViewById<LinearLayout>(R.id.nav_volume)?.setOnClickListener {
            showVolumeDialog()
        }
        findViewById<LinearLayout>(R.id.nav_add)?.setOnClickListener {
            showAddAppDialog()
        }
        renderCustomApps()
    }

    private fun setupCardClicks() {
        findViewById<View>(R.id.card_map)?.setOnClickListener { openFirstAvailableNavigation() }
        findViewById<View>(R.id.card_music)?.setOnClickListener { openFirstAvailableMusic() }
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
        setupVolumeSeek(dialog, R.id.seek_media_volume, R.id.tv_media_volume, AudioManager.STREAM_MUSIC)
        setupVolumeSeek(dialog, R.id.seek_ring_volume, R.id.tv_ring_volume, AudioManager.STREAM_RING)
        setupVolumeSeek(dialog, R.id.seek_alarm_volume, R.id.tv_alarm_volume, AudioManager.STREAM_ALARM)
        dialog.show()
    }

    private fun setupVolumeSeek(dialog: Dialog, seekId: Int, tvId: Int, streamType: Int) {
        val seek = dialog.findViewById<SeekBar>(seekId)
        val tv = dialog.findViewById<TextView>(tvId)
        val max = audioManager.getStreamMaxVolume(streamType)
        seek?.max = max
        seek?.progress = audioManager.getStreamVolume(streamType)
        tv?.text = "${seek?.progress}/$max"
        seek?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { audioManager.setStreamVolume(streamType, progress, 0); tv?.text = "$progress/$max" }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
                saveCustomApps(); renderCustomApps()
                Toast.makeText(this, "已添加: ${appInfo.appName}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null).show()
    }

    private fun renderCustomApps() {
        val container = findViewById<LinearLayout>(R.id.custom_apps_container) ?: return
        container.removeAllViews()
        for ((index, app) in customApps.withIndex()) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_custom_app, container, false)
            try { itemView.findViewById<ImageView>(R.id.iv_icon)?.setImageDrawable(packageManager.getApplicationIcon(app.packageName)) }
            catch (e: Exception) { itemView.findViewById<ImageView>(R.id.iv_icon)?.setImageResource(R.drawable.ic_apps) }
            itemView.findViewById<TextView>(R.id.tv_name)?.text = app.appName
            itemView.setOnClickListener { openApp(app.packageName, app.appName) }
            itemView.setOnLongClickListener {
                AlertDialog.Builder(this).setTitle(R.string.delete).setMessage(getString(R.string.custom_app_delete_confirm))
                    .setPositiveButton(R.string.confirm) { _, _ -> customApps.removeAt(index); saveCustomApps(); renderCustomApps(); Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show() }
                    .setNegativeButton(R.string.cancel, null).show()
                true
            }
            container.addView(itemView)
        }
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

    override fun onDestroy() { super.onDestroy(); handler.removeCallbacks(updateTimeRunnable) }

    data class CustomApp(val packageName: String, val appName: String)

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
