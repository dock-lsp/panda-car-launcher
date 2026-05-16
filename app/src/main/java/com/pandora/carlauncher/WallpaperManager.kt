package com.pandora.carlauncher

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat

/**
 * 应用壁纸管理器
 * 管理应用内背景壁纸，不是系统壁纸
 */
object WallpaperManager {
    private const val PREF_NAME = "wallpaper_pref"
    private const val KEY_WALLPAPER_TYPE = "wallpaper_type"
    private const val KEY_WALLPAPER_RES = "wallpaper_res"
    private const val KEY_WALLPAPER_PATH = "wallpaper_path"

    const val TYPE_DEFAULT = "default"
    const val TYPE_BUILTIN = "builtin"
    const val TYPE_CUSTOM = "custom"

    // 内置壁纸资源
    val BUILTIN_WALLPAPERS = listOf(
        R.drawable.wallpaper_1,
        R.drawable.wallpaper_2,
        R.drawable.wallpaper_3,
        R.drawable.wallpaper_4
    )

    fun getWallpaperType(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_WALLPAPER_TYPE, TYPE_BUILTIN) ?: TYPE_BUILTIN
    }

    fun setBuiltinWallpaper(context: Context, resId: Int) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, TYPE_BUILTIN)
            .putInt(KEY_WALLPAPER_RES, resId)
            .apply()
    }

    fun setCustomWallpaper(context: Context, path: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, TYPE_CUSTOM)
            .putString(KEY_WALLPAPER_PATH, path)
            .apply()
    }

    fun resetToDefault(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_WALLPAPER_TYPE, TYPE_DEFAULT)
            .remove(KEY_WALLPAPER_RES)
            .remove(KEY_WALLPAPER_PATH)
            .apply()
    }

    /**
     * 获取当前壁纸 Drawable
     */
    fun getWallpaperDrawable(context: Context): Drawable? {
        return when (getWallpaperType(context)) {
            TYPE_BUILTIN -> {
                val resId = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getInt(KEY_WALLPAPER_RES, R.drawable.wallpaper_1)
                try {
                    ContextCompat.getDrawable(context, resId)
                } catch (e: Exception) {
                    null
                }
            }
            TYPE_CUSTOM -> {
                // 自定义壁纸稍后实现
                null
            }
            else -> null // 默认使用主题背景
        }
    }

    /**
     * 应用壁纸到 View
     */
    fun applyWallpaper(context: Context, view: android.view.View) {
        val drawable = getWallpaperDrawable(context)
        if (drawable != null) {
            view.background = drawable
        } else {
            // 使用默认主题背景
            val themeManager = ThemeManager
            val bgColor = themeManager.getBackgroundColor(context)
            view.setBackgroundColor(bgColor)
        }
    }
}
