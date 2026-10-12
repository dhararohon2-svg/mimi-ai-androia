# মেরামত নোট

- Gemini response-এর candidates/parts/inner JSON যাচাই করে safe fallback এবং regression unit tests যোগ হয়েছে।
- সরবরাহ করা v2 patch-এর multilingual language detection/BCP-47 forwarding, TTS prosody ও `thinking` mood/animation রাখা হয়েছে; TTS initialization queue locale/emotion/callback-সহ drain হয়।
- Gradle 9.3.1 wrapper scripts/JAR যোগ হয়েছে। GitHub Actions-এ unit tests ও API 30/36 instrumentation tests সফল না হলে debug APK job চলবে না।
- Application ID, offline command/routine path, permissions, backup rules, চরিত্রের GLB asset, breathing/blinking/gaze/tap animation অপরিবর্তিত। Static scan-এ 48dp-এর কম স্পষ্ট clickable target পাওয়া যায়নি।
- উচ্চ resolution-এর original model texture না থাকায় texture upscale/fake করা হয়নি; dark UI-ও অপরিবর্তিত।
- কোনো runtime/crash log না থাকায় নির্দিষ্ট crash-এর root cause দাবি করছি না। Android build ও emulator tests GitHub Actions-এ চালাতে হবে।
