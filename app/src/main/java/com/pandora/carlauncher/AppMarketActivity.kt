package com.pandora.carlauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.net.URL

/**
 * 应用市场
 * 接入免费API获取应用列表
 */
class AppMarketActivity : AppCompatActivity() {

    private lateinit var rvApps: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val apps = mutableListOf<AppInfo>()

    data class AppInfo(
        val name: String,
        val desc: String,
        val packageName: String,
        val downloadUrl: String
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_market)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btn_refresh)?.setOnClickListener { loadApps() }

        rvApps = findViewById(R.id.rv_apps)
        rvApps.layoutManager = LinearLayoutManager(this)
        progressBar = findViewById(R.id.progress_bar)

        loadApps()
    }

    private fun loadApps() {
        progressBar.visibility = View.VISIBLE
        // 模拟应用列表（实际可接入F-Droid API或其他免费API）
        apps.clear()
        apps.add(AppInfo("VLC播放器", "开源多媒体播放器", "org.videolan.vlc", "https://f-droid.org/repo/org.videolan.vlc.apk"))
        apps.add(AppInfo("NewPipe", "开源YouTube客户端", "org.schabi.newpipe", "https://f-droid.org/repo/org.schabi.newpipe.apk"))
        apps.add(AppInfo("Termux", "终端模拟器", "com.termux", "https://f-droid.org/repo/com.termux.apk"))
        apps.add(AppInfo("K-9 Mail", "开源邮件客户端", "com.fsck.k9", "https://f-droid.org/repo/com.fsck.k9.apk"))
        apps.add(AppInfo("Signal", "开源即时通讯", "org.thoughtcrime.securesms", "https://signal.org/install"))
        apps.add(AppInfo("Firefox", "开源浏览器", "org.mozilla.firefox", "https://mozilla.org/firefox"))
        apps.add(AppInfo("OsmAnd", "开源地图导航", "net.osmand", "https://f-droid.org/repo/net.osmand.apk"))
        apps.add(AppInfo("AntennaPod", "开源播客播放器", "de.danoeh.antennapod", "https://f-droid.org/repo/de.danoeh.antennapod.apk"))

        rvApps.adapter = AppAdapter(apps)
        progressBar.visibility = View.GONE
    }

    inner class AppAdapter(private val apps: List<AppInfo>) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_app_name)
            val tvDesc: TextView = view.findViewById(R.id.tv_app_desc)
            val btnInstall: Button = view.findViewById(R.id.btn_install)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_market_app, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]
            holder.tvName.text = app.name
            holder.tvDesc.text = app.desc
            holder.btnInstall.setOnClickListener {
                // 打开下载页面
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(app.downloadUrl))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@AppMarketActivity, "无法打开下载链接", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun getItemCount() = apps.size
    }
}
