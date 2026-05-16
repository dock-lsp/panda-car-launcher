package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.SeekBar
import java.util.*

/**
 * 悬浮视频播放服务
 * 支持拖动、缩放、关闭
 */
class FloatingVideoService : Service() {

    companion object {
        private const val TAG = "FloatingVideo"
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"

        // 悬浮窗默认尺寸（可根据需要调整）
        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 360
        private const val MIN_WIDTH = 160   // 最小160px
        private const val MIN_HEIGHT = 90
        private const val MAX_WIDTH = 1920  // 最大1920px
        private const val MAX_HEIGHT = 1080
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var mediaPlayer: MediaPlayer? = null
    private var surfaceView: SurfaceView? = null
    private var videoUri: Uri? = null
    private var videoDuration = 0

    // 悬浮窗位置和尺寸
    private var params: WindowManager.LayoutParams? = null
    private var currentWidth = DEFAULT_WIDTH
    private var currentHeight = DEFAULT_HEIGHT

    // 拖动相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // 缩放相关
    private var initialDistance = 0f
    private var initialWidth = 0
    private var initialHeight = 0

    private val progressHandler = Handler(Looper.getMainLooper())
    private val progressRunnable = object : Runnable {
        override fun run() {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    val seekBar = floatingView?.findViewById<SeekBar>(R.id.floating_seek_bar)
                    val tvTime = floatingView?.findViewById<View>(R.id.floating_tv_time)
                    seekBar?.max = 1000
                    if (videoDuration > 0) {
                        val progress = ((mp.currentPosition.toLong() * 1000) / videoDuration).toInt()
                        seekBar?.progress = progress
                        tvTime?.let { if (it is android.widget.TextView) {
                            val cur = mp.currentPosition / 1000
                            val total = videoDuration / 1000
                            it.text = "${formatTime(cur)}/${formatTime(total)}"
                        }}
                    }
                }
                progressHandler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val uriStr = it.getStringExtra(EXTRA_VIDEO_URI)
            if (uriStr != null) {
                videoUri = Uri.parse(uriStr)
            }
        }

        if (floatingView != null) {
            // 已经在运行，直接播放新视频
            videoUri?.let { playVideo(it) }
            return START_NOT_STICKY
        }

        createFloatingView()
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_video, null)

        // 设置 WindowManager 参数
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = resources.displayMetrics
        params = WindowManager.LayoutParams(
            currentWidth,
            currentHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (displayMetrics.widthPixels - currentWidth) / 2
            y = (displayMetrics.heightPixels - currentHeight) / 2
        }

        windowManager?.addView(floatingView, params)

        val rootView = floatingView ?: return

        // SurfaceView
        surfaceView = rootView.findViewById(R.id.floating_surface_view)
        surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                videoUri?.let { playVideo(it) }
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                releasePlayer()
            }
        })

        // 关闭按钮
        rootView.findViewById<ImageView>(R.id.floating_btn_close)?.setOnClickListener {
            stopSelf()
        }

        // 播放/暂停
        rootView.findViewById<ImageView>(R.id.floating_btn_play)?.setOnClickListener {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) {
                    mp.pause()
                    rootView.findViewById<ImageView>(R.id.floating_btn_play)?.setImageResource(R.drawable.ic_play)
                } else {
                    mp.start()
                    rootView.findViewById<ImageView>(R.id.floating_btn_play)?.setImageResource(R.drawable.ic_pause)
                }
            }
        }

        // 进度条
        rootView.findViewById<SeekBar>(R.id.floating_seek_bar)?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && videoDuration > 0) {
                        val ms = (progress.toLong() * videoDuration) / 1000
                        mediaPlayer?.seekTo(ms.toInt())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        // 触摸事件：拖动 + 双指缩放
        rootView.setOnTouchListener(object : View.OnTouchListener {
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action and MotionEvent.ACTION_MASK) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params?.x ?: 0
                        initialY = params?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (event.pointerCount == 1) {
                            // 单指拖动
                            val dx = event.rawX - initialTouchX
                            val dy = event.rawY - initialTouchY
                            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) {
                                isDragging = true
                            }
                            if (isDragging) {
                                params?.x = initialX + dx.toInt()
                                params?.y = initialY + dy.toInt()
                                windowManager?.updateViewLayout(floatingView, params)
                            }
                        } else if (event.pointerCount == 2) {
                            // 双指缩放
                            val distance = getDistance(event)
                            if (initialDistance > 0) {
                                val scale = distance / initialDistance
                                val newWidth = (initialWidth * scale).toInt()
                                    .coerceIn(MIN_WIDTH, MAX_WIDTH)
                                val newHeight = (initialHeight * scale).toInt()
                                    .coerceIn(MIN_HEIGHT, MAX_HEIGHT)
                                currentWidth = newWidth
                                currentHeight = newHeight
                                params?.width = newWidth
                                params?.height = newHeight
                                windowManager?.updateViewLayout(floatingView, params)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount == 2) {
                            initialDistance = getDistance(event)
                            initialWidth = currentWidth
                            initialHeight = currentHeight
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                        initialDistance = 0f
                        return true
                    }
                }
                return false
            }

            private fun getDistance(event: MotionEvent): Float {
                val dx = event.getX(0) - event.getX(1)
                val dy = event.getY(0) - event.getY(1)
                return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            }
        })

        // 点击回到播放器
        rootView.setOnClickListener {
            if (!isDragging) {
                val intent = Intent(this@FloatingVideoService, VideoPlayerActivity::class.java).apply {
                    putExtra(VideoPlayerActivity.EXTRA_VIDEO_URI, videoUri?.toString())
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
        }
    }

    private fun playVideo(uri: Uri) {
        releasePlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@FloatingVideoService, uri)
                val holder = surfaceView?.holder
                if (holder != null) {
                    setDisplay(holder)
                }
                setOnPreparedListener { mp ->
                    videoDuration = mp.duration
                    mp.start()
                    progressHandler.post(progressRunnable)
                    floatingView?.findViewById<ImageView>(R.id.floating_btn_play)
                        ?.setImageResource(R.drawable.ic_pause)
                }
                setOnCompletionListener {
                    floatingView?.findViewById<ImageView>(R.id.floating_btn_play)
                        ?.setImageResource(R.drawable.ic_play)
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e(TAG, "悬浮播放失败", e)
        }
    }

    private fun releasePlayer() {
        progressHandler.removeCallbacks(progressRunnable)
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null
        videoDuration = 0
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        releasePlayer()
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
