package com.pandora.carlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 壁纸设置 - 应用内背景
 * 支持内置壁纸和自定义壁纸
 */
class WallpaperActivity : AppCompatActivity() {

    private lateinit var ivCurrentWallpaper: ImageView
    private lateinit var rvWallpapers: RecyclerView

    // 内置壁纸资源ID
    private val builtInWallpapers = listOf(
        R.drawable.wallpaper_1,
        R.drawable.wallpaper_2,
        R.drawable.wallpaper_3,
        R.drawable.wallpaper_4
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallpaper)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        ivCurrentWallpaper = findViewById(R.id.iv_current_wallpaper)
        rvWallpapers = findViewById(R.id.rv_wallpapers)

        // 显示当前壁纸
        showCurrentWallpaper()

        // 内置壁纸列表
        rvWallpapers.layoutManager = GridLayoutManager(this, 2)
        rvWallpapers.adapter = WallpaperAdapter(builtInWallpapers) { resId ->
            WallpaperManager.setBuiltinWallpaper(this, resId)
            showCurrentWallpaper()
            Toast.makeText(this, "壁纸已设置", Toast.LENGTH_SHORT).show()
        }

        // 从相册选择
        findViewById<View>(R.id.btn_pick_wallpaper)?.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, 1001)
        }

        // 恢复默认
        findViewById<View>(R.id.btn_default_wallpaper)?.setOnClickListener {
            WallpaperManager.resetToDefault(this)
            showCurrentWallpaper()
            Toast.makeText(this, "已恢复默认", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCurrentWallpaper() {
        val drawable = WallpaperManager.getWallpaperDrawable(this)
        if (drawable != null) {
            ivCurrentWallpaper.setImageDrawable(drawable)
        } else {
            // 显示默认背景色
            ivCurrentWallpaper.setBackgroundColor(ThemeManager.getBackgroundColor(this))
            ivCurrentWallpaper.setImageResource(0)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data?.data != null) {
            // 保存自定义壁纸路径
            val uri = data.data.toString()
            WallpaperManager.setCustomWallpaper(this, uri)
            showCurrentWallpaper()
            Toast.makeText(this, "自定义壁纸已设置", Toast.LENGTH_SHORT).show()
        }
    }

    inner class WallpaperAdapter(
        private val wallpapers: List<Int>,
        private val onSelect: (Int) -> Unit
    ) : RecyclerView.Adapter<WallpaperAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivWallpaper: ImageView = view.findViewById(R.id.iv_wallpaper_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_wallpaper, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.ivWallpaper.setImageResource(wallpapers[position])
            holder.itemView.setOnClickListener { onSelect(wallpapers[position]) }
        }

        override fun getItemCount() = wallpapers.size
    }
}
