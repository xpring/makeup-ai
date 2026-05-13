package com.makeupai.app.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.makeupai.app.Constants
import com.makeupai.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(Constants.PREFS, MODE_PRIVATE)
        restoreKeys()
        setupListeners()
    }

    private fun restoreKeys() {
        binding.etAnthropicKey.setText(prefs.getString(Constants.PREF_ANTHROPIC_KEY, ""))
        binding.etStabilityKey.setText(prefs.getString(Constants.PREF_STABILITY_KEY, ""))
        updateStartButton()
    }

    private fun setupListeners() {
        val watcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { updateStartButton() }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        binding.etAnthropicKey.addTextChangedListener(watcher)
        binding.etStabilityKey.addTextChangedListener(watcher)

        binding.btnStart.setOnClickListener { startCamera() }

        binding.tvStabilityHelp.setOnClickListener {
            Toast.makeText(
                this,
                "访问 platform.stability.ai 注册免费账号获取API Key",
                Toast.LENGTH_LONG
            ).show()
        }

        binding.tvAnthropicHelp.setOnClickListener {
            Toast.makeText(
                this,
                "访问 console.anthropic.com 获取 Anthropic API Key",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateStartButton() {
        val anthropicFilled = binding.etAnthropicKey.text.toString().trim().isNotEmpty()
        val stabilityFilled = binding.etStabilityKey.text.toString().trim().isNotEmpty()
        binding.btnStart.isEnabled = anthropicFilled && stabilityFilled
        binding.btnStart.alpha = if (binding.btnStart.isEnabled) 1f else 0.5f
    }

    private fun startCamera() {
        val anthropicKey = binding.etAnthropicKey.text.toString().trim()
        val stabilityKey = binding.etStabilityKey.text.toString().trim()

        if (anthropicKey.isEmpty() || stabilityKey.isEmpty()) {
            Toast.makeText(this, "请填写两个API Key", Toast.LENGTH_SHORT).show()
            return
        }

        // Save keys
        prefs.edit()
            .putString(Constants.PREF_ANTHROPIC_KEY, anthropicKey)
            .putString(Constants.PREF_STABILITY_KEY, stabilityKey)
            .apply()

        startActivity(Intent(this, CameraActivity::class.java))
    }
}
