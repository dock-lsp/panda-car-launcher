package com.pandora.carlauncher

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * 全局异常捕获器
 * 防止应用闪退，捕获异常并显示错误信息
 */
class CrashHandler private constructor() : Thread.UncaughtExceptionHandler {

    private lateinit var defaultHandler: Thread.UncaughtExceptionHandler
    private lateinit var context: Context

    fun init(ctx: Context) {
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        Log.e("CrashHandler", "应用崩溃: ", ex)

        // 保存崩溃日志
        try {
            val crashDir = File(context.filesDir, "crash_logs")
            if (!crashDir.exists()) crashDir.mkdirs()
            val logFile = File(crashDir, "crash_${System.currentTimeMillis()}.log")
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            ex.printStackTrace(pw)
            logFile.writeText(sw.toString())
        } catch (e: Exception) {
            Log.e("CrashHandler", "保存崩溃日志失败", e)
        }

        // 1秒后重启应用
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, 
                System.currentTimeMillis() + 1000, pendingIntent)
        }

        // 调用系统默认处理
        defaultHandler.uncaughtException(thread, ex)
    }

    companion object {
        val instance: CrashHandler by lazy(mode = LazyThreadSafetyMode.SYNCHRONIZED) {
            CrashHandler()
        }
    }
}
