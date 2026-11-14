# AIBondhu Mini Android

বাংলা অফলাইন চ্যাট অ্যাপ। arm64-v8a ফোনে llama.cpp JNI ব্যবহার করে GGUF মডেল চালানোর জন্য তৈরি।

## Build
GitHub Actions-এর **Android Debug APK** workflow llama.cpp checkout করে APK তৈরি করে। লোকাল build-এর আগে:
`git clone --depth 1 https://github.com/ggml-org/llama.cpp.git app/src/main/cpp/llama.cpp`
তারপর Android Studio-তে এই `android/` ফোল্ডার খুলে `app:assembleDebug` চালান।

## মডেল ব্যবহার
**Export AIBondhu GGUF** workflow থেকে `aibondhu-android-gguf-q4km` artifact ডাউনলোড করুন। ZIP extract করে `aibondhu-q4_k_m.gguf` ফোনে রাখুন। অ্যাপ খুলে **GGUF মডেল নির্বাচন করুন** চাপুন এবং ফাইলটি বেছে নিন। প্রথমবার কপি/লোড হতে সময় লাগবে; এরপর inference অফলাইনে হবে।

## সীমাবদ্ধতা
এটি ছোট 0.5B experimental model; অল্প বাংলা ডেটায় fine-tune করা, তাই উত্তর ChatGPT-সমমান নয়। বাস্তব rigged 3D avatar এবং বাংলা offline speech এখনো যুক্ত নয়।
