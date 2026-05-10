package com.pandora.carlauncher.modules.appmanager

import android.graphics.drawable.Drawable

/**
 * 应用信息数据类
 *
 * @property packageName 包名
 * @property appName 应用名称
 * @property icon 应用图标
 * @property isSystemApp 是否为系统应用
 * @property versionName 版本名称
 * @property size 应用大小（字节）
 * @property installTime 安装时间
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isSystemApp: Boolean,
    val versionName: String,
    val size: Long,
    val installTime: Long = 0L
)
