package com.pandora.carlauncher.ui.activity

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R
import com.pandora.carlauncher.databinding.ActivityMainLauncherBinding
import com.pandora.carlauncher.modules.hvac.HvacControlFragment
import com.pandora.carlauncher.modules.media.MediaCenterFragment
import com.pandora.carlauncher.modules.navigation.NavigationFragment
import com.pandora.carlauncher.modules.settings.QuickSettingsFragment
import com.pandora.carlauncher.modules.appmanager.AppListFragment
import com.pandora.carlauncher.ui.fragment.DockFragment
import com.pandora.carlauncher.ui.fragment.StatusBarFragment
import kotlinx.coroutines.*

/**
 * 主桌面启动器Activity
 * 
 * 负责展示车机主界面，包含：
 * - 顶部状态栏（时间、信号、电量等）
 * - 中部主内容区（卡片式交互）
 * - 底部Dock导航区
 */
class MainLauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainLauncherActivity"
        
        // 权限请求码
        const val REQUEST_CODE_PERMISSIONS = 1001
        const val REQUEST_CODE_OVERLAY = 1002
        const val REQUEST_CODE_SETTINGS = 1003
        
        // 页面索引
        const val PAGE_HOME = 0
        const val PAGE_NAVIGATION = 1
        const val PAGE_MEDIA = 2
        const val PAGE_HVAC = 3
        const val PAGE_APPS = 4
    }
    
    private lateinit var binding: ActivityMainLauncherBinding
    private lateinit var application: PandaCarApplication
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // ViewPager适配器
    private lateinit var pagerAdapter: MainPagerAdapter
    
    // Dock Fragment引用
    private var dockFragment: DockFragment? = null
    
    // 权限Launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.i(TAG, "所有权限已授予")
            initializeComponents()
        } else {
            Log.w(TAG, "部分权限未授予")
            // 继续初始化，权限不足的功能会受限
            initializeComponents()
        }
    }
    
    // Overlay权限Launcher
    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Log.i(TAG, "悬浮窗权限已授予")
            startFloatingBallService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "========== 主桌面创建 ==========")
        
        // 初始化ViewBinding
        binding = ActivityMainLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 获取Application实例
        application = PandaCarApplication.getInstance()
        
        // 全屏显示
        setupFullScreenMode()
        
        // 检查并请求权限
        checkAndRequestPermissions()
    }
    
    /**
     * 设置全屏模式
     */
    private fun setupFullScreenMode() {
        // 隐藏状态栏和导航栏
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val controller = WindowInsetsControllerCompat(window, binding.root)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        // 保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 设置屏幕亮度
        val layoutParams = window.attributes
        layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
        window.attributes = layoutParams
    }
    
    /**
     * 检查并请求所需权限
     */
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_IMAGES
        )
        
        val notGranted = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (notGranted.isNotEmpty()) {
            permissionLauncher.launch(notGranted.toTypedArray())
        } else {
            initializeComponents()
        }
        
        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:$packageName")
            )
            overlayLauncher.launch(intent)
        }
    }
    
    /**
     * 初始化界面组件
     */
    private fun initializeComponents() {
        Log.d(TAG, "初始化界面组件...")
        
        // 初始化顶部状态栏
        setupStatusBar()
        
        // 初始化主内容ViewPager
        setupViewPager()
        
        // 初始化底部Dock
        setupDock()
        
        // 启动悬浮球服务
        startFloatingBallService()
        
        // 更新显示信息
        updateDisplayInfo()
        
        Log.d(TAG, "界面组件初始化完成")
    }
    
    /**
     * 设置顶部状态栏
     */
    private fun setupStatusBar() {
        // 状态栏Fragment已在布局中嵌入，这里获取引用并更新
        supportFragmentManager.beginTransaction()
            .replace(R.id.status_bar_container, StatusBarFragment())
            .commit()
    }
    
    /**
     * 设置主内容ViewPager
     */
    private fun setupViewPager() {
        pagerAdapter = MainPagerAdapter(this)
        binding.mainViewPager.apply {
            adapter = pagerAdapter
            offscreenPageLimit = 2
            orientation = ViewPager2.ORIENTATION_HORIZONTAL
            
            // 页面切换监听
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    Log.d(TAG, "页面切换到: $position")
                    updateDockSelection(position)
                }
            })
        }
        
        // 设置TabLayout与ViewPager联动
        TabLayoutMediator(binding.pageIndicator, binding.mainViewPager) { tab, position ->
            tab.text = getPageTitle(position)
        }.attach()
    }
    
    /**
     * 获取页面标题
     */
    private fun getPageTitle(position: Int): String {
        return when (position) {
            PAGE_HOME -> "主页"
            PAGE_NAVIGATION -> "导航"
            PAGE_MEDIA -> "媒体"
            PAGE_HVAC -> "空调"
            PAGE_APPS -> "应用"
            else -> ""
        }
    }
    
    /**
     * 设置底部Dock
     */
    private fun setupDock() {
        dockFragment = DockFragment()
        supportFragmentManager.beginTransaction()
            .replace(R.id.dock_container, dockFragment!!)
            .commit()
    }
    
    /**
     * 切换到指定页面
     */
    fun switchToPage(position: Int) {
        binding.mainViewPager.currentItem = position
    }
    
    /**
     * 更新Dock选中状态
     */
    private fun updateDockSelection(position: Int) {
        // 通过Dock Fragment更新选中状态
        dockFragment?.updateSelection(position)
    }
    
    /**
     * 启动悬浮球服务
     */
    private fun startFloatingBallService() {
        if (Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, 
                    com.pandora.carlauncher.modules.floatingball.FloatingBallService::class.java))
                Log.i(TAG, "悬浮球服务已启动")
            } catch (e: Exception) {
                Log.e(TAG, "悬浮球服务启动失败", e)
            }
        }
    }
    
    /**
     * 更新显示信息
     */
    private fun updateDisplayInfo() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        Log.i(TAG, "屏幕分辨率: ${metrics.widthPixels} x ${metrics.heightPixels}")
        Log.i(TAG, "屏幕密度: ${metrics.densityDpi} dpi")
        Log.i(TAG, "屏幕尺寸: ${metrics.xdpi} x ${metrics.ydpi} dpi")
    }
    
    /**
     * 处理返回键
     */
    @Deprecated("使用OnBackPressedDispatcher替代")
    override fun onBackPressed() {
        if (binding.mainViewPager.currentItem != PAGE_HOME) {
            binding.mainViewPager.currentItem = PAGE_HOME
        } else {
            super.onBackPressed()
        }
    }
    
    /**
     * 按下Home键
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Log.d(TAG, "用户按Home键")
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "主桌面恢复")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "主桌面暂停")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        Log.i(TAG, "主桌面销毁")
    }
    
    /**
     * ViewPager适配器
     */
    inner class MainPagerAdapter(activity: FragmentActivity) : 
        FragmentStateAdapter(activity) {
        
        override fun getItemCount(): Int = 5
        
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                PAGE_HOME -> QuickSettingsFragment()
                PAGE_NAVIGATION -> NavigationFragment()
                PAGE_MEDIA -> MediaCenterFragment()
                PAGE_HVAC -> HvacControlFragment()
                PAGE_APPS -> AppListFragment()
                else -> QuickSettingsFragment()
            }
        }
    }
}
