package com.pandora.carlauncher.modules.voiceassistant

import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.pandora.carlauncher.PandaCarApplication
import com.pandora.carlauncher.R
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 语音助手服务
 * 
 * 功能：
 * - 离线/在线语音唤醒
 * - 语义理解
 * - 语音指令执行
 * - TTS语音播报
 */
class VoiceAssistantService : android.app.Service() {

    companion object {
        private const val TAG = "VoiceAssistantService"
        
        // 唤醒词
        private const val WAKE_WORD = "你好小熊猫"
        
        // 音频参数
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        
        // 指令类型
        const val CMD_NAVIGATE = "navigate"
        const val CMD_PLAY_MUSIC = "play_music"
        const val CMD_PAUSE_MUSIC = "pause_music"
        const val CMD_PLAY_NEXT = "play_next"
        const val CMD_PLAY_PREV = "play_prev"
        const val CMD_SET_TEMPERATURE = "set_temperature"
        const val CMD_OPEN_AC = "open_ac"
        const val CMD_CLOSE_AC = "close_ac"
        const val CMD_CALL = "call"
        const val CMD_UNKNOWN = "unknown"
        
        // 广播Action
        const val ACTION_START_LISTENING = "com.pandora.carlauncher.START_LISTENING"
        const val ACTION_STOP_LISTENING = "com.pandora.carlauncher.STOP_LISTENING"
        const val ACTION_WAKE_WORD_DETECTED = "com.pandora.carlauncher.WAKE_WORD_DETECTED"
        const val ACTION_VOICE_STATE_CHANGED = "com.pandora.carlauncher.VOICE_STATE_CHANGED"
        
        const val EXTRA_STATE = "state"
        
        // 状态常量
        const val STATE_IDLE = 0
        const val STATE_LISTENING = 1
        const val STATE_PROCESSING = 2
        const val STATE_SPEAKING = 3
        
        // 通知相关
        private const val CHANNEL_ID = "voice_assistant_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    // 协程作用域
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // 音频录制
    private var audioRecord: AudioRecord? = null
    private var isRecording = AtomicBoolean(false)
    private val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    
    // 状态
    val isListening = MutableLiveData(false)
    val isWakeWordDetected = MutableLiveData(false)
    
    // Handler
    private val handler = Handler(Looper.getMainLooper())
    
    // 语义理解器
    private lateinit var nluEngine: NLUEngine

    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "语音助手服务启动")
        
        // 创建前台通知
        createNotification()
        
        // 初始化NLU引擎
        initNLUEngine()
        
        // 开始监听
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LISTENING -> startListening()
            ACTION_STOP_LISTENING -> stopListening()
            ACTION_WAKE_WORD_DETECTED -> onWakeWordDetected()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): android.os.IBinder? {
        return VoiceBinder()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        scope.cancel()
    }

    /**
     * 创建前台通知
     */
    private fun createNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("语音助手")
            .setContentText("语音助手运行中...")
            .setSmallIcon(R.drawable.ic_voice_assistant)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        startForeground(NOTIFICATION_ID, notification)
    }

    /**
     * 初始化NLU引擎
     */
    private fun initNLUEngine() {
        nluEngine = NLUEngine(this)
    }

    /**
     * 开始语音监听
     */
    fun startListening() {
        if (isRecording.get()) return
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败")
                return
            }
            
            isRecording.set(true)
            audioRecord?.startRecording()
            isListening.postValue(true)
            
            // 开始音频处理
            processAudio()
            
            Log.i(TAG, "开始语音监听")
        } catch (e: SecurityException) {
            Log.e(TAG, "缺少麦克风权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "语音监听启动失败", e)
        }
    }

    /**
     * 停止语音监听
     */
    fun stopListening() {
        if (!isRecording.get()) return
        
        try {
            isRecording.set(false)
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
            isListening.postValue(false)
            
            Log.i(TAG, "停止语音监听")
        } catch (e: Exception) {
            Log.e(TAG, "停止语音监听失败", e)
        }
    }

    /**
     * 处理音频数据
     */
    private fun processAudio() {
        scope.launch {
            val buffer = ByteArray(bufferSize)
            
            while (isRecording.get()) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (readSize > 0) {
                    // 检测唤醒词
                    if (detectWakeWord(buffer, readSize)) {
                        withContext(Dispatchers.Main) {
                            onWakeWordDetected()
                        }
                    }
                    
                    // TODO: 实时语音识别（需要集成ASR服务）
                }
            }
        }
    }

    /**
     * 检测唤醒词
     * 
     * 这里使用简单的能量检测作为示例
     * 实际项目中应该使用专门的唤醒词检测模型
     */
    private fun detectWakeWord(buffer: ByteArray, size: Int): Boolean {
        // 计算音频能量
        var energy = 0L
        for (i in 0 until size step 2) {
            if (i + 1 < size) {
                val sample = (buffer[i].toInt() and 0xFF) or 
                            ((buffer[i + 1].toInt() shl 8) and 0xFF00)
                energy += sample * sample
            }
        }
        
        val avgEnergy = energy / (size / 2)
        
        // 简单阈值检测（实际应该用模型）
        return avgEnergy > 500000
    }

    /**
     * 唤醒词检测回调
     */
    fun onWakeWordDetected() {
        Log.i(TAG, "唤醒词检测成功")
        isWakeWordDetected.postValue(true)
        
        // 播放提示音
        playWakeSound()
        
        // 显示视觉反馈
        showListeningFeedback()
        
        // 录制语音指令
        recordCommand()
    }

    /**
     * 播放唤醒提示音
     */
    private fun playWakeSound() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(this, notification)
            ringtone?.play()
        } catch (e: Exception) {
            // 忽略
        }
    }

    /**
     * 显示聆听反馈
     */
    private fun showListeningFeedback() {
        // 发送广播通知UI更新
        val intent = Intent(ACTION_VOICE_STATE_CHANGED)
        intent.putExtra(EXTRA_STATE, STATE_LISTENING)
        sendBroadcast(intent)
    }

    /**
     * 录制语音指令
     */
    private fun recordCommand() {
        scope.launch {
            // 录制3秒
            val commandData = recordAudioData(3000)
            
            withContext(Dispatchers.Main) {
                // 发送状态更新
                val intent = Intent(ACTION_VOICE_STATE_CHANGED)
                intent.putExtra(EXTRA_STATE, STATE_PROCESSING)
                sendBroadcast(intent)
                
                // 处理语音指令
                processVoiceCommand(commandData)
            }
        }
    }

    /**
     * 录制音频数据
     */
    private suspend fun recordAudioData(durationMs: Long): ByteArray {
        return withContext(Dispatchers.IO) {
            val buffer = ByteArray(bufferSize * 10)
            var totalRead = 0
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < durationMs && 
                   isRecording.get()) {
                val read = audioRecord?.read(buffer, totalRead, bufferSize) ?: 0
                if (read > 0) {
                    totalRead += read
                }
            }
            
            buffer.copyOf(totalRead)
        }
    }

    /**
     * 处理语音指令
     */
    private fun processVoiceCommand(audioData: ByteArray) {
        scope.launch {
            try {
                // 调用NLU引擎进行语义理解
                val command = nluEngine.process(audioData)
                
                withContext(Dispatchers.Main) {
                    executeCommand(command)
                }
            } catch (e: Exception) {
                Log.e(TAG, "语音指令处理失败", e)
                withContext(Dispatchers.Main) {
                    speak("抱歉，我没有听清楚")
                }
            }
        }
    }

    /**
     * 执行语音指令
     */
    private fun executeCommand(command: VoiceCommand) {
        Log.i(TAG, "执行指令: ${command.type} - ${command.content}")
        
        when (command.type) {
            CMD_NAVIGATE -> {
                speak("正在为您导航到${command.content}")
                // 调用导航功能
            }
            CMD_PLAY_MUSIC -> {
                speak("好的，开始播放音乐")
                // 播放音乐
            }
            CMD_PAUSE_MUSIC -> {
                speak("已暂停播放")
                // 暂停音乐
            }
            CMD_SET_TEMPERATURE -> {
                speak("好的，将温度设置为${command.content}度")
                // 设置温度
            }
            CMD_OPEN_AC -> {
                speak("好的，开启空调")
                // 开启空调
            }
            CMD_CLOSE_AC -> {
                speak("好的，关闭空调")
                // 关闭空调
            }
            CMD_CALL -> {
                speak("好的，正在拨打${command.content}")
                // 拨打电话
            }
            else -> {
                speak("抱歉，我不理解这个指令")
            }
        }
        
        // 隐藏聆听反馈
        hideListeningFeedback()
    }

    /**
     * 隐藏聆听反馈
     */
    private fun hideListeningFeedback() {
        isWakeWordDetected.postValue(false)
        
        val intent = Intent(ACTION_VOICE_STATE_CHANGED)
        intent.putExtra(EXTRA_STATE, STATE_IDLE)
        sendBroadcast(intent)
    }

    // TTS实例
    private var ttsEngine: android.speech.tts.TextToSpeech? = null

    /**
     * 语音播报
     */
    private fun speak(text: String) {
        scope.launch {
            try {
                // 使用TTS进行语音播报
                if (ttsEngine == null) {
                    ttsEngine = android.speech.tts.TextToSpeech(this@VoiceAssistantService) { status ->
                        if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                            ttsEngine?.language = java.util.Locale.CHINESE
                            ttsEngine?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                        }
                    }
                } else {
                    ttsEngine?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_id")
                }
            } catch (e: Exception) {
                Log.e(TAG, "TTS播报失败", e)
            }
        }
    }

    /**
     * 语音命令数据类
     */
    data class VoiceCommand(
        val type: String,
        val content: String,
        val confidence: Float = 0.0f
    )

    /**
     * NLU引擎实现
     */
    class NLUEngine(private val context: android.content.Context) {
        suspend fun process(audioData: ByteArray): VoiceCommand {
            // 这里应该调用实际的语音识别和语义理解服务
            // 暂时返回模拟结果
            return VoiceCommand(
                type = CMD_UNKNOWN,
                content = "",
                confidence = 0.5f
            )
        }
    }

    /**
     * Binder
     */
    inner class VoiceBinder : android.os.Binder() {
        fun getService(): VoiceAssistantService = this@VoiceAssistantService
    }
}
