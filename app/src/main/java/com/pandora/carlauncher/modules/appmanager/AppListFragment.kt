package com.pandora.carlauncher.modules.appmanager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.pandora.carlauncher.R
import kotlinx.coroutines.*

/**
 * 应用列表Fragment
 * 
 * 功能：
 * - 显示已安装应用列表
 * - 应用搜索
 * - 应用卸载
 * - 显示应用详情
 */
class AppListFragment : Fragment() {

    companion object {
        private const val TAG = "AppListFragment"
        
        // 分类常量
        const val CATEGORY_ALL = 0
        const val CATEGORY_SYSTEM = 1
        const val CATEGORY_USER = 2
        const val CATEGORY_FREQUENT = 3
    }

    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // UI控件
    private lateinit var searchEditText: EditText
    private lateinit var appListView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var categoryTabs: TabLayout
    private lateinit var emptyText: TextView

    // 数据
    private val allApps = mutableListOf<AppInfo>()
    private val filteredApps = mutableListOf<AppInfo>()
    private lateinit var appAdapter: AppListAdapterFragment
    
    // 当前分类
    private var currentCategory = CATEGORY_ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_app_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupListeners()
        loadApps()
    }

    /**
     * 初始化视图
     */
    private fun initViews(view: View) {
        searchEditText = view.findViewById(R.id.et_search)
        appListView = view.findViewById(R.id.lv_apps)
        loadingProgress = view.findViewById(R.id.progress_loading)
        categoryTabs = view.findViewById(R.id.tab_category)
        emptyText = view.findViewById(R.id.tv_empty)

        // 初始化 RecyclerView
        appListView.layoutManager = LinearLayoutManager(requireContext())
        appAdapter = AppListAdapterFragment(requireContext(), filteredApps) { app ->
            showAppOptions(app)
        }
        appListView.adapter = appAdapter
        
        // 设置分类标签
        setupCategoryTabs()
    }

    /**
     * 设置分类标签
     */
    private fun setupCategoryTabs() {
        categoryTabs.addTab(categoryTabs.newTab().setText("全部"))
        categoryTabs.addTab(categoryTabs.newTab().setText("系统"))
        categoryTabs.addTab(categoryTabs.newTab().setText("用户"))
        categoryTabs.addTab(categoryTabs.newTab().setText("常用"))
        
        categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentCategory = tab?.position ?: CATEGORY_ALL
                filterApps()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 搜索框文字变化监听
        searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                filterApps()
            }
        })
    }

    /**
     * 加载应用列表
     */
    private fun loadApps() {
        loadingProgress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        scope.launch(Dispatchers.IO) {
            allApps.clear()
            
            val pm = requireContext().packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            packages.forEach { appInfo ->
                // 过滤系统应用（保留必要的系统应用）
                val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                // 只显示可启动的应用
                val launchIntent = pm.getLaunchIntentForPackage(appInfo.packageName)
                if (launchIntent != null) {
                    val app = AppInfo(
                        packageName = appInfo.packageName,
                        appName = pm.getApplicationLabel(appInfo).toString(),
                        icon = pm.getApplicationIcon(appInfo),
                        isSystemApp = isSystemApp && !isUpdatedSystemApp,
                        versionName = getAppVersion(appInfo.packageName),
                        size = getAppSize(appInfo.packageName),
                        installTime = getAppInstallTime(appInfo.packageName)
                    )
                    allApps.add(app)
                }
            }

            // 按名称排序
            allApps.sortBy { it.appName }

            withContext(Dispatchers.Main) {
                filterApps()
                loadingProgress.visibility = View.GONE
                
                if (allApps.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                    emptyText.text = "未找到应用"
                }
            }
        }
    }

    /**
     * 获取应用版本
     */
    private fun getAppVersion(packageName: String): String {
        return try {
            val pInfo = requireContext().packageManager.getPackageInfo(packageName, 0)
            pInfo.versionName ?: "未知"
        } catch (e: Exception) {
            "未知"
        }
    }

    /**
     * 获取应用大小
     */
    private fun getAppSize(packageName: String): Long {
        return try {
            val appInfo = requireContext().packageManager.getApplicationInfo(packageName, 0)
            java.io.File(appInfo.sourceDir).length()
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取应用安装时间
     */
    private fun getAppInstallTime(packageName: String): Long {
        return try {
            val pInfo = requireContext().packageManager.getPackageInfo(packageName, 0)
            pInfo.firstInstallTime
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 过滤应用列表
     */
    private fun filterApps() {
        filteredApps.clear()
        
        val searchText = searchEditText.text.toString().lowercase()
        
        allApps.forEach { app ->
            // 分类过滤
            val matchCategory = when (currentCategory) {
                CATEGORY_ALL -> true
                CATEGORY_SYSTEM -> app.isSystemApp
                CATEGORY_USER -> !app.isSystemApp
                CATEGORY_FREQUENT -> true  // TODO: 实现常用应用逻辑
                else -> true
            }
            
            // 搜索过滤
            val matchSearch = searchText.isEmpty() ||
                    app.appName.lowercase().contains(searchText) ||
                    app.packageName.lowercase().contains(searchText)
            
            if (matchCategory && matchSearch) {
                filteredApps.add(app)
            }
        }
        
        appAdapter.notifyDataSetChanged()
        
        if (filteredApps.isEmpty()) {
            emptyText.visibility = View.VISIBLE
            emptyText.text = "未找到匹配的应用"
        } else {
            emptyText.visibility = View.GONE
        }
    }

    /**
     * 显示应用选项对话框
     */
    private fun showAppOptions(app: AppInfo) {
        val options = mutableListOf<String>()
        options.add("打开")
        
        // 非系统应用可以卸载
        if (!app.isSystemApp) {
            options.add("卸载")
        }
        
        options.add("应用信息")

        AlertDialog.Builder(requireContext())
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
            val intent = requireContext().packageManager
                .getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开应用", Toast.LENGTH_SHORT).show()
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
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:${app.packageName}")
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法显示应用信息", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}

/**
 * 应用列表适配器 (RecyclerView 版本)
 */
class AppListAdapterFragment(
    private val context: Context,
    private val apps: List<AppInfo>,
    private val onItemClick: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapterFragment.ViewHolder>() {

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
        
        if (app.isSystemApp) {
            holder.systemBadge.visibility = View.VISIBLE
            holder.systemBadge.text = "系统"
        } else {
            holder.systemBadge.visibility = View.GONE
        }
        
        holder.itemView.setOnClickListener {
            onItemClick(app)
        }
    }

    override fun getItemCount(): Int = apps.size
}
