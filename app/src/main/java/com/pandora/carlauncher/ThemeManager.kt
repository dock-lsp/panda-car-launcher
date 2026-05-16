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

    fun getCurrentTheme(context: Context): String {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_THEME, THEME_DARK) ?: THEME_DARK
    }

    fun setTheme(context: Context, theme: String) {
        val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME, theme).apply()
    }

    fun applyTheme(context: Context) {
        val theme = getCurrentTheme(context)
        // 主题通过 colors.xml 资源切换，这里只是标记
    }

    fun isDarkTheme(context: Context): Boolean {
        return getCurrentTheme(context) == THEME_DARK
    }
}
