package com.pandora.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 开机自启广播接收器
 * 参考布丁桌面的 BootCompleteReceiver 实现
 * 监听开机完成广播，自动启动桌面
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "开机完成，启动桌面")
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } catch (e: Exception) {
                Log.e(TAG, "启动桌面失败", e)
            }
        }
    }
}
