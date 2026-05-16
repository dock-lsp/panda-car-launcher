package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.*

/**
 * 悬浮地图服务
 * 
 * 实现方式：
 * 1. 优先通过广播协议打开高德悬浮版地图（com.autonavi.plus.openmap）
 * 2. 备选方案：WebView 嵌入高德地图 JS API 显示地图
 */
class FloatingMapService : Service() {

    companion object {
        private const val TAG = "FloatingMap"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_map"

        const val EXTRA_MAP_TYPE = "extra_map_type"
        const val EXTRA_MAP_PACKAGE = "extra_map_package"
        const val EXTRA_MAP_NAME = "extra_map_name"

        const val TYPE_NATIVE_FLOAT = "native_float"  // 原生悬浮版（广播协议）
        const val TYPE_WEBVIEW = "webview"            // WebView 嵌入

        // 高德悬浮版广播
        private const val ACTION_OPEN_MAP = "com.autonavi.plus.openmap"
        private const val ACTION_CLOSE_MAP = "com.autonavi.plus.closemap"

        // 悬浮窗尺寸
        private const val DEFAULT_WIDTH = 600
        private const val DEFAULT_HEIGHT = 400
        private const val MIN_WIDTH = 200
        private const val MIN_HEIGHT = 120
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1080
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private var tvTitle: TextView? = null
    private var tvStatus: TextView? = null

    private var currentWidth = DEFAULT_WIDTH
    private var currentHeight = DEFAULT_HEIGHT
    private var mapType: String? = null
    private var mapPackage: String? = null
    private var mapName: String? = null

    // 拖动
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
        intent?.let {
            mapType = it.getStringExtra(EXTRA_MAP_TYPE) ?: TYPE_WEBVIEW
            mapPackage = it.getStringExtra(EXTRA_MAP_PACKAGE)
            mapName = it.getStringExtra(EXTRA_MAP_NAME) ?: "导航"
        }

        // 启动前台通知
        startForeground(NOTIFICATION_ID, buildNotification())

        if (mapType == TYPE_NATIVE_FLOAT) {
            // 原生悬浮版：通过广播打开高德悬浮地图
            openNativeFloatingMap()
        } else {
            // WebView 模式：创建悬浮窗
            if (floatingView == null) {
                createFloatingWindow()
            }
        }

        return START_STICKY
    }

    /**
     * 方式1：通过广播协议打开高德悬浮版地图
     * 这是布丁桌面使用的方式，最干净
     */
    private fun openNativeFloatingMap() {
        try {
            val intent = Intent(ACTION_OPEN_MAP).apply {
                putExtra("x", 0)
                putExtra("y", 0)
                putExtra("w", 0)
                putExtra("h", 0)
            }
            sendBroadcast(intent)
            Log.d(TAG, "已发送高德悬浮版广播")
            Toast.makeText(this, "已启动${mapName}悬浮地图", Toast.LENGTH_SHORT).show()
            // 不需要悬浮窗，直接停止服务
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "广播打开悬浮地图失败", e)
            Toast.makeText(this, "打开悬浮地图失败，尝试WebView模式", Toast.LENGTH_SHORT).show()
            // 回退到 WebView 模式
            mapType = TYPE_WEBVIEW
            createFloatingWindow()
        }
    }

    /**
     * 方式2：WebView 嵌入地图
     */
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_map, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            currentWidth,
            currentHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
            return
        }

        val rootView = floatingView ?: return
        webView = rootView.findViewById(R.id.map_webview)
        tvTitle = rootView.findViewById(R.id.map_tv_title)
        tvStatus = rootView.findViewById(R.id.map_tv_status)

        tvTitle?.text = mapName
        tvStatus?.text = "正在加载地图..."

        setupWebView()
        setupDrag(rootView)
        setupButtons(rootView)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val wv = webView ?: return
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                tvStatus?.visibility = View.GONE
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                tvStatus?.text = "加载失败"
                tvStatus?.visibility = View.VISIBLE
            }
        }

        wv.webChromeClient = WebChromeClient()

        // 加载地图
        loadMapUrl()
    }

    private fun loadMapUrl() {
        val wv = webView ?: return
        when {
            // 高德地图
            mapPackage?.contains("autonavi") == true -> {
                wv.loadUrl("https://m.amap.com/navi/?dest=116.397428,39.90923&destName=目的地&hideRouteIcon=1&key=")
            }
            // 百度地图
            mapPackage?.contains("baidu") == true -> {
                wv.loadUrl("https://map.baidu.com/mobile/webapp/index/index/index.html")
            }
            // 腾讯地图
            mapPackage?.contains("tencent") == true -> {
                wv.loadUrl("https://apis.map.qq.com/tools/routeplan/?type=drive&to=北京&tocoord=39.916527,116.397128&referer=myapp")
            }
            // 默认高德
            else -> {
                wv.loadUrl("https://m.amap.com/navi/?dest=116.397428,39.90923&destName=目的地&hideRouteIcon=1&key=")
            }
        }
    }

    private fun setupDrag(rootView: View) {
        val titleBar = rootView.findViewById<View>(R.id.map_title_bar) ?: return
        titleBar.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
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
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        try { windowManager?.updateViewLayout(floatingView, params) } catch (_: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> return true
                }
                return false
            }
        })
    }

    private fun setupButtons(rootView: View) {
        // 关闭
        rootView.findViewById<ImageView>(R.id.map_btn_close)?.setOnClickListener {
            closeMap()
            stopSelf()
        }

        // 缩放
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        // 点击内容区域 - 如果是 WebView 模式则允许交互
        rootView.findViewById<View>(R.id.map_content)?.setOnClickListener {
            if (!isDragging && mapType == TYPE_WEBVIEW) {
                // 让 WebView 获取焦点
                params?.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                try { windowManager?.updateViewLayout(floatingView, params) } catch (_: Exception) {}
            }
        }
    }

    private fun resizeWindow(scale: Float) {
        val newWidth = (currentWidth * scale).toInt().coerceIn(MIN_WIDTH, MAX_WIDTH)
        val newHeight = (currentHeight * scale).toInt().coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        currentWidth = newWidth
        currentHeight = newHeight
        params?.width = newWidth
        params?.height = newHeight
        try { windowManager?.updateViewLayout(floatingView, params) } catch (_: Exception) {}
    }

    private fun closeMap() {
        // 如果是原生悬浮版，发送关闭广播
        if (mapType == TYPE_NATIVE_FLOAT) {
            try {
                sendBroadcast(Intent(ACTION_CLOSE_MAP))
            } catch (_: Exception) {}
        }
        // 销毁 WebView
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        webView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "悬浮地图", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮地图运行中")
                .setContentText(mapName ?: "导航")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮地图运行中")
                .setContentText(mapName ?: "导航")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeMap()
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
