# Gemma AI Avatar — Android source

Android Kotlin project with an illustrated avatar, chat interface, microphone input, Android text-to-speech, and an on-device Gemma integration using LiteRT-LM.

## Requirements
- JDK 17
- Android SDK platform 36 and matching build tools
- Gradle 8.13+
- Android 8.0 (API 26) or newer
- A compatible Gemma 3 1B LiteRT-LM `.litertlm` model

## Build in Termux

Install/configure JDK 17, Android SDK platform 36 and Gradle. Set `ANDROID_HOME` to your SDK directory and ensure SDK tools are available.

```bash
git clone -b android-source https://github.com/sajjadbodenjnt/Gemma-AI-Avatar.git
cd Gemma-AI-Avatar
gradle wrapper --gradle-version 8.13
chmod +x gradlew
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Offline model setup

The app's model picker expects a compatible LiteRT-LM `.litertlm` model. Select it in the app using **Choose Offline Gemma Model**. A GGUF file (such as a llama.cpp quantization) is not interchangeable with a LiteRT-LM model.

See Google's runtime documentation: https://developers.google.com/edge/litert-lm/android

Model availability: https://huggingface.co/litert-community/Gemma3-1B-IT

## Important limitations

- The avatar currently is a simple 2D illustration, not a photorealistic 3D human.
- Android speech recognition may rely on an installed speech service and is not guaranteed to work offline.
- Text-to-speech depends on the device's installed TTS engine and Bengali voice availability.
- This source has been committed to the `android-source` branch. It has not been built or tested on a device in this environment; the first Termux build may expose dependency/API or SDK configuration issues that need adjustment.
