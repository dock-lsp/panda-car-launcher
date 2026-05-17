package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

/**
 * 悬浮音乐服务
 * - 使用 WindowManager TYPE_APPLICATION_OVERLAY 显示悬浮窗
 * - 悬浮窗大小：宽度 35% 屏幕，高度 70% 屏幕
 * - 位置：屏幕右侧居中
 * - 支持拖动
 * - 前台 Service 保活
 * - 从 MusicNotificationListener 获取播放数据
 */
class FloatingMusicService : Service() {

    companion object {
        private const val TAG = "FloatingMusic"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "floating_music"
        private const val PREF_NAME = "floating_music_prefs"

        const val ACTION_CLOSE = "action_close"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    // UI 元素
    private var tvTitle: TextView? = null
    private var tvArtist: TextView? = null
    private var ivPlayPause: ImageView? = null

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val handler = Handler(Looper.getMainLooper())

    private val refreshRunnable = object : Runnable {
        override fun run() {
            updateMusicInfo()
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        // 注册 MusicNotificationListener 回调
        MusicNotificationListener.onMusicUpdate = { title, artist, isPlaying, pkg ->
            handler.post {
                tvTitle?.text = if (title.isNotEmpty()) title else "未在播放"
                tvArtist?.text = artist
                ivPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
                updateNotification(title, artist)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (floatingView == null) {
            createFloatingWindow()
        }

        // 启动轮询，从 MusicNotificationListener 读取数据
        handler.postDelayed(refreshRunnable, 1000)

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_music, null)

        val dm = resources.displayMetrics
        val width = (dm.widthPixels * 0.35).toInt()
        val height = (dm.heightPixels * 0.70).toInt()

        // 加载保存的位置，默认右侧居中
        val savedX = prefs.getInt("pos_x", Int.MIN_VALUE)
        val savedY = prefs.getInt("pos_y", Int.MIN_VALUE)
        val defaultX = (dm.widthPixels - width) * 3 / 4  // 偏右侧
        val defaultY = (dm.heightPixels - height) / 2

        params = WindowManager.LayoutParams(
            width, height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX != Int.MIN_VALUE) savedX else defaultX
            y = if (savedY != Int.MIN_VALUE) savedY else defaultY
        }

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮音乐窗已添加")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
            return
        }

        // 绑定 UI
        val rootView = floatingView ?: return
        tvTitle = rootView.findViewById(R.id.music_title)
        tvArtist = rootView.findViewById(R.id.music_artist)
        ivPlayPause = rootView.findViewById(R.id.music_play)

        setupButtons()
        setupDrag()
        updateUI()
    }

    private fun setupButtons() {
        val rootView = floatingView ?: return

        // 关闭
        rootView.findViewById<ImageView>(R.id.music_close)?.setOnClickListener {
            stopSelf()
        }

        // 上一首
        rootView.findViewById<ImageView>(R.id.music_prev)?.setOnClickListener {
            sendMediaAction(android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS)
        }

        // 播放/暂停
        rootView.findViewById<ImageView>(R.id.music_play)?.setOnClickListener {
            val playing = MusicNotificationListener.isPlaying
            if (playing) {
                sendMediaAction(android.media.session.PlaybackState.ACTION_PAUSE)
            } else {
                sendMediaAction(android.media.session.PlaybackState.ACTION_PLAY)
            }
        }

        // 下一首
        rootView.findViewById<ImageView>(R.id.music_next)?.setOnClickListener {
            sendMediaAction(android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT)
        }
    }

    /**
     * 从 MusicNotificationListener 获取音乐数据并更新UI
     */
    private fun updateMusicInfo() {
        handler.postDelayed({
            val title = MusicNotificationListener.currentTitle
            val artist = MusicNotificationListener.currentArtist
            val playing = MusicNotificationListener.isPlaying

            handler.post {
                tvTitle?.text = if (title.isNotEmpty()) title else "未在播放"
                tvArtist?.text = artist
                ivPlayPause?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
            }

            // 继续轮询
            handler.postDelayed(refreshRunnable, 2000)
        }, 0)
    }

    /**
     * 通过 MusicNotificationListener 中的 activeMediaController 发送媒体控制指令
     */
    private fun sendMediaAction(action: Long) {
        val controller = MusicNotificationListener.activeMediaController
        if (controller != null) {
            try {
                val transportControls = controller.transportControls
                when (action) {
                    android.media.session.PlaybackState.ACTION_PLAY -> transportControls.play()
                    android.media.session.PlaybackState.ACTION_PAUSE -> transportControls.pause()
                    android.media.session.PlaybackState.ACTION_SKIP_TO_NEXT -> transportControls.skipToNext()
                    android.media.session.PlaybackState.ACTION_SKIP_TO_PREVIOUS -> transportControls.skipToPrevious()
                }
                Log.d(TAG, "发送媒体控制: $action")
            } catch (e: Exception) {
                Log.e(TAG, "发送媒体控制失败", e)
                MusicNotificationListener.activeMediaController = null
            }
        } else {
            trySendMediaBroadcast(action)
        }
    }

    /**
     * 通过广播方式发送媒体控制（兼容方案）
     */
    private fun trySendMediaBroadcast(action: Long) {
        try {
            val intent = when (action) {
                android.media.session.PlaybackState.ACTION_PLAY -> {
                    Intent("com.android.music.musicservicecommand").apply {
                        putExtra("command", "play")
                    }
                }
                android.media.session.PlaybackState.ACTION_PAUSE -> {
                    Intent("com.android.music.musicservicecommand").apply {
                        putExtra("command", "pause")
                    }
                }
                else -> return
            }
            sendBroadcast(intent)
        } catch (e: Exception) {
            Log.e(TAG, "广播方式控制失败", e)
        }
    }

    private fun updateUI() {
        val title = MusicNotificationListener.currentTitle
        val artist = MusicNotificationListener.currentArtist
        val playing = MusicNotificationListener.isPlaying
        tvTitle?.text = if (title.isNotEmpty()) title else "未在播放"
        tvArtist?.text = artist
        ivPlayPause?.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDrag() {
        val rootView = floatingView ?: return

        rootView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (dx * dx + dy * dy) > 25) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        try {
                            windowManager?.updateViewLayout(floatingView, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        // 保存位置
                        params?.let { p ->
                            prefs.edit()
                                .putInt("pos_x", p.x)
                                .putInt("pos_y", p.y)
                                .apply()
                        }
                    }
                    isDragging
                }
                else -> false
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮音乐",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "悬浮音乐控制服务运行中"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val title = MusicNotificationListener.currentTitle
        val artist = MusicNotificationListener.currentArtist
        val titleText = if (title.isNotEmpty()) title else "悬浮音乐"
        val contentText = if (artist.isNotEmpty()) artist else "音乐控制服务运行中"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(titleText)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_music)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(titleText)
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_music)
                .build()
        }
    }

    /**
     * 更新前台通知内容
     */
    private fun updateNotification(title: String, artist: String) {
        try {
            val titleText = if (title.isNotEmpty()) title else "悬浮音乐"
            val contentText = if (artist.isNotEmpty()) artist else "音乐控制服务运行中"
            val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(titleText)
                    .setContentText(contentText)
                    .setSmallIcon(R.drawable.ic_music)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
                    .setContentTitle(titleText)
                    .setContentText(contentText)
                    .setSmallIcon(R.drawable.ic_music)
                    .build()
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Log.w(TAG, "更新通知失败", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
        MusicNotificationListener.onMusicUpdate = null

        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {}
        }
        floatingView = null
        Log.d(TAG, "悬浮音乐服务已销毁")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
