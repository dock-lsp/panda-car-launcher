package com.pandora.carlauncher.modules.floatingball

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.view.GestureDetectorCompat
import com.pandora.carlauncher.R
import com.pandora.carlauncher.modules.voiceassistant.VoiceAssistantService
import com.pandora.carlauncher.ui.activity.MainLauncherActivity
import kotlin.math.abs

/**
 * 悬浮球服务
 * 
 * 功能：
 * - 悬浮球显示和拖拽
 * - 贴边吸附效果
 * - 点击弹出快捷菜单
 * - 快捷操作（返回、主页、语音、截图等）
 */
class FloatingBallService : android.app.Service() {

    companion object {
        private const val TAG = "FloatingBallService"
        
        // 悬浮球大小
        private const val BALL_SIZE = 60
        private const val MENU_SIZE = 50
        private const val PADDING = 16
        
        // 吸附边距
        private const val EDGE_MARGIN = 20
        
        // 移动阈值
        private const val MOVE_THRESHOLD = 10
    }

    // 窗口管理器
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    
    // 悬浮球视图
    private lateinit var floatingBall: View
    private lateinit var menuView: View
    
    // 手势检测
    private lateinit var gestureDetector: GestureDetectorCompat
    
    // 状态标志
    private var isMenuVisible = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isMoving = false
    
    // Handler
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        
        initWindowManager()
        initGestureDetector()
        createFloatingBall()
        createMenuView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        removeViews()
    }

    /**
     * 初始化窗口管理器
     */
    private fun initWindowManager() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            width = dp2px(BALL_SIZE)
            height = dp2px(BALL_SIZE)
            gravity = Gravity.TOP or Gravity.START
            x = getScreenWidth() - dp2px(BALL_SIZE + EDGE_MARGIN)
            y = getScreenHeight() / 2
        }
    }

    /**
     * 初始化手势检测
     */
    private fun initGestureDetector() {
        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isMoving) {
                    toggleMenu()
                }
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 双击打开语音助手
                startVoiceAssistant()
                return true
            }
            
            override fun onDown(e: MotionEvent): Boolean {
                isMoving = false
                return true
            }
            
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (abs(e2.rawX - initialTouchX) > MOVE_THRESHOLD || 
                    abs(e2.rawY - initialTouchY) > MOVE_THRESHOLD) {
                    isMoving = true
                    hideMenu()
                }
                return false
            }
        })
    }

    /**
     * 创建悬浮球
     */
    private fun createFloatingBall() {
        floatingBall = View.inflate(this, R.layout.view_floating_ball, null)!!
        
        // 设置触摸监听
        floatingBall.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (isMoving) {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        
                        layoutParams.x = initialX + deltaX
                        layoutParams.y = initialY + deltaY
                        
                        windowManager.updateViewLayout(floatingBall, layoutParams)
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    if (isMoving) {
                        // 吸附到边缘
                       吸附ToEdge()
                    }
                    true
                }
                
                else -> false
            }
        }
        
        // 添加到窗口
        windowManager.addView(floatingBall, layoutParams)
    }

    /**
     * 创建菜单视图
     */
    private fun createMenuView() {
        menuView = View.inflate(this, R.layout.view_floating_menu, null)!!
        
        // 菜单位置参数
        val menuParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        
        // 设置菜单项点击事件
        menuView.findViewById<View>(R.id.menu_home)?.setOnClickListener {
            openHome()
            hideMenu()
        }
        
        menuView.findViewById<View>(R.id.menu_back)?.setOnClickListener {
            simulateBack()
            hideMenu()
        }
        
        menuView.findViewById<View>(R.id.menu_voice)?.setOnClickListener {
            startVoiceAssistant()
            hideMenu()
        }
        
        menuView.findViewById<View>(R.id.menu_screenshot)?.setOnClickListener {
            takeScreenshot()
            hideMenu()
        }
        
        menuView.findViewById<View>(R.id.menu_close)?.setOnClickListener {
            hideMenu()
            hideBall()
        }
        
        // 菜单默认隐藏
        menuView.visibility = View.GONE
        windowManager.addView(menuView, menuParams)
    }

    /**
     * 切换菜单显示
     */
    private fun toggleMenu() {
        if (isMenuVisible) {
            hideMenu()
        } else {
            showMenu()
        }
    }

    /**
     * 显示菜单
     */
    private fun showMenu() {
        isMenuVisible = true
        updateMenuPosition()
        menuView.visibility = View.VISIBLE
        menuView.alpha = 0f
        menuView.animate()
            .alpha(1f)
            .setDuration(200)
            .start()
    }

    /**
     * 隐藏菜单
     */
    private fun hideMenu() {
        if (!isMenuVisible) return
        
        isMenuVisible = false
        menuView.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                menuView.visibility = View.GONE
            }
            .start()
    }

    /**
     * 更新菜单位置
     */
    private fun updateMenuPosition() {
        val menuLayoutParams = menuView.layoutParams as WindowManager.LayoutParams
        
        // 根据悬浮球位置调整菜单位置
        val ballCenterX = layoutParams.x + dp2px(BALL_SIZE) / 2
        val ballCenterY = layoutParams.y + dp2px(BALL_SIZE) / 2
        
        // 菜单显示在悬浮球旁边
        val screenWidth = getScreenWidth()
        val menuWidth = dp2px(300) // 估算菜单宽度
        
        if (ballCenterX > screenWidth / 2) {
            // 悬浮球在右侧，菜单显示在左侧
            menuLayoutParams.x = layoutParams.x - menuWidth - dp2px(10)
        } else {
            // 悬浮球在左侧，菜单显示在右侧
            menuLayoutParams.x = layoutParams.x + dp2px(BALL_SIZE) + dp2px(10)
        }
        
        menuLayoutParams.y = layoutParams.y - dp2px(20)
        
        windowManager.updateViewLayout(menuView, menuLayoutParams)
    }

    /**
     * 吸附到边缘
     */
    private fun 吸附ToEdge() {
        val screenWidth = getScreenWidth()
        val targetX: Int
        
        // 根据当前位置判断吸附到哪边
        if (layoutParams.x > screenWidth / 2) {
            targetX = screenWidth - dp2px(BALL_SIZE) - EDGE_MARGIN
        } else {
            targetX = EDGE_MARGIN
        }
        
        // 动画移动
        val startX = layoutParams.x
        ValueAnimator.ofInt(startX, targetX).apply {
            duration = 200
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { animation ->
                layoutParams.x = animation.animatedValue as Int
                windowManager.updateViewLayout(floatingBall, layoutParams)
            }
            start()
        }
    }

    /**
     * 隐藏悬浮球
     */
    private fun hideBall() {
        floatingBall.animate()
            .scaleX(0f)
            .scaleY(0f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                floatingBall.visibility = View.GONE
            }
            .start()
        
        // 延迟后重新显示
        handler.postDelayed({
            floatingBall.visibility = View.VISIBLE
            floatingBall.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(200)
                .start()
        }, 3000)
    }

    /**
     * 打开主页
     */
    private fun openHome() {
        val intent = Intent(this, MainLauncherActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * 模拟返回键
     */
    private fun simulateBack() {
        try {
            val keyEvent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            sendBroadcast(keyEvent)
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 打开最近任务
     */
    private fun openRecentApps() {
        try {
            val intent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            intent.putExtra("recentapps", true)
            sendBroadcast(intent)
        } catch (e: Exception) {
            // 无法直接打开最近任务，需要系统权限
            Toast.makeText(this, "无法打开最近任务", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 启动语音助手
     */
    private fun startVoiceAssistant() {
        val intent = Intent(this, VoiceAssistantService::class.java)
        startService(intent)
    }

    /**
     * 截图
     */
    private fun takeScreenshot() {
        try {
            // 截图需要系统权限，这里仅作示意
            Toast.makeText(this, "截图功能需要系统权限", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 移除所有视图
     */
    private fun removeViews() {
        try {
            if (::floatingBall.isInitialized) {
                windowManager.removeView(floatingBall)
            }
            if (::menuView.isInitialized) {
                windowManager.removeView(menuView)
            }
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * dp转px
     */
    private fun dp2px(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    /**
     * 获取屏幕宽度
     */
    private fun getScreenWidth(): Int {
        return resources.displayMetrics.widthPixels
    }

    /**
     * 获取屏幕高度
     */
    private fun getScreenHeight(): Int {
        return resources.displayMetrics.heightPixels
    }
}
