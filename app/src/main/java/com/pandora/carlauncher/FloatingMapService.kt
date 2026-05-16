package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 地图悬浮窗服务
 * 显示导航应用的小窗口，支持拖动
 */
class FloatingMapService : Service() {

    companion object {
        private const val TAG = "FloatingMap"
        const val ACTION_SHOW = "action_show"
        const val ACTION_HIDE = "action_hide"
        
        // 默认尺寸
        private const val DEFAULT_WIDTH = 600
        private const val DEFAULT_HEIGHT = 400
        private const val MIN_WIDTH = 320
        private const val MIN_HEIGHT = 200
        private const val MAX_WIDTH = 1200
        private const val MAX_HEIGHT = 800
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var currentWidth = DEFAULT_WIDTH
    private var currentHeight = DEFAULT_HEIGHT
    
    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                if (floatingView == null) {
                    createFloatingView()
                }
            }
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_map, null)

        // 设置 WindowManager 参数
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
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

        // 标题栏拖动
        val titleBar = rootView.findViewById<View>(R.id.map_title_bar)
        titleBar?.setOnTouchListener(object : View.OnTouchListener {
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
                        if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                            isDragging = true
                        }
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        windowManager?.updateViewLayout(floatingView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        return true
                    }
                }
                return false
            }
        })

        // 关闭按钮
        rootView.findViewById<ImageView>(R.id.map_btn_close)?.setOnClickListener {
            stopSelf()
        }

        // 打开地图应用按钮
        rootView.findViewById<Button>(R.id.map_btn_open)?.setOnClickListener {
            openMapApp()
        }

        // 缩放按钮
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        // 点击内容区域打开地图
        rootView.findViewById<View>(R.id.map_content)?.setOnClickListener {
            if (!isDragging) {
                openMapApp()
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
        windowManager?.updateViewLayout(floatingView, params)
    }

    private fun openMapApp() {
        // 尝试启动高德地图车机版
        val mapPackages = arrayOf(
            "com.autonavi.amapauto",
            "com.autonavi.minimap",
            "com.baidu.BaiduMap",
            "com.baidu.naviauto"
        )
        
        for (pkg in mapPackages) {
            try {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                // 尝试下一个
            }
        }
        
        Toast.makeText(this, "未找到地图应用", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
