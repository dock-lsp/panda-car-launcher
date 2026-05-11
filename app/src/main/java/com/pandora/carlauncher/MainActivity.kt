package com.pandora.carlauncher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("MM月dd日 EEEE", Locale.getDefault())

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 初始化时间显示
        updateTime()
        handler.post(updateTimeRunnable)
    }

    private fun updateTime() {
        val now = Calendar.getInstance()
        
        findViewById<TextView>(R.id.tv_time)?.text = timeFormat.format(now.time)
        findViewById<TextView>(R.id.tv_date)?.text = dateFormat.format(now.time)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTimeRunnable)
    }
}
