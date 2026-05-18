package com.pandora.carlauncher

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class MusicAppsAdapter(
    private val context: Context,
    private val apps: List<MusicAppInfo>,
    private val selectedPackage: String?,
    private val onAppSelected: (MusicAppInfo) -> Unit
) : RecyclerView.Adapter<MusicAppsAdapter.ViewHolder>() {

    data class MusicAppInfo(
        val packageName: String,
        val appName: String,
        val icon: android.graphics.drawable.Drawable
    )

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.app_icon)
        val name: TextView = view.findViewById(R.id.app_name)
        val check: ImageView = view.findViewById(R.id.app_check)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_music_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.icon.setImageDrawable(app.icon)
        holder.name.text = app.appName
        holder.check.visibility = if (app.packageName == selectedPackage) View.VISIBLE else View.GONE
        
        holder.itemView.setOnClickListener {
            onAppSelected(app)
        }
    }

    override fun getItemCount() = apps.size
}
