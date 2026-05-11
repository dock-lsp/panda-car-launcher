package com.pandora.carlauncher

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val PREF_NAME = "panda_launcher_prefs"
        private const val KEY_CUSTOM_APPS = "custom_apps"
        private const val MAX_CUSTOM_APPS = 5
        
        // 常用应用包名
        private const val PACKAGE_AMAP = "com.autonavi.minimap" // 高德地图
        private const val PACKAGE_KUWO = "cn.kuwo.player" // 酷我音乐
        private const val PACKAGE_QQMUSIC = "com.tencent.qqmusic" // QQ音乐
        private const val PACKAGE_NETEASE_MUSIC = "com.netease.cloudmusic" // 网易云音乐
        private const val PACKAGE_FILE_MANAGER = "com.android.documentsui" // 文件管理
        private const val PACKAGE_APP_STORE = "com.android.vending" // Google Play
    }

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault())
    
    private lateinit var audioManager: AudioManager
    private var customApps = mutableListOf<CustomApp>()
    
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // 初始化时间显示
        updateTime()
        handler.post(updateTimeRunnable)
        
        // 加载自定义应用
        loadCustomApps()
        
        // 设置底部导航栏
        setupBottomNavigation()
        
        // 设置卡片点击事件
        setupCardClicks()
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        findViewById<TextView>(R.id.tv_time)?.text = timeFormat.format(now.time)
        findViewById<TextView>(R.id.tv_date)?.text = dateFormat.format(now.time)
    }

    private fun setupBottomNavigation() {
        // 主页按钮 - 刷新当前页面
        findViewById<LinearLayout>(R.id.nav_home)?.setOnClickListener {
            Toast.makeText(this, "主页", Toast.LENGTH_SHORT).show()
        }
        
        // 导航按钮 - 打开高德地图
        findViewById<LinearLayout>(R.id.nav_navigation)?.setOnClickListener {
            openApp(PACKAGE_AMAP, "高德地图")
        }
        
        // 音乐按钮 - 打开音乐应用
        findViewById<LinearLayout>(R.id.nav_music)?.setOnClickListener {
            // 优先打开酷我，其次QQ音乐，再网易云音乐
            val musicPackages = listOf(PACKAGE_KUWO, PACKAGE_QQMUSIC, PACKAGE_NETEASE_MUSIC)
            var opened = false
            for (pkg in musicPackages) {
                if (isAppInstalled(pkg)) {
                    openApp(pkg, "音乐")
                    opened = true
                    break
                }
            }
            if (!opened) {
                Toast.makeText(this, "未安装音乐应用", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 应用商店按钮
        findViewById<LinearLayout>(R.id.nav_app_store)?.setOnClickListener {
            openApp(PACKAGE_APP_STORE, "应用商店")
        }
        
        // 文件管理按钮
        findViewById<LinearLayout>(R.id.nav_file_manager)?.setOnClickListener {
            openFileManager()
        }
        
        // 音量调节按钮
        findViewById<LinearLayout>(R.id.nav_volume)?.setOnClickListener {
            showVolumeDialog()
        }
        
        // 添加按钮
        findViewById<LinearLayout>(R.id.nav_add)?.setOnClickListener {
            showAddAppDialog()
        }
        
        // 渲染自定义应用
        renderCustomApps()
    }

    private fun setupCardClicks() {
        // 地图卡片 - 打开地图
        findViewById<View>(R.id.card_map)?.setOnClickListener {
            openApp(PACKAGE_AMAP, "高德地图")
        }
        
        // 音乐卡片 - 打开音乐
        findViewById<View>(R.id.card_music)?.setOnClickListener {
            val musicPackages = listOf(PACKAGE_KUWO, PACKAGE_QQMUSIC, PACKAGE_NETEASE_MUSIC)
            for (pkg in musicPackages) {
                if (isAppInstalled(pkg)) {
                    openApp(pkg, "音乐")
                    break
                }
            }
        }
    }

    private fun openApp(packageName: String, appName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                // 应用未安装，打开应用市场搜索
                try {
                    val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                    marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(marketIntent)
                } catch (e: Exception) {
                    Toast.makeText(this, "$appName 未安装", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开应用失败: $packageName", e)
            Toast.makeText(this, "打开 $appName 失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFileManager() {
        try {
            // 尝试打开系统文件管理器
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse("content://com.android.externalstorage.documents"), "vnd.android.document/root")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // 备用方案
            try {
                val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
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
        
        // 媒体音量
        val seekMedia = dialog.findViewById<SeekBar>(R.id.seek_media_volume)
        val tvMedia = dialog.findViewById<TextView>(R.id.tv_media_volume)
        val maxMedia = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        seekMedia?.max = maxMedia
        seekMedia?.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        tvMedia?.text = "${seekMedia?.progress}/${maxMedia}"
        seekMedia?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
                    tvMedia?.text = "$progress/$maxMedia"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 铃声音量
        val seekRing = dialog.findViewById<SeekBar>(R.id.seek_ring_volume)
        val tvRing = dialog.findViewById<TextView>(R.id.tv_ring_volume)
        val maxRing = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        seekRing?.max = maxRing
        seekRing?.progress = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        tvRing?.text = "${seekRing?.progress}/${maxRing}"
        seekRing?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, progress, 0)
                    tvRing?.text = "$progress/$maxRing"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 闹钟音量
        val seekAlarm = dialog.findViewById<SeekBar>(R.id.seek_alarm_volume)
        val tvAlarm = dialog.findViewById<TextView>(R.id.tv_alarm_volume)
        val maxAlarm = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        seekAlarm?.max = maxAlarm
        seekAlarm?.progress = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        tvAlarm?.text = "${seekAlarm?.progress}/${maxAlarm}"
        seekAlarm?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    audioManager.setStreamVolume(AudioManager.STREAM_ALARM, progress, 0)
                    tvAlarm?.text = "$progress/$maxAlarm"
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        dialog.show()
    }

    private fun showAddAppDialog() {
        if (customApps.size >= MAX_CUSTOM_APPS) {
            Toast.makeText(this, R.string.custom_app_max_reached, Toast.LENGTH_SHORT).show()
            return
        }
        
        // 获取已安装应用列表
        val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { 
                // 过滤掉系统应用和本应用
                it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != packageName
            }
            .sortedBy { it.loadLabel(packageManager).toString() }
        
        val appNames = installedApps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle(R.string.custom_app_add_title)
            .setItems(appNames) { _, which ->
                val appInfo = installedApps[which]
                val customApp = CustomApp(
                    packageName = appInfo.packageName,
                    appName = appInfo.loadLabel(packageManager).toString()
                )
                customApps.add(customApp)
                saveCustomApps()
                renderCustomApps()
                Toast.makeText(this, "已添加: ${customApp.appName}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun renderCustomApps() {
        val container = findViewById<LinearLayout>(R.id.custom_apps_container) ?: return
        container.removeAllViews()
        
        for ((index, app) in customApps.withIndex()) {
            val itemView = LayoutInflater.from(this).inflate(R.layout.item_custom_app, container, false)
            
            // 设置图标
            try {
                val icon = packageManager.getApplicationIcon(app.packageName)
                itemView.findViewById<ImageView>(R.id.iv_icon)?.setImageDrawable(icon)
            } catch (e: Exception) {
                itemView.findViewById<ImageView>(R.id.iv_icon)?.setImageResource(R.drawable.ic_apps)
            }
            
            // 设置名称
            itemView.findViewById<TextView>(R.id.tv_name)?.text = app.appName
            
            // 点击打开应用
            itemView.setOnClickListener {
                openApp(app.packageName, app.appName)
            }
            
            // 长按删除
            itemView.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle(R.string.delete)
                    .setMessage(getString(R.string.custom_app_delete_confirm))
                    .setPositiveButton(R.string.confirm) { _, _ ->
                        customApps.removeAt(index)
                        saveCustomApps()
                        renderCustomApps()
                        Toast.makeText(this, "已删除: ${app.appName}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
                true
            }
            
            container.addView(itemView)
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun loadCustomApps() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CUSTOM_APPS, "[]") ?: "[]"
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                customApps.add(CustomApp(
                    packageName = obj.getString("packageName"),
                    appName = obj.getString("appName")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载自定义应用失败", e)
        }
    }

    private fun saveCustomApps() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        for (app in customApps) {
            val obj = JSONObject()
            obj.put("packageName", app.packageName)
            obj.put("appName", app.appName)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_APPS, jsonArray.toString()).apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
    }
    
    data class CustomApp(
        val packageName: String,
        val appName: String
    )
}
