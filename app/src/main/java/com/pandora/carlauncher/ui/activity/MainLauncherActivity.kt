package com.pandora.carlauncher.ui.activity

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.pandora.carlauncher.R

/**
 * 主桌面启动器Activity - 简化版
 */
class MainLauncherActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainLauncherActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainLauncherActivity onCreate")
        
        // 使用最简单的布局
        setContentView(R.layout.activity_main_launcher)
    }
}
