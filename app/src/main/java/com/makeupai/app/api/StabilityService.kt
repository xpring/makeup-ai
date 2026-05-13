package com.makeupai.app.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.makeupai.app.Constants
import com.makeupai.app.model.MakeupStyle
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class StabilityService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS) // Image generation can be slow
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * Generates a makeup-applied image using Stability AI img2img.
     * The original selfie is used as the init_image; the sd_prompt guides makeup style.
     * Returns a Bitmap of the generated image.
     */
    suspend fun generateMakeupImage(bitmap: Bitmap, style: MakeupStyle): Bitmap {
        val imageBytes = bitmapToJpegBytes(bitmap)

        // Negative prompt suppresses common AI artifacts
        val negativePrompt = "deformed, disfigured, bad anatomy, extra limbs, mutated, " +
                "ugly, blurry, low quality, watermark, text, logo, cartoon, anime, " +
                "painting, drawing, illustration, oversaturated"

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "init_image",
                "selfie.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .addFormDataPart("text_prompts[0][text]", style.sdPrompt)
            .addFormDataPart("text_prompts[0][weight]", "1.0")
            .addFormDataPart("text_prompts[1][text]", negativePrompt)
            .addFormDataPart("text_prompts[1][weight]", "-1.0")
            .addFormDataPart("cfg_scale", Constants.CFG_SCALE.toString())
            .addFormDataPart("image_strength", Constants.IMAGE_STRENGTH.toString())
            .addFormDataPart("steps", Constants.STEPS.toString())
            .addFormDataPart("samples", "1")
            .addFormDataPart("style_preset", "photographic")
            .build()

        val request = Request.Builder()
            .url(Constants.STABILITY_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .post(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("StabilityService", "API error ${response.code}: $errorBody")
                    throw ApiException("Stability API error ${response.code}: ${parseErrorMessage(errorBody)}")
                }

                val responseBody = response.body?.string()
                    ?: throw ApiException("Empty response from Stability AI")

                Log.d("StabilityService", "Success for style: ${style.nameCn}")
                parseBitmapFromResponse(responseBody)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            Log.e("StabilityService", "Error generating image for ${style.nameCn}", e)
            throw ApiException("Generation failed for ${style.nameCn}: ${e.message}")
        }
    }

    private fun parseBitmapFromResponse(responseBody: String): Bitmap {
        val json = JSONObject(responseBody)
        val artifacts = json.getJSONArray("artifacts")
        if (artifacts.length() == 0) throw ApiException("No images returned")

        val artifact = artifacts.getJSONObject(0)
        val finishReason = artifact.optString("finishReason")
        if (finishReason == "ERROR") throw ApiException("Image generation failed (ERROR finish reason)")
        if (finishReason == "CONTENT_FILTERED") throw ApiException("Content filtered by safety system")

        val base64Image = artifact.getString("base64")
        val imageBytes = Base64.decode(base64Image, Base64.DEFAULT)

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw ApiException("Failed to decode generated image")
    }

    private fun bitmapToJpegBytes(bitmap: Bitmap): ByteArray {
        // Stability AI SDXL works best with 1024x1024
        val size = 1024
        val resized = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return stream.toByteArray()
    }

    private fun parseErrorMessage(body: String): String {
        return try {
            val json = JSONObject(body)
            json.optString("message")
                ?: json.optJSONArray("errors")?.getJSONObject(0)?.optString("message")
                ?: body.take(200)
        } catch (e: Exception) {
            body.take(200)
        }
    }
}
