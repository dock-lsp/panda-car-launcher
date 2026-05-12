package com.pandora.carlauncher

import android.app.Application

class PandaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.instance.init(this)
    }
}
