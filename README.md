# Gemma AI Avatar — Android source

A Kotlin Android AI companion prototype with a 2D animated avatar, chat, Bengali voice input/output, LiteRT-LM on-device inference, in-app Hugging Face model download, resumable partial downloads, and an app launcher logo.

## Model download

The app downloads the official LiteRT-LM Gemma 3 1B file from the LiteRT Community Hugging Face repository:
- File: gemma3-1b-it-int4.litertlm
- Published size: about 584 MB
- Runtime: LiteRT-LM

First-time Hugging Face setup is required: the model repository is gated. The user must sign in to Hugging Face, accept the Gemma license on the model page, and create a read-access token. Paste that token in the app once. The app stores it encrypted using Android Keystore. Never hard-code or commit a personal token.

The app downloads into private app storage, supports HTTP Range resume where the Hub/CDN provides it, checks that the response is not HTML and that the result is plausibly large before loading it. It retains a partial download after cancellation. Model initialization still depends on device RAM, CPU/GPU support, Android build, and LiteRT-LM compatibility.

## Build in Termux

Clone the android-source branch and build:

    git clone -b android-source https://github.com/sajjadbodenjnt/Gemma-AI-Avatar.git
    cd Gemma-AI-Avatar
    ./gradlew assembleDebug

APK output: app/build/outputs/apk/debug/app-debug.apk

## Important limitations

- The avatar is a stylized 2D illustration, not a realistic 3D human avatar.
- Android SpeechRecognizer may use a network-backed recognition service; offline recognition is not guaranteed.
- Bengali TTS depends on an installed Bengali voice.
- The model is approximately 584 MB; additional free storage and substantial RAM are required. A 1B parameter model can still be slow or fail on some low-memory phones.
- Hugging Face gated access and Gemma license acceptance cannot be bypassed.
- No universal 100% device compatibility guarantee is possible. Build and on-device runtime testing are required.
