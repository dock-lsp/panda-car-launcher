package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 歌词悬浮窗口
 * 显示在地图APP之上
 */
class FloatingLyricsWindow(private val context: Context) {

    companion object {
        private const val TAG = "FloatingLyrics"
        @Volatile var isShowing = false
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    // 拖动
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (floatingView != null) return

        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.layout_floating_lyrics, null)

        val dm = context.resources.displayMetrics
        val width = (dm.widthPixels * 0.35).toInt()
        val height = ViewGroup.LayoutParams.WRAP_CONTENT

        params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dm.heightPixels / 4 // 显示在屏幕上方1/4处
        }

        try {
            windowManager.addView(floatingView, params)
            isShowing = true
            Log.d(TAG, "歌词悬浮窗已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示失败", e)
            return
        }

        // 拖动
        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.x = initialX + (event.rawX - initialTouchX).toInt()
                    params?.y = initialY + (event.rawY - initialTouchY).toInt()
                    try { windowManager.updateViewLayout(floatingView, params) } catch (_: Exception) {}
                    true
                }
                else -> false
            }
        }

        // 关闭按钮
        floatingView?.findViewById<ImageView>(R.id.lyrics_close)?.setOnClickListener {
            hide()
        }

        // 播放控制
        floatingView?.findViewById<ImageView>(R.id.lyrics_prev)?.setOnClickListener {
            sendMediaAction("prev")
        }
        floatingView?.findViewById<ImageView>(R.id.lyrics_play)?.setOnClickListener {
            sendMediaAction("play_pause")
        }
        floatingView?.findViewById<ImageView>(R.id.lyrics_next)?.setOnClickListener {
            sendMediaAction("next")
        }

        // 开始刷新
        startRefresh()
    }

    fun hide() {
        stopRefresh()
        floatingView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
        isShowing = false
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateLyrics()
            handler.postDelayed(this, 1000)
        }
    }

    private fun startRefresh() {
        handler.post(refreshRunnable)
    }

    private fun stopRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun updateLyrics() {
        val title = MusicNotificationListener.currentTitle
        val artist = MusicNotificationListener.currentArtist
        val isPlaying = MusicNotificationListener.isPlaying

        floatingView?.findViewById<TextView>(R.id.lyrics_title)?.text =
            if (title.isNotEmpty()) title else "未在播放"
        floatingView?.findViewById<TextView>(R.id.lyrics_artist)?.text = artist
        floatingView?.findViewById<ImageView>(R.id.lyrics_play)?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        // 歌词（模拟）
        val lyricsText = if (title.isNotEmpty()) {
            "♪ 正在播放 ♪\n$title\n- $artist -"
        } else {
            "点击播放音乐"
        }
        floatingView?.findViewById<TextView>(R.id.lyrics_text)?.text = lyricsText
    }

    private fun sendMediaAction(action: String) {
        val controller = MusicNotificationListener.activeMediaController
        if (controller != null) {
            when (action) {
                "prev" -> controller.transportControls?.skipToPrevious()
                "next" -> controller.transportControls?.skipToNext()
                "play_pause" -> {
                    if (MusicNotificationListener.isPlaying) {
                        controller.transportControls?.pause()
                    } else {
                        controller.transportControls?.play()
                    }
                }
            }
        }
    }
}
