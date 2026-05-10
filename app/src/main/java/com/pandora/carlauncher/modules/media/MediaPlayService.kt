package com.pandora.carlauncher.modules.media

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pandora.carlauncher.R
import com.pandora.carlauncher.ui.activity.MainLauncherActivity
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException

/**
 * 媒体播放服务
 * 
 * 功能：
 * - 音频播放控制
 * - 播放队列管理
 * - 音频焦点管理
 * - 媒体通知控制
 * - 播放状态回调
 */
class MediaPlayService : Service() {

    companion object {
        private const val TAG = "MediaPlayService"
        
        // 通知频道
        private const val CHANNEL_ID = "media_playback_channel"
        private const val NOTIFICATION_ID = 2001
        
        // 播放状态
        const val STATE_IDLE = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_STOPPED = 3
        
        // 广播Action
        const val ACTION_PLAY = "com.pandora.carlauncher.media.PLAY"
        const val ACTION_PAUSE = "com.pandora.carlauncher.media.PAUSE"
        const val ACTION_STOP = "com.pandora.carlauncher.media.STOP"
        const val ACTION_NEXT = "com.pandora.carlauncher.media.NEXT"
        const val ACTION_PREV = "com.pandora.carlauncher.media.PREV"
        const val ACTION_SEEK = "com.pandora.carlauncher.media.SEEK"
        
        const val EXTRA_POSITION = "position"
    }
    
    // Binder
    inner class MediaBinder : Binder() {
        fun getService(): MediaPlayService = this@MediaPlayService
    }
    
    private val binder = MediaBinder()
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 媒体播放器
    private var mediaPlayer: MediaPlayer? = null
    
    // 音频管理器
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // 播放队列
    private val playQueue = mutableListOf<String>()
    private var currentIndex = 0
    
    // 播放状态
    @Volatile
    var playbackState = STATE_IDLE
        private set
    
    var currentMediaPath: String? = null
        private set
    
    var currentPosition: Long = 0
        private set
    
    var duration: Long = 0
        internal set
    
    // 回调监听器
    private val listeners = mutableListOf<PlaybackListener>()
    
    // 进度更新Job
    private var progressJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "媒体播放服务创建")
        
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        createNotificationChannel()
        initAudioFocusRequest()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> play()
            ACTION_PAUSE -> pause()
            ACTION_STOP -> stop()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_SEEK -> {
                val position = intent.getLongExtra(EXTRA_POSITION, 0)
                seekTo(position)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        releasePlayer()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "媒体播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "媒体播放控制"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 初始化音频焦点请求
     */
    private fun initAudioFocusRequest() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener { focusChange ->
                handleAudioFocusChange(focusChange)
            }
            .build()
    }

    /**
     * 处理音频焦点变化
     */
    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (playbackState == STATE_PAUSED) {
                    play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
        }
    }

    /**
     * 初始化媒体播放器
     */
    private fun initPlayer() {
        releasePlayer()
        
        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            
            setOnPreparedListener {
                duration = it.duration.toLong()
                notifyDurationChanged()
                startProgressUpdates()
            }
            
            setOnCompletionListener {
                playbackState = STATE_STOPPED
                notifyPlaybackStateChanged()
                playNext()
            }
            
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "播放错误: what=$what, extra=$extra")
                playbackState = STATE_IDLE
                notifyPlaybackStateChanged()
                true
            }
        }
    }

    /**
     * 播放媒体
     */
    fun playMedia(path: String) {
        initPlayer()
        
        try {
            mediaPlayer?.apply {
                setDataSource(path)
                prepareAsync()
            }
            
            currentMediaPath = path
            playbackState = STATE_PAUSED
            
            // 请求音频焦点
            if (requestAudioFocus()) {
                startForeground(NOTIFICATION_ID, createNotification())
            }
            
        } catch (e: IOException) {
            Log.e(TAG, "无法播放: $path", e)
        }
    }

    /**
     * 开始播放
     */
    fun play() {
        if (mediaPlayer == null && currentMediaPath != null) {
            playMedia(currentMediaPath!!)
            return
        }
        
        if (requestAudioFocus()) {
            mediaPlayer?.start()
            playbackState = STATE_PLAYING
            startProgressUpdates()
            notifyPlaybackStateChanged()
            updateNotification()
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        mediaPlayer?.pause()
        playbackState = STATE_PAUSED
        stopProgressUpdates()
        notifyPlaybackStateChanged()
        updateNotification()
    }

    /**
     * 停止播放
     */
    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.reset()
        playbackState = STATE_STOPPED
        currentPosition = 0
        stopProgressUpdates()
        abandonAudioFocus()
        notifyPlaybackStateChanged()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * 跳转
     */
    fun seekTo(position: Long) {
        mediaPlayer?.seekTo(position.toInt())
        currentPosition = position
        notifyPositionChanged()
    }

    /**
     * 设置音量
     */
    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    /**
     * 播放下一首
     */
    fun playNext() {
        if (playQueue.isEmpty()) return
        
        currentIndex = (currentIndex + 1) % playQueue.size
        playMedia(playQueue[currentIndex])
    }

    /**
     * 播放上一首
     */
    fun playPrevious() {
        if (playQueue.isEmpty()) return
        
        currentIndex = if (currentIndex > 0) currentIndex - 1 else playQueue.size - 1
        playMedia(playQueue[currentIndex])
    }

    /**
     * 添加到播放队列
     */
    fun addToQueue(path: String) {
        playQueue.add(path)
    }

    /**
     * 清空播放队列
     */
    fun clearQueue() {
        playQueue.clear()
        currentIndex = 0
    }

    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * 放弃音频焦点
     */
    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
    }

    /**
     * 开始进度更新
     */
    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && playbackState == STATE_PLAYING) {
                mediaPlayer?.let {
                    currentPosition = it.currentPosition.toLong()
                    notifyPositionChanged()
                }
                delay(1000)
            }
        }
    }

    /**
     * 停止进度更新
     */
    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    /**
     * 释放播放器
     */
    private fun releasePlayer() {
        stopProgressUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        abandonAudioFocus()
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainLauncherActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val playPauseAction = if (playbackState == STATE_PLAYING) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause,
                "暂停",
                createPendingIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play,
                "播放",
                createPendingIntent(ACTION_PLAY)
            )
        }
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在播放")
            .setContentText(currentMediaPath?.substringAfterLast('/') ?: "未知")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_previous, "上一首", 
                createPendingIntent(ACTION_PREV))
            .addAction(playPauseAction)
            .addAction(android.R.drawable.ic_media_next, "下一首", 
                createPendingIntent(ACTION_NEXT))
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(playbackState == STATE_PLAYING)
            .build()
    }

    /**
     * 创建PendingIntent
     */
    private fun createPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlayService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * 更新通知
     */
    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    /**
     * 添加播放监听器
     */
    fun addListener(listener: PlaybackListener) {
        listeners.add(listener)
    }

    /**
     * 移除播放监听器
     */
    fun removeListener(listener: PlaybackListener) {
        listeners.remove(listener)
    }

    /**
     * 通知播放状态变化
     */
    private fun notifyPlaybackStateChanged() {
        listeners.forEach { it.onPlaybackStateChanged(playbackState) }
    }

    /**
     * 通知播放位置变化
     */
    private fun notifyPositionChanged() {
        listeners.forEach { it.onPositionChanged(currentPosition) }
    }

    /**
     * 通知时长变化
     */
    private fun notifyDurationChanged() {
        listeners.forEach { it.onDurationChanged(duration) }
    }

    /**
     * 播放监听器接口
     */
    interface PlaybackListener {
        fun onPlaybackStateChanged(state: Int)
        fun onPositionChanged(position: Long)
        fun onDurationChanged(duration: Long)
    }
}
