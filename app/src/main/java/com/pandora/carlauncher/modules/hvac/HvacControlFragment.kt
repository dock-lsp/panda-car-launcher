package com.pandora.carlauncher.modules.hvac

import android.car.Car
import android.car.hardware.CarPropertyManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R

/**
 * 空调控制Fragment
 * 
 * 功能：
 * - 双温区温度调节
 * - 风量控制
 * - AC开关
 * - 风向模式切换
 */
class HvacControlFragment : Fragment() {

    companion object {
        private const val TAG = "HvacControlFragment"
        
        // 温度范围（单位：0.1℃）
        const val MIN_TEMP = 160  // 16℃
        const val MAX_TEMP = 300  // 30℃
        
        // 风量范围
        const val MIN_FAN_SPEED = 0
        const val MAX_FAN_SPEED = 7
    }
    
    // UI组件
    private lateinit var driverTempText: TextView
    private lateinit var passengerTempText: TextView
    private lateinit var driverTempSeekBar: SeekBar
    private lateinit var passengerTempSeekBar: SeekBar
    private lateinit var fanSpeedSeekBar: SeekBar
    private lateinit var acButton: ImageButton
    private lateinit var autoButton: ImageButton
    private lateinit var recycleButton: ImageButton
    
    // 状态
    private var driverTemp = 220  // 22℃
    private var passengerTemp = 220
    private var fanSpeed = 3
    private var isAcOn = false
    private var isAutoMode = false
    private var isRecycleOn = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hvac_control, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
        updateUI()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews(view: View) {
        driverTempText = view.findViewById(R.id.tv_driver_temp)
        passengerTempText = view.findViewById(R.id.tv_passenger_temp)
        driverTempSeekBar = view.findViewById(R.id.seek_driver_temp)
        passengerTempSeekBar = view.findViewById(R.id.seek_passenger_temp)
        fanSpeedSeekBar = view.findViewById(R.id.seek_fan_speed)
        acButton = view.findViewById(R.id.btn_ac)
        autoButton = view.findViewById(R.id.btn_auto)
        recycleButton = view.findViewById(R.id.btn_recirculation)
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 驾驶座温度
        driverTempSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    driverTemp = progress + MIN_TEMP
                    updateTemperatureDisplay(true)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 副驾驶温度
        passengerTempSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    passengerTemp = progress + MIN_TEMP
                    updateTemperatureDisplay(false)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 风量
        fanSpeedSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    fanSpeed = progress
                    updateFanSpeedDisplay()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // AC按钮
        acButton.setOnClickListener {
            isAcOn = !isAcOn
            updateAcButton()
        }
        
        // 自动模式
        autoButton.setOnClickListener {
            isAutoMode = !isAutoMode
            updateAutoButton()
        }
        
        // 循环模式
        recycleButton.setOnClickListener {
            isRecycleOn = !isRecycleOn
            updateRecycleButton()
        }
    }
    
    /**
     * 更新UI
     */
    private fun updateUI() {
        updateTemperatureDisplay(true)
        updateTemperatureDisplay(false)
        updateFanSpeedDisplay()
        updateAcButton()
        updateAutoButton()
        updateRecycleButton()
    }
    
    /**
     * 更新温度显示
     */
    private fun updateTemperatureDisplay(isDriver: Boolean) {
        val temp = if (isDriver) driverTemp else passengerTemp
        val tempC = temp / 10.0
        val text = String.format("%.1f°C", tempC)
        
        if (isDriver) {
            driverTempText.text = text
            driverTempSeekBar.progress = driverTemp - MIN_TEMP
        } else {
            passengerTempText.text = text
            passengerTempSeekBar.progress = passengerTemp - MIN_TEMP
        }
    }
    
    /**
     * 更新风量显示
     */
    private fun updateFanSpeedDisplay() {
        fanSpeedSeekBar.progress = fanSpeed
    }
    
    /**
     * 更新AC按钮
     */
    private fun updateAcButton() {
        acButton.isSelected = isAcOn
        acButton.setColorFilter(
            if (isAcOn) resources.getColor(R.color.ac_on, null)
            else resources.getColor(R.color.text_secondary, null)
        )
    }
    
    /**
     * 更新自动模式按钮
     */
    private fun updateAutoButton() {
        autoButton.isSelected = isAutoMode
    }
    
    /**
     * 更新循环模式按钮
     */
    private fun updateRecycleButton() {
        recycleButton.isSelected = isRecycleOn
    }
}
