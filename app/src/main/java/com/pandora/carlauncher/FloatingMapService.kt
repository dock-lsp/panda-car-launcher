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
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.*
import android.widget.*

/**
 * 悬浮地图服务 - 新版设计
 * 参考：分层悬浮架构，地图居中，信息卡片叠加
 */
class FloatingMapService : Service() {

    companion object {
        private const val TAG = "FloatingMap"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_map"

        const val EXTRA_MAP_PACKAGE = "extra_map_package"
        const val EXTRA_MAP_NAME = "extra_map_name"

        private const val DEFAULT_WIDTH = 800
        private const val DEFAULT_HEIGHT = 500
        private const val MIN_WIDTH = 400
        private const val MIN_HEIGHT = 250
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1200
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var webView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private var loadingContainer: View? = null
    private var tvStatus: TextView? = null
    private var navCard: View? = null

    private var currentWidth = DEFAULT_WIDTH
    private var currentHeight = DEFAULT_HEIGHT
    private var mapPackage: String? = null
    private var mapName: String? = null
    private var currentMapType = "amap" // amap, baidu, tencent

    // 拖动
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        intent?.let {
            mapPackage = it.getStringExtra(EXTRA_MAP_PACKAGE)
            mapName = it.getStringExtra(EXTRA_MAP_NAME) ?: "导航"
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (floatingView == null) {
            createFloatingWindow()
        }

        return START_STICKY
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        Log.d(TAG, "createFloatingWindow")
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_map, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 居中显示
        val dm = resources.displayMetrics
        val x = (dm.widthPixels - currentWidth) / 2
        val y = (dm.heightPixels - currentHeight) / 2

        params = WindowManager.LayoutParams(
            currentWidth,
            currentHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮窗已添加 ${currentWidth}x${currentHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
            return
        }

        val rootView = floatingView ?: return
        webView = rootView.findViewById(R.id.map_webview)
        loadingContainer = rootView.findViewById(R.id.loading_container)
        tvStatus = rootView.findViewById(R.id.tv_status)
        navCard = rootView.findViewById(R.id.map_nav_card)

        setupWebView()
        setupButtons(rootView)
        setupDrag()
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
            allowContentAccess = true
            allowFileAccess = true
            // 自适应屏幕
            layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "页面加载完成: $url")
                loadingContainer?.visibility = View.GONE
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.e(TAG, "页面加载错误: $error")
                tvStatus?.text = "加载失败"
            }
        }

        wv.webChromeClient = WebChromeClient()
        loadMapUrl(currentMapType)
    }

    private fun loadMapUrl(type: String) {
        val wv = webView ?: return
        val url = when (type) {
            "baidu" -> "https://map.baidu.com/mobile/webapp/index/index"
            "tencent" -> "https://map.qq.com/m/"
            else -> "https://m.amap.com/navi/"
        }
        Log.d(TAG, "加载地图: $url")
        wv.loadUrl(url)
        loadingContainer?.visibility = View.VISIBLE
        tvStatus?.text = "正在加载${when(type) {"baidu"->"百度" "tencent"->"腾讯" else->"高德"}}地图..."
    }

    private fun setupDrag() {
        // 拖动导航卡片移动整个窗口
        navCard?.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - initialTouchX
                        val dy = event.rawY - initialTouchY
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
        rootView.findViewById<ImageView>(R.id.btn_close)?.setOnClickListener {
            stopSelf()
        }

        // 缩放
        rootView.findViewById<ImageView>(R.id.btn_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }
        rootView.findViewById<ImageView>(R.id.btn_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        // 刷新
        rootView.findViewById<ImageView>(R.id.btn_refresh)?.setOnClickListener {
            webView?.reload()
        }

        // 地图类型切换
        rootView.findViewById<LinearLayout>(R.id.map_type_container)?.setOnClickListener {
            switchMapType("amap")
        }
        rootView.findViewById<LinearLayout>(R.id.map_type_baidu)?.setOnClickListener {
            switchMapType("baidu")
        }
        rootView.findViewById<LinearLayout>(R.id.map_type_tencent)?.setOnClickListener {
            switchMapType("tencent")
        }
    }

    private fun switchMapType(type: String) {
        if (currentMapType == type) return
        currentMapType = type
        loadMapUrl(type)
        
        // 更新按钮样式
        val rootView = floatingView ?: return
        rootView.findViewById<ImageView>(R.id.btn_map_amap)?.setBackgroundResource(
            if (type == "amap") R.drawable.bg_map_btn_selected else R.drawable.bg_map_btn
        )
        rootView.findViewById<ImageView>(R.id.btn_map_baidu)?.setBackgroundResource(
            if (type == "baidu") R.drawable.bg_map_btn_selected else R.drawable.bg_map_btn
        )
        rootView.findViewById<ImageView>(R.id.btn_map_tencent)?.setBackgroundResource(
            if (type == "tencent") R.drawable.bg_map_btn_selected else R.drawable.bg_map_btn
        )
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮地图", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮地图")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮地图")
                .setSmallIcon(R.drawable.ic_navigation)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
