package com.pandora.carlauncher.ui.fragment

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.pandora.carlauncher.R
import com.pandora.carlauncher.ui.activity.MainLauncherActivity
import com.pandora.carlauncher.modules.settings.SettingsActivity

/**
 * 底部Dock导航Fragment
 * 
 * 提供快速导航入口：
 * - 主页
 * - 导航
 * - 媒体
 * - 空调
 * - 应用
 */
class DockFragment : Fragment() {

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
            navigateTo(MainLauncherActivity.PAGE_HOME)
        }
        
        // 导航
        view.findViewById<CardView>(R.id.dock_navigation)?.setOnClickListener {
            navigateTo(MainLauncherActivity.PAGE_NAVIGATION)
        }
        
        // 媒体
        view.findViewById<CardView>(R.id.dock_media)?.setOnClickListener {
            navigateTo(MainLauncherActivity.PAGE_MEDIA)
        }
        
        // 空调
        view.findViewById<CardView>(R.id.dock_hvac)?.setOnClickListener {
            navigateTo(MainLauncherActivity.PAGE_HVAC)
        }
        
        // 应用
        view.findViewById<CardView>(R.id.dock_apps)?.setOnClickListener {
            navigateTo(MainLauncherActivity.PAGE_APPS)
        }
        
        // 设置（最右侧）
        view.findViewById<CardView>(R.id.dock_settings)?.setOnClickListener {
            openSettings()
        }
    }
    
    /**
     * 导航到指定页面
     */
    private fun navigateTo(page: Int) {
        activity?.let { act ->
            if (act is MainLauncherActivity) {
                act.switchToPage(page)
            }
        }
    }
    
    /**
     * 更新选中状态
     */
    fun updateSelection(position: Int) {
        view?.let { v ->
            val items = listOf(
                R.id.dock_home to MainLauncherActivity.PAGE_HOME,
                R.id.dock_navigation to MainLauncherActivity.PAGE_NAVIGATION,
                R.id.dock_media to MainLauncherActivity.PAGE_MEDIA,
                R.id.dock_hvac to MainLauncherActivity.PAGE_HVAC,
                R.id.dock_apps to MainLauncherActivity.PAGE_APPS
            )
            
            items.forEach { (id, page) ->
                v.findViewById<CardView>(id)?.isSelected = (position == page)
            }
        }
    }
    
    /**
     * 打开设置页面
     */
    private fun openSettings() {
        startActivity(Intent(requireContext(), SettingsActivity::class.java))
    }
}
