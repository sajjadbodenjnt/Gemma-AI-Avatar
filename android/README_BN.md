# Android Starter

এই ফোল্ডার Android Studio project. Java 17, Android SDK 35 এবং Gradle 8.9 ব্যবহার করে।

## Build
Android Studio-তে `android/` folder open করে Gradle sync এবং `app:assembleDebug` চালান।
GitHub Actions-এ Android Debug APK workflow চালালে APK artifact তৈরি হওয়ার কথা।

## বাস্তব offline inference যোগ করার পরের ধাপ
- llama.cpp Android arm64 build যুক্ত করুন
- Qwen base model-কে GGUF-এ convert/quantize করুন অথবা compatible GGUF base ব্যবহার করুন
- LoRA adapter merge করে GGUF export করুন
- model file app-private storage-এ রাখুন এবং JNI থেকে llama.cpp load করুন

বর্তমান UI build starter; এটি এখনও trained model চালায় না এবং avatar view placeholder।
