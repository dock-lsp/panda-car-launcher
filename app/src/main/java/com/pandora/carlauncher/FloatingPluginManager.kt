package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.sqrt

/**
 * 悬浮插件管理器 - 单例模式
 * 统一管理所有悬浮插件的添加/移除、拖动、缩放、位置记忆
 */
class FloatingPluginManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FloatingPluginManager"
        private const val PREF_NAME = "floating_plugin_positions"

        @Volatile
        private var instance: FloatingPluginManager? = null

        fun getInstance(ctx: Context): FloatingPluginManager {
            return instance ?: synchronized(this) {
                instance ?: FloatingPluginManager(ctx.applicationContext).also { instance = it }
            }
        }
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val pluginViews = mutableMapOf<String, View>()
    private val pluginParams = mutableMapOf<String, WindowManager.LayoutParams>()
    private val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // ========== 权限检查 ==========

    fun hasPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !hasPermission()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${activity.packageName}")
            )
            activity.startActivity(intent)
        }
    }

    // ========== 插件显示/隐藏 ==========

    @SuppressLint("ClickableViewAccessibility")
    fun showPlugin(pluginId: String, view: View, width: Int, height: Int) {
        if (pluginViews.containsKey(pluginId)) {
            Log.w(TAG, "插件 $pluginId 已存在，先移除旧视图")
            removePlugin(pluginId)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 加载保存的位置
        val savedPos = loadPosition(pluginId)
        val dm = context.resources.displayMetrics
        val defaultX = (dm.widthPixels - width) / 2
        val defaultY = (dm.heightPixels - height) / 2

        val params = WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedPos.first.coerceIn(0, dm.widthPixels - width)
            y = savedPos.second.coerceIn(0, dm.heightPixels - height)
            if (x == 0 && y == 0) {
                x = defaultX
                y = defaultY
            }
        }

        try {
            windowManager.addView(view, params)
            pluginViews[pluginId] = view
            pluginParams[pluginId] = params
            Log.d(TAG, "插件 $pluginId 已显示 (${params.x}, ${params.y}) ${width}x${height}")
        } catch (e: Exception) {
            Log.e(TAG, "显示插件 $pluginId 失败", e)
        }
    }

    fun hidePlugin(pluginId: String) {
        val view = pluginViews[pluginId] ?: return
        val params = pluginParams[pluginId] ?: return
        try {
            view.visibility = View.GONE
            Log.d(TAG, "插件 $pluginId 已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏插件 $pluginId 失败", e)
        }
    }

    fun removePlugin(pluginId: String) {
        val view = pluginViews.remove(pluginId) ?: return
        pluginParams.remove(pluginId)
        try {
            windowManager.removeView(view)
            Log.d(TAG, "插件 $pluginId 已移除")
        } catch (e: Exception) {
            Log.e(TAG, "移除插件 $pluginId 失败", e)
        }
    }

    fun isPluginVisible(pluginId: String): Boolean {
        val view = pluginViews[pluginId] ?: return false
        return view.visibility == View.VISIBLE
    }

    // ========== 位置管理 ==========

    fun updatePluginPosition(pluginId: String, x: Int, y: Int) {
        val params = pluginParams[pluginId] ?: return
        val view = pluginViews[pluginId] ?: return
        params.x = x
        params.y = y
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "更新插件 $pluginId 位置失败", e)
        }
    }

    fun savePosition(pluginId: String, x: Int, y: Int) {
        prefs.edit()
            .putInt("${pluginId}_x", x)
            .putInt("${pluginId}_y", y)
            .apply()
    }

    fun loadPosition(pluginId: String): Pair<Int, Int> {
        val x = prefs.getInt("${pluginId}_x", 0)
        val y = prefs.getInt("${pluginId}_y", 0)
        return Pair(x, y)
    }

    // ========== 拖动逻辑 ==========

    fun setupDrag(view: View, pluginId: String, callback: DragCallback? = null) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val params = pluginParams[pluginId]
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
                        val newX = initialX + dx.toInt()
                        val newY = initialY + dy.toInt()
                        updatePluginPosition(pluginId, newX, newY)
                        callback?.onDrag(newX, newY)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val params = pluginParams[pluginId]
                        if (params != null) {
                            savePosition(pluginId, params.x, params.y)
                        }
                        callback?.onDragEnd(params?.x ?: 0, params?.y ?: 0)
                    }
                    isDragging
                }
                else -> false
            }
        }
    }

    // ========== 双指缩放逻辑 ==========

    fun setupPinchZoom(view: View, pluginId: String, callback: ZoomCallback? = null) {
        var initialDistance = 0f
        var initialWidth = 0
        var initialHeight = 0

        view.setOnTouchListener { _, event ->
            val params = pluginParams[pluginId] ?: return@setOnTouchListener false

            when (event.pointerCount) {
                1 -> {
                    // 单指事件交给拖动处理
                    false
                }
                2 -> {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_POINTER_DOWN -> {
                            initialDistance = getDistance(event)
                            initialWidth = params.width
                            initialHeight = params.height
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (initialDistance > 0) {
                                val newDistance = getDistance(event)
                                val scale = newDistance / initialDistance
                                val newWidth = (initialWidth * scale).toInt()
                                    .coerceIn(200, 1920)
                                val newHeight = (initialHeight * scale).toInt()
                                    .coerceIn(150, 1200)
                                params.width = newWidth
                                params.height = newHeight
                                try {
                                    windowManager.updateViewLayout(
                                        pluginViews[pluginId], params
                                    )
                                    callback?.onZoom(newWidth, newHeight, scale)
                                } catch (e: Exception) {
                                    Log.e(TAG, "缩放失败", e)
                                }
                            }
                            true
                        }
                        MotionEvent.ACTION_POINTER_UP -> {
                            initialDistance = 0f
                            true
                        }
                        else -> false
                    }
                }
                else -> false
            }
        }
    }

    private fun getDistance(event: MotionEvent): Float {
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }

    // ========== 回调接口 ==========

    interface DragCallback {
        fun onDrag(x: Int, y: Int)
        fun onDragEnd(x: Int, y: Int)
    }

    interface ZoomCallback {
        fun onZoom(width: Int, height: Int, scale: Float)
    }
}
