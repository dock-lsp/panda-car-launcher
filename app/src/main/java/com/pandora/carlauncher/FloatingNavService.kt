package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
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
 * - 前台 Service 保活
 * - 通过广播协议控制高德悬浮版
 * - 支持拖动、缩放
 */
class FloatingNavService : Service() {

    companion object {
        private const val TAG = "FloatingNavService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "floating_nav_service"

        const val ACTION_START = "com.pandora.carlauncher.FloatingNavService.START"
        const val ACTION_STOP = "com.pandora.carlauncher.FloatingNavService.STOP"

        private const val PLUGIN_ID = "floating_nav"
        private const val DEFAULT_WIDTH = 300
        private const val DEFAULT_HEIGHT = 160
        private const val MIN_WIDTH = 200
        private const val MIN_HEIGHT = 120
        private const val MAX_WIDTH = 600
        private const val MAX_HEIGHT = 400

        // 高德悬浮版广播 Action
        private const val ACTION_AMAP_OPEN = "com.autonavi.plus.openmap"
        private const val ACTION_AMAP_CLOSE = "com.autonavi.plus.closemap"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var pluginManager: FloatingPluginManager? = null

    private var currentWidth = DEFAULT_WIDTH
    private var currentHeight = DEFAULT_HEIGHT

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // 缩放状态
    private var initialPinchDistance = 0f
    private var initialPinchWidth = 0
    private var initialPinchHeight = 0

    private var isNavActive = false

    private val navReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // 监听高德导航状态变化
            updateNavStatus()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        pluginManager = FloatingPluginManager.getInstance(this)
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
        }

        // 注册广播监听导航状态
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_AMAP_OPEN)
                addAction(ACTION_AMAP_CLOSE)
                addAction("android.intent.action.PACKAGE_ADDED")
                addAction("android.intent.action.PACKAGE_REMOVED")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(navReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(navReceiver, filter)
            }
        } catch (e: Exception) {
            Log.e(TAG, "注册广播接收器失败", e)
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
        val savedPos = pluginManager?.loadPosition(PLUGIN_ID) ?: Pair(0, 0)
        val dm = resources.displayMetrics
        val defaultX = (dm.widthPixels - currentWidth) / 2
        val defaultY = 80

        params = WindowManager.LayoutParams(
            currentWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedPos.first != 0) savedPos.first else defaultX
            y = if (savedPos.second != 0) savedPos.second else defaultY
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
        setupDragAndZoom()
        updateNavStatus()
    }

    private fun setupButtons() {
        val rootView = floatingView ?: return

        // 关闭悬浮窗
        rootView.findViewById<ImageView>(R.id.floating_nav_close)?.setOnClickListener {
            stopSelf()
        }

        // 放大
        rootView.findViewById<ImageView>(R.id.floating_nav_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }

        // 缩小
        rootView.findViewById<ImageView>(R.id.floating_nav_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        // 打开导航
        rootView.findViewById<Button>(R.id.floating_nav_open)?.setOnClickListener {
            openNavigation()
        }

        // 关闭导航
        rootView.findViewById<Button>(R.id.floating_nav_close_nav)?.setOnClickListener {
            closeNavigation()
        }
    }

    private fun openNavigation() {
        // 尝试通过广播打开高德悬浮版
        try {
            val intent = Intent(ACTION_AMAP_OPEN)
            intent.setPackage("com.autonavi.amapauto")
            sendBroadcast(intent)
            isNavActive = true
            updateNavStatus()
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
                    isNavActive = true
                    updateNavStatus()
                    Toast.makeText(this, "已启动高德地图", Toast.LENGTH_SHORT).show()
                } else {
                    // 尝试手机版高德
                    val phoneIntent = packageManager.getLaunchIntentForPackage("com.autonavi.minimap")
                    if (phoneIntent != null) {
                        phoneIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(phoneIntent)
                        isNavActive = true
                        updateNavStatus()
                        Toast.makeText(this, "已启动高德地图(手机版)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "未检测到高德地图，请先安装", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e2: Exception) {
                Log.e(TAG, "启动高德地图失败", e2)
                Toast.makeText(this, "打开导航失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun closeNavigation() {
        try {
            val intent = Intent(ACTION_AMAP_CLOSE)
            intent.setPackage("com.autonavi.amapauto")
            sendBroadcast(intent)
            isNavActive = false
            updateNavStatus()
            Toast.makeText(this, "已关闭导航", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "关闭导航失败", e)
            isNavActive = false
            updateNavStatus()
        }
    }

    private fun updateNavStatus() {
        val rootView = floatingView ?: return
        val tvStatus = rootView.findViewById<TextView>(R.id.floating_nav_status)
        val tvTitle = rootView.findViewById<TextView>(R.id.floating_nav_title)

        if (isNavActive) {
            tvStatus?.text = "导航运行中"
            tvStatus?.setTextColor(getColor(R.color.primary))
            tvTitle?.text = "导航 - 运行中"
        } else {
            tvStatus?.text = "点击打开导航"
            tvStatus?.setTextColor(getColor(R.color.text_secondary))
            tvTitle?.text = "导航"
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragAndZoom() {
        val rootView = floatingView ?: return

        rootView.setOnTouchListener { _, event ->
            when (event.pointerCount) {
                1 -> handleDrag(event)
                2 -> handlePinchZoom(event)
                else -> false
            }
        }
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params?.x ?: 0
                initialY = params?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isDragging = false
                return true
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
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    params?.let { p ->
                        pluginManager?.savePosition(PLUGIN_ID, p.x, p.y)
                    }
                }
                return isDragging
            }
        }
        return false
    }

    private fun handlePinchZoom(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialPinchDistance = getPinchDistance(event)
                    initialPinchWidth = currentWidth
                    initialPinchHeight = currentHeight
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 2 && initialPinchDistance > 0) {
                    val newDistance = getPinchDistance(event)
                    val scale = newDistance / initialPinchDistance
                    val newWidth = (initialPinchWidth * scale).toInt().coerceIn(MIN_WIDTH, MAX_WIDTH)
                    val newHeight = (initialPinchHeight * scale).toInt().coerceIn(MIN_HEIGHT, MAX_HEIGHT)
                    currentWidth = newWidth
                    currentHeight = newHeight
                    params?.width = newWidth
                    try {
                        windowManager?.updateViewLayout(floatingView, params)
                    } catch (_: Exception) {}
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                initialPinchDistance = 0f
                return true
            }
        }
        return false
    }

    private fun getPinchDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun resizeWindow(scale: Float) {
        val newWidth = (currentWidth * scale).toInt().coerceIn(MIN_WIDTH, MAX_WIDTH)
        currentWidth = newWidth
        params?.width = newWidth
        try {
            windowManager?.updateViewLayout(floatingView, params)
        } catch (_: Exception) {}
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
        try {
            unregisterReceiver(navReceiver)
        } catch (_: Exception) {}

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
