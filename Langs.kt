package app.capri.mobile

import java.util.Locale

// Daftar bahasa Capri, sama dengan versi PC. Dipakai untuk balasan, suara HP, dan pengenal suara.
object Langs {
    class L(val native: String, val english: String, val tag: String)

    val all: List<L> = listOf(
        L("Bahasa Indonesia", "Indonesian", "id"),
        L("English", "English", "en"),
        L("Español", "Spanish", "es"),
        L("Français", "French", "fr"),
        L("Deutsch", "German", "de"),
        L("Italiano", "Italian", "it"),
        L("Português", "Portuguese", "pt"),
        L("Català", "Catalan", "ca"),
        L("Galego", "Galician", "gl"),
        L("Euskara", "Basque", "eu"),
        L("Nederlands", "Dutch", "nl"),
        L("Afrikaans", "Afrikaans", "af"),
        L("Svenska", "Swedish", "sv"),
        L("Dansk", "Danish", "da"),
        L("Norsk", "Norwegian", "no"),
        L("Íslenska", "Icelandic", "is"),
        L("Suomi", "Finnish", "fi"),
        L("Eesti", "Estonian", "et"),
        L("Latviešu", "Latvian", "lv"),
        L("Lietuvių", "Lithuanian", "lt"),
        L("Polski", "Polish", "pl"),
        L("Čeština", "Czech", "cs"),
        L("Slovenčina", "Slovak", "sk"),
        L("Magyar", "Hungarian", "hu"),
        L("Slovenščina", "Slovenian", "sl"),
        L("Hrvatski", "Croatian", "hr"),
        L("Bosanski", "Bosnian", "bs"),
        L("Srpski", "Serbian", "sr"),
        L("Shqip", "Albanian", "sq"),
        L("Română", "Romanian", "ro"),
        L("Gaeilge", "Irish", "ga"),
        L("Cymraeg", "Welsh", "cy"),
        L("Malti", "Maltese", "mt"),
        L("Türkçe", "Turkish", "tr"),
        L("Azərbaycanca", "Azerbaijani", "az"),
        L("ქართული", "Georgian", "ka"),
        L("Հայերեն", "Armenian", "hy"),
        L("Oʻzbekcha", "Uzbek", "uz"),
        L("Русский", "Russian", "ru"),
        L("Українська", "Ukrainian", "uk"),
        L("Беларуская", "Belarusian", "be"),
        L("Български", "Bulgarian", "bg"),
        L("Македонски", "Macedonian", "mk"),
        L("Қазақша", "Kazakh", "kk"),
        L("Монгол", "Mongolian", "mn"),
        L("Ελληνικά", "Greek", "el"),
        L("Tiếng Việt", "Vietnamese", "vi"),
        L("Bahasa Melayu", "Malay", "ms"),
        L("Basa Jawa", "Javanese", "jv"),
        L("Filipino", "Filipino", "fil"),
        L("Kiswahili", "Swahili", "sw"),
        L("Hausa", "Hausa", "ha"),
        L("Yorùbá", "Yoruba", "yo"),
        L("Igbo", "Igbo", "ig"),
        L("isiZulu", "Zulu", "zu"),
        L("isiXhosa", "Xhosa", "xh"),
        L("Soomaali", "Somali", "so"),
        L("አማርኛ", "Amharic", "am"),
        L("ไทย", "Thai", "th"),
        L("ລາວ", "Lao", "lo"),
        L("ខ្មែរ", "Khmer", "km"),
        L("မြန်မာ", "Burmese", "my"),
        L("简体中文", "Simplified Chinese", "zh-CN"),
        L("繁體中文", "Traditional Chinese", "zh-TW"),
        L("日本語", "Japanese", "ja"),
        L("한국어", "Korean", "ko"),
        L("हिन्दी", "Hindi", "hi"),
        L("नेपाली", "Nepali", "ne"),
        L("বাংলা", "Bengali", "bn"),
        L("मराठी", "Marathi", "mr"),
        L("ગુજરાતી", "Gujarati", "gu"),
        L("ਪੰਜਾਬੀ", "Punjabi", "pa"),
        L("தமிழ்", "Tamil", "ta"),
        L("తెలుగు", "Telugu", "te"),
        L("ಕನ್ನಡ", "Kannada", "kn"),
        L("മലയാളം", "Malayalam", "ml"),
        L("العربية", "Arabic", "ar"),
        L("اردو", "Urdu", "ur"),
        L("فارسی", "Persian", "fa"),
        L("עברית", "Hebrew", "he")
    )

    fun find(english: String): L? = all.firstOrNull { it.english == english }

    // Nama bahasa untuk perintah ke otak. null = ikut bahasa yang dipakai pengguna.
    fun promptName(value: String): String? = when (value) {
        "auto" -> null
        "id" -> "Indonesian"
        "en" -> "English"
        else -> find(value)?.english ?: value
    }

    fun locale(value: String): Locale = when (value) {
        "auto" -> Locale.getDefault()
        "id" -> Locale.forLanguageTag("id")
        "en" -> Locale.forLanguageTag("en")
        else -> Locale.forLanguageTag(find(value)?.tag ?: "en")
    }
}
