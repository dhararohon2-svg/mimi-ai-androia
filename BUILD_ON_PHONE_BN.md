# ফোন থেকে GitHub Actions-এ build/test

## সোর্স আপলোড
1. `mimi-assistant-github-ready.zip` ফোনে ডাউনলোড করে extract করুন। ZIP ফাইলটিকে repository-তে রাখলে GitHub সেটি নিজে খুলে build করবে না।
2. GitHub-এ একটি repository তৈরি করুন। প্রকল্পের character/model asset-গুলোর license/redistribution অধিকার নিশ্চিত না হলে repository private রাখুন।
3. Extract করা project-এর ফাইল/ফোল্ডারগুলো repository root-এ commit/push করুন। root-এ `settings.gradle.kts`, `gradlew`, `app/`, `.github/workflows/android-build.yml` থাকবে। ফোনের browser nested folder upload করতে না পারলে আপনার পছন্দের mobile Git client ব্যবহার করুন।

## Workflow চালানো
- `main` বা `master` branch-এ push করলে build workflow নিজে থেকে শুরু হবে।
- Manual run করতে repository-এর **Actions** থেকে **Android Build** নির্বাচন করে **Run workflow** চাপুন।
- আগে unit tests এবং API 30/36 emulator tests চলবে। সব সফল হলেই `build-debug-apk` job চলবে।
- Run সফল হলে **Artifacts** থেকে `mimi-ai-debug-apk` download করে extract করুন; ভেতরের `.apk` ফোনে install করুন।

## খেয়াল রাখুন
- এটি debug APK। অন্য keystore-এ signed পুরোনো APK-এর ওপর update নাও হতে পারে; দরকার হলে পুরোনো app uninstall করে নতুনটি install করুন—এতে app data মুছে যেতে পারে। Release/update signing-এর জন্য নিজের keystore GitHub Secrets-এ configure করুন; ZIP/repository-তে keystore বা API key রাখবেন না।
- Gemini API key build-এর জন্য দরকার নেই; app settings-এ দিন। `.env`, `local.properties`, এবং সাধারণ signing-key ফাইল `.gitignore`-এ বাদ আছে।
- এই sandbox-এ Android SDK/Java/Gradle ছিল না, তাই এখানে APK build বা emulator test চালানো হয়নি। GitHub job ব্যর্থ হলে failed job-এর সম্পূর্ণ log সংরক্ষণ করুন।
