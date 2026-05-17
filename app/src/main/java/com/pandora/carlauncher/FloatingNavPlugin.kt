package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 导航悬浮插件
 * 通过广播协议控制高德/百度车机版悬浮地图
 */
class FloatingNavPlugin : Service() {

    companion object {
        private const val TAG = "FloatingNavPlugin"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_nav"
        const val ACTION_SHOW = "action_show"
        const val ACTION_HIDE = "action_hide"
        const val ACTION_CLOSE = "action_close"

        // 高德悬浮版广播
        private const val ACTION_AMAP_OPEN = "com.autonavi.plus.openmap"
        private const val ACTION_AMAP_CLOSE = "com.autonavi.plus.closemap"
        
        @Volatile var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var currentNavType = "amap" // amap, baidu, tencent

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE, ACTION_CLOSE -> {
                hideFloatingMap()
                if (intent?.action == ACTION_CLOSE) stopSelf()
            }
            ACTION_SHOW -> {
                showFloatingMap()
            }
            else -> {
                showFloatingMap()
            }
        }
        return START_STICKY
    }

    private fun showFloatingMap() {
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        
        // 发送广播打开高德悬浮版
        try {
            val intent = Intent(ACTION_AMAP_OPEN).apply {
                putExtra("x", 0)
                putExtra("y", 0)
                putExtra("w", 0)
                putExtra("h", 0)
            }
            sendBroadcast(intent)
            Log.d(TAG, "已发送高德悬浮版广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送广播失败", e)
        }

        // 显示桌面悬浮信息卡片
        if (floatingView == null) createFloatingCard()
    }

    private fun hideFloatingMap() {
        isRunning = false
        // 发送广播关闭高德悬浮版
        try {
            sendBroadcast(Intent(ACTION_AMAP_CLOSE))
        } catch (_: Exception) {}
        
        // 移除桌面悬浮卡片
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingCard() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_nav_card, null)

        val dm = resources.displayMetrics
        val width = (dm.widthPixels * 0.25).toInt()
        val height = (dm.heightPixels * 0.6).toInt()

        params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = (dm.heightPixels - height) / 2
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮卡片失败", e)
            return
        }

        // 关闭按钮
        floatingView?.findViewById<ImageView>(R.id.nav_card_close)?.setOnClickListener {
            hideFloatingMap()
            stopSelf()
        }

        // 打开导航按钮
        floatingView?.findViewById<Button>(R.id.nav_card_open)?.setOnClickListener {
            openMapApp()
        }

        // 切换导航类型
        floatingView?.findViewById<TextView>(R.id.nav_card_switch)?.setOnClickListener {
            switchNavType()
        }
    }

    private fun switchNavType() {
        currentNavType = when (currentNavType) {
            "amap" -> "baidu"
            "baidu" -> "tencent"
            else -> "amap"
        }
        val navName = when (currentNavType) {
            "baidu" -> "百度"
            "tencent" -> "腾讯"
            else -> "高德"
        }
        floatingView?.findViewById<TextView>(R.id.nav_card_switch)?.text = "$navName ▼"
        floatingView?.findViewById<TextView>(R.id.nav_card_status)?.text = "${navName}导航运行中"
        
        // 重新发送广播
        hideFloatingMap()
        handler.postDelayed({ showFloatingMap() }, 500)
    }

    private fun openMapApp() {
        val packages = when (currentNavType) {
            "baidu" -> arrayOf("com.baidu.BaiduMap", "com.baidu.naviauto")
            "tencent" -> arrayOf("com.tencent.map")
            else -> arrayOf("com.autonavi.amapauto", "com.autonavi.minimap")
        }
        for (pkg in packages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "未找到地图应用", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮导航", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮导航运行中")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮导航运行中")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingMap()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
