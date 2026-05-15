package com.pandora.carlauncher

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * 关于我们页面
 * 显示软件版本信息、功能介绍、版权信息
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // 返回按钮
        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        // 设置版本号
        val versionText = findViewById<TextView>(R.id.tv_version)
        versionText?.text = "版本 ${BuildConfig.VERSION_NAME}"
    }
}
