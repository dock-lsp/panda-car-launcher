package com.pandora.carlauncher

import android.app.Application
import android.car.Car
import android.car.CarVersion
import android.util.Log
import com.pandora.carlauncher.utils.PreferencesManager
import com.pandora.carlauncher.utils.CarConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 熊猫车机桌面应用类
 * 
 * 应用启动入口，负责全局初始化和状态管理
 */
class PandaCarApplication : Application() {

    companion object {
        private const val TAG = "PandaCarApplication"
        
        @Volatile
        private var instance: PandaCarApplication? = null
        
        fun getInstance(): PandaCarApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }
    
    // 应用级别协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 首选项管理器
    lateinit var preferencesManager: PreferencesManager
        private set
    
    // 车辆连接管理器
    lateinit var carConnectionManager: CarConnectionManager
        private set
    
    // Car服务实例
    private var _carService: Car? = null
    val carService: Car? get() = _carService
    
    // 驾驶状态标志
    var isDriving: Boolean = false
        private set
    
    // 屏幕状态标志
    var isScreenOn: Boolean = true
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.i(TAG, "========== 熊猫车机桌面启动 ==========")
        
        try {
            Log.i(TAG, "应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Log.i(TAG, "Android版本: ${android.os.Build.VERSION.RELEASE}")
        } catch (e: Exception) {
            Log.e(TAG, "获取版本信息失败", e)
        }
        
        try {
            Log.i(TAG, "CarLibrary版本: ${CarVersion.getCarLibraryVersion()}")
        } catch (e: Exception) {
            Log.w(TAG, "无法获取CarLibrary版本: ${e.message}")
        }
        
        // 在主线程同步初始化核心模块
        initializeModules()
        
        // 异步连接车辆服务
        connectToCarService()
        
        Log.i(TAG, "应用初始化完成")
    }
    
    /**
     * 初始化各功能模块（主线程同步执行）
     */
    private fun initializeModules() {
        try {
            // 初始化首选项管理器
            preferencesManager = PreferencesManager(this)
            Log.d(TAG, "首选项管理器初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "首选项管理器初始化失败", e)
        }
        
        try {
            // 初始化车辆连接管理器
            carConnectionManager = CarConnectionManager(this)
            Log.d(TAG, "车辆连接管理器初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "车辆连接管理器初始化失败", e)
        }
    }
    
    /**
     * 连接Android Automotive车辆服务（异步）
     */
    private fun connectToCarService() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // 检查Car服务是否支持
                val isSupported = try {
                    Car.isCarServiceSupported(this@PandaCarApplication)
                } catch (e: Exception) {
                    Log.w(TAG, "检查Car服务支持失败: ${e.message}")
                    false
                }
                
                if (!isSupported) {
                    Log.w(TAG, "当前设备不支持Car服务")
                    return@launch
                }
                
                // 创建Car连接
                try {
                    val car = Car.createCar(this@PandaCarApplication, object : Car.CarConnectionCallback {
                        override fun onConnected(car: Car) {
                            Log.i(TAG, "Car服务已连接")
                            _carService = car
                            try {
                                carConnectionManager.onCarConnected(car)
                            } catch (e: Exception) {
                                Log.e(TAG, "Car连接回调失败", e)
                            }
                        }
                        
                        override fun onDisconnected(car: Car) {
                            Log.i(TAG, "Car服务已断开")
                            _carService = null
                        }
                    })
                    _carService = car
                } catch (e: Exception) {
                    Log.e(TAG, "创建Car服务失败: ${e.message}")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Car服务连接失败", e)
            }
        }
    }
    
    /**
     * 更新驾驶状态
     */
    fun updateDrivingState(driving: Boolean) {
        isDriving = driving
        Log.d(TAG, "驾驶状态更新: ${if (driving) "行驶中" else "停车"}")
    }
    
    /**
     * 更新屏幕状态
     */
    fun updateScreenState(screenOn: Boolean) {
        isScreenOn = screenOn
        Log.d(TAG, "屏幕状态更新: ${if (screenOn) "亮屏" else "熄屏"}")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "应用终止")
        
        try {
            _carService?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "断开Car服务失败", e)
        }
        _carService = null
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "系统内存不足")
    }
    
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "内存Trim级别: $level")
    }
}
