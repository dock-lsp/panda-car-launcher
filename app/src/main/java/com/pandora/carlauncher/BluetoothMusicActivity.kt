package com.pandora.carlauncher

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.view.KeyEvent
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * 蓝牙音乐播放器
 * 通过 MediaSession 控制蓝牙音乐播放
 */
class BluetoothMusicActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BluetoothMusic"
        private const val REQUEST_BT_PERMISSIONS = 1001
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pairedDevices = mutableListOf<BluetoothDevice>()
    private var connectedDevice: BluetoothDevice? = null
    private val handler = Handler(Looper.getMainLooper())

    // MediaSession 控制
    private var mediaSessionManager: MediaSessionManager? = null
    private var mediaController: MediaController? = null
    private var audioManager: AudioManager? = null

    private lateinit var rvDevices: RecyclerView
    private lateinit var ivBluetoothStatus: ImageView
    private lateinit var tvSongTitle: TextView
    private lateinit var tvSongArtist: TextView
    private lateinit var tvLyrics: TextView
    private lateinit var seekProgress: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnPrev: ImageView
    private lateinit var btnNext: ImageView

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    updateBluetoothStatus(state == BluetoothAdapter.STATE_ON)
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { onDeviceConnected(it) }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { onDeviceDisconnected(it) }
                }
            }
        }
    }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updatePlaybackState(state)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMetadata(metadata)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_music)

        initViews()
        checkBluetoothPermissions()
        registerBluetoothReceiver()
        initMediaSession()
    }

    private fun initViews() {
        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        rvDevices = findViewById(R.id.rv_bluetooth_devices)
        rvDevices.layoutManager = LinearLayoutManager(this)

        ivBluetoothStatus = findViewById(R.id.iv_bluetooth_status)
        tvSongTitle = findViewById(R.id.tv_song_title)
        tvSongArtist = findViewById(R.id.tv_song_artist)
        tvLyrics = findViewById(R.id.tv_lyrics)
        seekProgress = findViewById(R.id.seek_progress)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalTime = findViewById(R.id.tv_total_time)
        btnPlayPause = findViewById(R.id.btn_play_pause)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)

        findViewById<View>(R.id.btn_scan_bluetooth)?.setOnClickListener {
            scanBluetoothDevices()
        }

        btnPrev.setOnClickListener { sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        btnNext.setOnClickListener { sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_NEXT) }
        btnPlayPause.setOnClickListener { togglePlayPause() }

        seekProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    // 蓝牙音乐通常不支持精确 seek
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun initMediaSession() {
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        // 查找活动的 MediaController
        findActiveMediaController()
    }

    private fun findActiveMediaController() {
        try {
            val controllers = mediaSessionManager?.getActiveSessions(null)
            if (!controllers.isNullOrEmpty()) {
                // 使用第一个活动的 MediaController
                setupMediaController(controllers[0])
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "需要通知监听权限")
        }
    }

    private fun setupMediaController(controller: MediaController?) {
        mediaController?.unregisterCallback(mediaCallback)
        mediaController = controller
        controller?.registerCallback(mediaCallback)

        // 更新 UI
        updatePlaybackState(controller?.playbackState)
        updateMetadata(controller?.metadata)
    }

    private fun updatePlaybackState(state: PlaybackState?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        // 更新进度
        val position = state?.position ?: 0
        val duration = mediaController?.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0

        if (duration > 0) {
            seekProgress.max = duration.toInt()
            seekProgress.progress = position.toInt()
            tvCurrentTime.text = formatTime(position)
            tvTotalTime.text = formatTime(duration)
        }
    }

    private fun updateMetadata(metadata: MediaMetadata?) {
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "未知歌曲"
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "未知歌手"

        tvSongTitle.text = title
        tvSongArtist.text = artist
        tvLyrics.text = "暂无歌词"

        // 更新总时长
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
        if (duration > 0) {
            tvTotalTime.text = formatTime(duration)
            seekProgress.max = duration.toInt()
        }
    }

    private fun togglePlayPause() {
        val state = mediaController?.playbackState?.state
        if (state == PlaybackState.STATE_PLAYING) {
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PAUSE)
        } else {
            sendMediaKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
        }
    }

    private fun sendMediaKeyEvent(keyCode: Int) {
        try {
            // 方法1: 使用 MediaController
            when (keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> mediaController?.transportControls?.play()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> mediaController?.transportControls?.pause()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> mediaController?.transportControls?.skipToPrevious()
                KeyEvent.KEYCODE_MEDIA_NEXT -> mediaController?.transportControls?.skipToNext()
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaController 控制失败", e)
            // 方法2: 使用 AudioManager 发送媒体按键事件
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager?.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
    }

    private fun checkBluetoothPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissions.add(Manifest.permission.BLUETOOTH)
        permissions.add(Manifest.permission.BLUETOOTH_ADMIN)

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_BT_PERMISSIONS)
        } else {
            initBluetooth()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_BT_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initBluetooth()
        }
    }

    private fun initBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            tvSongTitle.text = "设备不支持蓝牙"
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, 1002)
            return
        }

        updateBluetoothStatus(true)
        loadPairedDevices()
    }

    private fun updateBluetoothStatus(enabled: Boolean) {
        ivBluetoothStatus?.setImageResource(
            if (enabled) R.drawable.ic_bluetooth else R.drawable.ic_bluetooth_disabled
        )
    }

    private fun loadPairedDevices() {
        pairedDevices.clear()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.bondedDevices?.let {
                pairedDevices.addAll(it)
            }
        }
        rvDevices.adapter = DeviceAdapter(pairedDevices) { device ->
            connectToDevice(device)
        }
    }

    private fun scanBluetoothDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.startDiscovery()
            Toast.makeText(this, "正在扫描设备...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        connectedDevice = device
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "已选择: ${device.name ?: "未知设备"}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onDeviceConnected(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "已连接: ${device.name}", Toast.LENGTH_SHORT).show()
            // 重新查找 MediaController
            findActiveMediaController()
        }
    }

    private fun onDeviceDisconnected(device: BluetoothDevice) {
        Toast.makeText(this, "已断开连接", Toast.LENGTH_SHORT).show()
    }

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        // 重新查找 MediaController
        findActiveMediaController()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
        mediaController?.unregisterCallback(mediaCallback)
    }

    // 蓝牙设备适配器
    inner class DeviceAdapter(
        private val devices: List<BluetoothDevice>,
        private val onConnect: (BluetoothDevice) -> Unit
    ) : RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tv_device_name)
            val tvAddress: TextView = view.findViewById(R.id.tv_device_address)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bluetooth_device, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position]
            if (ActivityCompat.checkSelfPermission(this@BluetoothMusicActivity,
                    Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                holder.tvName.text = device.name ?: "未知设备"
            }
            holder.tvAddress.text = device.address
            holder.itemView.setOnClickListener { onConnect(device) }
        }

        override fun getItemCount() = devices.size
    }
}
