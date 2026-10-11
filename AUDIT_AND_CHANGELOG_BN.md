# Mimi AI — অডিট ও পরিবর্তনের রিপোর্ট

## প্রয়োগ করা পরিবর্তন
- ব্যবহারকারীর দেওয়া `gz.glb` ডিফল্ট `anime_mimi.glb` asset হিসেবে প্যাকেজ করা হয়েছে; application ID অপরিবর্তিত।
- 2D canvas-ভিত্তিক avatar renderer-এর পরিবর্তে SceneView/Filament-ভিত্তিক GLB renderer যুক্ত করা হয়েছে।
- নতুন ইনস্টলের জন্য TTS pitch ও speech rate আরও স্বাভাবিক শুরুর মানে আনা হয়েছে (pitch 1.05, rate 0.94)। পূর্বে সংরক্ষিত ব্যবহারকারীর সেটিংস অক্ষুণ্ণ থাকে।
- TTS voice বাছাইয়ে কেবল voice-এর নামের মধ্যে “female” লেখা আছে কি না তার ওপর নির্ভর না করে একই ভাষার offline voice-গুলোর quality/latency অগ্রাধিকার দেওয়া হয়েছে।
- TTS shutdown-এ pending speech ও callback পরিষ্কার করা হয়।
- SpeechRecognizer error-এর জন্য ব্যবহারকারী-পাঠযোগ্য feedback এবং retry-র আগে recognizer cancel করা হয়েছে।
- Manifest-এ cleartext HTTP স্পষ্টভাবে নিষ্ক্রিয় করা হয়েছে। Backup আচরণ অপরিবর্তিত রাখা হয়েছে।
- GitHub Actions workflow যোগ হয়েছে: debug APK build, unit test, APK artifact upload।

## নতুন মডেল যাচাই
- GLB v2, মোট আকার প্রায় 45.1 MB।
- 1 scene, 1 node, 1 mesh, 1 material এবং 3 texture image পাওয়া গেছে।
- Skin/skeleton ও animation clip নেই। ফলে মডেলটি static 3D হিসেবে render হতে পারে; প্রকৃত facial blend-shape, rigged body animation এবং phoneme-ভিত্তিক lip-sync এই ফাইলে নেই।
- `KHR_materials_specular` extension ব্যবহৃত হয়েছে; ডিভাইসে rendering/texture compatibility বাস্তব build ও run test-এ যাচাই করতে হবে।

## এখনো যাচাই করা হয়নি
- এই পরিবেশে Gradle CLI ও Android SDK/build toolchain নেই এবং archive-এ Gradle wrapper script/JAR নেই; তাই compile, unit test, APK signing বা device run এখানে সম্পন্ন হয়নি।
- SceneView dependency/API ও Android Gradle plugin compatibility GitHub Actions-এর প্রথম build-এ নিশ্চিত করতে হবে।
- মূল release keystore পাওয়া যায়নি/নিশ্চিত নয়। GitHub workflow debug APK তৈরি করে; এটি release key দিয়ে signed নয় এবং সাধারণত আগের release APK-র ওপর update হিসেবে ইনস্টল হবে না।
- Neural/cloud TTS service যোগ করা হয়নি; বর্তমান পরিবর্তন Android-এর ইনস্টল করা TTS engine-কে আরও ভালোভাবে বেছে নেয়। কণ্ঠের প্রকৃত মান ডিভাইসের engine ও ভাষার voice data-এর ওপর নির্ভরশীল।

## নিরাপত্তা নোট
- `.env.example`-এ placeholder API key আছে, বাস্তব secret নয়। কোনো private key/secret উদ্ধার বা প্রকাশ করা হয়নি।
- Accessibility, notification, contact, call ও SMS-সম্পর্কিত বিদ্যমান ফিচার/permission ইচ্ছাকৃতভাবে সরানো হয়নি। এগুলো আলাদা ডিভাইস-ভিত্তিক permission ও security test চায়।
