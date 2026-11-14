# AIBondhu Mini — Bengali Offline AI

Android prototype for offline Bengali chat using Qwen2.5-0.5B-Instruct fine-tuned with a small Bengali LoRA dataset.

## Model pipeline
1. `Train Bengali Mini Model` creates the `aibondhu-lora-adapter` Actions artifact.
2. `Export AIBondhu GGUF` merges the adapter into Qwen and converts/quantizes it to `aibondhu-q4_k_m.gguf`.
3. Download that GGUF artifact and select it inside the Android app. The app copies it into private storage and loads it through llama.cpp JNI.

GGUF export is a separate CPU-heavy job and may take a long time or fail if the runner runs out of memory. The model is not bundled into the APK because it is much larger than the app itself.

## Build Android APK
GitHub Actions clones llama.cpp before compiling. For a local Android Studio build, clone llama.cpp into `android/app/src/main/cpp/llama.cpp`, then open `android/` and build `app:assembleDebug`. Target ABI is arm64-v8a.

## Current limitations
- This is a 0.5B prototype, not a ChatGPT-level model. The training dataset is tiny, so Bengali answer quality will be limited.
- First-time GGUF download/import requires internet or a file already on the phone. Inference itself is designed to run offline.
- The current app does not yet contain a rigged 3D human asset. A real animated human requires a licensed rigged GLB/GLTF model and renderer.
- Bengali speech input/output is not yet integrated.

## License
Project code is MIT. Qwen base model, training data and avatar assets retain their own licenses.
