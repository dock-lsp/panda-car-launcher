package com.pandora.carlauncher

import android.content.Context
import android.content.SharedPreferences

/**
 * 主题管理器
 * 支持深色/浅色两套主题切换
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

    fun getCurrentTheme(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    fun setTheme(context: Context, theme: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
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
}
