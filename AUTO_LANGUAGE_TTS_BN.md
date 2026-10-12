# স্বয়ংক্রিয় ভাষা, mood ও TTS — সেটআপ

- Home screen-এ আর বাংলা/English নির্বাচন নেই। Android 14+ (API 34)-এ recognition service language detect/switch করতে অনুরোধ পায়; ফল device/service/model সমর্থনের ওপর নির্ভরশীল। Android 13 বা পুরোনো সংস্করণে device locale শুধু soft hint, তাই ওই সংস্করণে mixed-language recognition নিশ্চিত নয়।
- Gemini-কে বাংলা-English, Hindi-English, transliteration ও ভাষা বদল mirror করতে বলা হয়েছে। Gemini-এর `speechLocale` প্রধান TTS locale; Unicode script detection fallback।
- Mood inference কথার text/context থেকে সতর্কভাবে হয়, raw audio prosody classifier নয়। ElevenLabs expressive handling থাকে; Chirp 3 HD-তে সামান্য speaking-rate cue আছে, পূর্ণ emotional acting guarantee নয়।
- Order: enabled/configured ElevenLabs → enabled/configured Google Chirp 3 HD; synthesis/playback failure হলে পরের provider; সব cloud বন্ধ/ব্যর্থ হলে Android local TTS।
- Google Cloud project-এ Cloud Text-to-Speech API enable করুন, billing/quota যাচাই করুন, restricted API key তৈরি করে Settings-এ সংরক্ষণ ও switch চালু করুন। API key source code-এ hard-code করবেন না। Keystore encryption at-rest সুরক্ষা দেয়, কিন্তু compromised/rooted device-এ mobile API key বেরিয়ে যেতে পারে; public release-এর জন্য backend proxy বেশি নিরাপদ। Cloud switch বন্ধ থাকলে ঐ provider-এ reply পাঠানো হয় না।
- পরীক্ষা/build করতে project root-এ `./gradlew testDebugUnitTest` এবং `./gradlew assembleDebug` চালান। Provider routing ও language-script tests যোগ করা হয়েছে; actual device recognition, credentials, billing, ও playback device-এ যাচাই করতে হবে।
