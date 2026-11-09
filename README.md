# Mira — Gemma AI Avatar (Android)

An Android Kotlin prototype with an animated 2D female-style avatar, Bengali chat and TTS, voice input, optional front-camera preview with on-device smile detection, and one-tap model download.

## Model download

- Model: Gemma 3 1B IT, INT4, MediaPipe task format (~555 MB).
- Public mirror: https://huggingface.co/MiCkSoftware/Gemma3-1B-IT-LiteRT
- Direct model file: https://huggingface.co/MiCkSoftware/Gemma3-1B-IT-LiteRT/resolve/main/gemma3-1b-it-int4.task
- SHA-256 pinned in the app: e3d981c01aeaaac69a84ffa0d4be13281b3176731063f1bea1c9fe6887bd9dee.
- The app displays the Gemma Terms of Use and requires the user to affirm agreement before download. No Hugging Face access token is requested by this public mirror. The model remains subject to Google's Gemma Terms: https://ai.google.dev/gemma/terms

The app supports partial download resume where the server accepts HTTP Range requests. It verifies the downloaded model checksum before loading it.

## Runtime note

This version uses Google's MediaPipe LLM Inference Android API because the public mirror is a task bundle. Google currently describes this API as maintenance-only and recommends LiteRT-LM for new development. A future migration should use an openly downloadable litertlm artifact if one is made available under terms compatible with direct no-token download.

## Avatar, camera, and voice

- Animated 2D avatar with talking mouth, arm movement, and simple happy/sad/surprised expression cues.
- Optional front camera preview and on-device ML Kit smile detection. The camera is not streamed to Gemma and no camera frames are uploaded by this implementation.
- Bengali Android Text-to-Speech depends on a Bengali voice installed on the device.
- Android SpeechRecognizer may use a network-backed recognition service; offline speech recognition is not guaranteed.
- The avatar is an animated illustration, not a photorealistic 3D human.
- The current camera feature detects smile probability only; it does not identify the user or infer complex emotions.

## Build in Termux

    git clone -b android-source https://github.com/sajjadbodenjnt/Gemma-AI-Avatar.git
    cd Gemma-AI-Avatar
    chmod +x gradlew
    ./gradlew assembleDebug

APK: app/build/outputs/apk/debug/app-debug.apk

## Device requirements and limitations

The model download is about 555 MB; allow extra free storage for the partial file and final file. Model loading requires additional RAM and compatible CPU/GPU support. On a device with only 2 GB currently free RAM, loading may fail or Android may close the app. Close other apps before loading. Build and real-device testing are required; compatibility is not guaranteed on every Honor device.
