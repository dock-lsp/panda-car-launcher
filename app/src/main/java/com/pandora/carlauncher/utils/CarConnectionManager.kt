package com.pandora.carlauncher.utils

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyManager
import android.car.hardware.hvac.CarClimateManager
import android.car.hardware.media.CarAudioManager
import android.car.hardware.navigation.CarNavigationManager
import android.car.hardware.speed.CarSpeedManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 车辆连接管理器
 * 
 * 管理与Android Automotive服务的连接
 */
class CarConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "CarConnectionManager"
    }

    // Car服务实例
    private var car: Car? = null
    
    // 各个管理器
    private var carPropertyManager: CarPropertyManager? = null
    private var carClimateManager: CarClimateManager? = null
    private var carAudioManager: CarAudioManager? = null
    private var carNavigationManager: CarNavigationManager? = null
    private var carSpeedManager: CarSpeedManager? = null
    
    // 连接状态
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected
    
    // 车辆速度
    private val _vehicleSpeed = MutableStateFlow(0f)
    val vehicleSpeed: StateFlow<Float> = _vehicleSpeed
    
    // Handler
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 连接Car服务
     */
    fun connect() {
        try {
            if (!Car.isCarServiceSupported(context)) {
                Log.w(TAG, "当前设备不支持Car服务")
                return
            }
            
            car = Car.createCar(context, object : Car.CarConnectionCallback {
                override fun onConnected(carInstance: Car) {
                    Log.i(TAG, "Car服务连接状态: ${carInstance.isConnected}")
                    if (carInstance.isConnected) {
                        onConnected(carInstance)
                    }
                }
                
                override fun onDisconnected(carInstance: Car) {
                    onDisconnected()
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Car服务连接失败", e)
        }
    }
    
    /**
     * Car服务连接成功回调（从 PandaCarApplication 调用）
     */
    fun onCarConnected(carInstance: Car) {
        onConnected(carInstance)
    }

    /**
     * Car服务连接成功
     */
    private fun onConnected(carInstance: Car) {
        _isConnected.value = true
        Log.i(TAG, "Car服务连接成功")
        
        // 获取各个管理器
        try {
            carPropertyManager = carInstance.getCarManager(CarPropertyManager::class.java) 
                as? CarPropertyManager
            Log.d(TAG, "CarPropertyManager已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取CarPropertyManager失败", e)
        }
        
        try {
            carClimateManager = carInstance.getCarManager(CarClimateManager::class.java) 
                as? CarClimateManager
            Log.d(TAG, "CarClimateManager已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取CarClimateManager失败", e)
        }
        
        try {
            carAudioManager = carInstance.getCarManager(CarAudioManager::class.java) 
                as? CarAudioManager
            Log.d(TAG, "CarAudioManager已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取CarAudioManager失败", e)
        }
        
        try {
            carNavigationManager = carInstance.getCarManager(CarNavigationManager::class.java) 
                as? CarNavigationManager
            Log.d(TAG, "CarNavigationManager已获取")
        } catch (e: Exception) {
            Log.e(TAG, "获取CarNavigationManager失败", e)
        }
        
        try {
            carSpeedManager = carInstance.getCarManager(CarSpeedManager::class.java) 
                as? CarSpeedManager
            Log.d(TAG, "CarSpeedManager已获取")
            
            // 开始监听车速
            startSpeedListener()
        } catch (e: Exception) {
            Log.e(TAG, "获取CarSpeedManager失败", e)
        }
    }

    /**
     * Car服务断开连接
     */
    private fun onDisconnected() {
        _isConnected.value = false
        Log.i(TAG, "Car服务断开连接")
    }

    /**
     * 开始监听车速
     */
    private fun startSpeedListener() {
        handler.post(object : Runnable {
            override fun run() {
                if (_isConnected.value && carSpeedManager != null) {
                    try {
                        val speed = carSpeedManager?.speed ?: 0f
                        _vehicleSpeed.value = speed
                    } catch (e: Exception) {
                        // 忽略
                    }
                }
                handler.postDelayed(this, 1000)
            }
        })
    }

    /**
     * 获取驾驶状态
     */
    fun isDriving(): Boolean {
        return _vehicleSpeed.value > 0.5f
    }

    /**
     * 断开Car服务
     */
    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        car?.disconnect()
        car = null
        _isConnected.value = false
        Log.i(TAG, "Car服务已断开")
    }
}
