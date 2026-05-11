package com.pandora.carlauncher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

/**
 * 应用识别工具类
 * 支持识别标准版和共存版（双开版）应用
 */
object AppRecognizer {

    /**
     * 应用分类
     */
    enum class AppCategory(val label: String) {
        NAVIGATION("导航"),
        MUSIC("音乐"),
        VIDEO("视频"),
        TOOL("工具"),
        SOCIAL("社交"),
        OTHER("其他")
    }

    /**
     * 导航类应用包名列表（含共存版）
     */
    private val NAVIGATION_PACKAGES = listOf(
        // 高德地图
        "com.autonavi.minimap",
        "com.autonavi.minimap:1",           // 高德地图共存版
        "com.autonavi.xmgd",                // 高德地图车机版
        "com.autonavi.amapauto",            // 高德地图车机版(新版)
        // 百度地图
        "com.baidu.BaiduMap",
        "com.baidu.BaiduMap:1",             // 百度地图共存版
        "com.baidu.baidumap",               // 百度地图(备用包名)
        // 腾讯地图
        "com.tencent.map",
        "com.tencent.map:1",                // 腾讯地图共存版
        // Google Maps
        "com.google.android.apps.maps",
        // 搜狗地图
        "com.sogou.map",
        // 凯立德
        "com.careland.ck100",
        // 导航犬
        "com.daodao.android"
    )

    /**
     * 音乐类应用包名列表（含共存版）
     */
    private val MUSIC_PACKAGES = listOf(
        // 酷我音乐
        "cn.kuwo.player",
        "cn.kuwo.player:1",                // 酷我音乐共存版
        "cn.kuwo.player:2",                // 酷我音乐共存版2
        // QQ音乐
        "com.tencent.qqmusic",
        "com.tencent.qqmusic:1",           // QQ音乐共存版
        "com.tencent.qqmusic:2",           // QQ音乐共存版2
        // 网易云音乐
        "com.netease.cloudmusic",
        "com.netease.cloudmusic:1",        // 网易云音乐共存版
        "com.netease.cloudmusic:2",        // 网易云音乐共存版2
        // 酷狗音乐
        "com.kugou.android",
        "com.kugou.android:1",             // 酷狗音乐共存版
        // 咪咕音乐
        "cmccwm.mobilemusic",
        // 汽水音乐
        "com.ss.android.ugc.aweme.lite",    // 抖音汽水音乐
        // Spotify
        "com.spotify.music",
        // Apple Music
        "com.apple.android.music"
    )

    /**
     * 视频类应用包名列表
     */
    private val VIDEO_PACKAGES = listOf(
        "com.tencent.qqlive",              // 腾讯视频
        "com.qiyi.video",                  // 爱奇艺
        "com.youku.phone",                 // 优酷
        "tv.danmaku.bili",                 // 哔哩哔哩
        "com.ss.android.ugc.aweme"         // 抖音
    )

    /**
     * 获取已安装的导航类应用
     */
    fun getInstalledNavigationApps(context: Context): List<AppInfo> {
        return getInstalledAppsFromList(context, NAVIGATION_PACKAGES)
    }

    /**
     * 获取已安装的音乐类应用
     */
    fun getInstalledMusicApps(context: Context): List<AppInfo> {
        return getInstalledAppsFromList(context, MUSIC_PACKAGES)
    }

    /**
     * 获取已安装的视频类应用
     */
    fun getInstalledVideoApps(context: Context): List<AppInfo> {
        return getInstalledAppsFromList(context, VIDEO_PACKAGES)
    }

    /**
     * 从包名列表中筛选已安装的应用
     */
    private fun getInstalledAppsFromList(
        context: Context,
        packageNames: List<String>
    ): List<AppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()
        for (pkg in packageNames) {
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                result.add(
                    AppInfo(
                        packageName = pkg,
                        appName = appInfo.loadLabel(pm).toString(),
                        icon = appInfo.loadIcon(pm),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    )
                )
            } catch (e: PackageManager.NameNotFoundException) {
                // 应用未安装，跳过
            }
        }
        return result
    }

    /**
     * 判断包名是否为共存版应用
     */
    fun isDualApp(packageName: String): Boolean {
        return packageName.contains(":1") || packageName.contains(":2") || packageName.contains(":3")
    }

    /**
     * 获取应用分类
     */
    fun getCategory(packageName: String): AppCategory {
        return when {
            NAVIGATION_PACKAGES.contains(packageName) -> AppCategory.NAVIGATION
            MUSIC_PACKAGES.contains(packageName) -> AppCategory.MUSIC
            VIDEO_PACKAGES.contains(packageName) -> AppCategory.VIDEO
            packageName.contains("social") || packageName.contains("chat") || 
                packageName.contains("wechat") || packageName.contains("qq") -> AppCategory.SOCIAL
            packageName.contains("tool") || packageName.contains("file") ||
                packageName.contains("clean") || packageName.contains("manager") -> AppCategory.TOOL
            else -> AppCategory.OTHER
        }
    }

    /**
     * 获取所有已安装的第三方应用
     */
    fun getAllInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        return pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { 
                (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != context.packageName
            }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .map {
                AppInfo(
                    packageName = it.packageName,
                    appName = it.loadLabel(pm).toString(),
                    icon = it.loadIcon(pm),
                    isSystemApp = false,
                    category = getCategory(it.packageName)
                )
            }
    }

    /**
     * 搜索应用
     */
    fun searchApps(context: Context, query: String): List<AppInfo> {
        if (query.isBlank()) return getAllInstalledApps(context)
        return getAllInstalledApps(context).filter {
            it.appName.contains(query, ignoreCase = true) || 
            it.packageName.contains(query, ignoreCase = true)
        }
    }

    /**
     * 应用信息
     */
    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable? = null,
        val isSystemApp: Boolean = false,
        val category: AppCategory = AppCategory.OTHER
    ) {
        val isDualApp: Boolean
            get() = packageName.contains(":")
    }
}
