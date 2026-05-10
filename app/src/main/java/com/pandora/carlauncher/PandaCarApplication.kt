package com.pandora.carlauncher

import android.app.Application
import android.util.Log

/**
 * 熊猫车机桌面应用类
 */
class PandaCarApplication : Application() {

    companion object {
        private const val TAG = "PandaCarApplication"
        private var instance: PandaCarApplication? = null

        fun getInstance(): PandaCarApplication {
            return instance ?: throw IllegalStateException("Application not initialized")
        }
    }

    // 车辆连接服务实例
    var carService: Any? = null

    // 偏好设置管理器
    val preferencesManager: PreferencesManager by lazy { PreferencesManager(this) }

    // 屏幕状态
    private var screenOn: Boolean = true

    // 驾驶状态
    private var isDriving: Boolean = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "PandaCarApplication created")
    }

    /**
     * 更新屏幕状态
     * @param isOn 屏幕是否亮起
     */
    fun updateScreenState(isOn: Boolean) {
        screenOn = isOn
        Log.d(TAG, "Screen state updated: ${if (isOn) "ON" else "OFF"}")
    }

    /**
     * 更新驾驶状态
     * @param driving 是否正在驾驶
     */
    fun updateDrivingState(driving: Boolean) {
        isDriving = driving
        Log.d(TAG, "Driving state updated: ${if (driving) "DRIVING" else "PARKED"}")
    }

    /**
     * 获取当前屏幕状态
     */
    fun isScreenOn(): Boolean = screenOn

    /**
     * 获取当前驾驶状态
     */
    fun isDriving(): Boolean = isDriving

    /**
     * 偏好设置管理器类
     */
    inner class PreferencesManager(private val context: Application) {

        private val prefs = context.getSharedPreferences("panda_car_prefs", MODE_PRIVATE)

        companion object {
            private const val KEY_NIGHT_MODE = "night_mode"
            private const val KEY_DRIVING_MODE = "driving_mode"
            private const val KEY_VOLUME = "volume"
            private const val KEY_BRIGHTNESS = "brightness"
        }

        /**
         * 设置夜间模式
         */
        fun setNightMode(enabled: Boolean) {
            prefs.edit().putBoolean(KEY_NIGHT_MODE, enabled).apply()
        }

        /**
         * 是否启用夜间模式
         */
        fun isNightMode(): Boolean = prefs.getBoolean(KEY_NIGHT_MODE, false)

        /**
         * 设置驾驶模式
         */
        fun setDrivingMode(enabled: Boolean) {
            prefs.edit().putBoolean(KEY_DRIVING_MODE, enabled).apply()
        }

        /**
         * 是否启用驾驶模式
         */
        fun isDrivingMode(): Boolean = prefs.getBoolean(KEY_DRIVING_MODE, false)

        /**
         * 保存音量
         */
        fun setVolume(volume: Int) {
            prefs.edit().putInt(KEY_VOLUME, volume).apply()
        }

        /**
         * 获取保存的音量
         */
        fun getVolume(): Int = prefs.getInt(KEY_VOLUME, -1)

        /**
         * 保存亮度
         */
        fun setBrightness(brightness: Int) {
            prefs.edit().putInt(KEY_BRIGHTNESS, brightness).apply()
        }

        /**
         * 获取保存的亮度
         */
        fun getBrightness(): Int = prefs.getInt(KEY_BRIGHTNESS, -1)
    }
}
