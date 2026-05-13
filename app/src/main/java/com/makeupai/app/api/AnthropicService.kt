package com.makeupai.app.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makeupai.app.Constants
import com.makeupai.app.model.ClaudeStyleResponse
import com.makeupai.app.model.MakeupStyle
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class AnthropicService(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Sends the selfie to Claude, gets back 4 makeup style prompts.
     * Returns a list of MakeupStyle objects (without generated images yet).
     */
    suspend fun analyzeFaceAndGetPrompts(bitmap: Bitmap): List<MakeupStyle> {
        val base64Image = bitmapToBase64(bitmap)

        val systemPrompt = """
You are an expert makeup artist and AI image generation prompt engineer.
Analyze the face in the photo and generate exactly 4 makeup look prompts.
Each prompt must be tailored to the person's actual face features (skin tone, eye shape, face structure).
Return ONLY a valid JSON array. No explanation, no markdown, no code blocks — raw JSON only.
        """.trimIndent()

        val userPrompt = """
Analyze this person's face and return a JSON array of exactly 4 makeup styles.

Required styles (in this order):
1. 日常清新妆 (Natural Daily)
2. 欧美烟熏妆 (Smoky Western)
3. 复古红唇妆 (Vintage Red Lip)  
4. 日系甜美妆 (Japanese Sweet)

JSON structure:
[
  {
    "name_cn": "日常清新妆",
    "name_en": "Natural Daily",
    "sd_prompt": "portrait photo, professional makeup, [describe makeup for this style], [person's specific skin tone and features], photorealistic, soft lighting, beauty photography, 8k",
    "characteristics": "底妆: 轻薄透亮 | 眼妆: 自然棕色 | 唇色: 裸粉橘"
  }
]

Rules for sd_prompt:
- Always start with: "portrait photo, professional makeup photography, beautiful woman,"
- Reference the person's skin tone: ${getSkinToneHint()}
- Include specific makeup details (foundation coverage, eye shadow colors/technique, liner, blush, lip color/finish)
- End with: "photorealistic, high quality, soft studio lighting, 8k resolution"
- The prompt must be 60-100 words, vivid and specific

Return ONLY the JSON array. Nothing else.
        """.trimIndent()

        val requestJson = JSONObject().apply {
            put("model", Constants.ANTHROPIC_MODEL)
            put("max_tokens", 2000)
            put("system", systemPrompt)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        put(JSONObject().apply {
                            put("type", "image")
                            put("source", JSONObject().apply {
                                put("type", "base64")
                                put("media_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", userPrompt)
                        })
                    })
                })
            })
        }

        val request = Request.Builder()
            .url(Constants.ANTHROPIC_URL)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", Constants.ANTHROPIC_VERSION)
            .addHeader("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    Log.e("AnthropicService", "API error ${response.code}: $errorBody")
                    throw ApiException("Anthropic API error ${response.code}: ${parseErrorMessage(errorBody)}")
                }

                val responseBody = response.body?.string()
                    ?: throw ApiException("Empty response from Anthropic")

                Log.d("AnthropicService", "Response: $responseBody")
                parseStylesFromResponse(responseBody)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            Log.e("AnthropicService", "Network error", e)
            throw ApiException("Network error: ${e.message}")
        }
    }

    private fun parseStylesFromResponse(responseBody: String): List<MakeupStyle> {
        val responseJson = JSONObject(responseBody)
        val content = responseJson
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
            .trim()

        // Strip any accidental markdown code fences
        val cleanJson = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        Log.d("AnthropicService", "Parsed content: $cleanJson")

        val type = object : TypeToken<List<ClaudeStyleResponse>>() {}.type
        val styleResponses: List<ClaudeStyleResponse> = gson.fromJson(cleanJson, type)

        return styleResponses.mapIndexed { index, r ->
            MakeupStyle(
                nameCn = r.name_cn,
                nameEn = r.name_en,
                sdPrompt = r.sd_prompt,
                characteristics = r.characteristics,
                accentColor = Constants.STYLE_COLORS.getOrElse(index) { "#F4A7B9" }
            )
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        // Resize to max 1024px on longest side to reduce payload
        val resized = resizeBitmap(bitmap, 1024)
        val stream = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun resizeBitmap(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxOf(w, h)
        return Bitmap.createScaledBitmap(bitmap, (w * scale).toInt(), (h * scale).toInt(), true)
    }

    private fun parseErrorMessage(body: String): String {
        return try {
            JSONObject(body).optJSONObject("error")?.optString("message") ?: body
        } catch (e: Exception) {
            body.take(200)
        }
    }

    // Hint for prompt generation — this gets embedded in the user message
    private fun getSkinToneHint(): String = "observed from photo"
}
