package com.pandora.carlauncher

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * 小组件页面
 * 时钟、天气、日历
 */
class WidgetsActivity : AppCompatActivity() {

    private lateinit var tvClockTime: TextView
    private lateinit var tvClockDate: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widgets)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        tvClockTime = findViewById(R.id.tv_clock_time)
        tvClockDate = findViewById(R.id.tv_clock_date)

        startClockUpdate()
        loadWeather()
    }

    private fun startClockUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateClock()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun updateClock() {
        val now = Date()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy年M月d日 E", Locale.getDefault())
        tvClockTime.text = timeFormat.format(now)
        tvClockDate.text = dateFormat.format(now)
    }

    private fun loadWeather() {
        // 模拟天气数据（实际可接入天气API）
        findViewById<TextView>(R.id.tv_weather_temp)?.text = "25°C"
        findViewById<TextView>(R.id.tv_weather_desc)?.text = "晴"
        findViewById<TextView>(R.id.tv_weather_city)?.text = "本地"
        findViewById<TextView>(R.id.tv_weather_humidity)?.text = "湿度: 60%"
        findViewById<TextView>(R.id.tv_weather_wind)?.text = "风速: 3m/s"

        val monthFormat = SimpleDateFormat("yyyy年M月", Locale.getDefault())
        findViewById<TextView>(R.id.tv_calendar_month)?.text = monthFormat.format(Date())
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
    }
}
