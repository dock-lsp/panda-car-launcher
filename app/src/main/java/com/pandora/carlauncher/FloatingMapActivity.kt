package com.pandora.carlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

/**
 * 地图悬浮窗 Activity
 * 使用 MediaProjection 截屏显示真实导航画面
 */
class FloatingMapActivity : Activity() {

    companion object {
        private const val TAG = "FloatingMapActivity"
        private const val REQUEST_CAPTURE = 1001
        const val EXTRA_MAP_PACKAGE = "extra_map_package"
        const val EXTRA_MAP_NAME = "extra_map_name"

        fun start(context: Context, pkg: String, name: String) {
            val intent = Intent(context, FloatingMapActivity::class.java).apply {
                putExtra(EXTRA_MAP_PACKAGE, pkg)
                putExtra(EXTRA_MAP_NAME, name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var ivScreenshot: ImageView? = null
    private var tvStatus: TextView? = null
    private var tvTitle: TextView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false
    private var mapPackage: String? = null
    private var mapName: String? = null

    // 拖动
    private var initialX = 0f
    private var initialY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mapPackage = intent.getStringExtra(EXTRA_MAP_PACKAGE)
        mapName = intent.getStringExtra(EXTRA_MAP_NAME)

        if (mapPackage == null) {
            finish()
            return
        }

        setupWindow()
        setContentView(R.layout.activity_floating_map)

        ivScreenshot = findViewById(R.id.floating_map_screenshot)
        tvStatus = findViewById(R.id.floating_map_status)
        tvTitle = findViewById(R.id.floating_map_title)

        tvTitle?.text = mapName
        tvStatus?.text = "正在启动导航..."

        setupDrag()
        setupButtons()

        // 启动地图应用
        openMapApp(mapPackage!!)

        // 请求截屏权限
        requestScreenCapture()
    }

    private fun setupWindow() {
        window.setType(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
        )

        val params = window.attributes
        params.width = 640
        params.height = 400
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50
        params.y = 200
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                       WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        window.attributes = params
    }

    private fun setupDrag() {
        val titleBar = findViewById<View>(R.id.floating_map_title_bar) ?: return

        titleBar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = window.attributes.x.toFloat()
                    initialY = window.attributes.y.toFloat()
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val params = window.attributes
                    params.x = (initialX + dx).toInt()
                    params.y = (initialY + dy).toInt()
                    window.attributes = params
                    true
                }
                else -> false
            }
        }
    }

    private fun setupButtons() {
        findViewById<View>(R.id.floating_map_close)?.setOnClickListener {
            stopCapture()
            finish()
        }

        findViewById<View>(R.id.floating_map_zoom_in)?.setOnClickListener {
            resizeWindow(1.2f)
        }

        findViewById<View>(R.id.floating_map_zoom_out)?.setOnClickListener {
            resizeWindow(0.8f)
        }

        findViewById<View>(R.id.floating_map_content)?.setOnClickListener {
            mapPackage?.let { openMapApp(it) }
        }
    }

    private fun resizeWindow(scale: Float) {
        val params = window.attributes
        params.width = (params.width * scale).toInt().coerceIn(240, 1920)
        params.height = (params.height * scale).toInt().coerceIn(150, 1080)
        window.attributes = params
    }

    private fun openMapApp(pkg: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动地图失败", e)
        }
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                startCapture(resultCode, data)
            } else {
                tvStatus?.text = "截屏权限被拒绝"
                tvStatus?.visibility = View.VISIBLE
            }
        }
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(displayMetrics)

            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)

            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    val bitmap = imageToBitmap(image)
                    image.close()

                    bitmap?.let { bmp ->
                        handler.post {
                            ivScreenshot?.setImageBitmap(bmp)
                            ivScreenshot?.visibility = View.VISIBLE
                            tvStatus?.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理截图失败", e)
                }
            }, handler)

            @Suppress("DEPRECATION")
            mediaProjection?.createVirtualDisplay(
                "MapCapture",
                screenWidth, screenHeight, displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )

            isCapturing = true
            Log.d(TAG, "截屏已启动")

        } catch (e: Exception) {
            Log.e(TAG, "启动截屏失败", e)
            tvStatus?.text = "截屏启动失败"
            tvStatus?.visibility = View.VISIBLE
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width
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

    private fun stopCapture() {
        isCapturing = false
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
    }
}
