package com.pandora.carlauncher

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 画中画容器Activity
 * 用于在其他应用上以悬浮窗口形式显示
 * 支持悬浮导航、悬浮音乐播放器
 */
class PipActivity : AppCompatActivity() {

    private var targetPackageName: String? = null
    private var targetAppName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        targetPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        targetAppName = intent.getStringExtra(EXTRA_APP_NAME)
        
        if (targetPackageName == null) {
            Toast.makeText(this, "未指定应用", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 设置内容视图
        val frameLayout = FrameLayout(this)
        setContentView(frameLayout)

        // 添加启动目标应用的按钮
        val launchButton = ImageView(this).apply {
            setImageResource(R.drawable.ic_apps)
            setBackgroundResource(R.drawable.bg_floating_ball)
            setOnClickListener {
                launchTargetApp()
            }
        }
        frameLayout.addView(launchButton, FrameLayout.LayoutParams(
            100, 100
        ))

        // 进入画中画模式
        enterPipMode()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(1, 1)) // 1:1 正方形
                .build()
            enterPictureInPictureMode(params)
        }
    }

    private fun launchTargetApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage(targetPackageName!!)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } else {
                Toast.makeText(this, "无法启动应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration?
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode) {
            // 退出画中画模式时关闭Activity
            finish()
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_APP_NAME = "extra_app_name"

        fun start(activity: Activity, packageName: String, appName: String) {
            val intent = Intent(activity, PipActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_NAME, appName)
            }
            activity.startActivity(intent)
        }
    }
}
