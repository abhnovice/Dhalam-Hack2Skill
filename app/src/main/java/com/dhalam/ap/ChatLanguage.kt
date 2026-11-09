package com.dhalam.ap

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Chat languages. [code] is sent to the backend and must match
 * `SUPPORTED_LANGUAGES` in backend/triage/schemas.py.
 */
internal data class ChatLanguage(val code: String, val name: String, val tag: String, val strings: ChatStrings)

internal data class ChatStrings(
    val hint: String, val emptyTitle: String, val emptySubtitle: String,
    val urgent: String, val low: String, val medium: String,
    val checking: String, val check: String, val listening: String,
    val dictate: String, val readAloud: String, val stopReading: String,
    val voiceUnavailable: String, val language: String
)

private val en = ChatStrings(
    "I can detect deepfakes too", "Not sure?\nLet’s check together.", "Share a message or photo.",
    "Stop. This looks risky.", "No clear scam signs.", "Pause. Let’s check first.",
    "I’m checking. Take a breath.", "Check", "Listening…", "Speak your message", "Read aloud", "Stop reading",
    "Voice is not available for this language on this phone.", "Language"
)
private val hi = ChatStrings(
    "संदेश लिखें या पेस्ट करें…", "पक्का नहीं हैं?\nचलिए साथ मिलकर जाँचें।", "बताइए क्या हुआ, या कोई संदेश दिखाइए।",
    "रुकिए। यह जोखिम भरा लगता है।", "ठगी के कोई साफ़ संकेत नहीं।", "ठहरिए। पहले जाँच लें।",
    "मैं जाँच रहा हूँ। गहरी साँस लें।", "जाँचें", "सुन रहा हूँ…", "बोलकर लिखें", "उत्तर सुनें", "पढ़ना बंद करें",
    "इस फ़ोन पर इस भाषा में आवाज़ उपलब्ध नहीं है।", "भाषा"
)
private val mr = ChatStrings(
    "संदेश लिहा किंवा पेस्ट करा…", "खात्री नाही?\nचला, एकत्र तपासूया.", "काय झाले ते सांगा किंवा संदेश दाखवा.",
    "थांबा. हे धोकादायक वाटते.", "फसवणुकीची स्पष्ट चिन्हे नाहीत.", "थोडे थांबा. आधी तपासूया.",
    "मी तपासत आहे. दीर्घ श्वास घ्या.", "तपासा", "ऐकत आहे…", "बोलून लिहा", "उत्तर ऐका", "वाचन थांबवा",
    "या फोनवर या भाषेत आवाज उपलब्ध नाही.", "भाषा"
)
private val bn = ChatStrings(
    "বার্তা লিখুন বা পেস্ট করুন…", "নিশ্চিত নন?\nচলুন একসঙ্গে যাচাই করি।", "কী হয়েছে বলুন, অথবা কোনো বার্তা দেখান।",
    "থামুন। এটি ঝুঁকিপূর্ণ মনে হচ্ছে।", "প্রতারণার স্পষ্ট লক্ষণ নেই।", "একটু থামুন। আগে যাচাই করি।",
    "আমি যাচাই করছি। একটু শ্বাস নিন।", "যাচাই করুন", "শুনছি…", "বলে লিখুন", "উত্তর শুনুন", "পড়া বন্ধ করুন",
    "এই ফোনে এই ভাষায় ভয়েস উপলব্ধ নেই।", "ভাষা"
)
private val ta = ChatStrings(
    "செய்தியை தட்டச்சு செய்யவும் அல்லது ஒட்டவும்…", "உறுதியாகத் தெரியவில்லையா?\nசேர்ந்து சரிபார்ப்போம்.",
    "என்ன நடந்தது என்று சொல்லுங்கள், அல்லது ஒரு செய்தியைக் காட்டுங்கள்.",
    "நில்லுங்கள். இது ஆபத்தாகத் தெரிகிறது.", "மோசடிக்கான தெளிவான அறிகுறிகள் இல்லை.", "சற்று நிறுத்துங்கள். முதலில் சரிபார்ப்போம்.",
    "நான் சரிபார்க்கிறேன். ஆழ்ந்து மூச்சு விடுங்கள்.", "சரிபார்", "கேட்கிறேன்…", "பேசி எழுதவும்", "பதிலைக் கேளுங்கள்", "வாசிப்பதை நிறுத்து",
    "இந்த ஃபோனில் இந்த மொழிக்கு குரல் வசதி இல்லை.", "மொழி"
)
private val te = ChatStrings(
    "సందేశాన్ని టైప్ చేయండి లేదా పేస్ట్ చేయండి…", "ఖచ్చితంగా తెలియదా?\nకలిసి పరిశీలిద్దాం.", "ఏం జరిగిందో చెప్పండి, లేదా ఒక సందేశం చూపండి.",
    "ఆగండి. ఇది ప్రమాదకరంగా కనిపిస్తోంది.", "మోసం యొక్క స్పష్టమైన సంకేతాలు లేవు.", "కొంచెం ఆగండి. ముందు పరిశీలిద్దాం.",
    "నేను పరిశీలిస్తున్నాను. లోతుగా శ్వాస తీసుకోండి.", "పరిశీలించు", "వింటున్నాను…", "మాట్లాడి రాయండి", "సమాధానం వినండి", "చదవడం ఆపండి",
    "ఈ ఫోన్‌లో ఈ భాషకు వాయిస్ అందుబాటులో లేదు.", "భాష"
)

internal val chatLanguages = listOf(
    ChatLanguage("en", "English", "en-IN", en),
    ChatLanguage("hi", "हिन्दी", "hi-IN", hi),
    ChatLanguage("mr", "मराठी", "mr-IN", mr),
    ChatLanguage("bn", "বাংলা", "bn-IN", bn),
    ChatLanguage("ta", "தமிழ்", "ta-IN", ta),
    ChatLanguage("te", "తెలుగు", "te-IN", te)
)

/** The device language when supported, otherwise English. */
internal fun languageForCode(code: String?): ChatLanguage = chatLanguages.firstOrNull { it.code == code } ?: chatLanguages.first()

internal fun triageHeadline(level: String, strings: ChatStrings = en): String = when (level) {
    "HIGH", "CRITICAL" -> strings.urgent
    "LOW" -> strings.low
    else -> strings.medium
}

internal fun spokenText(level: String, summary: String, firstAction: String, strings: ChatStrings): String =
    listOf(triageHeadline(level, strings), summary.split(Regex("(?<=[.!?।])\\s+")).first(), firstAction)
        .filter { it.isNotBlank() }.joinToString(". ")

internal fun spokenAnswer(assessment: org.json.JSONObject, strings: ChatStrings): String = spokenText(
    assessment.optString("threat_level"), assessment.optString("summary"),
    assessment.optJSONArray("recommended_actions")?.optString(0).orEmpty(), strings
)

/** Intent for Android's own speech-to-text screen; launching it throws ActivityNotFoundException when none exists. */
internal fun speechIntent(language: ChatLanguage, prompt: String): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, language.tag)
        putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
    }

/** Reads answers aloud. [speak] returns false when the device has no voice for the language. */
internal class AnswerSpeaker(context: Context, private val onDone: () -> Unit) {
    private var ready = false
    private var tts: TextToSpeech? = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }.also {
        it.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { this@AnswerSpeaker.onDone() }
            @Deprecated("Required by the abstract class") override fun onError(utteranceId: String?) { this@AnswerSpeaker.onDone() }
        })
    }

    fun speak(text: String, language: ChatLanguage): Boolean {
        val engine = tts?.takeIf { ready } ?: return false
        val locale = Locale.forLanguageTag(language.tag)
        val availability = engine.isLanguageAvailable(locale)
        if (availability < TextToSpeech.LANG_AVAILABLE) return false
        engine.language = locale
        engine.setSpeechRate(0.9f)
        return engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "answer") == TextToSpeech.SUCCESS
    }

    fun stop() { tts?.stop() }
    fun shutdown() { tts?.stop(); tts?.shutdown(); tts = null }
}
