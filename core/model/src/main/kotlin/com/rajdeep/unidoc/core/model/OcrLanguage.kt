package com.rajdeep.unidoc.core.model

/** Where a language's OCR runs. */
enum class OcrEngine {
    /** ML Kit Text Recognition v2, bundled. */
    ON_DEVICE,

    /** Tesseract via Unidoc Cloud. */
    CLOUD
}

/** OCR languages commonly used in India. [tag] is BCP-47; [nativeName] in its own script. */
enum class OcrLanguage(val tag: String, val englishName: String, val nativeName: String, val engine: OcrEngine) {
    ENGLISH("en", "English", "English", OcrEngine.ON_DEVICE),
    HINDI("hi", "Hindi", "हिन्दी", OcrEngine.ON_DEVICE),
    MARATHI("mr", "Marathi", "मराठी", OcrEngine.ON_DEVICE),
    NEPALI("ne", "Nepali", "नेपाली", OcrEngine.ON_DEVICE),
    SANSKRIT("sa", "Sanskrit", "संस्कृतम्", OcrEngine.ON_DEVICE),
    BENGALI("bn", "Bengali", "বাংলা", OcrEngine.CLOUD),
    ASSAMESE("as", "Assamese", "অসমীয়া", OcrEngine.CLOUD),
    TAMIL("ta", "Tamil", "தமிழ்", OcrEngine.CLOUD),
    TELUGU("te", "Telugu", "తెలుగు", OcrEngine.CLOUD),
    KANNADA("kn", "Kannada", "ಕನ್ನಡ", OcrEngine.CLOUD),
    MALAYALAM("ml", "Malayalam", "മലയാളം", OcrEngine.CLOUD),
    GUJARATI("gu", "Gujarati", "ગુજરાતી", OcrEngine.CLOUD),
    PUNJABI("pa", "Punjabi", "ਪੰਜਾਬੀ", OcrEngine.CLOUD),
    ODIA("or", "Odia", "ଓଡ଼ିଆ", OcrEngine.CLOUD),
    URDU("ur", "Urdu", "اردو", OcrEngine.CLOUD)
}
