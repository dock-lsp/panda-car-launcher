package com.pandora.carlauncher

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.nio.ByteBuffer

/**
 * 屏幕捕获工具
 * 使用 MediaProjection 截屏，用于悬浮窗画中画显示
 */
class ScreenCaptureHelper(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCapture"
        const val REQUEST_CODE = 2001
        private const val CAPTURE_INTERVAL_MS = 1000L // 每秒刷新一次
    }

    interface Callback {
        fun onCaptureFrame(bitmap: Bitmap?)
        fun onCaptureError(error: String?)
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var captureRunnable: Runnable? = null
    private var isCapturing = false

    private val projectionManager: MediaProjectionManager
        get() = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    /**
     * 获取录屏授权 Intent
     */
    fun getProjectionIntent(): Intent {
        return projectionManager.createScreenCaptureIntent()
    }

    /**
     * 处理授权结果
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?, callback: Callback) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                startCapture(resultCode, data, callback)
            } else {
                callback.onCaptureError("用户拒绝了录屏权限")
            }
        }
    }

    /**
     * 开始截屏捕获
     */
    @Suppress("DEPRECATION")
    private fun startCapture(resultCode: Int, data: Intent, callback: Callback) {
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped")
                    stopCapture()
                }
            }, handler)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)

            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            val density = displayMetrics.densityDpi

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader?.setOnImageAvailableListener({ reader ->
                try {
                    val image: Image? = reader.acquireLatestImage()
                    if (image != null) {
                        val bitmap = imageToBitmap(image)
                        callback.onCaptureFrame(bitmap)
                        image.close()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理截图失败", e)
                }
            }, handler)

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )

            isCapturing = true
            Log.d(TAG, "截屏捕获已启动: ${width}x${height}")

        } catch (e: Exception) {
            Log.e(TAG, "启动截屏失败", e)
            callback.onCaptureError("启动截屏失败: ${e.message}")
        }
    }

    /**
     * 开始定时截屏
     */
    fun startPeriodicCapture(resultCode: Int, data: Intent, callback: Callback) {
        startCapture(resultCode, data, callback)
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val planes = image.planes
        if (planes.isEmpty()) return null
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride

        val width = image.width
        val height = image.height

        val bitmap = Bitmap.createBitmap(
            width + rowPadding / pixelStride,
            height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        // 裁剪掉 padding
        return if (rowPadding > 0) {
            Bitmap.createBitmap(bitmap, 0, 0, width, height)
        } else {
            bitmap
        }
    }

    /**
     * 停止截屏
     */
    fun stopCapture() {
        isCapturing = false
        captureRunnable?.let { handler.removeCallbacks(it) }
        captureRunnable = null

        virtualDisplay?.release()
        virtualDisplay = null

        imageReader?.close()
        imageReader = null

        mediaProjection?.stop()
        mediaProjection = null

        Log.d(TAG, "截屏捕获已停止")
    }

    val isRunning: Boolean get() = isCapturing
}
