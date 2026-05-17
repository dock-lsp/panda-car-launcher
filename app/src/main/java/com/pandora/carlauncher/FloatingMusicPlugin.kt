package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.*

/**
 * 音乐悬浮插件
 * 通过 MediaSession/NotificationListener 监听所有音乐 APP
 */
class FloatingMusicPlugin : Service() {

    companion object {
        private const val TAG = "FloatingMusicPlugin"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "floating_music"
        const val ACTION_SHOW = "action_show"
        const val ACTION_HIDE = "action_hide"
        const val ACTION_CLOSE = "action_close"
        
        @Volatile var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_HIDE, ACTION_CLOSE -> {
                hideFloatingCard()
                if (intent?.action == ACTION_CLOSE) stopSelf()
            }
            else -> showFloatingCard()
        }
        return START_STICKY
    }

    private fun showFloatingCard() {
        startForeground(NOTIFICATION_ID, buildNotification())
        isRunning = true
        if (floatingView == null) createFloatingCard()
        startRefresh()
    }

    private fun hideFloatingCard() {
        isRunning = false
        stopRefresh()
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingCard() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_music_card, null)

        val dm = resources.displayMetrics
        val width = (dm.widthPixels * 0.22).toInt()
        val height = (dm.heightPixels * 0.5).toInt()

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
            x = 16
            y = (dm.heightPixels - height) / 2
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮卡片失败", e)
            return
        }

        // 关闭按钮
        floatingView?.findViewById<ImageView>(R.id.music_card_close)?.setOnClickListener {
            hideFloatingCard()
            stopSelf()
        }

        // 播放控制
        floatingView?.findViewById<ImageView>(R.id.music_card_prev)?.setOnClickListener {
            sendMediaAction("prev")
        }
        floatingView?.findViewById<ImageView>(R.id.music_card_play)?.setOnClickListener {
            sendMediaAction("play_pause")
        }
        floatingView?.findViewById<ImageView>(R.id.music_card_next)?.setOnClickListener {
            sendMediaAction("next")
        }
    }

    private fun startRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                updateMusicInfo()
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun stopRefresh() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
    }

    private fun updateMusicInfo() {
        val title = MusicNotificationListener.currentTitle
        val artist = MusicNotificationListener.currentArtist
        val isPlaying = MusicNotificationListener.isPlaying

        handler.post {
            floatingView?.findViewById<TextView>(R.id.music_card_title)?.text = 
                if (title.isNotEmpty()) title else "未在播放"
            floatingView?.findViewById<TextView>(R.id.music_card_artist)?.text = artist
            floatingView?.findViewById<ImageView>(R.id.music_card_play)?.setImageResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
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
                    val intent = Intent("android.intent.action.MEDIA_BUTTON").apply {
                        putExtra("android.intent.extra.KEY_EVENT", when(action) {
                            "prev" -> android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                            "next" -> android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_NEXT)
                            else -> android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, 
                                if (MusicNotificationListener.isPlaying) android.view.KeyEvent.KEYCODE_MEDIA_PAUSE 
                                else android.view.KeyEvent.KEYCODE_MEDIA_PLAY)
                        })
                        setPackage(pkg)
                    }
                    sendBroadcast(intent)
                } catch (_: Exception) {}
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "悬浮音乐", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("悬浮音乐运行中")
                .setSmallIcon(R.drawable.ic_music)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("悬浮音乐运行中")
                .setSmallIcon(R.drawable.ic_music)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingCard()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
