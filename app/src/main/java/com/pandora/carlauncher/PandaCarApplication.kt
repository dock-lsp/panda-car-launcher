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

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "PandaCarApplication created")
    }
}
