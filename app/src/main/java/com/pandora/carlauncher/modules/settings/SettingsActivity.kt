package com.pandora.carlauncher.modules.settings

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.*
import android.media.AudioManager
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.pandora.carlauncher.BuildConfig
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R
import com.pandora.carlauncher.databinding.ActivitySettingsBinding

/**
 * 设置Activity
 * 
 * 功能：
 * - 网络设置（WiFi、移动网络）
 * - 蓝牙设置
 * - 声音设置
 * - 显示设置
 * - 应用管理
 * - 系统信息
 * - 工厂模式入口
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SettingsActivity"
        
        // 工厂模式密码
        private const val FACTORY_PASSWORD = "123456"
    }

    private lateinit var binding: ActivitySettingsBinding
    
    // 系统服务
    private lateinit var wifiManager: WifiManager
    private lateinit var audioManager: AudioManager
    private var bluetoothAdapter: BluetoothAdapter? = null
    
    // 蓝牙设备列表
    private val pairedDevices = mutableListOf<BluetoothDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        initServices()
        setupUI()
        setupListeners()
        loadSettings()
    }

    /**
     * 初始化系统服务
     */
    private fun initServices() {
        wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    }

    /**
     * 设置UI
     */
    private fun setupUI() {
        // 设置Toolbar
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        
        // 设置版本号
        binding.versionText.text = "版本: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // WiFi设置
        binding.cardWifi.setOnClickListener { openWifiSettings() }
        binding.switchWifi.setOnCheckedChangeListener { _, isChecked ->
            setWifiEnabled(isChecked)
        }
        
        // 蓝牙设置
        binding.cardBluetooth.setOnClickListener { openBluetoothSettings() }
        binding.switchBluetooth.setOnCheckedChangeListener { _, isChecked ->
            setBluetoothEnabled(isChecked)
        }
        
        // 声音设置
        binding.cardSound.setOnClickListener { openSoundSettings() }
        
        // 音量滑块
        binding.seekVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setVolume(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 显示设置
        binding.cardDisplay.setOnClickListener { openDisplaySettings() }
        binding.switchNightMode.setOnCheckedChangeListener { _, isChecked ->
            setNightMode(isChecked)
        }
        
        // 应用管理
        binding.cardApps.setOnClickListener { openAppManager() }
        
        // 文件管理
        binding.cardFiles.setOnClickListener { openFileManager() }
        
        // 系统信息
        binding.cardAbout.setOnClickListener { showAboutDialog() }
        
        // 工厂模式（隐藏入口）
        binding.versionText.setOnClickListener {
            showFactoryModePasswordDialog()
        }
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        // WiFi状态
        binding.switchWifi.isChecked = wifiManager.isWifiEnabled
        binding.tvWifiStatus.text = if (wifiManager.isWifiEnabled) "已开启" else "已关闭"
        
        // 蓝牙状态
        binding.switchBluetooth.isChecked = bluetoothAdapter?.isEnabled == true
        binding.tvBluetoothStatus.text = if (bluetoothAdapter?.isEnabled == true) "已开启" else "已关闭"
        
        // 音量
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        binding.seekVolume.max = maxVolume
        binding.seekVolume.progress = currentVolume
        binding.tvVolumeValue.text = "$currentVolume / $maxVolume"
    }

    /**
     * 打开WiFi设置
     */
    private fun openWifiSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开WiFi设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置WiFi开关
     */
    private fun setWifiEnabled(enabled: Boolean) {
        try {
            wifiManager.isWifiEnabled = enabled
            binding.tvWifiStatus.text = if (enabled) "已开启" else "已关闭"
        } catch (e: Exception) {
            Toast.makeText(this, "WiFi设置失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开蓝牙设置
     */
    private fun openBluetoothSettings() {
        try {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开蓝牙设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置蓝牙开关
     */
    private fun setBluetoothEnabled(enabled: Boolean) {
        try {
            if (enabled) {
                bluetoothAdapter?.enable()
            } else {
                bluetoothAdapter?.disable()
            }
            binding.tvBluetoothStatus.text = if (enabled) "已开启" else "已关闭"
        } catch (e: Exception) {
            Toast.makeText(this, "蓝牙设置失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开声音设置
     */
    private fun openSoundSettings() {
        try {
            startActivity(Intent(Settings.ACTION_SOUND_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开声音设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置音量
     */
    private fun setVolume(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
        binding.tvVolumeValue.text = "$volume / ${audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}"
    }

    /**
     * 打开显示设置
     */
    private fun openDisplaySettings() {
        try {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开显示设置", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 设置夜间模式
     */
    private fun setNightMode(enabled: Boolean) {
        // 保存设置
        PandaCarApplication.getInstance().preferencesManager.setNightMode(enabled)
        
        // 应用主题
        if (enabled) {
            setTheme(R.style.Theme_PandaCarLauncher_Night)
        } else {
            setTheme(R.style.Theme_PandaCarLauncher)
        }
    }

    /**
     * 打开应用管理
     */
    private fun openAppManager() {
        try {
            startActivity(Intent(this, com.pandora.carlauncher.modules.appmanager.AppManagerActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开应用管理", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 打开文件管理
     */
    private fun openFileManager() {
        try {
            startActivity(Intent(this, com.pandora.carlauncher.modules.filemanager.FileManagerActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开文件管理", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        AlertDialog.Builder(this)
            .setTitle("关于熊猫车机桌面")
            .setMessage("""
                熊猫车机桌面 v${BuildConfig.VERSION_NAME}
                
                这是一款专为Android Automotive OS设计的
                车载信息娱乐系统桌面启动器。
                
                © 2024 PandaCar
            """.trimIndent())
            .setPositiveButton("确定", null)
            .show()
    }

    /**
     * 显示工厂模式密码对话框
     */
    private fun showFactoryModePasswordDialog() {
        val editText = EditText(this).apply {
            hint = "请输入工厂模式密码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                       android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        
        AlertDialog.Builder(this)
            .setTitle("工厂模式")
            .setView(editText)
            .setPositiveButton("进入") { _, _ ->
                val password = editText.text.toString()
                if (password == FACTORY_PASSWORD) {
                    openFactoryMode()
                } else {
                    Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 打开工厂模式
     */
    private fun openFactoryMode() {
        try {
            startActivity(Intent(this, 
                com.pandora.carlauncher.modules.factorymode.FactoryModeActivity::class.java))
        } catch (e: Exception) {
            Toast.makeText(this, "无法打开工厂模式", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSettings()
    }
}
