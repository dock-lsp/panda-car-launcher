package com.pandora.carlauncher

import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * 系统设置页面
 * 包含设备存储信息、悬浮球开关、各类系统设置跳转
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 返回按钮
        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        // 加载存储信息
        loadStorageInfo()

        // 悬浮球开关
        setupFloatBallSwitch()

        // 设置列表项点击事件
        setupSettingItems()
    }

    /**
     * 通过 StatFs 获取并显示设备存储信息
     */
    private fun loadStorageInfo() {
        try {
            val statFs = StatFs(android.os.Environment.getDataDirectory().path)
            val totalBytes = statFs.totalBytes
            val availableBytes = statFs.availableBytes
            val usedBytes = totalBytes - availableBytes

            val tvTotal = findViewById<TextView>(R.id.tv_total_storage)
            val tvUsed = findViewById<TextView>(R.id.tv_used_storage)
            val tvAvailable = findViewById<TextView>(R.id.tv_available_storage)
            val progressBar = findViewById<ProgressBar>(R.id.storage_progress)

            tvTotal?.text = formatFileSize(totalBytes)
            tvUsed?.text = formatFileSize(usedBytes)
            tvAvailable?.text = formatFileSize(availableBytes)

            val usedPercent = if (totalBytes > 0) {
                (usedBytes * 100 / totalBytes).toInt()
            } else {
                0
            }
            progressBar?.progress = usedPercent
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups.coerceAtMost(units.size - 1)]
        )
    }

    /**
     * 设置悬浮球开关
     */
    private fun setupFloatBallSwitch() {
        val switchFloatBall = findViewById<Switch>(R.id.switch_float_ball)
        val prefs = getSharedPreferences(PREF_FLOAT_BALL, MODE_PRIVATE)
        switchFloatBall?.isChecked = prefs.getBoolean(KEY_FLOAT_BALL_ENABLED, false)

        switchFloatBall?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_FLOAT_BALL_ENABLED, isChecked).apply()
            
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
                    switchFloatBall.isChecked = false
                    return@setOnCheckedChangeListener
                }
                startService(Intent(this, FloatingBallService::class.java))
                Toast.makeText(this, "悬浮球已开启", Toast.LENGTH_SHORT).show()
            } else {
                stopService(Intent(this, FloatingBallService::class.java))
                Toast.makeText(this, "悬浮球已关闭", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 设置各列表项的点击事件
     */
    private fun setupSettingItems() {
        // 悬浮球设置
        findViewById<View>(R.id.item_float_ball_setting)?.setOnClickListener {
            startActivity(Intent(this, FloatingBallSettingsActivity::class.java))
        }
        bindSettingItem(
            R.id.item_float_ball_setting,
            R.drawable.ic_settings,
            "悬浮球设置",
            "自定义悬浮球样式和功能"
        )

        // 视频播放器
        findViewById<View>(R.id.item_video_player)?.setOnClickListener {
            startActivity(Intent(this, VideoPlayerActivity::class.java))
        }
        bindSettingItem(
            R.id.item_video_player,
            R.drawable.ic_play,
            "视频播放器",
            "播放本地视频和在线视频"
        )

        // WiFi设置
        findViewById<View>(R.id.item_wifi)?.setOnClickListener {
            openSystemSettings(Settings.ACTION_WIFI_SETTINGS)
        }
        bindSettingItem(
            R.id.item_wifi,
            R.drawable.ic_settings,
            "WiFi设置",
            "管理无线网络连接"
        )

        // 蓝牙设置
        findViewById<View>(R.id.item_bluetooth)?.setOnClickListener {
            openSystemSettings(Settings.ACTION_BLUETOOTH_SETTINGS)
        }
        bindSettingItem(
            R.id.item_bluetooth,
            R.drawable.ic_settings,
            "蓝牙设置",
            "管理蓝牙设备和连接"
        )

        // 显示设置
        findViewById<View>(R.id.item_display)?.setOnClickListener {
            openSystemSettings(Settings.ACTION_DISPLAY_SETTINGS)
        }
        bindSettingItem(
            R.id.item_display,
            R.drawable.ic_settings,
            "显示设置",
            "亮度、壁纸、字体大小"
        )

        // 声音设置
        findViewById<View>(R.id.item_sound)?.setOnClickListener {
            openSystemSettings(Settings.ACTION_SOUND_SETTINGS)
        }
        bindSettingItem(
            R.id.item_sound,
            R.drawable.ic_volume,
            "声音设置",
            "音量、铃声、通知音"
        )

        // 应用管理
        findViewById<View>(R.id.item_app_manager)?.setOnClickListener {
            startActivity(Intent(this, AppManagerActivity::class.java))
        }
        bindSettingItem(
            R.id.item_app_manager,
            R.drawable.ic_apps,
            "应用管理",
            "查看和管理已安装应用"
        )

        // 存储设置
        findViewById<View>(R.id.item_storage)?.setOnClickListener {
            openSystemSettings(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
        }
        bindSettingItem(
            R.id.item_storage,
            R.drawable.ic_file,
            "存储设置",
            "管理内部存储空间"
        )

        // 位置设置
        findViewById<View>(R.id.item_location)?.setOnClickListener {
            openSystemSettings(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        }
        bindSettingItem(
            R.id.item_location,
            R.drawable.ic_navigation,
            "位置设置",
            "GPS定位和位置服务"
        )

        // 关于我们
        findViewById<View>(R.id.item_about)?.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        bindSettingItem(
            R.id.item_about,
            R.drawable.ic_apps,
            "关于我们",
            "版本信息和软件说明"
        )
    }

    /**
     * 绑定设置项的图标、标题、副标题
     */
    private fun bindSettingItem(
        itemId: Int,
        iconRes: Int,
        title: String,
        subtitle: String
    ) {
        val itemView = findViewById<View>(itemId)
        itemView?.findViewById<ImageView>(R.id.iv_setting_icon)?.setImageResource(iconRes)
        itemView?.findViewById<TextView>(R.id.tv_setting_title)?.text = title
        val tvSubtitle = itemView?.findViewById<TextView>(R.id.tv_setting_subtitle)
        if (tvSubtitle != null) {
            tvSubtitle.text = subtitle
            tvSubtitle.visibility = View.VISIBLE
        }
    }

    /**
     * 打开系统设置页面
     */
    private fun openSystemSettings(action: String) {
        try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val PREF_FLOAT_BALL = "float_ball_pref"
        private const val KEY_FLOAT_BALL_ENABLED = "float_ball_enabled"
        private const val KEY_FLOAT_BALL_COLOR = "float_ball_color"
        private const val KEY_FLOAT_BALL_SIZE = "float_ball_size"
        private const val KEY_FLOAT_BALL_ALPHA = "float_ball_alpha"
        private const val KEY_FLOAT_BALL_FUNC_HOME = "float_ball_func_home"
        private const val KEY_FLOAT_BALL_FUNC_BACK = "float_ball_func_back"
        private const val KEY_FLOAT_BALL_FUNC_RECENT = "float_ball_func_recent"
        private const val KEY_FLOAT_BALL_FUNC_MUSIC = "float_ball_func_music"
        private const val KEY_FLOAT_BALL_FUNC_NAV = "float_ball_func_nav"
    }
}
