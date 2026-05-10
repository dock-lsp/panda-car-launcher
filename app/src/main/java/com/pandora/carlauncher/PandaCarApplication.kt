package com.pandora.carlauncher

import android.app.Application
import android.car.Car
import android.car.CarVersion
import android.content.Context
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
        private lateinit var instance: PandaCarApplication
        
        fun getInstance(): PandaCarApplication = instance
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
    
    fun setCarService(car: Car?) {
        _carService = car
    }
    
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
        Log.i(TAG, "应用版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Log.i(TAG, "Android版本: ${android.os.Build.VERSION.RELEASE}")
        Log.i(TAG, "CarLibrary版本: ${CarVersion.getCarLibraryVersion()}")
        
        // 初始化各模块
        initializeModules()
        
        // 连接车辆服务
        connectToCarService()
        
        Log.i(TAG, "应用初始化完成")
    }
    
    /**
     * 初始化各功能模块
     */
    private fun initializeModules() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                // 初始化首选项管理器
                preferencesManager = PreferencesManager(this@PandaCarApplication)
                Log.d(TAG, "首选项管理器初始化完成")
                
                // 初始化车辆连接管理器
                carConnectionManager = CarConnectionManager(this@PandaCarApplication)
                Log.d(TAG, "车辆连接管理器初始化完成")
                
            } catch (e: Exception) {
                Log.e(TAG, "模块初始化失败", e)
            }
        }
    }
    
    /**
     * 连接Android Automotive车辆服务
     */
    private fun connectToCarService() {
        applicationScope.launch(Dispatchers.IO) {
            try {
                if (!Car.isCarServiceSupported(this@PandaCarApplication)) {
                    Log.w(TAG, "当前设备不支持Car服务")
                    return@launch
                }
                
                carService = Car.createCar(this@PandaCarApplication, object : Car.CarConnectionCallback {
                    override fun onConnected(car: Car) {
                        Log.i(TAG, "Car服务已连接")
                        carConnectionManager.onCarConnected(car)
                    }
                    
                    override fun onDisconnected(car: Car) {
                        Log.i(TAG, "Car服务已断开")
                    }
                })
                
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
        
        // 根据驾驶状态调整UI限制
        if (driving) {
            enableDrivingMode()
        } else {
            disableDrivingMode()
        }
    }
    
    /**
     * 更新屏幕状态
     */
    fun updateScreenState(screenOn: Boolean) {
        isScreenOn = screenOn
        Log.d(TAG, "屏幕状态更新: ${if (screenOn) "亮屏" else "熄屏"}")
    }
    
    /**
     * 启用驾驶模式 - 限制某些功能
     */
    private fun enableDrivingMode() {
        Log.d(TAG, "启用驾驶模式")
        // 通知各个模块进入驾驶模式
        // 限制视频播放、复杂操作等
    }
    
    /**
     * 禁用驾驶模式 - 恢复全部功能
     */
    private fun disableDrivingMode() {
        Log.d(TAG, "禁用驾驶模式")
        // 恢复所有功能
    }
    
    override fun onTerminate() {
        super.onTerminate()
        Log.i(TAG, "应用终止")
        
        // 断开Car服务
        carService?.disconnect()
        carService = null
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
