package com.makeupai.app.model

import android.graphics.Bitmap

data class MakeupStyle(
    val nameCn: String,
    val nameEn: String,
    val sdPrompt: String,
    val characteristics: String,
    val accentColor: String,
    var resultBitmap: Bitmap? = null,
    var state: State = State.PENDING
) {
    enum class State { PENDING, LOADING, SUCCESS, ERROR }
}

// Raw JSON structure returned by Claude
data class ClaudeStyleResponse(
    val name_cn: String,
    val name_en: String,
    val sd_prompt: String,
    val characteristics: String
)
