package com.pandora.carlauncher

import android.app.WallpaperManager
import android.content.Intent
import android.graphics.BitmapFactory
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
import java.io.InputStream

/**
 * 壁纸设置
 * 支持内置壁纸和自定义壁纸
 */
class WallpaperActivity : AppCompatActivity() {

    private lateinit var ivCurrentWallpaper: ImageView
    private lateinit var rvWallpapers: RecyclerView
    private val wallpaperManager: WallpaperManager by lazy { WallpaperManager.getInstance(this) }

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
            setWallpaperFromResource(resId)
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
            try {
                wallpaperManager.clear()
                Toast.makeText(this, "已恢复默认壁纸", Toast.LENGTH_SHORT).show()
                showCurrentWallpaper()
            } catch (e: Exception) {
                Toast.makeText(this, "恢复失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showCurrentWallpaper() {
        try {
            val drawable = wallpaperManager.drawable
            ivCurrentWallpaper.setImageDrawable(drawable)
        } catch (e: Exception) {
            ivCurrentWallpaper.setBackgroundColor(0xFF0F0F1A.toInt())
        }
    }

    private fun setWallpaperFromResource(resId: Int) {
        try {
            val inputStream = resources.openRawResource(resId)
            wallpaperManager.setStream(inputStream)
            inputStream.close()
            Toast.makeText(this, "壁纸已设置", Toast.LENGTH_SHORT).show()
            showCurrentWallpaper()
        } catch (e: Exception) {
            Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data?.data != null) {
            setWallpaperFromUri(data.data!!)
        }
    }

    private fun setWallpaperFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                wallpaperManager.setStream(inputStream)
                inputStream.close()
                Toast.makeText(this, "壁纸已设置", Toast.LENGTH_SHORT).show()
                showCurrentWallpaper()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "设置失败", Toast.LENGTH_SHORT).show()
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
