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
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * 悬浮导航服务
 * - 使用 WindowManager TYPE_APPLICATION_OVERLAY 显示悬浮窗
 * - 悬浮窗大小：宽度 45% 屏幕，高度 70% 屏幕
 * - 位置：屏幕水平居中，垂直居中
 * - 支持拖动
 * - 前台 Service 保活
 * - 广播协议打开高德悬浮版
 */
class FloatingNavService : Service() {

    companion object {
        private const val TAG = "FloatingNav"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_nav"
        private const val PREF_NAME = "floating_nav_prefs"

        const val ACTION_CLOSE = "action_close"

        // 高德悬浮版广播 Action
        private const val ACTION_AMAP_OPEN = "com.autonavi.plus.openmap"
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
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (floatingView == null) {
            createFloatingWindow()
            // 发送广播打开高德悬浮版
            openAmapFloating()
        }

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_nav, null)

        val dm = resources.displayMetrics
        val width = (dm.widthPixels * 0.45).toInt()
        val height = (dm.heightPixels * 0.70).toInt()

        // 加载保存的位置，默认居中
        val savedX = prefs.getInt("pos_x", Int.MIN_VALUE)
        val savedY = prefs.getInt("pos_y", Int.MIN_VALUE)
        val defaultX = (dm.widthPixels - width) / 2
        val defaultY = (dm.heightPixels - height) / 2

        params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX != Int.MIN_VALUE) savedX else defaultX
            y = if (savedY != Int.MIN_VALUE) savedY else defaultY
        }

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮导航窗已添加")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
            return
        }

        setupButtons()
        setupDrag()
    }

    private fun setupButtons() {
        val rootView = floatingView ?: return

        // 关闭按钮
        rootView.findViewById<ImageView>(R.id.nav_close)?.setOnClickListener {
            stopSelf()
        }

        // 打开导航按钮
        rootView.findViewById<Button>(R.id.nav_open)?.setOnClickListener {
            openMapApp()
        }

        // 切换导航类型
        rootView.findViewById<TextView>(R.id.nav_switch)?.setOnClickListener {
            showNavSwitchDialog()
        }

        // 点击导航状态区域也可以打开导航
        rootView.findViewById<TextView>(R.id.nav_status)?.setOnClickListener {
            openMapApp()
        }

        rootView.findViewById<TextView>(R.id.nav_hint)?.setOnClickListener {
            openMapApp()
        }
    }

    /**
     * 发送广播打开高德悬浮版
     */
    private fun openAmapFloating() {
        try {
            val intent = Intent(ACTION_AMAP_OPEN)
            intent.setPackage("com.autonavi.amapauto")
            intent.putExtra("x", 0)
            intent.putExtra("y", 0)
            intent.putExtra("w", 0)
            intent.putExtra("h", 0)
            sendBroadcast(intent)
            Log.d(TAG, "已发送高德悬浮版广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送高德广播失败", e)
        }
    }

    /**
     * 打开地图应用（依次尝试高德车机版、高德手机版、百度、腾讯）
     */
    private fun openMapApp() {
        val packages = arrayOf(
            "com.autonavi.amapauto",
            "com.autonavi.minimap",
            "com.baidu.BaiduMap",
            "com.tencent.map"
        )
        for (pkg in packages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    updateNavStatus(true)
                    return
                }
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "未找到地图应用", Toast.LENGTH_SHORT).show()
        updateNavStatus(false)
    }

    /**
     * 显示导航切换对话框（通过 Toast 提示）
     */
    private fun showNavSwitchDialog() {
        // 简单实现：显示已安装的导航应用列表
        val navApps = mutableListOf<String>()
        val navPackages = mapOf(
            "com.autonavi.amapauto" to "高德地图(车机版)",
            "com.autonavi.minimap" to "高德地图(手机版)",
            "com.baidu.BaiduMap" to "百度地图",
            "com.tencent.map" to "腾讯地图"
        )
        for ((pkg, name) in navPackages) {
            try {
                if (packageManager.getPackageInfo(pkg, 0) != null) {
                    navApps.add(name)
                }
            } catch (_: Exception) {}
        }

        if (navApps.isNotEmpty()) {
            Toast.makeText(this, "已安装导航: ${navApps.joinToString(", ")}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "未检测到导航应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNavStatus(isActive: Boolean) {
        val rootView = floatingView ?: return
        val tvStatus = rootView.findViewById<TextView>(R.id.nav_status)
        val tvHint = rootView.findViewById<TextView>(R.id.nav_hint)

        if (isActive) {
            tvStatus?.text = "导航运行中"
            tvHint?.text = "点击返回导航"
        } else {
            tvStatus?.text = "导航未启动"
            tvHint?.text = "点击打开导航"
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
                        // 保存位置
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
