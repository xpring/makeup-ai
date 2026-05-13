package com.makeupai.app

object Constants {
    // API endpoints
    const val ANTHROPIC_URL = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_MODEL = "claude-sonnet-4-20250514"
    const val ANTHROPIC_VERSION = "2023-06-01"

    // Stability AI img2img endpoint (SDXL)
    const val STABILITY_URL =
        "https://api.stability.ai/v1/generation/stable-diffusion-xl-1024-v1-0/image-to-image"

    // SharedPreferences
    const val PREFS = "makeup_ai_prefs"
    const val PREF_ANTHROPIC_KEY = "anthropic_api_key"
    const val PREF_STABILITY_KEY = "stability_api_key"

    // Intent extras
    const val EXTRA_IMAGE_PATH = "image_path"

    // Generation parameters
    const val IMAGE_STRENGTH = 0.38f   // 62% face preserved
    const val CFG_SCALE = 8
    const val STEPS = 30

    // Makeup styles to generate
    val STYLE_NAMES_CN = listOf("日常清新妆", "欧美烟熏妆", "复古红唇妆", "日系甜美妆")
    val STYLE_NAMES_EN = listOf("Natural Daily", "Smoky Western", "Vintage Red Lip", "J-Pop Sweet")

    // Makeup style accent colors (hex strings for UI)
    val STYLE_COLORS = listOf("#F4A7B9", "#5C4A72", "#C0392B", "#FFB7C5")
}
