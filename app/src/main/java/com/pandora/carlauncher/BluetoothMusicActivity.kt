package com.pandora.carlauncher

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL
import java.util.*

/**
 * 蓝牙音乐播放器
 * 支持蓝牙A2DP播放、歌词显示
 */
class BluetoothMusicActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BluetoothMusic"
        private const val REQUEST_BT_PERMISSIONS = 1001
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var pairedDevices = mutableListOf<BluetoothDevice>()
    private var connectedDevice: BluetoothDevice? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null

    private lateinit var rvDevices: RecyclerView
    private lateinit var ivBluetoothStatus: ImageView
    private lateinit var tvSongTitle: TextView
    private lateinit var tvSongArtist: TextView
    private lateinit var tvLyrics: TextView
    private lateinit var seekProgress: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlayPause: ImageView

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        if (ActivityCompat.checkSelfPermission(this@BluetoothMusicActivity,
                                Manifest.permission.BluetoothConnect) == PackageManager.PERMISSION_GRANTED) {
                            if (it.name != null && it.name.isNotEmpty()) {
                                // 设备发现
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    updateBluetoothStatus(state == BluetoothAdapter.STATE_ON)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_music)

        initViews()
        checkBluetoothPermissions()
        registerBluetoothReceiver()
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

        findViewById<View>(R.id.btn_scan_bluetooth)?.setOnClickListener {
            scanBluetoothDevices()
        }

        findViewById<View>(R.id.btn_prev)?.setOnClickListener { playPrev() }
        findViewById<View>(R.id.btn_next)?.setOnClickListener { playNext() }
        btnPlayPause?.setOnClickListener { togglePlayPause() }

        seekProgress?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    mediaPlayer?.seekTo(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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
            tvSongTitle?.text = "设备不支持蓝牙"
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
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BluetoothConnect) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.bondedDevices?.let {
                pairedDevices.addAll(it)
            }
        }
        rvDevices.adapter = DeviceAdapter(pairedDevices) { device ->
            connectToDevice(device)
        }
    }

    private fun scanBluetoothDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BluetoothScan) == PackageManager.PERMISSION_GRANTED) {
            bluetoothAdapter?.startDiscovery()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        connectedDevice = device
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BluetoothConnect) == PackageManager.PERMISSION_GRANTED) {
            tvSongTitle?.text = "已连接: ${device.name}"
        }
    }

    private fun togglePlayPause() {
        if (isPlaying) {
            mediaPlayer?.pause()
            btnPlayPause?.setImageResource(R.drawable.ic_play)
        } else {
            mediaPlayer?.start()
            btnPlayPause?.setImageResource(R.drawable.ic_pause)
            startProgressUpdate()
        }
        isPlaying = !isPlaying
    }

    private fun playPrev() {
        // 上一曲
    }

    private fun playNext() {
        // 下一曲
    }

    private fun startProgressUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                mediaPlayer?.let { mp ->
                    if (mp.isPlaying) {
                        seekProgress?.progress = mp.currentPosition
                        tvCurrentTime?.text = formatTime(mp.currentPosition)
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(updateRunnable!!)
    }

    private fun formatTime(ms: Int): String {
        val seconds = ms / 1000
        return "%02d:%02d".format(seconds / 60, seconds % 60)
    }

    /**
     * 解析LRC歌词
     */
    private fun parseLyrics(lrcContent: String): List<Pair<Long, String>> {
        val lines = mutableListOf<Pair<Long, String>>()
        val regex = Regex("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)")
        lrcContent.lines().forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val (min, sec, ms, text) = match.destructured
                val time = (min.toLong() * 60000) + (sec.toLong() * 1000) + (ms.toLong() * 10)
                lines.add(time to text)
            }
        }
        return lines.sortedBy { it.first }
    }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(bluetoothReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(bluetoothReceiver) } catch (_: Exception) {}
        updateRunnable?.let { handler.removeCallbacks(it) }
        mediaPlayer?.release()
        mediaPlayer = null
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
                    Manifest.permission.BluetoothConnect) == PackageManager.PERMISSION_GRANTED) {
                holder.tvName.text = device.name ?: "未知设备"
            }
            holder.tvAddress.text = device.address
            holder.itemView.setOnClickListener { onConnect(device) }
        }

        override fun getItemCount() = devices.size
    }
}
