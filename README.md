<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/0c5b097e-1784-45d3-a6b9-7803917329c0

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Remove this line from the app's `build.gradle.kts` file: `signingConfig = signingConfigs.getByName("debugConfig")`
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

## Mobile-browser build (GitHub Actions)

This source archive does not include the Gradle wrapper scripts/JAR. The included workflow installs Gradle 9.3.1 in GitHub Actions, builds a debug APK, runs unit tests, and uploads the APK as a workflow artifact. Push the project to a GitHub repository and run **Actions → Android Build → Run workflow**.

The provided `gz.glb` model is packaged as `app/src/main/assets/models/anime_mimi.glb`. It contains a static mesh and PBR textures, but no skin or animation clips; facial/lip-sync animation therefore requires a rigged model or additional animation assets. The APK is not signed with the original release key unless you configure that key securely in the repository environment.
