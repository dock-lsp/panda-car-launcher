package com.pandora.carlauncher

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

/**
 * 悬浮球设置二级页面
 * 包含样式设置（颜色/大小/透明度）和功能选择
 */
class FloatingBallSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_floating_ball_settings)

        findViewById<ImageView>(R.id.btn_back)?.setOnClickListener { finish() }

        val prefs = getSharedPreferences(PREF_FLOAT_BALL, MODE_PRIVATE)

        // 颜色选择
        val rgColor = findViewById<RadioGroup>(R.id.rg_ball_color)
        when (prefs.getString(KEY_FLOAT_BALL_COLOR, "cyan")) {
            "cyan" -> rgColor?.check(R.id.rb_color_cyan)
            "blue" -> rgColor?.check(R.id.rb_color_blue)
            "green" -> rgColor?.check(R.id.rb_color_green)
            "orange" -> rgColor?.check(R.id.rb_color_orange)
        }
        rgColor?.setOnCheckedChangeListener { _, checkedId ->
            val color = when (checkedId) {
                R.id.rb_color_cyan -> "cyan"
                R.id.rb_color_blue -> "blue"
                R.id.rb_color_green -> "green"
                R.id.rb_color_orange -> "orange"
                else -> "cyan"
            }
            prefs.edit().putString(KEY_FLOAT_BALL_COLOR, color).apply()
            updatePreview(prefs)
        }

        // 大小选择
        val rgSize = findViewById<RadioGroup>(R.id.rg_ball_size)
        when (prefs.getString(KEY_FLOAT_BALL_SIZE, "medium")) {
            "small" -> rgSize?.check(R.id.rb_size_small)
            "medium" -> rgSize?.check(R.id.rb_size_medium)
            "large" -> rgSize?.check(R.id.rb_size_large)
        }
        rgSize?.setOnCheckedChangeListener { _, checkedId ->
            val size = when (checkedId) {
                R.id.rb_size_small -> "small"
                R.id.rb_size_medium -> "medium"
                R.id.rb_size_large -> "large"
                else -> "medium"
            }
            prefs.edit().putString(KEY_FLOAT_BALL_SIZE, size).apply()
            updatePreview(prefs)
        }

        // 透明度
        val seekAlpha = findViewById<SeekBar>(R.id.seek_ball_alpha)
        seekAlpha?.progress = prefs.getInt(KEY_FLOAT_BALL_ALPHA, 100)
        seekAlpha?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    prefs.edit().putInt(KEY_FLOAT_BALL_ALPHA, progress).apply()
                    updatePreview(prefs)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 功能选择
        setupCheckBox(prefs, R.id.cb_func_home, KEY_FLOAT_BALL_FUNC_HOME, true)
        setupCheckBox(prefs, R.id.cb_func_back, KEY_FLOAT_BALL_FUNC_BACK, true)
        setupCheckBox(prefs, R.id.cb_func_recent, KEY_FLOAT_BALL_FUNC_RECENT, true)
        setupCheckBox(prefs, R.id.cb_func_music, KEY_FLOAT_BALL_FUNC_MUSIC, true)
        setupCheckBox(prefs, R.id.cb_func_nav, KEY_FLOAT_BALL_FUNC_NAV, true)

        // 初始化预览
        updatePreview(prefs)
    }

    private fun setupCheckBox(prefs: android.content.SharedPreferences, cbId: Int, key: String, default: Boolean) {
        val cb = findViewById<CheckBox>(cbId)
        cb?.isChecked = prefs.getBoolean(key, default)
        cb?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(key, isChecked).apply()
        }
    }

    private fun updatePreview(prefs: android.content.SharedPreferences) {
        val preview = findViewById<View>(R.id.ball_preview) ?: return

        // 更新颜色
        val colorRes = when (prefs.getString(KEY_FLOAT_BALL_COLOR, "cyan")) {
            "blue" -> R.drawable.bg_icon_blue
            "green" -> R.drawable.bg_icon_green
            "orange" -> R.drawable.bg_icon_orange
            else -> R.drawable.bg_floating_ball
        }
        preview.setBackgroundResource(colorRes)

        // 更新大小
        val sizeDp = when (prefs.getString(KEY_FLOAT_BALL_SIZE, "medium")) {
            "small" -> 40
            "large" -> 72
            else -> 56
        }
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
        preview.layoutParams?.let {
            it.width = sizePx
            it.height = sizePx
            preview.layoutParams = it
        }

        // 更新透明度
        val alpha = prefs.getInt(KEY_FLOAT_BALL_ALPHA, 100) / 100f
        preview.alpha = alpha
    }

    companion object {
        const val PREF_FLOAT_BALL = "float_ball_pref"
        const val KEY_FLOAT_BALL_COLOR = "float_ball_color"
        const val KEY_FLOAT_BALL_SIZE = "float_ball_size"
        const val KEY_FLOAT_BALL_ALPHA = "float_ball_alpha"
        const val KEY_FLOAT_BALL_FUNC_HOME = "float_ball_func_home"
        const val KEY_FLOAT_BALL_FUNC_BACK = "float_ball_func_back"
        const val KEY_FLOAT_BALL_FUNC_RECENT = "float_ball_func_recent"
        const val KEY_FLOAT_BALL_FUNC_MUSIC = "float_ball_func_music"
        const val KEY_FLOAT_BALL_FUNC_NAV = "float_ball_func_nav"
    }
}
