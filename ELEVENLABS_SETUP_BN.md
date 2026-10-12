# Mimi-তে ElevenLabs voice চালু করা (বাংলা)

## সেটআপ
1. নিজের ElevenLabs account/API access থেকে একটি API key নিন; এখানে কোনো key source code বা GitHub-এ দেবেন না।
2. Mimi অ্যাপের **Settings → 3D Character & Voice** অংশে key-টি লিখে **Save key securely** চাপুন। Key-টি app-private, backup-বহির্ভূত ফাইলে AES-GCM দিয়ে encrypt করে রাখা হয়; encryption key Android Keystore-এ থাকে।
3. ElevenLabs account-এ থাকা, আপনার ব্যবহারের অনুমতিপ্রাপ্ত এবং Bengali-তে কথা বলতে পারে এমন voice নির্বাচন করে তার voice ID লিখুন। Voice ID টাইপ করার সঙ্গে সঙ্গে device preferences-এ সংরক্ষিত হয়।
4. **Send speech to ElevenLabs** switch চালু করুন। এরপর settings-এর Bengali/English test বোতাম বা স্বাভাবিক Mimi reply দিয়ে পরীক্ষা করুন। Switch বন্ধ থাকলে native Android TTS ব্যবহৃত হবে।

## Voice quality ও আবেগ
- Cloud request-এ `eleven_v4` model ব্যবহার করা হয়; বিদ্যমান emotion label অনুযায়ী happy/sad/angry/shy/thinking audio tag যোগ হয়। Screen-এ দেখানো chat text বদলায় না।
- ভাষা ও কণ্ঠের ফল voice নির্বাচন, voice-এর Bengali দক্ষতা, ElevenLabs account/access, নেটওয়ার্ক এবং provider-side model আচরণের ওপর নির্ভরশীল। তাই কোনো নির্দিষ্ট কণ্ঠ/উচ্চারণ বা human-level আবেগ নিশ্চিত নয়।
- Request ব্যর্থ হলে বা সেটআপ অসম্পূর্ণ হলে Mimi native Android TTS-এ fallback করে। 8,000 অক্ষরের বেশি একক reply-ও cloud-এ না পাঠিয়ে local fallback ব্যবহার করে।

## গোপনীয়তা ও খরচ
- Cloud switch চালু থাকলে TTS-এর জন্য assistant reply text ElevenLabs-এর server-এ পাঠানো হয়। সংবেদনশীল/private text হলে switch বন্ধ রাখুন। Chat-এর মূল text Mimi-তে অপরিবর্তিত থাকে।
- API key disk-এ encrypted এবং Android backup থেকে বাদ; তবে app চলার সময় key process-এ ব্যবহৃত হয়—rooted/compromised device বা runtime instrumentation থেকে তা পুরোপুরি সুরক্ষিত নয়। Publicly distributed app-এর জন্য নিজের backend proxy ব্যবহার করা অধিক নিরাপদ; client app-এ shared team key embed করবেন না।
- ElevenLabs account, API entitlement/usage allowance এবং সম্ভাব্য metered usage আপনার account-এ প্রযোজ্য। নির্দিষ্ট মূল্য/allowance এখানে hard-code করা নেই; account-এর বর্তমান শর্ত যাচাই করুন।

## সীমাবদ্ধতা
- API key ও account-এর voice ID এই প্রকল্পের ব্যবহারকারীকেই দিতে হবে; repository-তে কোনো credential নেই।
- API/network/voice error হলে local TTS-এ নীরব fallback হয়। Android build, বাস্তব device, API key বা voice ID দিয়ে end-to-end test এই source change-এর অংশ নয়।
