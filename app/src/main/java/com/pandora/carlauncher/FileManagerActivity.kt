package com.pandora.carlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件管理器
 * 支持浏览、打开文件
 */
class FileManagerActivity : AppCompatActivity() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var tvCurrentPath: TextView
    private var currentPath = Environment.getExternalStorageDirectory().absolutePath
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_manager)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            val parent = File(currentPath).parentFile
            if (parent != null && parent.canRead()) {
                currentPath = parent.absolutePath
                loadFiles()
            } else {
                finish()
            }
        }

        findViewById<View>(R.id.btn_refresh)?.setOnClickListener { loadFiles() }
        findViewById<View>(R.id.btn_sort)?.setOnClickListener { /* 排序 */ }

        rvFiles = findViewById(R.id.rv_files)
        rvFiles.layoutManager = LinearLayoutManager(this)
        tvCurrentPath = findViewById(R.id.tv_current_path)

        loadFiles()
    }

    private fun loadFiles() {
        tvCurrentPath.text = currentPath
        val dir = File(currentPath)
        val files = dir.listFiles()?.toList()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()

        rvFiles.adapter = FileAdapter(files) { file ->
            if (file.isDirectory) {
                currentPath = file.absolutePath
                loadFiles()
            } else {
                openFile(file)
            }
        }
    }

    private fun openFile(file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = Uri.fromFile(file)
        val mimeType = getMimeType(file.name)
        intent.setDataAndType(uri, mimeType)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "无法打开此文件", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "png", "gif", "bmp" -> "image/*"
            "mp4", "3gp", "mkv", "avi" -> "video/*"
            "mp3", "wav", "flac", "aac" -> "audio/*"
            "pdf" -> "application/pdf"
            "txt", "log" -> "text/plain"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "zip", "rar", "7z" -> "application/zip"
            else -> "*/*"
        }
    }

    inner class FileAdapter(
        private val files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivIcon: ImageView = view.findViewById(R.id.iv_file_icon)
            val tvName: TextView = view.findViewById(R.id.tv_file_name)
            val tvInfo: TextView = view.findViewById(R.id.tv_file_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.tvName.text = file.name
            holder.ivIcon.setImageResource(
                when {
                    file.isDirectory -> R.drawable.ic_folder
                    file.name.endsWith(".jpg") || file.name.endsWith(".png") -> R.drawable.ic_image
                    file.name.endsWith(".mp4") -> R.drawable.ic_video
                    file.name.endsWith(".mp3") -> R.drawable.ic_music
                    else -> R.drawable.ic_file
                }
            )
            val size = if (file.isDirectory) "${file.listFiles()?.size ?: 0} 项" else formatSize(file.length())
            val date = dateFormat.format(Date(file.lastModified()))
            holder.tvInfo.text = "$size | $date"
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = files.size

        private fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
                else -> "${bytes / 1024 / 1024 / 1024} GB"
            }
        }
    }
}
