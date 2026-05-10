package com.pandora.carlauncher.modules.settings

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R
import com.pandora.carlauncher.modules.settings.SettingsActivity

/**
 * 快速设置Fragment
 * 
 * 功能：
 * - WiFi开关
 * - 蓝牙开关
 * - 音量快捷调节
 * - 屏幕亮度调节
 * - 驾驶模式开关
 * - 时间日期设置
 * - 快捷进入完整设置
 */
class QuickSettingsFragment : Fragment() {

    // UI控件
    private lateinit var wifiSwitch: SwitchCompat
    private lateinit var bluetoothSwitch: SwitchCompat
    private lateinit var volumeSeekBar: SeekBar
    private lateinit var volumeText: TextView
    private lateinit var brightnessSeekBar: SeekBar
    private lateinit var brightnessText: TextView
    private lateinit var drivingModeSwitch: SwitchCompat
    private lateinit var nightModeSwitch: SwitchCompat
    private lateinit var settingsButton: ImageButton
    private lateinit var timeText: TextView
    private lateinit var dateText: TextView
    
    // 系统服务
    private var wifiManager: WifiManager? = null
    private lateinit var audioManager: AudioManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // 广播接收器
    private var networkReceiver: BroadcastReceiver? = null
    private var bluetoothReceiver: BroadcastReceiver? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_quick_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initServices()
        initViews(view)
        setupListeners()
        registerReceivers()
        updateStatus()
    }

    /**
     * 初始化系统服务
     */
    private fun initServices() {
        try {
            wifiManager = requireContext().applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (e: Exception) {
            // WiFi 不可用
        }
        
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        try {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        } catch (e: Exception) {
            // 蓝牙不可用
        }
    }

    /**
     * 初始化视图
     */
    private fun initViews(view: View) {
        wifiSwitch = view.findViewById(R.id.switch_wifi)
        bluetoothSwitch = view.findViewById(R.id.switch_bluetooth)
        volumeSeekBar = view.findViewById(R.id.seek_volume)
        volumeText = view.findViewById(R.id.tv_volume)
        brightnessSeekBar = view.findViewById(R.id.seek_brightness)
        brightnessText = view.findViewById(R.id.tv_brightness)
        drivingModeSwitch = view.findViewById(R.id.switch_driving_mode)
        nightModeSwitch = view.findViewById(R.id.switch_night_mode)
        settingsButton = view.findViewById(R.id.btn_settings)
        timeText = view.findViewById(R.id.tv_time)
        dateText = view.findViewById(R.id.tv_date)
        
        // 初始化音量滑块
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSeekBar.max = maxVolume
        volumeSeekBar.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // 初始化亮度滑块
        try {
            val brightness = Settings.System.getInt(
                requireContext().contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
            brightnessSeekBar.max = 255
            brightnessSeekBar.progress = brightness
        } catch (e: Exception) {
            brightnessSeekBar.max = 100
            brightnessSeekBar.progress = 50
        }
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // WiFi开关
        wifiSwitch.setOnCheckedChangeListener { _, isChecked ->
            setWifiEnabled(isChecked)
        }

        // 蓝牙开关
        bluetoothSwitch.setOnCheckedChangeListener { _, isChecked ->
            setBluetoothEnabled(isChecked)
        }

        // 音量调节
        volumeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setVolume(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 亮度调节
        brightnessSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setBrightness(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 驾驶模式开关
        drivingModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            setDrivingMode(isChecked)
        }

        // 夜间模式开关
        nightModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            setNightMode(isChecked)
        }

        // 设置按钮
        settingsButton.setOnClickListener {
            openSettings()
        }
    }

    /**
     * 注册广播接收器
     */
    private fun registerReceivers() {
        try {
            // 网络状态接收器
            networkReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    updateWifiStatus()
                }
            }
            requireContext().registerReceiver(
                networkReceiver,
                IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION)
            )
        } catch (e: Exception) {
            // 忽略
        }

        try {
            // 蓝牙状态接收器
            bluetoothReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    updateBluetoothStatus()
                }
            }
            requireContext().registerReceiver(
                bluetoothReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            )
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 更新状态显示
     */
    private fun updateStatus() {
        updateWifiStatus()
        updateBluetoothStatus()
        updateVolumeDisplay()
        updateBrightnessDisplay()
        updateTimeDisplay()
    }

    /**
     * 更新WiFi状态
     */
    private fun updateWifiStatus() {
        wifiSwitch.isChecked = wifiManager?.isWifiEnabled == true
    }

    /**
     * 更新蓝牙状态
     */
    private fun updateBluetoothStatus() {
        bluetoothSwitch.isChecked = bluetoothAdapter?.isEnabled == true
    }

    /**
     * 更新音量显示
     */
    private fun updateVolumeDisplay() {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val percent = if (maxVolume > 0) (currentVolume * 100 / maxVolume) else 0
        volumeText.text = "$percent%"
    }

    /**
     * 更新亮度显示
     */
    private fun updateBrightnessDisplay() {
        val brightness = brightnessSeekBar.progress
        val percentage = (brightness * 100 / 255)
        brightnessText.text = "$percentage%"
    }

    /**
     * 更新时间显示
     */
    private fun updateTimeDisplay() {
        val now = java.util.Calendar.getInstance()
        val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        val dateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日 E", java.util.Locale.getDefault())
        
        timeText.text = timeFormat.format(now.time)
        dateText.text = dateFormat.format(now.time)
    }

    /**
     * 设置WiFi开关
     */
    private fun setWifiEnabled(enabled: Boolean) {
        wifiManager?.isWifiEnabled = enabled
    }

    /**
     * 设置蓝牙开关
     */
    private fun setBluetoothEnabled(enabled: Boolean) {
        if (bluetoothAdapter != null) {
            if (enabled) {
                bluetoothAdapter?.enable()
            } else {
                bluetoothAdapter?.disable()
            }
        }
    }

    /**
     * 设置音量
     */
    private fun setVolume(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        updateVolumeDisplay()
    }

    /**
     * 设置屏幕亮度
     */
    private fun setBrightness(brightness: Int) {
        try {
            val layoutParams = requireActivity().window.attributes
            layoutParams.screenBrightness = brightness / 255f
            requireActivity().window.attributes = layoutParams
            
            // 保存设置
            Settings.System.putInt(
                requireContext().contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                brightness
            )
            
            updateBrightnessDisplay()
        } catch (e: Exception) {
            Toast.makeText(context, "无法设置亮度", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置驾驶模式
     */
    private fun setDrivingMode(enabled: Boolean) {
        PandaCarApplication.getInstance().updateDrivingState(enabled)
        
        if (enabled) {
            Toast.makeText(context, "驾驶模式已开启", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "驾驶模式已关闭", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置夜间模式
     */
    private fun setNightMode(enabled: Boolean) {
        if (enabled) {
            requireActivity().setTheme(R.style.Theme_PandaCarLauncher_Night)
        } else {
            requireActivity().setTheme(R.style.Theme_PandaCarLauncher)
        }
        
        // 需要重启Activity才能生效
        requireActivity().recreate()
    }

    /**
     * 打开完整设置页面
     */
    private fun openSettings() {
        val intent = Intent(requireContext(), SettingsActivity::class.java)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // 注销广播接收器
        networkReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {}
        }
        bluetoothReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {}
        }
    }
}
