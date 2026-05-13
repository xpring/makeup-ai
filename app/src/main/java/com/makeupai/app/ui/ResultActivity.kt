package com.makeupai.app.ui

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.makeupai.app.Constants
import com.makeupai.app.adapter.StyleGridAdapter
import com.makeupai.app.api.AnthropicService
import com.makeupai.app.api.ApiException
import com.makeupai.app.api.StabilityService
import com.makeupai.app.databinding.ActivityResultBinding
import com.makeupai.app.model.MakeupStyle
import kotlinx.coroutines.*

class ResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResultBinding
    private lateinit var adapter: StyleGridAdapter
    private lateinit var selfie: Bitmap

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private val styles = mutableListOf<MakeupStyle>()

    companion object {
        private const val TAG = "ResultActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load selfie
        val path = intent.getStringExtra(Constants.EXTRA_IMAGE_PATH)
        if (path == null) {
            Toast.makeText(this, "图片路径丢失", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        selfie = BitmapFactory.decodeFile(path)
            ?: run {
                Toast.makeText(this, "无法读取照片", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

        binding.ivSelfie.setImageBitmap(selfie)

        setupRecyclerView()
        setupButtons()
        startGeneration()
    }

    private fun setupRecyclerView() {
        adapter = StyleGridAdapter(styles) { style, _ ->
            if (style.state == MakeupStyle.State.SUCCESS) {
                showFullscreen(style)
            }
        }
        binding.rvStyles.layoutManager = GridLayoutManager(this, 2)
        binding.rvStyles.adapter = adapter
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { startGeneration() }
        binding.btnSaveAll.setOnClickListener { saveSuccessfulImages() }
    }

    private fun startGeneration() {
        styles.clear()
        adapter.notifyDataSetChanged()

        binding.btnRetry.visibility = View.GONE
        binding.btnSaveAll.visibility = View.GONE
        binding.tvStatus.visibility = View.VISIBLE
        binding.tvStatus.text = "正在分析面部特征..."
        binding.progressMain.visibility = View.VISIBLE

        val prefs = getSharedPreferences(Constants.PREFS, MODE_PRIVATE)
        val anthropicKey = prefs.getString(Constants.PREF_ANTHROPIC_KEY, "") ?: ""
        val stabilityKey = prefs.getString(Constants.PREF_STABILITY_KEY, "") ?: ""

        scope.launch {
            try {
                // Step 1: Claude analyzes face and returns 4 style prompts
                val generatedStyles = withContext(Dispatchers.IO) {
                    AnthropicService(anthropicKey).analyzeFaceAndGetPrompts(selfie)
                }

                Log.d(TAG, "Got ${generatedStyles.size} styles from Claude")

                // Add placeholder styles to UI immediately
                styles.addAll(generatedStyles)
                styles.forEach { it.state = MakeupStyle.State.LOADING }
                adapter.notifyDataSetChanged()

                binding.tvStatus.text = "AI正在生成妆效图，请稍候..."
                binding.progressMain.visibility = View.GONE

                // Step 2: Generate images in parallel (up to 2 concurrent requests)
                val stability = StabilityService(stabilityKey)
                val semaphore = kotlinx.coroutines.sync.Semaphore(2) // max 2 concurrent

                val jobs = styles.mapIndexed { index, style ->
                    launch {
                        semaphore.withPermit {
                            try {
                                val bitmap = withContext(Dispatchers.IO) {
                                    stability.generateMakeupImage(selfie, style)
                                }
                                style.resultBitmap = bitmap
                                style.state = MakeupStyle.State.SUCCESS
                                Log.d(TAG, "Style ${style.nameCn} generated successfully")
                            } catch (e: ApiException) {
                                Log.e(TAG, "Failed to generate ${style.nameCn}: ${e.message}")
                                style.state = MakeupStyle.State.ERROR
                            } catch (e: Exception) {
                                Log.e(TAG, "Unexpected error for ${style.nameCn}", e)
                                style.state = MakeupStyle.State.ERROR
                            } finally {
                                adapter.updateStyle(index)
                                updateStatusText()
                            }
                        }
                    }
                }
                jobs.joinAll()

                // All done
                val successCount = styles.count { it.state == MakeupStyle.State.SUCCESS }
                binding.tvStatus.visibility = View.GONE
                if (successCount > 0) {
                    binding.btnSaveAll.visibility = View.VISIBLE
                    Toast.makeText(
                        this@ResultActivity,
                        "✨ 生成完成！$successCount 种妆效已就绪",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    binding.tvStatus.visibility = View.VISIBLE
                    binding.tvStatus.text = "生成失败，请检查API Key后重试"
                    binding.btnRetry.visibility = View.VISIBLE
                }

            } catch (e: ApiException) {
                Log.e(TAG, "Anthropic error", e)
                withContext(Dispatchers.Main) {
                    binding.progressMain.visibility = View.GONE
                    binding.tvStatus.text = "面部分析失败: ${e.message}"
                    binding.btnRetry.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                withContext(Dispatchers.Main) {
                    binding.progressMain.visibility = View.GONE
                    binding.tvStatus.text = "发生错误: ${e.message}"
                    binding.btnRetry.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateStatusText() {
        val done = styles.count { it.state != MakeupStyle.State.LOADING && it.state != MakeupStyle.State.PENDING }
        val total = styles.size
        binding.tvStatus.text = "正在生成妆效图 ($done/$total)..."
    }

    private fun showFullscreen(style: MakeupStyle) {
        val bitmap = style.resultBitmap ?: return
        val intent = Intent(this, FullscreenActivity::class.java)

        // Save to temp file and pass path
        val tmpFile = java.io.File(cacheDir, "fullscreen_${style.nameEn.replace(" ", "_")}.jpg")
        java.io.FileOutputStream(tmpFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }

        intent.putExtra("image_path", tmpFile.absolutePath)
        intent.putExtra("title", style.nameCn)
        intent.putExtra("subtitle", style.characteristics)
        startActivity(intent)
    }

    private fun saveSuccessfulImages() {
        var savedCount = 0
        styles.filter { it.state == MakeupStyle.State.SUCCESS }.forEach { style ->
            val bitmap = style.resultBitmap ?: return@forEach
            if (saveBitmapToGallery(bitmap, "MakeupAI_${style.nameEn.replace(" ", "_")}_${System.currentTimeMillis()}")) {
                savedCount++
            }
        }
        Toast.makeText(this, "已保存 $savedCount 张图片到相册", Toast.LENGTH_SHORT).show()
    }

    private fun saveBitmapToGallery(bitmap: Bitmap, filename: String): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MakeupAI")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val resolver = contentResolver
            val uri: Uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false

            resolver.openOutputStream(uri)?.use { os ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
