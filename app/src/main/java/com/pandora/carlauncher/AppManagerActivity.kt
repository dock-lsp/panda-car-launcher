package com.pandora.carlauncher

import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppManagerActivity : AppCompatActivity() {

    private lateinit var rvAppList: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var adapter: AppAdapter
    private lateinit var gridLayoutManager: GridLayoutManager
    private var allApps = mutableListOf<AppRecognizer.AppInfo>()
    private var currentCategory: AppRecognizer.AppCategory? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_manager)

        rvAppList = findViewById(R.id.rv_app_list)
        etSearch = findViewById(R.id.et_search)

        val spanCount = getSpanCount()
        gridLayoutManager = GridLayoutManager(this, spanCount)
        rvAppList.layoutManager = gridLayoutManager
        adapter = AppAdapter()
        rvAppList.adapter = adapter

        loadApps()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener {
            finish()
        }

        setupCategoryTabs()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        gridLayoutManager.spanCount = getSpanCount()
    }

    private fun getSpanCount(): Int {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            5
        } else {
            4
        }
    }

    private fun setupCategoryTabs() {
        val tabs = mapOf(
            R.id.tab_all to null,
            R.id.tab_navigation to AppRecognizer.AppCategory.NAVIGATION,
            R.id.tab_music to AppRecognizer.AppCategory.MUSIC,
            R.id.tab_video to AppRecognizer.AppCategory.VIDEO,
            R.id.tab_tool to AppRecognizer.AppCategory.TOOL
        )

        for ((tabId, category) in tabs) {
            findViewById<TextView>(tabId)?.setOnClickListener {
                currentCategory = category
                for ((id, _) in tabs) {
                    val tab = findViewById<TextView>(id)
                    tab?.setTextColor(if (id == tabId) getColor(R.color.primary) else getColor(R.color.text_secondary))
                    tab?.setBackgroundResource(if (id == tabId) R.drawable.bg_icon_cyan else R.drawable.bg_card)
                }
                filterApps(etSearch.text.toString())
            }
        }
    }

    private fun loadApps() {
        allApps.clear()
        allApps.addAll(AppRecognizer.getAllInstalledApps(this))
        adapter.notifyDataSetChanged()
    }

    private fun filterApps(query: String) {
        val filtered = if (query.isBlank()) {
            if (currentCategory != null) {
                allApps.filter { it.category == currentCategory }
            } else {
                allApps
            }
        } else {
            val searchResult = AppRecognizer.searchApps(this, query)
            if (currentCategory != null) {
                searchResult.filter { it.category == currentCategory }
            } else {
                searchResult
            }
        }
        adapter.updateData(filtered)
    }

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        private var apps = mutableListOf<AppRecognizer.AppInfo>()

        fun updateData(newApps: List<AppRecognizer.AppInfo>) {
            apps.clear()
            apps.addAll(newApps)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_app_grid_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.bind(app)
        }

        override fun getItemCount(): Int = apps.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivIcon: ImageView = itemView.findViewById(R.id.iv_app_icon)
            private val tvName: TextView = itemView.findViewById(R.id.tv_app_name)

            fun bind(app: AppRecognizer.AppInfo) {
                tvName.text = app.appName
                
                if (app.icon != null) {
                    ivIcon.setImageDrawable(app.icon)
                } else {
                    ivIcon.setImageResource(R.drawable.ic_apps)
                }

                itemView.setOnClickListener {
                    openApp(app.packageName)
                }

                itemView.setOnLongClickListener {
                    showAppOptions(app)
                    true
                }
            }
        }
    }

    private fun showAppOptions(app: AppRecognizer.AppInfo) {
        val options = arrayOf("打开应用", "添加到桌面", "卸载应用")
        AlertDialog.Builder(this)
            .setTitle(app.appName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openApp(app.packageName)
                    1 -> addToDesktop(app)
                    2 -> uninstallApp(app)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开应用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToDesktop(app: AppRecognizer.AppInfo) {
        val prefs = getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE)
        val customAppsJson = prefs.getString(MainActivity.KEY_CUSTOM_APPS, "[]") ?: "[]"
        
        if (customAppsJson.contains("\"packageName\":\"${app.packageName}\"")) {
            Toast.makeText(this, "该应用已在桌面", Toast.LENGTH_SHORT).show()
            return
        }
        
        val count = org.json.JSONArray(customAppsJson).length()
        if (count >= MainActivity.MAX_CUSTOM_APPS) {
            Toast.makeText(this, R.string.custom_app_max_reached, Toast.LENGTH_SHORT).show()
            return
        }

        val jsonArray = org.json.JSONArray(customAppsJson)
        val obj = org.json.JSONObject()
        obj.put("packageName", app.packageName)
        obj.put("appName", app.appName)
        jsonArray.put(obj)
        
        prefs.edit().putString(MainActivity.KEY_CUSTOM_APPS, jsonArray.toString()).apply()
        Toast.makeText(this, "已添加: ${app.appName}", Toast.LENGTH_SHORT).show()
    }

    private fun uninstallApp(app: AppRecognizer.AppInfo) {
        AlertDialog.Builder(this)
            .setTitle("卸载应用")
            .setMessage("确定要卸载 ${app.appName} 吗？")
            .setPositiveButton("确定") { _, _ ->
                try {
                    val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "无法卸载此应用", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
