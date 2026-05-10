package com.pandora.carlauncher.ui.fragment

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.pandora.carlauncher.R
import java.text.SimpleDateFormat
import java.util.*

/**
 * 状态栏Fragment
 * 
 * 显示系统状态信息：
 * - 当前时间
 * - 网络状态
 * - 蓝牙状态
 * - GPS状态
 * - 车辆速度（如果有）
 */
class StatusBarFragment : Fragment() {

    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    private lateinit var networkIcon: ImageView
    private lateinit var bluetoothIcon: ImageView
    private lateinit var gpsIcon: ImageView
    
    private val handler = Handler(Looper.getMainLooper())
    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_status_bar, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        timeText = view.findViewById(R.id.tv_time)
        dateText = view.findViewById(R.id.tv_date)
        networkIcon = view.findViewById(R.id.icon_network)
        bluetoothIcon = view.findViewById(R.id.icon_bluetooth)
        gpsIcon = view.findViewById(R.id.icon_gps)
        
        // 初始更新时间
        updateTime()
        updateStatusIcons()
    }
    
    override fun onResume() {
        super.onResume()
        // 开始定时更新
        handler.post(updateTimeRunnable)
    }
    
    override fun onPause() {
        super.onPause()
        // 停止定时更新
        handler.removeCallbacks(updateTimeRunnable)
    }
    
    /**
     * 更新时间显示
     */
    private fun updateTime() {
        try {
            val calendar = Calendar.getInstance()
            
            // 格式化时间
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val dateFormat = SimpleDateFormat("MM月dd日 E", Locale.getDefault())
            
            timeText.text = timeFormat.format(calendar.time)
            dateText.text = dateFormat.format(calendar.time)
        } catch (e: Exception) {
            // 忽略
        }
    }
    
    /**
     * 更新状态图标
     */
    private fun updateStatusIcons() {
        // 默认隐藏状态图标，后续通过广播监听更新
        networkIcon.visibility = View.INVISIBLE
        bluetoothIcon.visibility = View.INVISIBLE
        gpsIcon.visibility = View.INVISIBLE
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacks(updateTimeRunnable)
    }
}
