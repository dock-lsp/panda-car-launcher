package com.pandora.carlauncher.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.pandora.carlauncher.R

/**
 * 底部Dock导航Fragment - 简化版
 */
class DockFragment : Fragment() {

    // 页面索引常量
    companion object {
        const val PAGE_HOME = 0
        const val PAGE_NAVIGATION = 1
        const val PAGE_MEDIA = 2
        const val PAGE_HVAC = 3
        const val PAGE_APPS = 4
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dock, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 设置Dock项的点击事件
        setupDockItems(view)
    }
    
    /**
     * 设置Dock项点击事件
     */
    private fun setupDockItems(view: View) {
        // 主页
        view.findViewById<CardView>(R.id.dock_home)?.setOnClickListener {
            showToast("主页")
        }
        
        // 导航
        view.findViewById<CardView>(R.id.dock_navigation)?.setOnClickListener {
            showToast("导航")
        }
        
        // 媒体
        view.findViewById<CardView>(R.id.dock_media)?.setOnClickListener {
            showToast("媒体")
        }
        
        // 空调
        view.findViewById<CardView>(R.id.dock_hvac)?.setOnClickListener {
            showToast("空调")
        }
        
        // 应用
        view.findViewById<CardView>(R.id.dock_apps)?.setOnClickListener {
            showToast("应用")
        }
        
        // 设置
        view.findViewById<CardView>(R.id.dock_settings)?.setOnClickListener {
            showToast("设置")
        }
    }
    
    /**
     * 更新选中状态
     */
    fun updateSelection(position: Int) {
        // 简化版暂不实现
    }
    
    /**
     * 显示提示
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
