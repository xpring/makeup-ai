package com.makeupai.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.makeupai.app.Constants
import com.makeupai.app.databinding.ActivityCameraBinding
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val TAG = "CameraActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (hasCameraPermission()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_CODE
            )
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.viewFinder.surfaceProvider) }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            // Use front camera for selfie
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.d(TAG, "Camera bound successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
                Toast.makeText(this, "相机启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: run {
            Toast.makeText(this, "相机未就绪，请稍候", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnCapture.isEnabled = false
        binding.progressCapture.visibility = View.VISIBLE

        val outputFile = File(cacheDir, "selfie_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo saved to ${outputFile.absolutePath}")

                    // Front camera images are mirrored — flip horizontally
                    val flippedPath = flipAndSave(outputFile)

                    runOnUiThread {
                        binding.progressCapture.visibility = View.GONE
                        binding.btnCapture.isEnabled = true

                        val intent = Intent(this@CameraActivity, ResultActivity::class.java)
                        intent.putExtra(Constants.EXTRA_IMAGE_PATH, flippedPath)
                        startActivity(intent)
                        finish()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    runOnUiThread {
                        binding.progressCapture.visibility = View.GONE
                        binding.btnCapture.isEnabled = true
                        Toast.makeText(this@CameraActivity, "拍照失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    /** Front camera output is horizontally mirrored. Flip it before saving. */
    private fun flipAndSave(file: File): String {
        return try {
            val original = BitmapFactory.decodeFile(file.absolutePath)
            val matrix = Matrix().apply { preScale(-1f, 1f) }
            val flipped = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)

            val outFile = File(cacheDir, "selfie_flipped_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { fos ->
                flipped.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            original.recycle()

            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Flip failed, using original", e)
            file.absolutePath
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                Toast.makeText(this, "需要相机权限才能使用此功能", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
