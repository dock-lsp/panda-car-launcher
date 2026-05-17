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
 * 悬浮音乐窗口
 * 显示真实音乐信息 + 歌词
 */
class FloatingMusicWindow(private val context: Context) {

    companion object {
        private const val TAG = "FloatingMusicWindow"
        @Volatile var isShowing = false
    }

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show() {
        if (floatingView != null) return

        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.layout_floating_music_window, null)

        val dm = context.resources.displayMetrics
        val width = (dm.widthPixels * 0.25).toInt()
        val height = (dm.heightPixels * 0.45).toInt()

        params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = (dm.heightPixels - height) / 2
        }

        try {
            windowManager.addView(floatingView, params)
            isShowing = true
            Log.d(TAG, "悬浮音乐窗口已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗口失败", e)
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
        floatingView?.findViewById<ImageView>(R.id.float_music_close)?.setOnClickListener {
            hide()
        }

        // 播放控制
        floatingView?.findViewById<ImageView>(R.id.float_music_prev)?.setOnClickListener {
            sendMediaAction("prev")
        }
        floatingView?.findViewById<ImageView>(R.id.float_music_play)?.setOnClickListener {
            sendMediaAction("play_pause")
        }
        floatingView?.findViewById<ImageView>(R.id.float_music_next)?.setOnClickListener {
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
        Log.d(TAG, "悬浮音乐窗口已隐藏")
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateMusicInfo()
            handler.postDelayed(this, 1000)
        }
    }

    private fun startRefresh() {
        handler.post(refreshRunnable)
    }

    private fun stopRefresh() {
        handler.removeCallbacks(refreshRunnable)
    }

    private fun updateMusicInfo() {
        val title = MusicNotificationListener.currentTitle
        val artist = MusicNotificationListener.currentArtist
        val isPlaying = MusicNotificationListener.isPlaying

        floatingView?.findViewById<TextView>(R.id.float_music_title)?.text = 
            if (title.isNotEmpty()) title else "未在播放"
        floatingView?.findViewById<TextView>(R.id.float_music_artist)?.text = artist
        floatingView?.findViewById<ImageView>(R.id.float_music_play)?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        
        // 歌词显示（如果有）
        // floatingView?.findViewById<TextView>(R.id.float_music_lyrics)?.text = getLyrics()
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
        } else {
            // 兼容方案：发送媒体按钮广播
            val pkg = MusicNotificationListener.currentPackageName
            if (pkg.isNotEmpty()) {
                try {
                    val intent = android.content.Intent("android.intent.action.MEDIA_BUTTON").apply {
                        putExtra("android.intent.extra.KEY_EVENT", when(action) {
                            "prev" -> android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            "next" -> android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                            else -> android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, 
                                if (MusicNotificationListener.isPlaying) android.view.KeyEvent.KEYCODE_MEDIA_PAUSE 
                                else android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                        })
                        setPackage(pkg)
                    }
                    context.sendBroadcast(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
