package com.pandora.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 导航信息接收器
 * 监听高德地图导航广播
 */
class NavInfoReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NavInfoReceiver"
        
        // 高德导航广播 Action
        const val ACTION_NAV_INFO = "com.autonavi.amapauto.NAV_INFO"
        const val ACTION_NAV_STATUS = "com.autonavi.amapauto.NAV_STATUS"
        
        @Volatile var remainDistance = ""  // 剩余距离
        @Volatile var remainTime = ""      // 剩余时间
        @Volatile var currentRoad = ""     // 当前道路
        @Volatile var nextRoad = ""        // 下一道路
        @Volatile var nextDistance = ""    // 下一转向距离
        @Volatile var serviceArea = ""     // 服务区
        @Volatile var isNavigating = false // 是否正在导航
        
        var onNavInfoUpdate: (() -> Unit)? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "收到导航广播: ${intent.action}")
        
        when (intent.action) {
            ACTION_NAV_INFO, ACTION_NAV_STATUS -> {
                // 解析导航信息
                remainDistance = intent.getStringExtra("remain_distance") ?: remainDistance
                remainTime = intent.getStringExtra("remain_time") ?: remainTime
                currentRoad = intent.getStringExtra("current_road") ?: currentRoad
                nextRoad = intent.getStringExtra("next_road") ?: nextRoad
                nextDistance = intent.getStringExtra("next_distance") ?: nextDistance
                serviceArea = intent.getStringExtra("service_area") ?: serviceArea
                isNavigating = true
                
                Log.d(TAG, "导航信息: 距离=$remainDistance, 时间=$remainTime, 道路=$currentRoad")
                onNavInfoUpdate?.invoke()
            }
            
            "com.autonavi.amapauto.NAV_END" -> {
                isNavigating = false
                onNavInfoUpdate?.invoke()
            }
        }
    }
}
