/// Languages offered in the chat. [code] is sent to the backend (it must match
/// `SUPPORTED_LANGUAGES` in backend/triage/schemas.py); [locale] drives speech
/// recognition and text-to-speech.
class ChatLanguage {
  final String code;
  final String name;
  final String locale;
  final ChatStrings strings;
  const ChatLanguage(this.code, this.name, this.locale, this.strings);
}

class ChatStrings {
  final String hint;
  final String emptyTitle;
  final String emptySubtitle;
  final String urgentHeadline;
  final String lowHeadline;
  final String mediumHeadline;
  final String checking;
  final String check;
  final String listening;
  final String dictate;
  final String stopDictation;
  final String readAloud;
  final String stopReading;
  final String voiceUnavailable;
  final String language;
  const ChatStrings({
    required this.hint,
    required this.emptyTitle,
    required this.emptySubtitle,
    required this.urgentHeadline,
    required this.lowHeadline,
    required this.mediumHeadline,
    required this.checking,
    required this.check,
    required this.listening,
    required this.dictate,
    required this.stopDictation,
    required this.readAloud,
    required this.stopReading,
    required this.voiceUnavailable,
    required this.language,
  });
}

const _en = ChatStrings(
  hint: 'Type or paste a message…',
  emptyTitle: 'Not sure?\nLet’s check together.',
  emptySubtitle: 'Tell me what happened, or show me a message.',
  urgentHeadline: 'Stop. This looks risky.',
  lowHeadline: 'No clear scam signs.',
  mediumHeadline: 'Pause. Let’s check first.',
  checking: 'I’m checking. Take a breath.',
  check: 'Check',
  listening: 'Listening…',
  dictate: 'Speak your message',
  stopDictation: 'Stop listening',
  readAloud: 'Read aloud',
  stopReading: 'Stop reading',
  voiceUnavailable: 'Voice is not available for this language on this phone.',
  language: 'Language',
);

const _hi = ChatStrings(
  hint: 'संदेश लिखें या पेस्ट करें…',
  emptyTitle: 'पक्का नहीं हैं?\nचलिए साथ मिलकर जाँचें।',
  emptySubtitle: 'बताइए क्या हुआ, या कोई संदेश दिखाइए।',
  urgentHeadline: 'रुकिए। यह जोखिम भरा लगता है।',
  lowHeadline: 'ठगी के कोई साफ़ संकेत नहीं।',
  mediumHeadline: 'ठहरिए। पहले जाँच लें।',
  checking: 'मैं जाँच रहा हूँ। गहरी साँस लें।',
  check: 'जाँचें',
  listening: 'सुन रहा हूँ…',
  dictate: 'बोलकर लिखें',
  stopDictation: 'सुनना बंद करें',
  readAloud: 'उत्तर सुनें',
  stopReading: 'पढ़ना बंद करें',
  voiceUnavailable: 'इस फ़ोन पर इस भाषा में आवाज़ उपलब्ध नहीं है।',
  language: 'भाषा',
);

const _mr = ChatStrings(
  hint: 'संदेश लिहा किंवा पेस्ट करा…',
  emptyTitle: 'खात्री नाही?\nचला, एकत्र तपासूया.',
  emptySubtitle: 'काय झाले ते सांगा किंवा संदेश दाखवा.',
  urgentHeadline: 'थांबा. हे धोकादायक वाटते.',
  lowHeadline: 'फसवणुकीची स्पष्ट चिन्हे नाहीत.',
  mediumHeadline: 'थोडे थांबा. आधी तपासूया.',
  checking: 'मी तपासत आहे. दीर्घ श्वास घ्या.',
  check: 'तपासा',
  listening: 'ऐकत आहे…',
  dictate: 'बोलून लिहा',
  stopDictation: 'ऐकणे थांबवा',
  readAloud: 'उत्तर ऐका',
  stopReading: 'वाचन थांबवा',
  voiceUnavailable: 'या फोनवर या भाषेत आवाज उपलब्ध नाही.',
  language: 'भाषा',
);

const _bn = ChatStrings(
  hint: 'বার্তা লিখুন বা পেস্ট করুন…',
  emptyTitle: 'নিশ্চিত নন?\nচলুন একসঙ্গে যাচাই করি।',
  emptySubtitle: 'কী হয়েছে বলুন, অথবা কোনো বার্তা দেখান।',
  urgentHeadline: 'থামুন। এটি ঝুঁকিপূর্ণ মনে হচ্ছে।',
  lowHeadline: 'প্রতারণার স্পষ্ট লক্ষণ নেই।',
  mediumHeadline: 'একটু থামুন। আগে যাচাই করি।',
  checking: 'আমি যাচাই করছি। একটু শ্বাস নিন।',
  check: 'যাচাই করুন',
  listening: 'শুনছি…',
  dictate: 'বলে লিখুন',
  stopDictation: 'শোনা বন্ধ করুন',
  readAloud: 'উত্তর শুনুন',
  stopReading: 'পড়া বন্ধ করুন',
  voiceUnavailable: 'এই ফোনে এই ভাষায় ভয়েস উপলব্ধ নেই।',
  language: 'ভাষা',
);

const _ta = ChatStrings(
  hint: 'செய்தியை தட்டச்சு செய்யவும் அல்லது ஒட்டவும்…',
  emptyTitle: 'உறுதியாகத் தெரியவில்லையா?\nசேர்ந்து சரிபார்ப்போம்.',
  emptySubtitle:
      'என்ன நடந்தது என்று சொல்லுங்கள், அல்லது ஒரு செய்தியைக் காட்டுங்கள்.',
  urgentHeadline: 'நில்லுங்கள். இது ஆபத்தாகத் தெரிகிறது.',
  lowHeadline: 'மோசடிக்கான தெளிவான அறிகுறிகள் இல்லை.',
  mediumHeadline: 'சற்று நிறுத்துங்கள். முதலில் சரிபார்ப்போம்.',
  checking: 'நான் சரிபார்க்கிறேன். ஆழ்ந்து மூச்சு விடுங்கள்.',
  check: 'சரிபார்',
  listening: 'கேட்கிறேன்…',
  dictate: 'பேசி எழுதவும்',
  stopDictation: 'கேட்பதை நிறுத்து',
  readAloud: 'பதிலைக் கேளுங்கள்',
  stopReading: 'வாசிப்பதை நிறுத்து',
  voiceUnavailable: 'இந்த ஃபோனில் இந்த மொழிக்கு குரல் வசதி இல்லை.',
  language: 'மொழி',
);

const _te = ChatStrings(
  hint: 'సందేశాన్ని టైప్ చేయండి లేదా పేస్ట్ చేయండి…',
  emptyTitle: 'ఖచ్చితంగా తెలియదా?\nకలిసి పరిశీలిద్దాం.',
  emptySubtitle: 'ఏం జరిగిందో చెప్పండి, లేదా ఒక సందేశం చూపండి.',
  urgentHeadline: 'ఆగండి. ఇది ప్రమాదకరంగా కనిపిస్తోంది.',
  lowHeadline: 'మోసం యొక్క స్పష్టమైన సంకేతాలు లేవు.',
  mediumHeadline: 'కొంచెం ఆగండి. ముందు పరిశీలిద్దాం.',
  checking: 'నేను పరిశీలిస్తున్నాను. లోతుగా శ్వాస తీసుకోండి.',
  check: 'పరిశీలించు',
  listening: 'వింటున్నాను…',
  dictate: 'మాట్లాడి రాయండి',
  stopDictation: 'వినడం ఆపండి',
  readAloud: 'సమాధానం వినండి',
  stopReading: 'చదవడం ఆపండి',
  voiceUnavailable: 'ఈ ఫోన్‌లో ఈ భాషకు వాయిస్ అందుబాటులో లేదు.',
  language: 'భాష',
);

const chatLanguages = <ChatLanguage>[
  ChatLanguage('en', 'English', 'en-IN', _en),
  ChatLanguage('hi', 'हिन्दी', 'hi-IN', _hi),
  ChatLanguage('mr', 'मराठी', 'mr-IN', _mr),
  ChatLanguage('bn', 'বাংলা', 'bn-IN', _bn),
  ChatLanguage('ta', 'தமிழ்', 'ta-IN', _ta),
  ChatLanguage('te', 'తెలుగు', 'te-IN', _te),
];

/// The device language when supported, otherwise English.
ChatLanguage languageForCode(String? code) => chatLanguages.firstWhere(
  (language) => language.code == code,
  orElse: () => chatLanguages.first,
);
