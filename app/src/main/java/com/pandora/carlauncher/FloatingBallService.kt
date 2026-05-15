package com.pandora.carlauncher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * 悬浮球服务
 * 提供可拖拽的悬浮按钮，点击展开快捷功能菜单
 */
class FloatingBallService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBallView: View
    private lateinit var menuView: View
    private var params: WindowManager.LayoutParams? = null
    private var menuParams: WindowManager.LayoutParams? = null
    private var isMenuShowing = false
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private val CLICK_DRAG_THRESHOLD = 10

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingBall()
        createMenu()
    }

    private fun createFloatingBall() {
        floatingBallView = LayoutInflater.from(this).inflate(R.layout.layout_floating_ball, null)
        
        val ballIcon = floatingBallView.findViewById<ImageView>(R.id.iv_floating_ball)
        ballIcon.setImageResource(R.drawable.ic_apps)
        
        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }
        
        windowManager.addView(floatingBallView, params)
        
        setupTouchListener()
    }

    private fun setupTouchListener() {
        floatingBallView.setOnTouchListener { _, event ->
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
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(deltaX) > CLICK_DRAG_THRESHOLD || Math.abs(deltaY) > CLICK_DRAG_THRESHOLD) {
                        isDragging = true
                        params?.x = initialX + deltaX
                        params?.y = initialY + deltaY
                        windowManager.updateViewLayout(floatingBallView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleMenu()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun createMenu() {
        menuView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null)
        
        menuParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        
        // 设置菜单按钮点击事件
        menuView.findViewById<LinearLayout>(R.id.btn_menu_home)?.setOnClickListener {
            hideMenu()
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        
        menuView.findViewById<LinearLayout>(R.id.btn_menu_back)?.setOnClickListener {
            hideMenu()
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        
        menuView.findViewById<LinearLayout>(R.id.btn_menu_recent)?.setOnClickListener {
            hideMenu()
            performGlobalAction(GLOBAL_ACTION_RECENTS)
        }
        
        menuView.findViewById<LinearLayout>(R.id.btn_menu_music)?.setOnClickListener {
            hideMenu()
            openFirstAvailableMusic()
        }
        
        menuView.findViewById<LinearLayout>(R.id.btn_menu_nav)?.setOnClickListener {
            hideMenu()
            openFirstAvailableNav()
        }
        
        menuView.findViewById<LinearLayout>(R.id.btn_menu_close)?.setOnClickListener {
            hideMenu()
            stopSelf()
        }
    }

    private fun toggleMenu() {
        if (isMenuShowing) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    private fun showMenu() {
        if (!::menuView.isInitialized) return
        
        // 计算菜单位置（悬浮球下方）
        val ballX = params?.x ?: 0
        val ballY = (params?.y ?: 0) + floatingBallView.height
        
        menuParams?.x = ballX
        menuParams?.y = ballY
        
        try {
            windowManager.addView(menuView, menuParams)
            isMenuShowing = true
        } catch (e: Exception) {
            // 可能已经添加
        }
    }

    private fun hideMenu() {
        if (isMenuShowing && ::menuView.isInitialized) {
            try {
                windowManager.removeView(menuView)
            } catch (e: Exception) {
                // 可能已经移除
            }
            isMenuShowing = false
        }
    }

    private fun openFirstAvailableMusic() {
        val musicApps = AppRecognizer.getInstalledMusicApps(this)
        if (musicApps.isNotEmpty()) {
            val intent = packageManager.getLaunchIntentForPackage(musicApps[0].packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "未安装音乐应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFirstAvailableNav() {
        val navApps = AppRecognizer.getInstalledNavigationApps(this)
        if (navApps.isNotEmpty()) {
            val intent = packageManager.getLaunchIntentForPackage(navApps[0].packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } else {
            Toast.makeText(this, "未安装导航应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun performGlobalAction(action: Int) {
        // 使用辅助功能执行全局操作
        val intent = Intent("com.pandora.carlauncher.ACTION_GLOBAL").apply {
            putExtra("action", action)
        }
        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        hideMenu()
        if (::floatingBallView.isInitialized) {
            try {
                windowManager.removeView(floatingBallView)
            } catch (e: Exception) {
                // 忽略
            }
        }
    }

    companion object {
        fun isRunning(): Boolean {
            return false // 需要实际检测
        }
    }
}
