package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 真正的悬浮导航服务
 * 使用 WindowManager TYPE_APPLICATION_OVERLAY
 * 嵌入高德/百度悬浮版导航
 */
class FloatingNavService : Service() {

    companion object {
        private const val TAG = "FloatingNav"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_nav"
        const val ACTION_SHOW = "action_show"
        const val ACTION_HIDE = "action_hide"
        const val ACTION_CLOSE = "action_close"
        
        @Volatile var isRunning = false
        @Volatile var isMinimized = false
        
        // 检查悬浮窗权限
        fun canDrawOverlays(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        }
        
        // 请求悬浮窗权限
        fun requestOverlayPermission(context: Activity) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"))
                context.startActivityForResult(intent, 1001)
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var navContainer: FrameLayout? = null
    
    // 拖拽
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // 当前导航类型
    private var currentNavType = "amap" // amap, baidu, tencent

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> minimizeWindow()
            ACTION_CLOSE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> showFloatingWindow()
        }
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showFloatingWindow() {
        if (floatingView != null) return
        
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true

        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_nav_container, null)
        
        navContainer = floatingView?.findViewById(R.id.nav_container)

        val dm = resources.displayMetrics
        val width = (dm.widthPixels * 0.8).toInt()
        val height = (dm.heightPixels * 0.7).toInt()

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
            x = (dm.widthPixels - width) / 2
            y = (dm.heightPixels - height) / 4
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
            return
        }

        setupDragFunction()
        setupButtons()
        
        // 启动导航
        startNavigation()
    }

    private fun setupDragFunction() {
        val dragHandle = floatingView?.findViewById<View>(R.id.drag_handle)
        
        dragHandle?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    try { 
                        windowManager?.updateViewLayout(floatingView, params) 
                    } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        // 关闭按钮
        floatingView?.findViewById<ImageView>(R.id.btn_close)?.setOnClickListener {
            stopSelf()
        }
        
        // 最小化按钮
        floatingView?.findViewById<ImageView>(R.id.btn_minimize)?.setOnClickListener {
            minimizeWindow()
        }
        
        // 切换导航
        floatingView?.findViewById<TextView>(R.id.nav_switch)?.setOnClickListener {
            switchNavType()
        }
    }

    private fun startNavigation() {
        // 通过 Intent 调起高德/百度/腾讯悬浮版
        when (currentNavType) {
            "baidu" -> startBaiduNav()
            "tencent" -> startTencentNav()
            else -> startAmapNav()
        }
    }

    private fun startAmapNav() {
        // 共存版高德地图车机版包名列表
        val amapPackages = arrayOf(
            "com.autonavi.amapauto",      // 高德地图车机版
            "com.autonavi.amapauto.nx",    // 高德地图车机共存版
            "com.autonavi.amapauto.u3d"    // 高德地图车机共存版U3D
        )

        for (pkg in amapPackages) {
            try {
                val intent = Intent().apply {
                    setClassName(pkg, "$pkg.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)

                // 发送广播打开悬浮窗
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        sendBroadcast(Intent("com.autonavi.plus.openmap").apply {
                            putExtra("x", params?.x ?: 0)
                            putExtra("y", params?.y ?: 0)
                            putExtra("w", params?.width ?: 0)
                            putExtra("h", params?.height ?: 0)
                        })
                    } catch (_: Exception) {}
                }, 500)
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "请安装高德地图车机版", Toast.LENGTH_SHORT).show()
    }

    private fun startBaiduNav() {
        // 百度地图车机版包名列表
        val baiduPackages = arrayOf(
            "com.baidu.BaiduMap",          // 百度地图
            "com.baidu.naviauto"           // 百度地图车机版
        )

        for (pkg in baiduPackages) {
            try {
                val intent = Intent().apply {
                    setClassName(pkg, "$pkg.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "请安装百度地图", Toast.LENGTH_SHORT).show()
    }

    private fun startTencentNav() {
        // 腾讯地图车机版包名列表
        val tencentPackages = arrayOf(
            "com.tencent.map",              // 腾讯地图
            "com.tencent.map.ms"            // 腾讯地图车机版
        )

        for (pkg in tencentPackages) {
            try {
                val intent = Intent().apply {
                    setClassName(pkg, "$pkg.MainActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        Toast.makeText(this, "请安装腾讯地图", Toast.LENGTH_SHORT).show()
    }

    private fun switchNavType() {
        when (currentNavType) {
            "amap" -> {
                currentNavType = "baidu"
                startBaiduNav()
            }
            "baidu" -> {
                currentNavType = "tencent"
                startTencentNav()
            }
            else -> {
                currentNavType = "amap"
                startAmapNav()
            }
        }

        val navName = when (currentNavType) {
            "baidu" -> "百度"
            "tencent" -> "腾讯"
            else -> "高德"
        }
        floatingView?.findViewById<TextView>(R.id.nav_switch)?.text = "$navName ▼"
    }

    private fun minimizeWindow() {
        isMinimized = true
        params?.width = 200
        params?.height = 120
        params?.x = 20
        params?.y = 100
        
        floatingView?.findViewById<View>(R.id.nav_container)?.visibility = View.GONE
        floatingView?.findViewById<View>(R.id.minimized_view)?.visibility = View.VISIBLE
        
        try {
            windowManager?.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
        
        // 点击恢复
        floatingView?.findViewById<View>(R.id.minimized_view)?.setOnClickListener {
            restoreWindow()
        }
    }

    private fun restoreWindow() {
        isMinimized = false
        val dm = resources.displayMetrics
        params?.width = (dm.widthPixels * 0.8).toInt()
        params?.height = (dm.heightPixels * 0.7).toInt()
        
        floatingView?.findViewById<View>(R.id.nav_container)?.visibility = View.VISIBLE
        floatingView?.findViewById<View>(R.id.minimized_view)?.visibility = View.GONE
        
        try {
            windowManager?.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
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
        isRunning = false
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
