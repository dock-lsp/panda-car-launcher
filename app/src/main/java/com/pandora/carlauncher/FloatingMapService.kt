package com.pandora.carlauncher

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.*
import java.io.File
import java.io.FileOutputStream

/**
 * 地图悬浮窗服务 - 真实画中画
 * 使用 MediaProjection 截屏显示导航应用的真实画面
 */
class FloatingMapService : Service() {

    companion object {
        private const val TAG = "FloatingMap"
        const val EXTRA_MAP_PACKAGE = "extra_map_package"
        const val EXTRA_MAP_NAME = "extra_map_name"
        const val EXTRA_CAPTURE_RESULT = "extra_capture_result"
        const val EXTRA_CAPTURE_DATA = "extra_capture_data"

        // 支持的地图应用包名
        val MAP_PACKAGES = listOf(
            "com.autonavi.amapauto",     // 高德地图车机版
            "com.autonavi.amapauto.lite", // 高德地图车机版精简版
            "com.autonavi.minimap",       // 高德地图手机版
            "com.baidu.BaiduMap",         // 百度地图
            "com.baidu.naviauto",         // 百度地图车机版
            "com.tencent.map",            // 腾讯地图
            "com.autonavi.amapauto:du",   // 高德车机版(双开)
            "com.autonavi.minimap:du",    // 高德手机版(双开)
            "com.baidu.BaiduMap:du",      // 百度地图(双开)
            "com.tencent.map:du"          // 腾讯地图(双开)
        )

        private const val DEFAULT_WIDTH = 640
        private const val DEFAULT_HEIGHT = 400
        private const val MIN_WIDTH = 240
        private const val MIN_HEIGHT = 150
        private const val MAX_WIDTH = 1920
        private const val MAX_HEIGHT = 1080
        private const val REFRESH_INTERVAL_MS = 800L
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var ivScreenshot: ImageView? = null
    private var tvTitle: TextView? = null
    private var tvStatus: TextView? = null

    private var currentWidth = DEFAULT_WIDTH
    private var currentHeight = DEFAULT_HEIGHT
    private var mapPackage: String? = null
    private var mapName: String? = null

    // 拖动
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false

    // 截屏
    private var captureResultCode = 0
    private var captureData: Intent? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null
    private var isCapturing = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            mapPackage = it.getStringExtra(EXTRA_MAP_PACKAGE)
            mapName = it.getStringExtra(EXTRA_MAP_NAME)
            captureResultCode = it.getIntExtra(EXTRA_CAPTURE_RESULT, 0)
            captureData = it.getParcelableExtra(EXTRA_CAPTURE_DATA)
        }

        if (floatingView == null) {
            createFloatingView()
            // 启动地图应用
            mapPackage?.let { pkg -> openMapApp(pkg) }
            // 延迟启动截屏
            handler.postDelayed({
                startScreenCapture()
            }, 2000)
        }

        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingView() {
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_map, null)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            currentWidth,
            currentHeight,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        try {
            windowManager?.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            stopSelf()
            return
        }

        val rootView = floatingView ?: return
        ivScreenshot = rootView.findViewById(R.id.map_screenshot)
        tvTitle = rootView.findViewById(R.id.map_tv_title)
        tvStatus = rootView.findViewById(R.id.map_tv_status)

        tvTitle?.text = mapName ?: "导航"

        // 标题栏拖动
        val titleBar = rootView.findViewById<View>(R.id.map_title_bar)
        titleBar?.setOnTouchListener(touchListener)

        // 关闭
        rootView.findViewById<ImageView>(R.id.map_btn_close)?.setOnClickListener {
            stopScreenCapture()
            stopSelf()
        }

        // 缩放
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }
        rootView.findViewById<ImageView>(R.id.map_btn_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        // 点击回到地图
        rootView.findViewById<View>(R.id.map_content)?.setOnClickListener {
            if (!isDragging) {
                mapPackage?.let { pkg -> openMapApp(pkg) }
            }
        }
    }

    private val touchListener = object : View.OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    params?.x = initialX + dx.toInt()
                    params?.y = initialY + dy.toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    return true
                }
                MotionEvent.ACTION_UP -> return true
            }
            return false
        }
    }

    private fun resizeWindow(scale: Float) {
        val newWidth = (currentWidth * scale).toInt().coerceIn(MIN_WIDTH, MAX_WIDTH)
        val newHeight = (currentHeight * scale).toInt().coerceIn(MIN_HEIGHT, MAX_HEIGHT)
        currentWidth = newWidth
        currentHeight = newHeight
        params?.width = newWidth
        params?.height = newHeight
        windowManager?.updateViewLayout(floatingView, params)
    }

    private fun openMapApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                tvStatus?.text = "正在导航..."
            } else {
                tvStatus?.text = "无法启动"
            }
        } catch (e: Exception) {
            tvStatus?.text = "启动失败"
        }
    }

    /**
     * 启动截屏捕获
     */
    private fun startScreenCapture() {
        if (captureResultCode == 0 || captureData == null) {
            tvStatus?.text = "无截屏权限"
            return
        }

        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val mediaProjection = projectionManager.getMediaProjection(captureResultCode, captureData!!)

            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(displayMetrics)

            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

            imageReader.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val bitmap = imageToBitmap(image)
                    image.close()

                    bitmap?.let { bmp ->
                        // 裁剪到悬浮窗大小比例
                        val scaled = Bitmap.createScaledBitmap(bmp, currentWidth, currentHeight, true)
                        handler.post {
                            ivScreenshot?.setImageBitmap(scaled)
                            ivScreenshot?.visibility = View.VISIBLE
                            tvStatus?.visibility = View.GONE
                        }
                        if (!scaled.isRecycled) scaled.recycle()
                    }
                } catch (e: Exception) {
                    // 忽略偶尔的截屏失败
                }
            }, handler)

            @Suppress("DEPRECATION")
            mediaProjection.createVirtualDisplay(
                "MapCapture",
                screenWidth, screenHeight, displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface, null, handler
            )

            isCapturing = true
            Log.d(TAG, "截屏已启动")

        } catch (e: Exception) {
            Log.e(TAG, "截屏启动失败", e)
            tvStatus?.text = "截屏失败"
        }
    }

    private fun imageToBitmap(image: android.media.Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride
        val width = image.width
        val height = image.height

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else bitmap
    }

    private fun stopScreenCapture() {
        isCapturing = false
        captureRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenCapture()
        floatingView?.let {
            try { windowManager?.removeView(it) } catch (_: Exception) {}
        }
        floatingView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
