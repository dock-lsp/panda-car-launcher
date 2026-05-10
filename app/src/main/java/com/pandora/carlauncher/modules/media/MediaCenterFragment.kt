package com.pandora.carlauncher.modules.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R
import kotlinx.coroutines.*
import java.io.File

/**
 * 媒体中心Fragment
 * 
 * 功能：
 * - 本地媒体扫描和播放
 * - 蓝牙音乐播放
 * - USB/SD卡媒体播放
 * - 播放控制（播放/暂停/上一首/下一首）
 * - 专辑封面显示
 * - 播放列表管理
 */
class MediaCenterFragment : Fragment() {

    companion object {
        private const val TAG = "MediaCenterFragment"
        
        // 媒体来源
        const val SOURCE_LOCAL = 0
        const val SOURCE_BLUETOOTH = 1
        const val SOURCE_USB = 2
        const val SOURCE_SD = 3
    }
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // 媒体服务
    private var mediaService: MediaPlayService? = null
    private var mediaBound = false
    
    // 媒体会话
    private lateinit var mediaSession: MediaSession
    
    // 音频管理器
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    // UI控件
    private lateinit var albumArt: ImageView
    private lateinit var songTitle: TextView
    private lateinit var artistName: TextView
    private lateinit var albumName: TextView
    private lateinit var playPauseButton: ImageButton
    private lateinit var prevButton: ImageButton
    private lateinit var nextButton: ImageButton
    private lateinit var progressSeekBar: SeekBar
    private lateinit var currentTimeText: TextView
    private lateinit var totalTimeText: TextView
    private lateinit var sourceSpinner: Spinner
    private lateinit var volumeButton: ImageButton
    private lateinit var volumeSlider: SeekBar
    
    // 媒体列表
    private lateinit var mediaListView: ListView
    private lateinit var mediaAdapter: MediaListAdapter
    private val mediaList = mutableListOf<MediaItem>()
    
    // 当前播放状态
    private var isPlaying = false
    private var currentSource = SOURCE_LOCAL
    private var currentMediaIndex = 0
    private var currentPosition = 0L
    private var totalDuration = 0L
    
    // 音频焦点监听器
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 获得焦点，恢复播放
                mediaService?.play()
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 失去焦点，暂停播放
                mediaService?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 暂时失去焦点，暂停
                mediaService?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 暂时失去焦点但可继续播放，降低音量
                mediaService?.setVolume(0.3f)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_media_center, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        initMediaSession()
        initAudioManager()
        setupListeners()
        scanMediaFiles()
    }
    
    /**
     * 初始化视图
     */
    private fun initViews(view: View) {
        albumArt = view.findViewById(R.id.iv_album_art)
        songTitle = view.findViewById(R.id.tv_song_title)
        artistName = view.findViewById(R.id.tv_artist_name)
        albumName = view.findViewById(R.id.tv_album_name)
        playPauseButton = view.findViewById(R.id.btn_play_pause)
        prevButton = view.findViewById(R.id.btn_prev)
        nextButton = view.findViewById(R.id.btn_next)
        progressSeekBar = view.findViewById(R.id.seek_progress)
        currentTimeText = view.findViewById(R.id.tv_current_time)
        totalTimeText = view.findViewById(R.id.tv_total_time)
        sourceSpinner = view.findViewById(R.id.spinner_source)
        volumeButton = view.findViewById(R.id.btn_volume)
        volumeSlider = view.findViewById(R.id.seek_volume)
        mediaListView = view.findViewById(R.id.lv_media_list)
        
        // 初始化媒体列表适配器
        mediaAdapter = MediaListAdapter(requireContext(), mediaList)
        mediaListView.adapter = mediaAdapter
        
        // 初始化音量滑块
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volumeSlider.max = maxVolume
        volumeSlider.progress = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        
        // 初始化来源选择器
        setupSourceSpinner()
    }
    
    /**
     * 初始化媒体会话
     */
    private fun initMediaSession() {
        mediaSession = MediaSession(requireContext(), "PandaCarMedia").apply {
            setCallback(mediaSessionCallback)
            isActive = true
        }
    }
    
    /**
     * 初始化音频管理器
     */
    private fun initAudioManager() {
        audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // 创建音频焦点请求
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        
        audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
    }
    
    /**
     * 设置来源选择器
     */
    private fun setupSourceSpinner() {
        val sources = arrayOf("本地音乐", "蓝牙音乐", "USB音乐", "SD卡音乐")
        val adapter = ArrayAdapter(requireContext(), 
            android.R.layout.simple_spinner_dropdown_item, sources)
        sourceSpinner.adapter = adapter
        
        sourceSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSource = position
                scanMediaFiles()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 播放/暂停
        playPauseButton.setOnClickListener {
            togglePlayPause()
        }
        
        // 上一首
        prevButton.setOnClickListener {
            playPrevious()
        }
        
        // 下一首
        nextButton.setOnClickListener {
            playNext()
        }
        
        // 进度条
        progressSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    seekTo(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 音量按钮
        volumeButton.setOnClickListener {
            toggleMute()
        }
        
        // 音量滑块
        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    setVolume(progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 媒体列表项点击
        mediaListView.setOnItemClickListener { _, _, position, _ ->
            playMedia(position)
        }
    }
    
    /**
     * 扫描媒体文件
     */
    private fun scanMediaFiles() {
        scope.launch(Dispatchers.IO) {
            mediaList.clear()
            
            val paths = when (currentSource) {
                SOURCE_LOCAL -> listOf("/storage/emulated/0/Music")
                SOURCE_USB -> listOf("/storage/usbotg", "/storage/usbdisk")
                SOURCE_SD -> listOf("/storage/sdcard1/Music")
                else -> emptyList()
            }
            
            paths.forEach { path ->
                scanDirectory(File(path))
            }
            
            withContext(Dispatchers.Main) {
                mediaAdapter.notifyDataSetChanged()
                
                if (mediaList.isEmpty()) {
                    Toast.makeText(context, "未找到媒体文件", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 扫描目录
     */
    private fun scanDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) return
        
        directory.listFiles()?.forEach { file ->
            if (file.isFile && isMediaFile(file.name)) {
                val mediaItem = extractMediaInfo(file)
                mediaList.add(mediaItem)
            } else if (file.isDirectory) {
                scanDirectory(file)
            }
        }
    }
    
    /**
     * 检查是否为媒体文件
     */
    private fun isMediaFile(fileName: String): Boolean {
        val extensions = listOf(".mp3", ".wav", ".flac", ".aac", ".ogg", 
                                 ".mp4", ".avi", ".mkv", ".flv", ".mov")
        return extensions.any { fileName.lowercase().endsWith(it) }
    }
    
    /**
     * 提取媒体信息
     */
    private fun extractMediaInfo(file: File): MediaItem {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) 
                ?: file.nameWithoutExtension
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) 
                ?: "未知艺术家"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) 
                ?: "未知专辑"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            
            // 尝试获取专辑封面
            val art = retriever.embeddedPicture
            val bitmap = if (art != null) {
                BitmapFactory.decodeByteArray(art, 0, art.size)
            } else {
                null
            }
            
            MediaItem(title, artist, album, duration, file.absolutePath, bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "提取媒体信息失败: ${file.name}", e)
            MediaItem(file.nameWithoutExtension, "未知艺术家", "未知专辑", 0L, file.absolutePath, null)
        } finally {
            retriever.release()
        }
    }
    
    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        if (isPlaying) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * 播放
     */
    private fun play() {
        if (requestAudioFocus()) {
            mediaService?.play()
            isPlaying = true
            updatePlayPauseButton()
        }
    }
    
    /**
     * 暂停
     */
    private fun pause() {
        mediaService?.pause()
        isPlaying = false
        updatePlayPauseButton()
    }
    
    /**
     * 播放指定媒体
     */
    private fun playMedia(index: Int) {
        if (index < 0 || index >= mediaList.size) return
        
        currentMediaIndex = index
        val mediaItem = mediaList[index]
        
        // 更新UI
        updateNowPlayingUI(mediaItem)
        
        // 通知服务播放
        mediaService?.playMedia(mediaItem.path)
    }
    
    /**
     * 播放上一首
     */
    private fun playPrevious() {
        val newIndex = if (currentMediaIndex > 0) currentMediaIndex - 1 else mediaList.size - 1
        playMedia(newIndex)
    }
    
    /**
     * 播放下一首
     */
    private fun playNext() {
        val newIndex = if (currentMediaIndex < mediaList.size - 1) currentMediaIndex + 1 else 0
        playMedia(newIndex)
    }
    
    /**
     * 跳转到指定位置
     */
    private fun seekTo(position: Long) {
        mediaService?.seekTo(position)
        currentPosition = position
        updateProgressUI()
    }
    
    /**
     * 设置音量
     */
    private fun setVolume(volume: Int) {
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }
    
    /**
     * 切换静音
     */
    private fun toggleMute() {
        audioManager.adjustVolume(AudioManager.ADJUST_TOGGLE_MUTE, 0)
    }
    
    /**
     * 请求音频焦点
     */
    private fun requestAudioFocus(): Boolean {
        val result = audioManager.requestAudioFocus(audioFocusRequest!!)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    
    /**
     * 更新播放/暂停按钮
     */
    private fun updatePlayPauseButton() {
        playPauseButton.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause 
            else android.R.drawable.ic_media_play
        )
    }
    
    /**
     * 更新当前播放信息UI
     */
    private fun updateNowPlayingUI(mediaItem: MediaItem) {
        songTitle.text = mediaItem.title
        artistName.text = mediaItem.artist
        albumName.text = mediaItem.album
        totalTimeText.text = formatTime(mediaItem.duration)
        progressSeekBar.max = mediaItem.duration.toInt()
        totalDuration = mediaItem.duration
        
        // 设置专辑封面
        if (mediaItem.albumArt != null) {
            albumArt.setImageBitmap(mediaItem.albumArt)
        } else {
            albumArt.setImageResource(R.drawable.ic_album_placeholder)
        }
    }
    
    /**
     * 更新播放进度UI
     */
    private fun updateProgressUI() {
        currentTimeText.text = formatTime(currentPosition)
        progressSeekBar.progress = currentPosition.toInt()
    }
    
    /**
     * 格式化时间
     */
    private fun formatTime(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / 1000 / 60) % 60
        val hours = millis / 1000 / 60 / 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
    
    /**
     * 媒体会话回调
     */
    private val mediaSessionCallback = object : MediaSession.Callback() {
        override fun onPlay() {
            play()
        }
        
        override fun onPause() {
            pause()
        }
        
        override fun onSkipToNext() {
            playNext()
        }
        
        override fun onSkipToPrevious() {
            playPrevious()
        }
        
        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }
        
        override fun onStop() {
            mediaService?.stop()
            isPlaying = false
            updatePlayPauseButton()
        }
    }
    
    override fun onStart() {
        super.onStart()
        // 绑定媒体服务
        Intent(requireContext(), MediaPlayService::class.java).also { intent ->
            requireContext().bindService(intent, mediaConnection, Context.BIND_AUTO_CREATE)
        }
    }
    
    override fun onStop() {
        super.onStop()
        // 解绑媒体服务
        if (mediaBound) {
            requireContext().unbindService(mediaConnection)
            mediaBound = false
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        mediaSession.release()
        audioManager.abandonAudioFocusRequest(audioFocusRequest!!)
    }
    
    /**
     * 媒体服务连接
     */
    private val mediaConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlayService.MediaBinder
            mediaService = binder.getService()
            mediaBound = true
            Log.i(TAG, "媒体服务已连接")
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            mediaService = null
            mediaBound = false
        }
    }
}

/**
 * 媒体项数据类
 */
data class MediaItem(
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val albumArt: Bitmap? = null
) : java.io.Serializable

/**
 * 媒体列表适配器
 */
class MediaListAdapter(
    context: Context,
    private val mediaList: List<MediaItem>
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = mediaList.size

    override fun getItem(position: Int): MediaItem = mediaList[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_media, parent, false)
        val item = getItem(position)

        view.findViewById<TextView>(R.id.tv_name).text = item.title
        view.findViewById<TextView>(R.id.tv_info).text = "${item.artist} - ${formatDuration(item.duration)}"

        val albumArt = view.findViewById<ImageView>(R.id.iv_icon)
        if (item.albumArt != null) {
            albumArt.setImageBitmap(item.albumArt)
        } else {
            albumArt.setImageResource(R.drawable.ic_album_placeholder)
        }

        return view
    }

    private fun formatDuration(millis: Long): String {
        val minutes = (millis / 1000 / 60).toInt()
        val seconds = ((millis / 1000) % 60).toInt()
        return String.format("%d:%02d", minutes, seconds)
    }
}
