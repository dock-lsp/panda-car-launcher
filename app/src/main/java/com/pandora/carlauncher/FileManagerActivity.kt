package com.pandora.carlauncher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    private val REQUEST_STORAGE_PERMISSION = 1001

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

        checkStoragePermission()
    }

    private fun checkStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                // Android 11+ 需要 MANAGE_EXTERNAL_STORAGE 或 All Files Access
                if (Environment.isExternalStorageManager()) {
                    loadFiles()
                } else {
                    requestManageStoragePermission()
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Android 10
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    loadFiles()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_STORAGE_PERMISSION)
                }
            }
            else -> {
                // Android 9 及以下
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    loadFiles()
                } else {
                    ActivityCompat.requestPermissions(this, arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ), REQUEST_STORAGE_PERMISSION)
                }
            }
        }
    }

    private fun requestManageStoragePermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
            startActivityForResult(intent, 1002)
        } catch (e: Exception) {
            Toast.makeText(this, "需要文件管理权限", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadFiles()
        } else {
            Toast.makeText(this, "需要存储权限才能浏览文件", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1002) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                loadFiles()
            } else {
                Toast.makeText(this, "需要文件管理权限", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadFiles() {
        tvCurrentPath.text = currentPath
        val dir = File(currentPath)

        if (!dir.exists() || !dir.canRead()) {
            Toast.makeText(this, "无法访问此目录", Toast.LENGTH_SHORT).show()
            return
        }

        val files = try {
            dir.listFiles()?.toList()?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }) ?: emptyList()
        } catch (e: Exception) {
            emptyList<File>()
        }

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
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            } else {
                Uri.fromFile(file)
            }

            val mimeType = getMimeType(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // 检查是否有应用可以打开
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "没有应用可以打开此文件", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(name: String): String {
        return when (name.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac" -> "audio/aac"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "html", "htm" -> "text/html"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "apk" -> "application/vnd.android.package-archive"
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
            holder.ivIcon.setImageResource(getFileIcon(file))

            val size = if (file.isDirectory) {
                val count = try { file.listFiles()?.size ?: 0 } catch (e: Exception) { 0 }
                "$count 项"
            } else {
                formatSize(file.length())
            }
            val date = dateFormat.format(Date(file.lastModified()))
            holder.tvInfo.text = "$size | $date"
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount() = files.size

        private fun getFileIcon(file: File): Int {
            if (file.isDirectory) return R.drawable.ic_folder

            return when (file.extension.lowercase()) {
                "jpg", "jpeg", "png", "gif", "bmp", "webp" -> R.drawable.ic_image
                "mp4", "3gp", "mkv", "avi", "mov" -> R.drawable.ic_video
                "mp3", "wav", "flac", "aac", "ogg", "m4a" -> R.drawable.ic_music
                "pdf" -> R.drawable.ic_file
                "doc", "docx", "txt", "log" -> R.drawable.ic_file
                "xls", "xlsx" -> R.drawable.ic_file
                "ppt", "pptx" -> R.drawable.ic_file
                "zip", "rar", "7z" -> R.drawable.ic_file
                "apk" -> R.drawable.ic_apps
                else -> R.drawable.ic_file
            }
        }

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
