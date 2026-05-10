package com.pandora.carlauncher.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.Car
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R

/**
 * 车辆连接服务
 * 
 * 负责维护与车辆系统的连接，处理车辆状态变化
 */
class CarConnectionService : Service() {

    companion object {
        private const val TAG = "CarConnectionService"
        
        private const val CHANNEL_ID = "car_connection_channel"
        private const val NOTIFICATION_ID = 3001
    }

    private var car: Car? = null
    private val binder = LocalBinder()
    
    // 连接状态回调
    private val connectionCallbacks = mutableListOf<ConnectionCallback>()

    inner class LocalBinder : Binder() {
        fun getService(): CarConnectionService = this@CarConnectionService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "车辆连接服务创建")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 连接Car服务
        connectToCarService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromCarService()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "车辆连接",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "车辆连接状态监控"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("熊猫车机桌面")
            .setContentText("车辆连接服务运行中...")
            .setSmallIcon(R.drawable.ic_car)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * 连接Car服务
     */
    private fun connectToCarService() {
        try {
            if (!Car.isCarServiceSupported(this)) {
                Log.w(TAG, "当前设备不支持Car服务")
                updateNotification("Car服务不可用")
                return
            }
            
            car = Car.createCar(this, object : Car.CarConnectionCallback {
                override fun onConnected(carInstance: Car) {
                    if (carInstance.isConnected) {
                        onCarConnected(carInstance)
                    }
                }
                
                override fun onDisconnected(carInstance: Car) {
                    onCarDisconnected()
                }
            })
            
            // 设置超时
            Thread {
                Thread.sleep(5000)
                if (car?.isConnected != true) {
                    Log.w(TAG, "Car服务连接超时")
                    updateNotification("Car服务连接超时")
                }
            }.start()
            
        } catch (e: Exception) {
            Log.e(TAG, "连接Car服务失败", e)
            updateNotification("Car服务连接失败")
        }
    }

    /**
     * Car服务连接成功
     */
    private fun onCarConnected(carInstance: Car) {
        Log.i(TAG, "Car服务连接成功")
        updateNotification("车辆已连接")
        
        // 更新Application状态
        PandaCarApplication.getInstance().setCarService(carInstance)
        
        // 通知回调
        connectionCallbacks.forEach { it.onConnected() }
    }

    /**
     * Car服务断开连接
     */
    private fun onCarDisconnected() {
        Log.i(TAG, "Car服务断开连接")
        updateNotification("车辆已断开")
        
        // 尝试重新连接
        Thread {
            Thread.sleep(3000)
            if (car?.isConnected != true) {
                connectToCarService()
            }
        }.start()
        
        // 通知回调
        connectionCallbacks.forEach { it.onDisconnected() }
    }

    /**
     * 断开Car服务
     */
    private fun disconnectFromCarService() {
        car?.disconnect()
        car = null
    }

    /**
     * 更新通知
     */
    private fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("熊猫车机桌面")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_car)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * 获取Car服务实例
     */
    fun getCar(): Car? = car

    /**
     * 是否已连接
     */
    fun isConnected(): Boolean = car?.isConnected == true

    /**
     * 添加连接状态回调
     */
    fun addConnectionCallback(callback: ConnectionCallback) {
        connectionCallbacks.add(callback)
    }

    /**
     * 移除连接状态回调
     */
    fun removeConnectionCallback(callback: ConnectionCallback) {
        connectionCallbacks.remove(callback)
    }

    /**
     * 连接状态回调接口
     */
    interface ConnectionCallback {
        fun onConnected()
        fun onDisconnected()
    }
}
