package com.pandora.carlauncher.modules.appmanager

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pandora.carlauncher.R
import com.pandora.carlauncher.databinding.ActivityAppManagerBinding
import kotlinx.coroutines.*

/**
 * 应用信息数据类
 */
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable,
    val isSystemApp: Boolean,
    val versionName: String,
    val size: Long,
    val installTime: Long = 0L
)

/**
 * 应用管理器Activity
 * 
 * 提供应用管理功能：
 * - 应用列表
 * - 应用详情
 * - 卸载应用
 * - 强制停止
 */
class AppManagerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AppManagerActivity"
        const val FILTER_ALL = 0
        const val FILTER_SYSTEM = 1
        const val FILTER_USER = 2
    }
    
    private lateinit var binding: ActivityAppManagerBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val allApps = mutableListOf<AppInfo>()
    private val filteredApps = mutableListOf<AppInfo>()
    private lateinit var adapter: AppListAdapter
    
    private var currentFilter = FILTER_ALL

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupListeners()
        loadApps()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        adapter = AppListAdapter(this, filteredApps)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        // 筛选按钮
        binding.chipAll.setOnClickListener { filterApps(FILTER_ALL) }
        binding.chipSystem.setOnClickListener { filterApps(FILTER_SYSTEM) }
        binding.chipUser.setOnClickListener { filterApps(FILTER_USER) }
        
        // 搜索框
        binding.searchView.setOnQueryTextListener(object : androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                filterApps(currentFilter)
                return true
            }
            
            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(currentFilter)
                return true
            }
        })
    }

    /**
     * 加载应用列表
     */
    private fun loadApps() {
        binding.progressBar.visibility = View.VISIBLE
        
        scope.launch(Dispatchers.IO) {
            allApps.clear()
            
            val pm = packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            
            packages.forEach { appInfo ->
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    allApps.add(AppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        versionName = getVersionName(appInfo.packageName),
                        size = getApkSize(appInfo.packageName),
                        installTime = getInstallTime(appInfo.packageName)
                    ))
                }
            }
            
            allApps.sortBy { it.appName }
            
            withContext(Dispatchers.Main) {
                filterApps(currentFilter)
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    /**
     * 过滤应用
     */
    private fun filterApps(filter: Int) {
        currentFilter = filter
        
        val searchText = binding.searchView.query.toString().lowercase()
        
        filteredApps.clear()
        allApps.forEach { app ->
            val matchFilter = when (filter) {
                FILTER_ALL -> true
                FILTER_SYSTEM -> app.isSystemApp
                FILTER_USER -> !app.isSystemApp
                else -> true
            }
            
            val matchSearch = searchText.isEmpty() ||
                    app.appName.lowercase().contains(searchText) ||
                    app.packageName.lowercase().contains(searchText)
            
            if (matchFilter && matchSearch) {
                filteredApps.add(app)
            }
        }
        
        adapter.notifyDataSetChanged()
        
        // 更新统计
        binding.tvStats.text = "共 ${filteredApps.size} 个应用"
    }

    /**
     * 获取版本名
     */
    private fun getVersionName(packageName: String): String {
        return try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    /**
     * 获取APK大小
     */
    private fun getApkSize(packageName: String): Long {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            java.io.File(appInfo.sourceDir).length()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取安装时间
     */
    private fun getInstallTime(packageName: String): Long {
        return try {
            packageManager.getPackageInfo(packageName, 0).firstInstallTime
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 显示应用选项
     */
    fun showAppOptions(app: AppInfo) {
        val options = mutableListOf<String>()
        options.add("打开")
        options.add("应用信息")
        
        if (!app.isSystemApp) {
            options.add("卸载")
        }
        
        AlertDialog.Builder(this)
            .setTitle(app.appName)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "打开" -> openApp(app)
                    "卸载" -> uninstallApp(app)
                    "应用信息" -> showAppInfo(app)
                }
            }
            .show()
    }

    /**
     * 打开应用
     */
    private fun openApp(app: AppInfo) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开应用", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 卸载应用
     */
    private fun uninstallApp(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = android.net.Uri.parse("package:${app.packageName}")
        startActivity(intent)
    }

    /**
     * 显示应用信息
     */
    private fun showAppInfo(app: AppInfo) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${app.packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "无法显示应用信息", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadApps()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

/**
 * 应用列表适配器
 */
class AppListAdapter(
    private val context: android.content.Context,
    private val apps: List<AppInfo>
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.iv_app_icon)
        val name: TextView = view.findViewById(R.id.tv_app_name)
        val packageName: TextView = view.findViewById(R.id.tv_package_name)
        val systemBadge: TextView = view.findViewById(R.id.tv_system_badge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.packageName.text = app.packageName
        holder.systemBadge.visibility = if (app.isSystemApp) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener {
            (context as? AppManagerActivity)?.showAppOptions(app)
        }
    }

    override fun getItemCount(): Int = apps.size
}
