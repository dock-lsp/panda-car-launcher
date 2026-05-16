package com.pandora.carlauncher

import android.app.ActivityManager
import android.app.AlertDialog
import android.app.usage.UsageStatsManager
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
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 一键清理后台
 * 支持自定义选择要清理的应用
 */
class CleanBackgroundActivity : AppCompatActivity() {

    private lateinit var tvMemoryInfo: TextView
    private lateinit var rvProcesses: RecyclerView
    private lateinit var btnClean: View
    private val activityManager: ActivityManager by lazy {
        getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }
    private val pm: PackageManager by lazy { packageManager }
    private val runningApps = mutableListOf<AppProcessInfo>()
    private val selectedApps = mutableSetOf<String>()

    data class AppProcessInfo(
        val packageName: String,
        val appName: String,
        val isSystemApp: Boolean,
        var isSelected: Boolean = false
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_clean_background)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        tvMemoryInfo = findViewById(R.id.tv_memory_info)
        rvProcesses = findViewById(R.id.rv_processes)
        btnClean = findViewById(R.id.btn_clean)

        rvProcesses.layoutManager = LinearLayoutManager(this)

        updateMemoryInfo()

        // 检查是否有 UsageStats 权限（Android 5.0+ 需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (!hasUsageStatsPermission()) {
                requestUsageStatsPermission()
            } else {
                loadRunningProcesses()
            }
        } else {
            loadRunningProcessesLegacy()
        }

        btnClean.setOnClickListener {
            showCleanConfirmDialog()
        }

        findViewById<View>(R.id.btn_select_all)?.setOnClickListener {
            selectedApps.addAll(runningApps.map { it.packageName })
            runningApps.forEach { it.isSelected = true }
            (rvProcesses.adapter as? ProcessAdapter)?.notifyDataSetChanged()
        }

        findViewById<View>(R.id.btn_deselect_all)?.setOnClickListener {
            selectedApps.clear()
            runningApps.forEach { it.isSelected = false }
            (rvProcesses.adapter as? ProcessAdapter)?.notifyDataSetChanged()
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return true
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val time = System.currentTimeMillis()
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            time - 1000 * 60,
            time
        )
        return stats != null && stats.isNotEmpty()
    }

    private fun requestUsageStatsPermission() {
        AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage("清理后台功能需要\"使用情况访问权限\"来查看运行中的应用。\n\n请在设置中开启此权限。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("取消") { _, _ ->
                Toast.makeText(this, "没有权限无法显示应用列表", Toast.LENGTH_LONG).show()
            }
            .show()
    }

    private fun updateMemoryInfo() {
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val totalMem = memoryInfo.totalMem / (1024 * 1024)
        val availMem = memoryInfo.availMem / (1024 * 1024)
        val usedMem = totalMem - availMem
        val usedPercent = (usedMem * 100 / totalMem)

        tvMemoryInfo.text = "内存使用: $usedMem MB / $totalMem MB ($usedPercent%)"
    }

    private fun loadRunningProcesses() {
        runningApps.clear()

        // 系统进程白名单
        val systemPackages = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.android.phone",
            "com.android.contacts",
            "com.android.mms",
            "com.android.providers",
            "com.android.server",
            "android.process.system",
            "com.google.android.gms",
            "com.google.android.gsf",
            packageName // 保留自己
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // 使用 UsageStats 获取最近使用的应用
            val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60 * 60 * 24, // 过去24小时
                time
            )

            // 获取正在运行的服务
            val runningServices = activityManager.getRunningServices(100)
            val runningPackages = runningServices.map { it.service.packageName }.toSet()

            // 合并数据
            val allPackages = mutableSetOf<String>()
            stats?.forEach { allPackages.add(it.packageName) }
            allPackages.addAll(runningPackages)

            allPackages.forEach { pkg ->
                if (pkg !in systemPackages && !pkg.startsWith("com.android.")) {
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        runningApps.add(AppProcessInfo(pkg, appName, isSystem))
                    } catch (e: Exception) {
                        // 忽略
                    }
                }
            }
        }

        // 如果上面方法没有获取到，使用传统方法
        if (runningApps.isEmpty()) {
            loadRunningProcessesLegacy()
        }

        // 去重并排序
        val uniqueApps = runningApps.distinctBy { it.packageName }
            .sortedBy { it.appName }

        runningApps.clear()
        runningApps.addAll(uniqueApps)

        rvProcesses.adapter = ProcessAdapter(runningApps, selectedApps) { pkg, selected ->
            if (selected) selectedApps.add(pkg) else selectedApps.remove(pkg)
        }
    }

    private fun loadRunningProcessesLegacy() {
        runningApps.clear()

        // 系统进程白名单
        val systemPackages = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.launcher",
            "com.android.phone",
            "com.android.contacts",
            "com.android.mms",
            "com.android.providers",
            "com.android.server",
            "android.process.system",
            packageName
        )

        val processes = activityManager.runningAppProcesses
        val processedPackages = mutableSetOf<String>()

        processes?.forEach { process ->
            val pkg = process.processName
            if (pkg !in processedPackages &&
                pkg !in systemPackages &&
                !pkg.startsWith("com.android.")
            ) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                    runningApps.add(AppProcessInfo(pkg, appName, isSystem))
                    processedPackages.add(pkg)
                } catch (e: Exception) {
                    // 忽略
                }
            }
        }

        rvProcesses.adapter = ProcessAdapter(runningApps, selectedApps) { pkg, selected ->
            if (selected) selectedApps.add(pkg) else selectedApps.remove(pkg)
        }
    }

    private fun showCleanConfirmDialog() {
        if (selectedApps.isEmpty()) {
            Toast.makeText(this, "请先选择要清理的应用", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("确认清理")
            .setMessage("确定要清理选中的 ${selectedApps.size} 个应用吗？")
            .setPositiveButton("清理") { _, _ ->
                cleanSelectedApps()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun cleanSelectedApps() {
        var cleanedCount = 0
        selectedApps.forEach { pkg ->
            try {
                activityManager.killBackgroundProcesses(pkg)
                cleanedCount++
            } catch (e: Exception) {
                // 忽略
            }
        }

        selectedApps.clear()
        updateMemoryInfo()
        loadRunningProcesses()
        Toast.makeText(this, "已清理 $cleanedCount 个应用", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        // 检查权限后重新加载
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && hasUsageStatsPermission()) {
            loadRunningProcesses()
        }
    }

    inner class ProcessAdapter(
        private val apps: List<AppProcessInfo>,
        private val selected: MutableSet<String>,
        private val onToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<ProcessAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val cbSelect: CheckBox = view.findViewById(R.id.cb_select)
            val tvAppName: TextView = view.findViewById(R.id.tv_app_name)
            val tvPackageName: TextView = view.findViewById(R.id.tv_package_name)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_process, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvAppName.text = app.appName
            holder.tvPackageName.text = app.packageName
            holder.cbSelect.isChecked = selected.contains(app.packageName)
            holder.cbSelect.setOnCheckedChangeListener { _, isChecked ->
                onToggle(app.packageName, isChecked)
            }
            holder.itemView.setOnClickListener {
                holder.cbSelect.isChecked = !holder.cbSelect.isChecked
            }
        }

        override fun getItemCount() = apps.size
    }
}
