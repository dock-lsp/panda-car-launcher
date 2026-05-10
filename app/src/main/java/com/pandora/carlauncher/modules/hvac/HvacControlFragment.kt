package com.pandora.carlauncher.modules.hvac

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.pandora.carlauncher.R

/**
 * 空调控制Fragment
 * 
 * 功能：
 * - 双温区温度调节
 * - 风量控制
 * - AC开关
 * - 风向模式切换
 * - 前后除雾
 * - 通风模式选择
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
        
        // 通风模式
        const val VENT_FACE = 1
        const val VENT_BODY = 2
        const val VENT_FOOT = 3
        const val VENT_ALL = 4
    }
    
    // UI组件 - 温度控制
    private lateinit var driverTempText: TextView
    private lateinit var passengerTempText: TextView
    private lateinit var driverTempSeekBar: SeekBar
    private lateinit var passengerTempSeekBar: SeekBar
    private lateinit var fanSpeedSeekBar: SeekBar
    
    // UI组件 - 功能按钮
    private lateinit var acButton: ImageButton
    private lateinit var autoButton: ImageButton
    private lateinit var recirculationButton: ImageButton
    private lateinit var frontDefrostButton: ImageButton
    private lateinit var rearDefrostButton: ImageButton
    
    // UI组件 - 通风模式
    private lateinit var ventFaceButton: ImageButton
    private lateinit var ventBodyButton: ImageButton
    private lateinit var ventFootButton: ImageButton
    private lateinit var ventAllButton: ImageButton
    
    // 状态
    private var driverTemp = 220  // 22℃
    private var passengerTemp = 220
    private var fanSpeed = 3
    private var isAcOn = false
    private var isAutoMode = false
    private var isRecycleOn = false
    private var isFrontDefrostOn = false
    private var isRearDefrostOn = false
    private var currentVentMode = VENT_FACE

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
        // 温度控制
        driverTempText = view.findViewById(R.id.tv_driver_temp)
        passengerTempText = view.findViewById(R.id.tv_passenger_temp)
        driverTempSeekBar = view.findViewById(R.id.seek_driver_temp)
        passengerTempSeekBar = view.findViewById(R.id.seek_passenger_temp)
        fanSpeedSeekBar = view.findViewById(R.id.seek_fan_speed)
        
        // 功能按钮
        acButton = view.findViewById(R.id.btn_ac)
        autoButton = view.findViewById(R.id.btn_auto)
        recirculationButton = view.findViewById(R.id.btn_recirculation)
        frontDefrostButton = view.findViewById(R.id.btn_front_defrost)
        rearDefrostButton = view.findViewById(R.id.btn_rear_defrost)
        
        // 通风模式按钮
        ventFaceButton = view.findViewById(R.id.vent_face)
        ventBodyButton = view.findViewById(R.id.vent_body)
        ventFootButton = view.findViewById(R.id.vent_foot)
        ventAllButton = view.findViewById(R.id.vent_all)
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
            showToast(if (isAcOn) "空调已开启" else "空调已关闭")
        }
        
        // 自动模式
        autoButton.setOnClickListener {
            isAutoMode = !isAutoMode
            updateAutoButton()
            if (isAutoMode) {
                // 自动模式设置默认温度和风量
                driverTemp = 220
                passengerTemp = 220
                fanSpeed = 3
                updateUI()
                showToast("自动模式已开启")
            }
        }
        
        // 循环模式
        recirculationButton.setOnClickListener {
            isRecycleOn = !isRecycleOn
            updateRecycleButton()
            showToast(if (isRecycleOn) "内循环模式" else "外循环模式")
        }
        
        // 前除雾
        frontDefrostButton.setOnClickListener {
            isFrontDefrostOn = !isFrontDefrostOn
            updateFrontDefrostButton()
            showToast(if (isFrontDefrostOn) "前除雾已开启" else "前除雾已关闭")
        }
        
        // 后除雾
        rearDefrostButton.setOnClickListener {
            isRearDefrostOn = !isRearDefrostOn
            updateRearDefrostButton()
            showToast(if (isRearDefrostOn) "后除雾已开启" else "后除雾已关闭")
        }
        
        // 通风模式 - 面部
        ventFaceButton.setOnClickListener {
            setVentMode(VENT_FACE)
            showToast("面部通风")
        }
        
        // 通风模式 - 身体
        ventBodyButton.setOnClickListener {
            setVentMode(VENT_BODY)
            showToast("身体通风")
        }
        
        // 通风模式 - 脚部
        ventFootButton.setOnClickListener {
            setVentMode(VENT_FOOT)
            showToast("脚部通风")
        }
        
        // 通风模式 - 全部
        ventAllButton.setOnClickListener {
            setVentMode(VENT_ALL)
            showToast("全区域通风")
        }
    }
    
    /**
     * 设置通风模式
     */
    private fun setVentMode(mode: Int) {
        currentVentMode = mode
        updateVentModeButtons()
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
        updateFrontDefrostButton()
        updateRearDefrostButton()
        updateVentModeButtons()
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
        recirculationButton.isSelected = isRecycleOn
    }
    
    /**
     * 更新前除雾按钮
     */
    private fun updateFrontDefrostButton() {
        frontDefrostButton.isSelected = isFrontDefrostOn
    }
    
    /**
     * 更新后除雾按钮
     */
    private fun updateRearDefrostButton() {
        rearDefrostButton.isSelected = isRearDefrostOn
    }
    
    /**
     * 更新通风模式按钮状态
     */
    private fun updateVentModeButtons() {
        ventFaceButton.isSelected = (currentVentMode == VENT_FACE)
        ventBodyButton.isSelected = (currentVentMode == VENT_BODY)
        ventFootButton.isSelected = (currentVentMode == VENT_FOOT)
        ventAllButton.isSelected = (currentVentMode == VENT_ALL)
    }
    
    /**
     * 显示提示
     */
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
