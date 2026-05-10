package com.pandora.carlauncher.modules.filemanager

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pandora.carlauncher.R
import com.pandora.carlauncher.databinding.ActivityFileManagerBinding
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件管理器Activity
 * 
 * 功能：
 * - 目录浏览
 * - 文件分类查看
 * - 复制、粘贴、删除、重命名
 * - 新建文件夹
 */
class FileManagerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FileManagerActivity"
        
        // 文件类型
        const val TYPE_ALL = 0
        const val TYPE_IMAGE = 1
        const val TYPE_AUDIO = 2
        const val TYPE_VIDEO = 3
        const val TYPE_DOCUMENT = 4
    }

    private lateinit var binding: ActivityFileManagerBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 当前路径
    private var currentPath: File = Environment.getExternalStorageDirectory()
    
    // 文件列表
    private val fileList = mutableListOf<FileItem>()
    private lateinit var adapter: FileListAdapter
    
    // 剪切板操作
    private var isCutMode = false
    private var clipboardFiles = mutableListOf<File>()
    
    // 文件类型过滤器
    private var currentType = TYPE_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityFileManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupListeners()
        loadFiles()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        // 路径显示
        updatePathDisplay()
        
        // 初始化列表
        adapter = FileListAdapter(fileList) { fileItem, action ->
            handleFileAction(fileItem, action)
        }
        
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回上级目录
        binding.btnBack.setOnClickListener {
            navigateUp()
        }
        
        // 主页
        binding.btnHome.setOnClickListener {
            navigateToHome()
        }
        
        // 新建文件夹
        binding.btnNewFolder.setOnClickListener {
            showNewFolderDialog()
        }
        
        // 粘贴
        binding.btnPaste.setOnClickListener {
            pasteFiles()
        }
        
        // 排序
        binding.btnSort.setOnClickListener {
            showSortDialog()
        }
    }

    /**
     * 加载文件列表
     */
    private fun loadFiles() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE
        
        scope.launch(Dispatchers.IO) {
            fileList.clear()
            
            try {
                val files = currentPath.listFiles()?.toList() ?: emptyList()
                
                files.forEach { file ->
                    if (shouldIncludeFile(file)) {
                        fileList.add(FileItem(
                            name = file.name,
                            path = file.absolutePath,
                            isDirectory = file.isDirectory,
                            size = file.length(),
                            lastModified = file.lastModified()
                        ))
                    }
                }
                
                // 排序：文件夹在前，然后按名称
                fileList.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                
            } catch (e: Exception) {
                Log.e(TAG, "加载文件失败", e)
            }
            
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                adapter.notifyDataSetChanged()
                
                if (fileList.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "此目录为空"
                }
            }
        }
    }

    /**
     * 检查文件是否应该包含
     */
    private fun shouldIncludeFile(file: File): Boolean {
        if (currentType == TYPE_ALL) return true
        if (file.isDirectory) return true
        
        val name = file.name.lowercase()
        return when (currentType) {
            TYPE_IMAGE -> name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                         name.endsWith(".png") || name.endsWith(".gif") ||
                         name.endsWith(".bmp") || name.endsWith(".webp")
            TYPE_AUDIO -> name.endsWith(".mp3") || name.endsWith(".wav") ||
                         name.endsWith(".flac") || name.endsWith(".aac") ||
                         name.endsWith(".ogg") || name.endsWith(".m4a")
            TYPE_VIDEO -> name.endsWith(".mp4") || name.endsWith(".avi") ||
                         name.endsWith(".mkv") || name.endsWith(".mov") ||
                         name.endsWith(".wmv") || name.endsWith(".flv")
            TYPE_DOCUMENT -> name.endsWith(".pdf") || name.endsWith(".doc") ||
                            name.endsWith(".docx") || name.endsWith(".txt") ||
                            name.endsWith(".xls") || name.endsWith(".xlsx") ||
                            name.endsWith(".ppt") || name.endsWith(".pptx")
            else -> true
        }
    }

    /**
     * 设置文件类型
     */
    private fun setFileType(type: Int) {
        currentType = type
        loadFiles()
    }

    /**
     * 处理文件操作
     */
    private fun handleFileAction(fileItem: FileItem, action: String) {
        when (action) {
            "open" -> openFile(fileItem)
            "delete" -> deleteFile(fileItem)
            "rename" -> renameFile(fileItem)
            "copy" -> copyFile(fileItem)
            "cut" -> cutFile(fileItem)
            "info" -> showFileInfo(fileItem)
        }
    }

    /**
     * 打开文件或目录
     */
    private fun openFile(fileItem: FileItem) {
        val file = File(fileItem.path)
        
        if (file.isDirectory) {
            currentPath = file
            updatePathDisplay()
            loadFiles()
        } else {
            openWithSystemApp(file)
        }
    }

    /**
     * 使用系统应用打开文件
     */
    private fun openWithSystemApp(file: File) {
        try {
            val mimeType = getMimeType(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(file), mimeType)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开此文件", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 获取MIME类型
     */
    private fun getMimeType(fileName: String): String {
        val name = fileName.lowercase()
        return when {
            name.endsWith(".mp3") -> "audio/mpeg"
            name.endsWith(".mp4") -> "video/mp4"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".txt") -> "text/plain"
            name.endsWith(".html") -> "text/html"
            else -> "*/*"
        }
    }

    /**
     * 导航到上级目录
     */
    private fun navigateUp() {
        val parent = currentPath.parentFile
        if (parent != null) {
            currentPath = parent
            updatePathDisplay()
            loadFiles()
        }
    }

    /**
     * 导航到主页
     */
    private fun navigateToHome() {
        currentPath = Environment.getExternalStorageDirectory()
        updatePathDisplay()
        loadFiles()
    }

    /**
     * 更新路径显示
     */
    private fun updatePathDisplay() {
        binding.tvPath.text = currentPath.absolutePath
        
        // 启用/禁用返回按钮
        binding.btnBack.isEnabled = currentPath.parent != null
    }

    /**
     * 显示新建文件夹对话框
     */
    private fun showNewFolderDialog() {
        val editText = EditText(this).apply {
            hint = "文件夹名称"
        }
        
        AlertDialog.Builder(this)
            .setTitle("新建文件夹")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString()
                if (name.isNotBlank()) {
                    createNewFolder(name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 创建新文件夹
     */
    private fun createNewFolder(name: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val newFolder = File(currentPath, name)
                if (newFolder.mkdir()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileManagerActivity, "文件夹创建成功", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileManagerActivity, "文件夹创建失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "创建文件夹失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, "创建失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 删除文件
     */
    private fun deleteFile(fileItem: FileItem) {
        AlertDialog.Builder(this)
            .setTitle("删除")
            .setMessage("确定要删除 ${fileItem.name} 吗？此操作不可撤销。")
            .setPositiveButton("删除") { _, _ ->
                performDelete(fileItem)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行删除
     */
    private fun performDelete(fileItem: FileItem) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(fileItem.path)
                val success = if (file.isDirectory) {
                    file.deleteRecursively()
                } else {
                    file.delete()
                }
                
                withContext(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(this@FileManagerActivity, "删除成功", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    } else {
                        Toast.makeText(this@FileManagerActivity, "删除失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FileManagerActivity, "删除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * 重命名文件
     */
    private fun renameFile(fileItem: FileItem) {
        val editText = EditText(this).apply {
            setText(fileItem.name)
        }
        
        AlertDialog.Builder(this)
            .setTitle("重命名")
            .setView(editText)
            .setPositiveButton("确定") { _, _ ->
                val newName = editText.text.toString()
                if (newName.isNotBlank()) {
                    performRename(fileItem, newName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行重命名
     */
    private fun performRename(fileItem: FileItem, newName: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val file = File(fileItem.path)
                val newFile = File(file.parent, newName)
                
                if (file.renameTo(newFile)) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileManagerActivity, "重命名成功", Toast.LENGTH_SHORT).show()
                        loadFiles()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FileManagerActivity, "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "重命名失败", e)
            }
        }
    }

    /**
     * 复制文件
     */
    private fun copyFile(fileItem: FileItem) {
        clipboardFiles.clear()
        clipboardFiles.add(File(fileItem.path))
        isCutMode = false
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        updatePasteButton()
    }

    /**
     * 剪切文件
     */
    private fun cutFile(fileItem: FileItem) {
        clipboardFiles.clear()
        clipboardFiles.add(File(fileItem.path))
        isCutMode = true
        Toast.makeText(this, "已剪切到剪贴板", Toast.LENGTH_SHORT).show()
        updatePasteButton()
    }

    /**
     * 粘贴文件
     */
    private fun pasteFiles() {
        if (clipboardFiles.isEmpty()) return
        
        scope.launch(Dispatchers.IO) {
            clipboardFiles.forEach { sourceFile ->
                try {
                    val destFile = File(currentPath, sourceFile.name)
                    
                    if (isCutMode) {
                        sourceFile.renameTo(destFile)
                    } else {
                        if (sourceFile.isDirectory) {
                            sourceFile.copyRecursively(destFile)
                        } else {
                            sourceFile.copyTo(destFile)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "粘贴失败: ${sourceFile.name}", e)
                }
            }
            
            clipboardFiles.clear()
            
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FileManagerActivity, "粘贴完成", Toast.LENGTH_SHORT).show()
                updatePasteButton()
                loadFiles()
            }
        }
    }

    /**
     * 更新粘贴按钮状态
     */
    private fun updatePasteButton() {
        binding.btnPaste.isEnabled = clipboardFiles.isNotEmpty()
    }

    /**
     * 显示排序对话框
     */
    private fun showSortDialog() {
        val options = arrayOf("名称升序", "名称降序", "大小升序", "大小降序", "修改时间升序", "修改时间降序")
        
        AlertDialog.Builder(this)
            .setTitle("排序方式")
            .setItems(options) { _, which ->
                sortFiles(which)
            }
            .show()
    }

    /**
     * 排序文件
     */
    private fun sortFiles(mode: Int) {
        when (mode) {
            0 -> fileList.sortBy { it.name.lowercase() }
            1 -> fileList.sortByDescending { it.name.lowercase() }
            2 -> fileList.sortBy { it.size }
            3 -> fileList.sortByDescending { it.size }
            4 -> fileList.sortBy { it.lastModified }
            5 -> fileList.sortByDescending { it.lastModified }
        }
        
        // 文件夹始终在前
        fileList.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        
        adapter.notifyDataSetChanged()
    }

    /**
     * 显示文件信息
     */
    private fun showFileInfo(fileItem: FileItem) {
        val file = File(fileItem.path)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        
        val info = buildString {
            appendLine("名称: ${fileItem.name}")
            appendLine("类型: ${if (fileItem.isDirectory) "文件夹" else "文件"}")
            appendLine("大小: ${formatFileSize(fileItem.size)}")
            appendLine("路径: ${fileItem.path}")
            appendLine("创建时间: ${dateFormat.format(Date(fileItem.lastModified))}")
            appendLine("修改时间: ${dateFormat.format(Date(file.lastModified()))}")
            if (fileItem.isDirectory) {
                appendLine("包含: ${file.listFiles()?.size ?: 0} 个项目")
            }
        }
        
        AlertDialog.Builder(this)
            .setTitle("文件信息")
            .setMessage(info)
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.2f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    override fun onBackPressed() {
        if (currentPath.parent != null) {
            navigateUp()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

/**
 * 文件项数据类
 */
data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * 文件列表适配器
 */
class FileListAdapter(
    private val files: List<FileItem>,
    private val onAction: (FileItem, String) -> Unit
) : RecyclerView.Adapter<FileListAdapter.ViewHolder>() {

    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val icon: android.widget.ImageView = view.findViewById(R.id.iv_icon)
        val name: android.widget.TextView = view.findViewById(R.id.tv_name)
        val info: android.widget.TextView = view.findViewById(R.id.tv_info)
        val moreButton: android.widget.ImageButton = view.findViewById(R.id.btn_more)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = files[position]
        
        holder.icon.setImageResource(
            if (item.isDirectory) android.R.drawable.ic_menu_agenda
            else android.R.drawable.ic_menu_edit
        )
        
        holder.name.text = item.name
        
        holder.info.text = if (item.isDirectory) {
            "文件夹"
        } else {
            formatSize(item.size)
        }
        
        holder.itemView.setOnClickListener { onAction(item, "open") }
        holder.moreButton.setOnClickListener { showContextMenu(holder.itemView, item) }
    }

    private fun showContextMenu(view: android.view.View, item: FileItem) {
        val popup = android.widget.PopupMenu(view.context, view)
        popup.menu.add(0, 1, 0, "复制")
        popup.menu.add(0, 2, 0, "剪切")
        if (!item.isDirectory) {
            popup.menu.add(0, 3, 0, "打开方式")
        }
        popup.menu.add(0, 4, 0, "重命名")
        popup.menu.add(0, 5, 0, "删除")
        popup.menu.add(0, 6, 0, "详情")
        
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> onAction(item, "copy")
                2 -> onAction(item, "cut")
                3 -> onAction(item, "open")
                4 -> onAction(item, "rename")
                5 -> onAction(item, "delete")
                6 -> onAction(item, "info")
            }
            true
        }
        
        popup.show()
    }

    override fun getItemCount(): Int = files.size

    private fun formatSize(size: Long): String {
        if (size < 1024) return "$size B"
        val kb = size / 1024.0
        if (kb < 1024) return String.format("%.2f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }
}
