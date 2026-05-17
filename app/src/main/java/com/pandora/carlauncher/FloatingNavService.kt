package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * 悬浮导航服务（简化版）
 * - 启动时发送广播打开高德悬浮版
 * - 显示简单状态卡片
 * - 支持拖动
 */
class FloatingNavService : Service() {

    companion object {
        private const val TAG = "FloatingNavService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "floating_nav_service"
        private const val PREF_NAME = "floating_nav_prefs"

        const val ACTION_START = "com.pandora.carlauncher.FloatingNavService.START"
        const val ACTION_STOP = "com.pandora.carlauncher.FloatingNavService.STOP"

        // 高德悬浮版广播 Action
        private const val ACTION_AMAP_OPEN = "com.autonavi.plus.openmap"
        private const val ACTION_AMAP_CLOSE = "com.autonavi.plus.closemap"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (floatingView == null) {
            createFloatingWindow()
            // 启动时自动打开高德悬浮导航
            openNavigation()
        }

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        Log.d(TAG, "createFloatingWindow")
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_nav, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 加载保存的位置
        val savedX = prefs.getInt("pos_x", 0)
        val savedY = prefs.getInt("pos_y", 0)
        val dm = resources.displayMetrics
        val defaultX = (dm.widthPixels - 300) / 2
        val defaultY = 80

        params = WindowManager.LayoutParams(
            300,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX != 0) savedX else defaultX
            y = if (savedY != 0) savedY else defaultY
        }

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮导航窗已添加")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮导航窗失败", e)
            stopSelf()
            return
        }

        setupButtons()
        setupDrag()
    }

    private fun setupButtons() {
        val rootView = floatingView ?: return

        // 关闭悬浮窗
        rootView.findViewById<ImageView>(R.id.nav_close)?.setOnClickListener {
            stopSelf()
        }

        // 点击卡片打开导航
        rootView.findViewById<TextView>(R.id.nav_status)?.setOnClickListener {
            openNavigation()
        }

        rootView.findViewById<TextView>(R.id.nav_hint)?.setOnClickListener {
            openNavigation()
        }
    }

    private fun openNavigation() {
        // 尝试通过广播打开高德悬浮版
        try {
            val intent = Intent(ACTION_AMAP_OPEN)
            intent.setPackage("com.autonavi.amapauto")
            sendBroadcast(intent)
            updateNavStatus(true)
            Toast.makeText(this, "正在打开高德悬浮导航", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "已发送高德悬浮导航广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送高德广播失败，尝试直接启动", e)
            // 回退方案：直接启动高德地图
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage("com.autonavi.amapauto")
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    updateNavStatus(true)
                    Toast.makeText(this, "已启动高德地图", Toast.LENGTH_SHORT).show()
                } else {
                    // 尝试手机版高德
                    val phoneIntent = packageManager.getLaunchIntentForPackage("com.autonavi.minimap")
                    if (phoneIntent != null) {
                        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(phoneIntent)
                        updateNavStatus(true)
                        Toast.makeText(this, "已启动高德地图(手机版)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "未检测到高德地图，请先安装", Toast.LENGTH_LONG).show()
                        updateNavStatus(false)
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "启动高德地图失败", e2)
                Toast.makeText(this, "打开导航失败", Toast.LENGTH_SHORT).show()
                updateNavStatus(false)
            }
        }
    }

    private fun updateNavStatus(isActive: Boolean) {
        val rootView = floatingView ?: return
        val tvStatus = rootView.findViewById<TextView>(R.id.nav_status)
        val tvHint = rootView.findViewById<TextView>(R.id.nav_hint)

        if (isActive) {
            tvStatus?.text = "导航运行中"
            tvHint?.text = "点击返回高德导航"
        } else {
            tvStatus?.text = "导航未启动"
            tvHint?.text = "点击打开高德导航"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        val rootView = floatingView ?: return

        rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (dx * dx + dy * dy) > 25) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        params?.let { p ->
                            prefs.edit()
                                .putInt("pos_x", p.x)
                                .putInt("pos_y", p.y)
                                .apply()
                        }
                    }
                    isDragging
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮导航",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "悬浮导航服务运行中"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮导航")
                .setContentText("导航服务运行中")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮导航")
                .setContentText("导航服务运行中")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        floatingView = null
        Log.d(TAG, "悬浮导航服务已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
