package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 真正的悬浮导航窗口
 * 使用 WindowManager 悬浮在所有应用之上
 * 显示真实导航信息
 */
class FloatingNavWindow(private val context: Context) {

    companion object {
        private const val TAG = "FloatingNavWindow"
        @Volatile var isShowing = false
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    // 拖动
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    init {
        // 注册导航信息回调
        NavInfoReceiver.onNavInfoUpdate = {
            handler.post { updateNavInfo() }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (floatingView != null) return

        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.layout_floating_nav_window, null)

        val dm = context.resources.displayMetrics
        val width = (dm.widthPixels * 0.22).toInt() // 22% 屏幕宽
        val height = (dm.heightPixels * 0.35).toInt() // 35% 屏幕高

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
            x = 20
            y = (dm.heightPixels - height) / 2
        }

        try {
            windowManager.addView(floatingView, params)
            isShowing = true
            Log.d(TAG, "悬浮导航窗口已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗口失败", e)
            return
        }

        // 拖动
        floatingView?.setOnTouchListener { _, event ->
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
                    try { windowManager.updateViewLayout(floatingView, params) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        // 关闭按钮
        floatingView?.findViewById<ImageView>(R.id.float_nav_close)?.setOnClickListener {
            hide()
        }

        // 打开导航
        floatingView?.findViewById<Button>(R.id.float_nav_open)?.setOnClickListener {
            openMapApp()
        }

        // 初始更新导航信息
        updateNavInfo()
        
        // 开始定时刷新
        startRefresh()
    }

    fun hide() {
        stopRefresh()
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
        isShowing = false
        Log.d(TAG, "悬浮导航窗口已隐藏")
    }

    fun updateStatus(remain: String, road: String) {
        floatingView?.findViewById<TextView>(R.id.float_nav_remain)?.text = remain
        floatingView?.findViewById<TextView>(R.id.float_nav_road)?.text = road
    }

    /**
     * 更新导航信息
     */
    fun updateNavInfo() {
        if (NavInfoReceiver.isNavigating) {
            val remain = if (NavInfoReceiver.remainDistance.isNotEmpty()) {
                "${NavInfoReceiver.remainDistance} | ${NavInfoReceiver.remainTime}"
            } else {
                "导航运行中"
            }
            val road = if (NavInfoReceiver.currentRoad.isNotEmpty()) {
                NavInfoReceiver.currentRoad
            } else {
                "点击打开导航"
            }
            updateStatus(remain, road)
            
            // 更新服务区信息
            floatingView?.findViewById<TextView>(R.id.float_nav_service)?.text = 
                if (NavInfoReceiver.serviceArea.isNotEmpty()) "下一服务区: ${NavInfoReceiver.serviceArea}" else ""
            
            // 更新下一道路信息
            floatingView?.findViewById<TextView>(R.id.float_nav_next_road)?.text = 
                if (NavInfoReceiver.nextRoad.isNotEmpty()) {
                    val distance = if (NavInfoReceiver.nextDistance.isNotEmpty()) "(${NavInfoReceiver.nextDistance})" else ""
                    "下一: ${NavInfoReceiver.nextRoad} $distance"
                } else ""
        } else {
            updateStatus("未在导航", "点击开始导航")
            floatingView?.findViewById<TextView>(R.id.float_nav_service)?.text = ""
            floatingView?.findViewById<TextView>(R.id.float_nav_next_road)?.text = ""
        }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateNavInfo()
            handler.postDelayed(this, 2000) // 每2秒刷新一次
        }
    }

    private fun startRefresh() {
        handler.post(refreshRunnable)
    }

    private fun stopRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun openMapApp() {
        val packages = arrayOf("com.autonavi.amapauto", "com.autonavi.minimap", "com.baidu.BaiduMap")
        for (pkg in packages) {
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return
                }
            } catch (_: Exception) {}
        }
        Toast.makeText(context, "未找到地图应用", Toast.LENGTH_SHORT).show()
    }
}
