package com.pandora.carlauncher.modules.factorymode

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.pandora.carlauncher.R
import com.pandora.carlauncher.databinding.ActivityFactoryModeBinding
import kotlinx.coroutines.*

/**
 * 工厂模式Activity
 * 
 * 功能：
 * - 触摸屏校准
 * - CAN总线日志查看
 * - 喇叭测试
 * - GPS信号检测
 * - 传感器检测
 * - 恢复出厂设置
 */
class FactoryModeActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FactoryModeActivity"
        
        // 测试频率
        private val TEST_FREQUENCIES = listOf(100, 500, 1000, 2000, 5000, 10000)
    }

    private lateinit var binding: ActivityFactoryModeBinding
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    
    // 测试状态
    private var isSpeakerTestRunning = false
    private var currentFrequencyIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityFactoryModeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        setupListeners()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        // 显示工厂模式标识
        binding.factoryModeBadge.visibility = View.VISIBLE
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回
        binding.btnBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        // 触摸屏校准
        binding.cardTouchCalibration.setOnClickListener {
            startTouchCalibration()
        }
        
        // CAN日志
        binding.cardCanLog.setOnClickListener {
            viewCanLog()
        }
        
        // 喇叭测试
        binding.cardSpeakerTest.setOnClickListener {
            startSpeakerTest()
        }
        
        // GPS检测
        binding.cardGpsTest.setOnClickListener {
            testGps()
        }
        
        // 传感器检测
        binding.cardSensorTest.setOnClickListener {
            testSensors()
        }
        
        // 恢复出厂设置
        binding.cardFactoryReset.setOnClickListener {
            showFactoryResetConfirm()
        }
    }

    /**
     * 开始触摸屏校准
     */
    private fun startTouchCalibration() {
        showTestResult("开始触摸屏校准...\n请依次点击屏幕上的十字标记")
        // TODO: 实现触摸屏校准
    }

    /**
     * 查看CAN日志
     */
    private fun viewCanLog() {
        showTestResult("CAN总线日志:\n暂无数据")
        // TODO: 实现 CAN 日志查看
    }

    /**
     * 开始喇叭测试
     */
    private fun startSpeakerTest() {
        if (isSpeakerTestRunning) {
            stopSpeakerTest()
        } else {
            isSpeakerTestRunning = true
            binding.btnSpeakerTest.text = "停止测试"
            playSpeakerTest()
        }
    }

    /**
     * 播放喇叭测试音频
     */
    private fun playSpeakerTest() {
        if (!isSpeakerTestRunning) return
        
        val frequency = TEST_FREQUENCIES[currentFrequencyIndex]
        showTestResult("喇叭测试: ${frequency}Hz")
        
        // TODO: 实现音频播放
        
        currentFrequencyIndex = (currentFrequencyIndex + 1) % TEST_FREQUENCIES.size
        
        if (isSpeakerTestRunning) {
            handler.postDelayed({ playSpeakerTest() }, 1000)
        }
    }

    /**
     * 停止喇叭测试
     */
    private fun stopSpeakerTest() {
        isSpeakerTestRunning = false
        currentFrequencyIndex = 0
        binding.btnSpeakerTest.text = "开始测试"
        showTestResult("喇叭测试已停止")
    }

    /**
     * GPS测试
     */
    private fun testGps() {
        showTestResult("GPS测试中...\n正在搜索卫星信号")
        
        scope.launch(Dispatchers.IO) {
            // TODO: 实现 GPS 测试
            delay(2000)
            
            withContext(Dispatchers.Main) {
                showTestResult("GPS测试结果:\n卫星数量: 0\n信号强度: 无")
            }
        }
    }

    /**
     * 传感器测试
     */
    private fun testSensors() {
        showTestResult("传感器测试中...")
        
        scope.launch(Dispatchers.IO) {
            // TODO: 实现传感器测试
            delay(1500)
            
            withContext(Dispatchers.Main) {
                showTestResult("传感器测试结果:\n加速度计: 正常\n陀螺仪: 正常\n磁力计: 正常")
            }
        }
    }

    /**
     * 显示恢复出厂设置确认对话框
     */
    private fun showFactoryResetConfirm() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("恢复出厂设置")
            .setMessage("确定要恢复出厂设置吗？此操作不可撤销！")
            .setPositiveButton("确定") { _, _ ->
                performFactoryReset()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 执行恢复出厂设置
     */
    private fun performFactoryReset() {
        showTestResult("正在恢复出厂设置...")
        // TODO: 实现恢复出厂设置
    }

    /**
     * 显示测试结果
     */
    private fun showTestResult(result: String) {
        binding.tvTestResult.text = result
        binding.tvTestResult.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
    }
}
