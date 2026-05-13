package com.makeupai.app.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.makeupai.app.databinding.ActivityFullscreenBinding

class FullscreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullscreenBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullscreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val path = intent.getStringExtra("image_path")
        val title = intent.getStringExtra("title") ?: ""
        val subtitle = intent.getStringExtra("subtitle") ?: ""

        if (path != null) {
            val bmp = BitmapFactory.decodeFile(path)
            binding.ivFullscreen.setImageBitmap(bmp)
        }

        binding.tvTitle.text = title
        binding.tvSubtitle.text = subtitle
        binding.btnClose.setOnClickListener { finish() }
    }
}
