package com.pandora.carlauncher

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * 应用识别工具类
 * 支持识别标准版和共存版（双开版）应用
 */
object AppRecognizer {

    private const val TAG = "AppRecognizer"

    enum class AppCategory(val label: String) {
        NAVIGATION("导航"),
        MUSIC("音乐"),
        VIDEO("视频"),
        TOOL("工具"),
        SOCIAL("社交"),
        OTHER("其他")
    }

    /**
     * 导航类应用基础包名（不含共存版后缀）
     */
    private val NAVIGATION_BASE_PACKAGES = listOf(
        "com.autonavi.minimap",          // 高德地图
        "com.autonavi.xmgd",             // 高德地图车机版
        "com.autonavi.amapauto",         // 高德地图车机版(新版)
        "com.baidu.BaiduMap",            // 百度地图
        "com.baidu.baidumap",            // 百度地图(备用)
        "com.tencent.map",               // 腾讯地图
        "com.google.android.apps.maps",  // Google Maps
        "com.sogou.map",                 // 搜狗地图
        "com.careland.ck100",            // 凯立德
        "com.daodao.android",            // 导航犬
        "com.autonavi.minimap.auto",     // 高德车机版(另一包名)
        "com.baidu.navi",                // 百度导航
        "com.tencent.navii",             // 腾讯车载导航
        "com.gd.map",                    // 高德地图(部分设备)
        "cn.gov.cnnavic",                // 中国交通通
        "com.mapbar.android.preload",    // 图吧地图
        "com.autonavi.auto",             // 高德汽车版
        "com.baidu.carlife",             // 百度CarLife
        "com.tencent.carlife"            // 腾讯CarLife
    )

    /**
     * 音乐类应用基础包名（不含共存版后缀）
     */
    private val MUSIC_BASE_PACKAGES = listOf(
        "cn.kuwo.player",                // 酷我音乐
        "com.tencent.qqmusic",           // QQ音乐
        "com.netease.cloudmusic",        // 网易云音乐
        "com.kugou.android",             // 酷狗音乐
        "cmccwm.mobilemusic",            // 咪咕音乐
        "com.spotify.music",             // Spotify
        "com.apple.android.music",       // Apple Music
        "com.kuwo.player",               // 酷我音乐(备用包名)
        "com.kugou.music",               // 酷狗音乐(备用)
        "com.ting.mp3android",           // 蜻蜓FM
        "fm.qingting.qtradio",           // 蜻蜓FM(备用)
        "com.ximalaya.ting.android",     // 喜马拉雅
        "air.tv.douyu.android",          // 斗鱼(音频)
        "com.ss.android.ugc.aweme.lite",  // 汽水音乐
        "com.himalaya.soft.player",      // 喜马拉雅(备用)
        "com.tencent.qqmusiclite",       // QQ音乐轻享版
        "com.netease.cloudmusic.lite",   // 网易云音乐概念版
        "cn.kuwo.car",                   // 酷我音乐车机版
        "com.tencent.qqmusic.car"        // QQ音乐车机版
    )

    /**
     * 视频类应用包名
     */
    private val VIDEO_PACKAGES = listOf(
        "com.tencent.qqlive",            // 腾讯视频
        "com.qiyi.video",                // 爱奇艺
        "com.youku.phone",               // 优酷
        "tv.danmaku.bili",               // 哔哩哔哩
        "com.ss.android.ugc.aweme",      // 抖音
        "com.ss.android.ugc.livelite",   // 抖音直播
        "com.kuaishou.nebula",           // 快手
        "com.hunantv.imgo.activity",     // 芒果TV
        "com.mgtv.tv",                   // 芒果TV(备用)
        "com.pplive.androidphone",       // PPTV
        "tv.pptv.hd",                    // PPTV(备用)
        "com.leiniao.kiwitv",            // 樱花动漫
        "com.bilibili.roid",             // B站(备用)
        "com.qiyi.video.lite"            // 爱奇艺极速版
    )

    /**
     * 获取所有已安装的 PackageInfo
     */
    private fun getAllPackageInfos(context: Context): List<PackageInfo> {
        val pm = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(
                PackageManager.MATCH_ALL.toLong()
            ))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(PackageManager.GET_META_DATA or
                PackageManager.GET_SHARED_LIBRARY_FILES or
                PackageManager.MATCH_UNINSTALLED_PACKAGES or
                PackageManager.MATCH_DISABLED_COMPONENTS)
        }
    }

    /**
     * 获取已安装的导航类应用（自动识别共存版）
     */
    fun getInstalledNavigationApps(context: Context): List<AppInfo> {
        return findAppsByBasePackages(context, NAVIGATION_BASE_PACKAGES, AppCategory.NAVIGATION)
    }

    /**
     * 获取已安装的音乐类应用（自动识别共存版）
     */
    fun getInstalledMusicApps(context: Context): List<AppInfo> {
        return findAppsByBasePackages(context, MUSIC_BASE_PACKAGES, AppCategory.MUSIC)
    }

    /**
     * 获取已安装的视频类应用
     */
    fun getInstalledVideoApps(context: Context): List<AppInfo> {
        return findAppsByBasePackages(context, VIDEO_PACKAGES, AppCategory.VIDEO)
    }

    /**
     * 根据基础包名列表查找已安装应用（含共存版）
     * 共存版包名格式: com.xxx.app:1, com.xxx.app:2 等
     */
    private fun findAppsByBasePackages(
        context: Context,
        basePackages: List<String>,
        category: AppCategory
    ): List<AppInfo> {
        val pm = context.packageManager
        val result = mutableListOf<AppInfo>()
        val allPackages = getAllPackageInfos(context)

        for (pkgInfo in allPackages) {
            val pkgName = pkgInfo.packageName
            val baseName = pkgName.substringBefore(":")

            if (basePackages.contains(baseName)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    result.add(
                        AppInfo(
                            packageName = pkgName,
                            appName = appInfo.loadLabel(pm).toString(),
                            icon = appInfo.loadIcon(pm),
                            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                            category = category
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "获取应用信息失败: $pkgName", e)
                }
            }
        }
        return result
    }

    /**
     * 判断包名是否为共存版应用
     */
    fun isDualApp(packageName: String): Boolean {
        val userId = packageName.substringAfter(":", "")
        return userId.isNotEmpty() && userId.all { it.isDigit() }
    }

    /**
     * 获取应用分类
     */
    fun getCategory(packageName: String): AppCategory {
        val baseName = packageName.substringBefore(":")
        return when {
            NAVIGATION_BASE_PACKAGES.contains(baseName) -> AppCategory.NAVIGATION
            MUSIC_BASE_PACKAGES.contains(baseName) -> AppCategory.MUSIC
            VIDEO_PACKAGES.contains(baseName) -> AppCategory.VIDEO
            baseName.contains("social") || baseName.contains("chat") ||
                baseName.contains("wechat") || baseName.contains(".qq") -> AppCategory.SOCIAL
            baseName.contains("tool") || baseName.contains("file") ||
                baseName.contains("clean") || baseName.contains("manager") -> AppCategory.TOOL
            else -> AppCategory.OTHER
        }
    }

    /**
     * 获取所有已安装的第三方应用
     */
    fun getAllInstalledApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val allPackages = getAllPackageInfos(context)
        return allPackages
            .filter {
                val appInfo = it.applicationInfo
                (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                it.packageName != context.packageName
            }
            .sortedBy { it.applicationInfo.loadLabel(pm).toString().lowercase() }
            .map { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo
                AppInfo(
                    packageName = pkgInfo.packageName,
                    appName = appInfo.loadLabel(pm).toString(),
                    icon = appInfo.loadIcon(pm),
                    isSystemApp = false,
                    category = getCategory(pkgInfo.packageName)
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

    data class AppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable? = null,
        val isSystemApp: Boolean = false,
        val category: AppCategory = AppCategory.OTHER
    ) {
        val isDualApp: Boolean
            get() = isDualApp(packageName)
    }
}
