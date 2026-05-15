package com.pandora.carlauncher

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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

    companion object {
        const val PREF_FLOAT_BALL = "float_ball_pref"
        const val KEY_FLOAT_BALL_COLOR = "float_ball_color"
        const val KEY_FLOAT_BALL_SIZE = "float_ball_size"
        const val KEY_FLOAT_BALL_ALPHA = "float_ball_alpha"
        const val KEY_FLOAT_BALL_FUNC_HOME = "float_ball_func_home"
        const val KEY_FLOAT_BALL_FUNC_BACK = "float_ball_func_back"
        const val KEY_FLOAT_BALL_FUNC_RECENT = "float_ball_func_recent"
        const val KEY_FLOAT_BALL_FUNC_MUSIC = "float_ball_func_music"
        const val KEY_FLOAT_BALL_FUNC_NAV = "float_ball_func_nav"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var floatingBallView: View
    private lateinit var menuView: View
    private lateinit var prefs: SharedPreferences
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
        prefs = getSharedPreferences(PREF_FLOAT_BALL, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createFloatingBall()
        createMenu()
    }

    private fun createFloatingBall() {
        floatingBallView = LayoutInflater.from(this).inflate(R.layout.layout_floating_ball, null)
        
        val ballIcon = floatingBallView.findViewById<ImageView>(R.id.iv_floating_ball)
        ballIcon.setImageResource(R.drawable.ic_apps)
        
        // 读取设置
        applySettings()
        
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

    private fun applySettings() {
        // 应用大小设置
        val size = prefs.getString(KEY_FLOAT_BALL_SIZE, "medium") ?: "medium"
        val sizeDp = when (size) {
            "small" -> 40
            "large" -> 72
            else -> 56
        }
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        floatingBallView.layoutParams?.let {
            it.width = sizePx
            it.height = sizePx
            floatingBallView.layoutParams = it
        }

        // 应用透明度设置
        val alpha = prefs.getInt(KEY_FLOAT_BALL_ALPHA, 100) / 100f
        floatingBallView.alpha = alpha

        // 应用颜色设置
        val color = prefs.getString(KEY_FLOAT_BALL_COLOR, "cyan") ?: "cyan"
        val bgRes = when (color) {
            "blue" -> R.drawable.bg_icon_blue
            "green" -> R.drawable.bg_icon_green
            "orange" -> R.drawable.bg_icon_orange
            else -> R.drawable.bg_floating_ball
        }
        floatingBallView.setBackgroundResource(bgRes)
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
        
        // 根据设置显示/隐藏功能按钮
        setupMenuButtons()
    }

    private fun setupMenuButtons() {
        // 桌面
        menuView.findViewById<LinearLayout>(R.id.btn_menu_home)?.apply {
            visibility = if (prefs.getBoolean(KEY_FLOAT_BALL_FUNC_HOME, true)) View.VISIBLE else View.GONE
            setOnClickListener {
                hideMenu()
                startActivity(Intent(this@FloatingBallService, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
        
        // 返回
        menuView.findViewById<LinearLayout>(R.id.btn_menu_back)?.apply {
            visibility = if (prefs.getBoolean(KEY_FLOAT_BALL_FUNC_BACK, true)) View.VISIBLE else View.GONE
            setOnClickListener {
                hideMenu()
                sendBroadcast(Intent("com.pandora.carlauncher.ACTION_BACK"))
            }
        }
        
        // 最近任务
        menuView.findViewById<LinearLayout>(R.id.btn_menu_recent)?.apply {
            visibility = if (prefs.getBoolean(KEY_FLOAT_BALL_FUNC_RECENT, true)) View.VISIBLE else View.GONE
            setOnClickListener {
                hideMenu()
                sendBroadcast(Intent("com.pandora.carlauncher.ACTION_RECENTS"))
            }
        }
        
        // 音乐
        menuView.findViewById<LinearLayout>(R.id.btn_menu_music)?.apply {
            visibility = if (prefs.getBoolean(KEY_FLOAT_BALL_FUNC_MUSIC, true)) View.VISIBLE else View.GONE
            setOnClickListener {
                hideMenu()
                openFirstAvailableMusic()
            }
        }
        
        // 导航
        menuView.findViewById<LinearLayout>(R.id.btn_menu_nav)?.apply {
            visibility = if (prefs.getBoolean(KEY_FLOAT_BALL_FUNC_NAV, true)) View.VISIBLE else View.GONE
            setOnClickListener {
                hideMenu()
                openFirstAvailableNav()
            }
        }
        
        // 关闭
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
        
        // 重新读取设置并应用（可能设置已更改）
        applySettings()
        setupMenuButtons()
        
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
}
