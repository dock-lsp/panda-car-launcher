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
 * 悬浮地图服务 - WebView 嵌入地图
 * 
 * 实现方式：WebView 加载地图 H5 页面
 */
class FloatingMapService : Service() {

    companion object {
        private const val TAG = "FloatingMap"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "floating_map"

        const val EXTRA_MAP_PACKAGE = "extra_map_package"
        const val EXTRA_MAP_NAME = "extra_map_name"

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
        Log.d(TAG, "onStartCommand called")
        
        intent?.let {
            mapPackage = it.getStringExtra(EXTRA_MAP_PACKAGE)
            mapName = it.getStringExtra(EXTRA_MAP_NAME) ?: "导航"
            Log.d(TAG, "mapPackage=$mapPackage, mapName=$mapName")
        }

        // 启动前台通知
        startForeground(NOTIFICATION_ID, buildNotification())

        // 创建悬浮窗
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
            Log.d(TAG, "悬浮窗已添加")
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
        tvStatus?.visibility = View.VISIBLE

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
            allowContentAccess = true
            allowFileAccess = true
        }

        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                Log.d(TAG, "页面加载完成: $url")
                tvStatus?.visibility = View.GONE
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                Log.e(TAG, "页面加载错误: $error")
                tvStatus?.text = "加载失败，请检查网络"
                tvStatus?.visibility = View.VISIBLE
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                Log.e(TAG, "HTTP错误: ${errorResponse?.statusCode}")
            }
        }

        wv.webChromeClient = WebChromeClient()

        // 加载地图
        loadMapUrl()
    }

    private fun loadMapUrl() {
        val wv = webView ?: return
        val url = when {
            mapPackage?.contains("autonavi") == true -> {
                "https://m.amap.com/navi/"
            }
            mapPackage?.contains("baidu") == true -> {
                "https://map.baidu.com/mobile/webapp/index/index"
            }
            mapPackage?.contains("tencent") == true -> {
                "https://map.qq.com/m/"
            }
            else -> "https://m.amap.com/navi/"
        }
        Log.d(TAG, "加载地图URL: $url")
        wv.loadUrl(url)
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
            stopSelf()
        }

        // 缩放
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        // 刷新
        rootView.findViewById<ImageView>(R.id.map_btn_refresh)?.setOnClickListener {
            webView?.reload()
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
        Log.d(TAG, "onDestroy")
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        webView = null
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
