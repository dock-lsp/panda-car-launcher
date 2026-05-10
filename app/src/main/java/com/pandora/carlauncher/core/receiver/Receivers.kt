package com.pandora.carlauncher.core.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import com.pandora.carlauncher.modules.media.MediaPlayService

/**
 * 启动完成广播接收器
 * 
 * 在系统启动完成后启动必要的服务
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.i(TAG, "系统启动完成，开始初始化车机桌面...")
            
            // 启动主Activity
            val launchIntent = Intent(context, 
                com.pandora.carlauncher.ui.activity.MainLauncherActivity::class.java)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            
            // 启动车辆连接服务
            val carServiceIntent = Intent(context, 
                com.pandora.carlauncher.core.service.CarConnectionService::class.java)
            context.startService(carServiceIntent)
            
            Log.i(TAG, "车机桌面初始化完成")
        }
    }
}

/**
 * 车辆状态广播接收器
 * 
 * 接收车辆相关状态变化（速度、驾驶状态等）
 */
class CarStatusReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "CarStatusReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.car.Car.intent.action.ACTION_CAR_DRIVING_STATE_CHANGED" -> {
                val drivingState = intent.getIntExtra("drivingState", 0)
                Log.d(TAG, "驾驶状态变化: $drivingState")
                // 处理驾驶状态变化
            }
            
            "android.car.Car.intent.action.ACTION_CAR_SPEED_CHANGED" -> {
                val speed = intent.getFloatExtra("speed", 0f)
                Log.d(TAG, "车速变化: $speed km/h")
                // 处理车速变化
            }
            
            "android.car.Car.intent.action.ACTION_CAR_DISTANCE_CHANGED" -> {
                val distance = intent.getLongExtra("distance", 0)
                Log.d(TAG, "里程变化: $distance m")
                // 处理里程变化
            }
        }
    }
}

/**
 * 媒体按钮广播接收器
 * 
 * 处理线控耳机或方向盘按键的媒体控制
 */
class MediaButtonReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "MediaButtonReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MEDIA_BUTTON) {
            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
            
            keyEvent?.let {
                if (it.action == KeyEvent.ACTION_DOWN) {
                    Log.d(TAG, "媒体按键: ${it.keyCode}")
                    
                    val mediaIntent = when (it.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY -> 
                            Intent(MediaPlayService.ACTION_PLAY)
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> 
                            Intent(MediaPlayService.ACTION_PAUSE)
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> 
                            Intent(MediaPlayService.ACTION_PLAY)
                        KeyEvent.KEYCODE_MEDIA_NEXT -> 
                            Intent(MediaPlayService.ACTION_NEXT)
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 
                            Intent(MediaPlayService.ACTION_PREV)
                        KeyEvent.KEYCODE_MEDIA_STOP -> 
                            Intent(MediaPlayService.ACTION_STOP)
                        else -> null
                    }
                    
                    mediaIntent?.let { action ->
                        action.setPackage(context.packageName)
                        context.startService(action)
                    }
                }
            }
        }
    }
}

/**
 * 网络状态广播接收器
 * 
 * 监听网络连接状态变化
 */
class NetworkStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NetworkStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "android.net.conn.CONNECTIVITY_CHANGE" -> {
                Log.d(TAG, "网络连接状态变化")
                // 处理网络状态变化
            }
            
            "android.net.wifi.WIFI_STATE_CHANGED" -> {
                val wifiState = intent.getIntExtra("wifi_state", 0)
                val stateName = when (wifiState) {
                    1 -> "正在禁用"
                    2 -> "已禁用"
                    3 -> "正在启用"
                    4 -> "已启用"
                    0 -> "未知"
                    else -> "未知"
                }
                Log.d(TAG, "WiFi状态: $stateName")
            }
            
            "android.net.wifi.STATE_CHANGE" -> {
                Log.d(TAG, "WiFi连接状态变化")
                // 处理WiFi连接变化
            }
        }
    }
}

/**
 * 屏幕状态广播接收器
 * 
 * 监听屏幕亮灭状态
 */
class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = com.pandora.carlauncher.PandaCarApplication.getInstance()
        
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "屏幕亮起")
                app.updateScreenState(true)
            }
            
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "屏幕熄灭")
                app.updateScreenState(false)
            }
            
            Intent.ACTION_USER_PRESENT -> {
                Log.d(TAG, "用户解锁")
            }
        }
    }
}
