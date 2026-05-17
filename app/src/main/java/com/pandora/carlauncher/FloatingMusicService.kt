package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ComponentName
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
 * 悬浮音乐服务（简化版）
 * - 使用 MediaSessionManager 获取活跃媒体会话
 * - 显示歌曲名、歌手、播放控制
 * - 支持拖动
 */
class FloatingMusicService : Service() {

    companion object {
        private const val TAG = "FloatingMusicService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "floating_music_service"
        private const val PREF_NAME = "floating_music_prefs"

        const val ACTION_START = "com.pandora.carlauncher.FloatingMusicService.START"
        const val ACTION_STOP = "com.pandora.carlauncher.FloatingMusicService.STOP"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private val prefs by lazy { getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE) }

    // UI 元素
    private var tvTitle: TextView? = null
    private var tvArtist: TextView? = null
    private var ivPlayPause: ImageView? = null

    // 播放状态
    private var isPlaying = false
    private var currentSongTitle = ""
    private var currentArtist = ""

    // MediaController
    private var mediaController: android.media.session.MediaController? = null

    // 拖动状态
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = object : Runnable {
        override fun run() {
            refreshMediaState()
            handler.postDelayed(this, 2000)
        }
    }

    private val sessionCallback = object : android.media.session.MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            metadata?.let { meta ->
                currentSongTitle = meta.getString(android.media.MediaMetadata.METADATA_KEY_TITLE) ?: ""
                currentArtist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                updateUI()
            }
        }

        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) {
            state?.let {
                isPlaying = it.state == android.media.session.PlaybackState.STATE_PLAYING
                updateUI()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")

        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification())

        if (floatingView == null) {
            createFloatingWindow()
        }

        // 获取 MediaSession
        setupMediaSession()

        // 定时刷新播放状态
        handler.postDelayed(refreshRunnable, 1000)

        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        Log.d(TAG, "createFloatingWindow")
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_music, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 加载保存的位置
        val savedX = prefs.getInt("pos_x", 0)
        val savedY = prefs.getInt("pos_y", 0)
        val dm = resources.displayMetrics
        val defaultX = (dm.widthPixels - 320) / 2
        val defaultY = dm.heightPixels - 300

        params = WindowManager.LayoutParams(
            320,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (savedX != 0) savedX else defaultX
            y = if (savedY != 0) savedY else defaultY
        }

        try {
            windowManager?.addView(floatingView, params)
            Log.d(TAG, "悬浮音乐窗已添加")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮音乐窗失败", e)
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
            if (isPlaying) {
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
     * 通过 MediaSessionManager 获取活跃的 MediaController
     */
    @SuppressLint("WrongConstant")
    private fun setupMediaSession() {
        try {
            val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? android.media.session.MediaSessionManager
            if (mediaSessionManager == null) {
                Log.w(TAG, "无法获取 MediaSessionManager")
                return
            }

            val notificationListener = ComponentName(this, FloatingMusicService::class.java)
            val controllers = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                    mediaSessionManager.getActiveSessions(notificationListener)
                } else {
                    emptyList()
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "没有通知监听权限，尝试其他方式", e)
                try {
                    @Suppress("DEPRECATION")
                    mediaSessionManager.getActiveSessions(null)
                } catch (e2: Exception) {
                    Log.e(TAG, "获取 MediaSession 列表失败", e2)
                    emptyList()
                }
            }

            // 找到活跃的媒体控制器
            for (controller in controllers) {
                try {
                    val metadata = controller.metadata
                    if (metadata != null) {
                        mediaController?.unregisterCallback(sessionCallback)
                        mediaController = controller
                        mediaController?.registerCallback(sessionCallback)
                        // 立即更新一次
                        sessionCallback.onMetadataChanged(metadata)
                        sessionCallback.onPlaybackStateChanged(controller.playbackState)
                        Log.d(TAG, "已连接到媒体会话: ${controller.packageName}")
                        break
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "setupMediaSession 失败", e)
        }
    }

    /**
     * 刷新媒体状态
     */
    private fun refreshMediaState() {
        if (mediaController != null) {
            try {
                val metadata = mediaController?.metadata
                val state = mediaController?.playbackState
                if (metadata != null) {
                    sessionCallback.onMetadataChanged(metadata)
                }
                if (state != null) {
                    sessionCallback.onPlaybackStateChanged(state)
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaController 已失效，重新获取")
                mediaController = null
                setupMediaSession()
            }
        } else {
            setupMediaSession()
        }
    }

    /**
     * 发送媒体控制指令
     */
    private fun sendMediaAction(action: Long) {
        val controller = mediaController
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
                mediaController = null
                setupMediaSession()
            }
        } else {
            // 没有 MediaController，尝试发送广播方式控制
            trySendMediaBroadcast(action)
            setupMediaSession()
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
        tvTitle?.text = if (currentSongTitle.isNotEmpty()) currentSongTitle else "未在播放"
        tvArtist?.text = currentArtist
        ivPlayPause?.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
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
        val titleText = if (currentSongTitle.isNotEmpty()) currentSongTitle else "悬浮音乐"
        val contentText = if (currentArtist.isNotEmpty()) currentArtist else "音乐控制服务运行中"
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)

        try {
            mediaController?.unregisterCallback(sessionCallback)
        } catch (_: Exception) {}

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
