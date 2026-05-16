package com.pandora.carlauncher

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.core.content.ContextCompat

/**
 * 主题管理器
 * 支持深色/浅色两套主题实时切换
 */
object ThemeManager {
    private const val PREF_NAME = "theme_pref"
    private const val KEY_THEME = "current_theme"

    const val THEME_DARK = "dark"
    const val THEME_LIGHT = "light"

    // 深色主题颜色
    val DARK_COLORS = mapOf(
        "background" to 0xFF0F0F1A.toInt(),
        "card_glass" to 0xFF1A1A2E.toInt(),
        "text_primary" to 0xFFFFFFFF.toInt(),
        "text_secondary" to 0xFFB0B0B0.toInt(),
        "text_hint" to 0xFF666666.toInt(),
        "primary" to 0xFF00D4AA.toInt(),
        "primary_dark" to 0xFF00A080.toInt(),
        "accent" to 0xFFFF6B6B.toInt()
    )

    // 浅色主题颜色
    val LIGHT_COLORS = mapOf(
        "background" to 0xFFF5F5F5.toInt(),
        "card_glass" to 0xFFFFFFFF.toInt(),
        "text_primary" to 0xFF1A1A1A.toInt(),
        "text_secondary" to 0xFF666666.toInt(),
        "text_hint" to 0xFF999999.toInt(),
        "primary" to 0xFF0088CC.toInt(),
        "primary_dark" to 0xFF006699.toInt(),
        "accent" to 0xFFFF4444.toInt()
    )

    // 主题切换监听器
    private val listeners = mutableListOf<OnThemeChangeListener>()

    interface OnThemeChangeListener {
        fun onThemeChanged(isDark: Boolean)
    }

    fun addListener(listener: OnThemeChangeListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: OnThemeChangeListener) {
        listeners.remove(listener)
    }

    fun getCurrentTheme(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    fun setTheme(context: Context, theme: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()

        // 通知所有监听器
        val isDark = theme == THEME_DARK
        listeners.forEach { it.onThemeChanged(isDark) }
    }

    fun isDarkTheme(context: Context): Boolean {
        return getCurrentTheme(context) == THEME_DARK
    }

    fun getColors(context: Context): Map<String, Int> {
        return if (isDarkTheme(context)) DARK_COLORS else LIGHT_COLORS
    }

    fun getBackgroundColor(context: Context): Int {
        return getColors(context)["background"]!!
    }

    fun getCardColor(context: Context): Int {
        return getColors(context)["card_glass"]!!
    }

    fun getTextColor(context: Context): Int {
        return getColors(context)["text_primary"]!!
    }

    fun getSecondaryTextColor(context: Context): Int {
        return getColors(context)["text_secondary"]!!
    }

    fun getPrimaryColor(context: Context): Int {
        return getColors(context)["primary"]!!
    }

    fun getHintColor(context: Context): Int {
        return getColors(context)["text_hint"]!!
    }

    /**
     * 应用主题到 Activity
     */
    fun applyThemeToActivity(activity: Activity) {
        val colors = getColors(activity)
        activity.window?.decorView?.setBackgroundColor(colors["background"]!!)
    }

    /**
     * 获取主题颜色资源 ID
     */
    fun getColorResourceId(name: String): Int {
        return when (name) {
            "background" -> R.color.background
            "card_glass" -> R.color.card_glass
            "text_primary" -> R.color.text_primary
            "text_secondary" -> R.color.text_secondary
            "text_hint" -> R.color.text_hint
            "primary" -> R.color.primary
            "primary_dark" -> R.color.primary_dark
            "accent" -> R.color.accent
            else -> R.color.background
        }
    }
}

/**
 * 主题工具扩展函数
 */
fun android.view.View.applyThemeBackground(context: Context) {
    val wallpaperDrawable = WallpaperManager.getWallpaperDrawable(context)
    if (wallpaperDrawable != null) {
        background = wallpaperDrawable
    } else {
        setBackgroundColor(ThemeManager.getBackgroundColor(context))
    }
}
