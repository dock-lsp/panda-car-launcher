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
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * 视频播放器
 * 支持：本地视频/在线视频播放、全屏、锁屏观看、车速超5km自动关闭
 */
class VideoPlayerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoPlayer"
        private const val SPEED_LIMIT_KMH = 5.0f
        private const val SEEK_TIME_MS = 10000 // 快进快退10秒
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
    }

    private var videoView: VideoView? = null
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
    private var isPlaying = false
    private var currentSpeed = 0f
    private var isDraggingSeekBar = false
    private var locationManager: LocationManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var hideControlRunnable: Runnable? = null

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
        setupVideo()
        setupSpeedMonitor()
        registerSpeedReceiver()
    }

    private fun initViews() {
        videoView = findViewById(R.id.video_view)
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

        // 返回按钮
        ivBack?.setOnClickListener { finish() }

        // 锁屏按钮
        ivLock?.setOnClickListener { lockScreen() }

        // 播放/暂停
        ivPlayPause?.setOnClickListener { togglePlayPause() }

        // 快退10秒
        ivRewind?.setOnClickListener { seekRelative(-SEEK_TIME_MS) }

        // 快进10秒
        ivForward?.setOnClickListener { seekRelative(SEEK_TIME_MS) }

        // 进度条
        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    tvCurrentTime?.text = formatTime(progress / 1000)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isDraggingSeekBar = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isDraggingSeekBar = false
                videoView?.seekTo(seekBar?.progress ?: 0)
            }
        })

        // 锁屏面板点击区域 - 点击显示解锁按钮
        lockTouchArea?.setOnClickListener {
            if (isLocked) {
                showUnlockButton()
            }
        }

        // 解锁按钮点击
        ivLockUnlock?.setOnClickListener {
            unlockScreen()
        }

        // 自动隐藏控制面板
        startHideControlTimer()
    }

    private fun setupVideo() {
        val uriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (uriStr == null) {
            showFilePicker()
            return
        }

        val uri = Uri.parse(uriStr)
        videoView?.setVideoURI(uri)
        videoView?.setOnPreparedListener { mp ->
            mp.isLooping = false
            isPlaying = true
            updatePlayPauseIcon()
            val duration = mp.duration
            tvTotalTime?.text = formatTime(duration / 1000)
            seekBar?.max = duration
            startProgressUpdate()
        }
        videoView?.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "播放错误: what=$what extra=$extra")
            Toast.makeText(this, "视频播放失败", Toast.LENGTH_SHORT).show()
            true
        }
        videoView?.setOnCompletionListener {
            isPlaying = false
            updatePlayPauseIcon()
        }
        videoView?.start()
    }

    private fun showFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "video/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        startActivityForResult(intent, 1001)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK && data?.data != null) {
            videoView?.setVideoURI(data.data)
            videoView?.start()
        } else {
            finish()
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            videoView?.pause()
        } else {
            videoView?.start()
        }
        isPlaying = !isPlaying
        updatePlayPauseIcon()
        resetHideControlTimer()
    }

    private fun updatePlayPauseIcon() {
        ivPlayPause?.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun seekRelative(deltaMs: Int) {
        videoView?.let {
            val newPosition = (it.currentPosition + deltaMs).coerceIn(0, it.duration)
            it.seekTo(newPosition)
            seekBar?.progress = newPosition
            tvCurrentTime?.text = formatTime(newPosition / 1000)
        }
        resetHideControlTimer()
    }

    // ===== 锁屏功能 =====

    private fun lockScreen() {
        isLocked = true
        controlPanel?.visibility = View.GONE
        lockPanel?.visibility = View.VISIBLE
        ivLockUnlock?.visibility = View.GONE // 初始隐藏解锁按钮
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
        // 3秒后自动隐藏
        handler.postDelayed({
            ivLockUnlock?.visibility = View.GONE
        }, 3000)
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

    // ===== 进度更新 =====

    private fun startProgressUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                videoView?.let {
                    if (it.isPlaying && !isDraggingSeekBar) {
                        val currentPos = it.currentPosition
                        seekBar?.progress = currentPos
                        tvCurrentTime?.text = formatTime(currentPos / 1000)
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
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
            videoView?.stopPlayback()
            isPlaying = false
            isLocked = false

            AlertDialog.Builder(this)
                .setTitle("安全提示")
                .setMessage("检测到车辆行驶中（车速: ${"%.1f".format(currentSpeed)} km/h）\n\n行车中禁止观看视频，已自动关闭播放器。\n\n请安全驾驶！")
                .setCancelable(false)
                .setPositiveButton("我知道了") { _, _ -> finish() }
                .show()
        }
    }

    // ===== 按键和触摸控制 =====

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> {
                if (isLocked) {
                    showUnlockButton()
                    return true
                }
                finish()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_SPACE -> {
                togglePlayPause()
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                seekRelative(-SEEK_TIME_MS)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                seekRelative(SEEK_TIME_MS)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            if (isLocked) {
                showUnlockButton()
            } else {
                showControlPanel()
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateRunnable?.let { handler.removeCallbacks(it) }
        hideControlRunnable?.let { handler.removeCallbacks(it) }
        try { unregisterReceiver(speedReceiver) } catch (_: Exception) {}
        try { locationManager?.removeUpdates(locationListenerGps) } catch (_: Exception) {}
        stopService(Intent(this, SpeedMonitorService::class.java))
    }
}
