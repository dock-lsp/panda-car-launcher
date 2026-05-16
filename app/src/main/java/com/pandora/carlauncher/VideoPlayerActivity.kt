package com.pandora.carlauncher

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 视频播放器 - 使用 MediaPlayer + SurfaceView
 * 解决 VideoView 的 duration 返回0、进度条拖动无效等问题
 */
class VideoPlayerActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val TAG = "VideoPlayer"
        private const val SPEED_LIMIT_KMH = 5.0f
        private const val SEEK_TIME_MS = 10000
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }

    private var surfaceView: SurfaceView? = null
    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: Uri? = null
    private var videoDuration = 0

    private var tvTitle: TextView? = null
    private var tvSpeed: TextView? = null
    private var ivLock: ImageView? = null
    private var ivPlayPause: ImageView? = null
    private var ivBack: ImageView? = null
    private var ivRewind: ImageView? = null
    private var ivForward: ImageView? = null
    private var seekBar: SeekBar? = null
    private var tvCurrentTime: TextView? = null
    private var tvTotalTime: TextView? = null
    private var controlPanel: View? = null
    private var lockPanel: View? = null
    private var lockTouchArea: View? = null
    private var ivLockUnlock: ImageView? = null

    private var isLocked = false
    private var isVideoPlaying = false
    private var currentSpeed = 0f
    private var isDraggingSeekBar = false
    private var isSurfaceCreated = false
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var hideControlRunnable: Runnable? = null
    private var durationRetryRunnable: Runnable? = null

    private val speedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val speed = intent?.getFloatExtra(SpeedMonitorService.EXTRA_SPEED, 0f) ?: 0f
            currentSpeed = speed
            tvSpeed?.text = "车速: ${"%.1f".format(speed)} km/h"
            checkSpeedLimit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        initViews()
        setupSpeedMonitor()
        registerSpeedReceiver()
    }

    private fun initViews() {
        surfaceView = findViewById<SurfaceView>(R.id.surface_view)
        tvTitle = findViewById(R.id.tv_video_title)
        tvSpeed = findViewById(R.id.tv_speed)
        ivLock = findViewById(R.id.iv_lock)
        ivPlayPause = findViewById(R.id.iv_play_pause)
        ivBack = findViewById(R.id.iv_back)
        ivRewind = findViewById(R.id.iv_rewind)
        ivForward = findViewById(R.id.iv_forward)
        seekBar = findViewById(R.id.seek_bar)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        controlPanel = findViewById(R.id.control_panel)
        lockPanel = findViewById(R.id.lock_panel)
        lockTouchArea = findViewById(R.id.lock_touch_area)
        ivLockUnlock = findViewById(R.id.iv_lock_unlock)

        val title = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "视频播放"
        tvTitle?.text = title

        // SurfaceView 回调
        surfaceView?.holder?.addCallback(this)

        // 返回
        ivBack?.setOnClickListener { finish() }

        // 锁屏
        ivLock?.setOnClickListener { lockScreen() }

        // 视频列表
        findViewById<ImageView>(R.id.iv_video_list)?.setOnClickListener { showVideoList() }

        // 播放/暂停
        ivPlayPause?.setOnClickListener { togglePlayPause() }

        // 快退
        ivRewind?.setOnClickListener { seekRelative(-SEEK_TIME_MS) }

        // 快进
        ivForward?.setOnClickListener { seekRelative(SEEK_TIME_MS) }

        // 进度条 - 使用 0~1000 的范围，映射到实际时长
        seekBar?.max = 1000
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && videoDuration > 0) {
                    val ms = (progress.toLong() * videoDuration) / 1000
                    tvCurrentTime?.text = formatTime((ms / 1000).toInt())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDraggingSeekBar = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingSeekBar = false
                if (videoDuration > 0) {
                    val progress = seekBar?.progress ?: 0
                    val ms = (progress.toLong() * videoDuration) / 1000
                    mediaPlayer?.seekTo(ms.toInt())
                }
            }
        })

        // 锁屏面板
        lockTouchArea?.setOnClickListener {
            if (isLocked) showUnlockButton()
        }
        ivLockUnlock?.setOnClickListener { unlockScreen() }

        startHideControlTimer()

        // 初始化视频播放
        setupVideo()
    }

    // ===== SurfaceHolder.Callback =====

    override fun surfaceCreated(holder: SurfaceHolder) {
        isSurfaceCreated = true
        videoUri?.let { prepareAndPlay(it, holder) }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        isSurfaceCreated = false
        releasePlayer()
    }

    // ===== 播放器核心 =====

    private fun prepareAndPlay(uri: Uri, holder: SurfaceHolder) {
        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@VideoPlayerActivity, uri)
                setDisplay(holder)
                setOnPreparedListener { mp ->
                    videoDuration = mp.duration
                    Log.d(TAG, "onPrepared: duration=${videoDuration}ms")
                    updateDurationDisplay()
                    mp.start()
                    isVideoPlaying = true
                    updatePlayPauseIcon()
                    startProgressUpdate()
                    // 如果 duration 仍然为 0，延迟重试
                    if (videoDuration <= 0) {
                        retryGetDuration()
                    }
                }
                setOnCompletionListener {
                    isVideoPlaying = false
                    updatePlayPauseIcon()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "播放错误: what=$what extra=$extra")
                    Toast.makeText(this@VideoPlayerActivity, "视频播放失败", Toast.LENGTH_SHORT).show()
                    true
                }
                setOnBufferingUpdateListener { mp, percent ->
                    // 缓冲更新（可用于显示缓冲进度）
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化播放器失败", e)
            Toast.makeText(this, "无法播放此视频", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * duration 为 0 时延迟重试获取
     */
    private fun retryGetDuration() {
        var retryCount = 0
        val retryTask = object : Runnable {
            override fun run() {
                val duration = mediaPlayer?.duration ?: 0
                if (duration > 0) {
                    videoDuration = duration
                    Log.d(TAG, "retryGetDuration success: ${videoDuration}ms")
                    updateDurationDisplay()
                } else if (retryCount < 10) {
                    retryCount++
                    handler.postDelayed(this, 500)
                } else {
                    Log.w(TAG, "retryGetDuration failed after 10 retries")
                }
            }
        }
        durationRetryRunnable = retryTask
        handler.postDelayed(retryTask, 500)
    }

    private fun updateDurationDisplay() {
        if (videoDuration > 0) {
            tvTotalTime?.text = formatTime(videoDuration / 1000)
        }
    }

    private fun releasePlayer() {
        durationRetryRunnable?.let { handler.removeCallbacks(it) }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null
        isVideoPlaying = false
        videoDuration = 0
    }

    private fun setupVideo() {
        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriStr == null) {
            showFilePicker()
            return
        }
        videoUri = Uri.parse(uriStr)
        if (isSurfaceCreated) {
            val holder = surfaceView?.holder
            if (holder != null) {
                prepareAndPlay(videoUri!!, holder)
            }
        }
        // 如果 Surface 还没创建好，等 surfaceCreated 回调中播放
    }

    private fun showFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("video/mp4", "video/3gp", "video/mkv", "video/avi", "video/webm"))
        }
        startActivityForResult(intent, 1001)
    }

    private fun showVideoList() {
        // 扫描本地视频文件
        val videoFiles = scanLocalVideos()
        if (videoFiles.isEmpty()) {
            Toast.makeText(this, "未找到本地视频文件", Toast.LENGTH_SHORT).show()
            return
        }

        val names = videoFiles.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择视频")
            .setItems(names) { _, which ->
                val (uri, _) = videoFiles[which]
                videoUri = uri
                tvTitle?.text = names[which]
                if (isSurfaceCreated) {
                    val holder = surfaceView?.holder
                    if (holder != null) {
                        prepareAndPlay(uri, holder)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun scanLocalVideos(): List<Pair<Uri, String>> {
        val videos = mutableListOf<Pair<Uri, String>>()
        val projection = arrayOf(
            android.provider.MediaStore.Video.Media._ID,
            android.provider.MediaStore.Video.Media.DISPLAY_NAME,
            android.provider.MediaStore.Video.Media.DATA
        )

        try {
            contentResolver.query(
                android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${android.provider.MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val uri = android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI.buildUpon()
                        .appendPath(id.toString())
                        .build()
                    videos.add(uri to name)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描视频失败", e)
        }

        return videos
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data?.data != null) {
            videoUri = data.data
            // 持久化 URI 读取权限
            try {
                contentResolver.takePersistableUriPermission(
                    videoUri!!,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                Log.w(TAG, "无法持久化 URI 权限", e)
            }
            // 更新标题
            tvTitle?.text = "本地视频"
            // 开始播放
            if (isSurfaceCreated) {
                val holder = surfaceView?.holder
                if (holder != null) {
                    prepareAndPlay(videoUri!!, holder)
                }
            }
        } else if (requestCode == 1001 && resultCode != RESULT_OK) {
            // 用户取消了文件选择，关闭播放器
            finish()
        }
    }

    // ===== 播放控制 =====

    private fun togglePlayPause() {
        mediaPlayer?.let { mp ->
            if (isVideoPlaying) {
                mp.pause()
            } else {
                mp.start()
            }
            isVideoPlaying = !isVideoPlaying
            updatePlayPauseIcon()
            resetHideControlTimer()
        }
    }

    private fun updatePlayPauseIcon() {
        ivPlayPause?.setImageResource(if (isVideoPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun seekRelative(deltaMs: Int) {
        mediaPlayer?.let { mp ->
            if (videoDuration <= 0) return@let
            val newPosition = (mp.currentPosition + deltaMs).coerceIn(0, videoDuration)
            mp.seekTo(newPosition)
            // 立即更新 UI
            seekBar?.progress = ((newPosition.toLong() * 1000) / videoDuration).toInt()
            tvCurrentTime?.text = formatTime(newPosition / 1000)
        }
        resetHideControlTimer()
    }

    // ===== 进度更新 =====

    private fun startProgressUpdate() {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying && !isDraggingSeekBar && videoDuration > 0) {
                        val currentPos = mp.currentPosition
                        val progress = ((currentPos.toLong() * 1000) / videoDuration).toInt()
                        seekBar?.progress = progress
                        tvCurrentTime?.text = formatTime(currentPos / 1000)
                    }
                    // 即使 duration 仍为 0，也持续尝试获取
                    if (videoDuration <= 0) {
                        val d = mp.duration
                        if (d > 0) {
                            videoDuration = d
                            updateDurationDisplay()
                        }
                    }
                }
                handler.postDelayed(this, 300)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    // ===== 锁屏功能 =====

    private fun lockScreen() {
        isLocked = true
        controlPanel?.visibility = View.GONE
        lockPanel?.visibility = View.VISIBLE
        ivLockUnlock?.visibility = View.GONE
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun unlockScreen() {
        isLocked = false
        lockPanel?.visibility = View.GONE
        controlPanel?.visibility = View.VISIBLE
        ivLock?.setImageResource(R.drawable.ic_unlock)
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        resetHideControlTimer()
    }

    private fun showUnlockButton() {
        ivLockUnlock?.visibility = View.VISIBLE
        handler.postDelayed({ ivLockUnlock?.visibility = View.GONE }, 3000)
    }

    // ===== 控制面板自动隐藏 =====

    private fun startHideControlTimer() {
        hideControlRunnable = Runnable {
            if (!isLocked && !isDraggingSeekBar) {
                controlPanel?.visibility = View.GONE
            }
        }
        handler.postDelayed(hideControlRunnable!!, 5000)
    }

    private fun resetHideControlTimer() {
        hideControlRunnable?.let { handler.removeCallbacks(it) }
        handler.postDelayed(hideControlRunnable!!, 5000)
    }

    private fun showControlPanel() {
        if (!isLocked) {
            controlPanel?.visibility = View.VISIBLE
            resetHideControlTimer()
        }
    }

    // ===== 车速监测 =====

    private fun setupSpeedMonitor() {
        val serviceIntent = Intent(this, SpeedMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            try {
                locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0f, locationListenerGps)
            } catch (e: Exception) {
                Log.e(TAG, "GPS定位失败", e)
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 2001)
        }
    }

    private val locationListenerGps = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentSpeed = location.speed * 3.6f
            tvSpeed?.text = "车速: ${"%.1f".format(currentSpeed)} km/h"
            checkSpeedLimit()
        }
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun registerSpeedReceiver() {
        val filter = IntentFilter(SpeedMonitorService.ACTION_SPEED_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(speedReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(speedReceiver, filter)
        }
    }

    private fun checkSpeedLimit() {
        if (currentSpeed > SPEED_LIMIT_KMH) {
            releasePlayer()
            isVideoPlaying = false
            isLocked = false
            AlertDialog.Builder(this)
                .setTitle("安全提示")
                .setMessage("检测到车辆行驶中（车速: ${"%.1f".format(currentSpeed)} km/h）\n\n行车中禁止观看视频，已自动关闭播放器。\n\n请安全驾驶！")
                .setCancelable(false)
                .setPositiveButton("我知道了") { _, _ -> finish() }
                .show()
        }
    }

    // ===== 按键和触摸 =====

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isLocked) { showUnlockButton(); return true }
                finish(); return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> { togglePlayPause(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { seekRelative(-SEEK_TIME_MS); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { seekRelative(SEEK_TIME_MS); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            if (isLocked) showUnlockButton() else showControlPanel()
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
        hideControlRunnable?.let { handler.removeCallbacks(it) }
        durationRetryRunnable?.let { handler.removeCallbacks(it) }
        releasePlayer()
        try { unregisterReceiver(speedReceiver) } catch (_: Exception) {}
        try { locationManager?.removeUpdates(locationListenerGps) } catch (_: Exception) {}
        stopService(Intent(this, SpeedMonitorService::class.java))
    }
}
