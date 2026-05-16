package com.pandora.carlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream

/**
 * 应用壁纸管理器
 * 管理应用内背景壁纸，不是系统壁纸
 */
object WallpaperManager {
    private const val TAG = "WallpaperManager"
    private const val PREF_NAME = "wallpaper_pref"
    private const val KEY_WALLPAPER_TYPE = "wallpaper_type"
    private const val KEY_WALLPAPER_RES = "wallpaper_res"
    private const val KEY_WALLPAPER_PATH = "wallpaper_path"

    const val TYPE_DEFAULT = "default"
    const val TYPE_BUILTIN = "builtin"
    const val TYPE_CUSTOM = "custom"

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
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WALLPAPER_TYPE, TYPE_BUILTIN)
            .putInt(KEY_WALLPAPER_RES, resId)
            .apply()
    }

    /**
     * 设置自定义壁纸 - 将 URI 的图片复制到应用内部存储
     */
    fun setCustomWallpaper(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream != null) {
                val wallpaperFile = getCustomWallpaperFile(context)
                FileOutputStream(wallpaperFile).use { out ->
                    inputStream.copyTo(out)
                }
                inputStream.close()
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_WALLPAPER_TYPE, TYPE_CUSTOM)
                    .putString(KEY_WALLPAPER_PATH, wallpaperFile.absolutePath)
                    .apply()
            }
        } catch (e: Exception) {
            Log.e(TAG, "设置自定义壁纸失败", e)
        }
    }

    fun resetToDefault(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_WALLPAPER_TYPE, TYPE_DEFAULT)
            .remove(KEY_WALLPAPER_RES)
            .remove(KEY_WALLPAPER_PATH)
            .apply()
    }

    private fun getCustomWallpaperFile(context: Context): File {
        val dir = File(context.filesDir, "wallpapers")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "custom_wallpaper.jpg")
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
                loadCustomWallpaper(context)
            }
            else -> null
        }
    }

    /**
     * 从内部存储加载自定义壁纸
     */
    private fun loadCustomWallpaper(context: Context): Drawable? {
        val path = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_WALLPAPER_PATH, null) ?: return null
        return try {
            val file = File(path)
            if (file.exists()) {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val bitmap = BitmapFactory.decodeFile(path, options)
                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else null
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "加载自定义壁纸失败", e)
            null
        }
    }
}
