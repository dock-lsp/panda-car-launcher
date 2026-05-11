package com.pandora.carlauncher

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
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

        updateTime()
        handler.post(updateTimeRunnable)

        requestPermissions()
        loadCustomApps()
        setupBottomNavigation()
        setupCardClicks()
    }

    override fun onResume() {
        super.onResume()
        loadCustomApps()
        renderCustomApps()
    }

    /**
     * 请求必要权限
     */
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
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (i in permissions.indices) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "权限被拒绝: ${permissions[i]}")
                }
            }
        }
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        findViewById<TextView>(R.id.tv_time)?.text = timeFormat.format(now.time)
        findViewById<TextView>(R.id.tv_date)?.text = dateFormat.format(now.time)
    }

    private fun setupBottomNavigation() {
        // 主页按钮
        findViewById<LinearLayout>(R.id.nav_home)?.setOnClickListener {
            Toast.makeText(this, "主页", Toast.LENGTH_SHORT).show()
        }

        // 导航按钮 - 支持共存版识别
        findViewById<LinearLayout>(R.id.nav_navigation)?.setOnClickListener {
            openFirstAvailableNavigation()
        }

        // 音乐按钮 - 支持共存版识别
        findViewById<LinearLayout>(R.id.nav_music)?.setOnClickListener {
            openFirstAvailableMusic()
        }

        // 应用管理按钮
        findViewById<LinearLayout>(R.id.nav_app_store)?.setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
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

        renderCustomApps()
    }

    private fun setupCardClicks() {
        findViewById<View>(R.id.card_map)?.setOnClickListener {
            openFirstAvailableNavigation()
        }

        findViewById<View>(R.id.card_music)?.setOnClickListener {
            openFirstAvailableMusic()
        }
    }

    /**
     * 打开第一个可用的导航应用（支持共存版）
     */
    private fun openFirstAvailableNavigation() {
        val navApps = AppRecognizer.getInstalledNavigationApps(this)
        if (navApps.isNotEmpty()) {
            openApp(navApps[0].packageName, navApps[0].appName)
        } else {
            Toast.makeText(this, "未检测到导航应用\n请确认已安装高德/百度/腾讯地图", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 打开第一个可用的音乐应用（支持共存版）
     */
    private fun openFirstAvailableMusic() {
        val musicApps = AppRecognizer.getInstalledMusicApps(this)
        if (musicApps.isNotEmpty()) {
            openApp(musicApps[0].packageName, musicApps[0].appName)
        } else {
            Toast.makeText(this, "未检测到音乐应用\n请确认已安装酷我/QQ/网易云音乐", Toast.LENGTH_LONG).show()
        }
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
                if (fromUser) {
                    audioManager.setStreamVolume(streamType, progress, 0)
                    tv?.text = "$progress/$max"
                }
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

        // 使用 AppRecognizer 获取所有已安装应用
        val allApps = AppRecognizer.getAllInstalledApps(this)
        if (allApps.isEmpty()) {
            Toast.makeText(this, "未检测到已安装应用", Toast.LENGTH_SHORT).show()
            return
        }

        val appNames = allApps.map { it.appName }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle(R.string.custom_app_add_title)
            .setItems(appNames) { _, which ->
                val appInfo = allApps[which]
                val customApp = CustomApp(
                    packageName = appInfo.packageName,
                    appName = appInfo.appName
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

            try {
                val icon = packageManager.getApplicationIcon(app.packageName)
                itemView.findViewById<ImageView>(R.id.iv_icon)?.setImageDrawable(icon)
            } catch (e: Exception) {
                itemView.findViewById<ImageView>(R.id.iv_icon)?.setImageResource(R.drawable.ic_apps)
            }

            itemView.findViewById<TextView>(R.id.tv_name)?.text = app.appName

            itemView.setOnClickListener {
                openApp(app.packageName, app.appName)
            }

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

    private fun loadCustomApps() {
        customApps.clear()
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
