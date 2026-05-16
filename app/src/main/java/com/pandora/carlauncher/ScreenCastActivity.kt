package com.pandora.carlauncher

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * 手机投屏
 * 支持 Android Auto、CarPlay、Miracast
 */
class ScreenCastActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_cast)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        // Android Auto
        findViewById<android.view.View>(R.id.btn_android_auto)?.setOnClickListener {
            showAndroidAutoGuide()
        }

        // CarPlay
        findViewById<android.view.View>(R.id.btn_carplay)?.setOnClickListener {
            showCarPlayGuide()
        }

        // Miracast
        findViewById<android.view.View>(R.id.btn_miracast)?.setOnClickListener {
            showMiracastGuide()
        }
    }

    private fun showAndroidAutoGuide() {
        AlertDialog.Builder(this)
            .setTitle("Android Auto 使用说明")
            .setMessage("""
1. 准备工作
   • 确保手机已安装 Android Auto 应用
   • 准备一根 USB 数据线（建议原装线）
   • 车机支持 Android Auto 协议

2. 连接步骤
   • 用 USB 线连接手机和车机
   • 手机上会弹出授权提示，点击"允许"
   • 首次连接需要在手机上完成设置向导

3. 无线连接（部分车机支持）
   • 确保手机和车机连接同一 WiFi
   • 在 Android Auto 设置中启用无线模式

4. 功能支持
   • 导航：Google 地图、高德地图等
   • 音乐：支持主流音乐应用
   • 通话：免提通话和语音拨号
   • 消息：语音朗读和回复

是否现在打开 Android Auto？
            """.trimIndent())
            .setPositiveButton("打开") { _, _ ->
                try {
                    val intent = packageManager.getLaunchIntentForPackage("com.google.android.projection.gearhead")
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        // 打开 Google Play 下载页面
                        val playIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.projection.gearhead"))
                        startActivity(playIntent)
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "请先安装 Android Auto", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showCarPlayGuide() {
        AlertDialog.Builder(this)
            .setTitle("CarPlay 使用说明")
            .setMessage("""
1. 准备工作
   • 需要 iPhone 5 或更新机型
   • iOS 7.1 或更高版本
   • 车机硬件支持 CarPlay 协议

2. 连接方式

   有线连接：
   • 使用 Lightning 转 USB 线连接 iPhone
   • 首次连接需要在 iPhone 上确认信任此设备

   无线连接（部分车机支持）：
   • 确保蓝牙已开启
   • 在车机上选择 iPhone 进行配对
   • 配对成功后自动连接 CarPlay

3. 功能支持
   • Siri 语音助手
   • 电话和 FaceTime 音频
   • 信息（语音朗读和回复）
   • 地图（苹果地图、高德等）
   • 音乐（Apple Music、网易云音乐等）

4. 注意事项
   • CarPlay 需要车机硬件支持
   • 本应用仅提供连接入口
   • 实际连接由系统处理
            """.trimIndent())
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun showMiracastGuide() {
        AlertDialog.Builder(this)
            .setTitle("Miracast 无线投屏")
            .setMessage("""
1. 什么是 Miracast
   • Miracast 是无线显示标准
   • 可将手机屏幕镜像到车机
   • 支持 Android 4.2+ 设备

2. 使用步骤

   方法一：系统设置
   • 打开手机"设置" → "显示" → "无线显示"
   • 或"设置" → "连接与共享" → "投屏"
   • 搜索并选择车机设备

   方法二：快捷方式
   • 下拉通知栏，找到"投屏"或"无线显示"
   • 点击后搜索车机设备

3. 车机端设置
   • 确保车机开启了无线显示接收
   • 部分车机需要手动进入投屏模式

4. 注意事项
   • 手机和车机需连接同一 WiFi
   • 或使用 WiFi Direct 直连
   • 延迟约 100-300ms

是否打开系统投屏设置？
            """.trimIndent())
            .setPositiveButton("打开设置") { _, _ ->
                try {
                    // 尝试打开无线显示设置
                    val intent = Intent(Settings.ACTION_CAST_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        // 备选：打开显示设置
                        val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                        startActivity(intent)
                    } catch (e2: Exception) {
                        Toast.makeText(this, "无法打开设置", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
