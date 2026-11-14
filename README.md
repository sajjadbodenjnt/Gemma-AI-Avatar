# AIBondhu Mini — Bengali Offline AI + 3D Avatar

A practical starter project for an Android assistant with:
- a small Bengali-tuned language adapter (LoRA fine-tune of Qwen2.5-0.5B-Instruct),
- Android inference via llama.cpp after GGUF export,
- a rigged GLB/GLTF human avatar with facial animation hooks.

> **Honest status:** this repository contains training/build scaffolding, not a trained model or finished photorealistic avatar yet. GitHub stores code and can run workflows; it does not magically train a model without compute. The training workflow below is manual and uses a GitHub-hosted CPU runner, so it may be slow or hit resource limits. No model weights are committed.

## Project layout
- `training/`: Bengali instruction dataset and LoRA fine-tuning script
- `.github/workflows/train-mini-model.yml`: manual training workflow; saves adapter as an Actions artifact
- `android/`: Android Studio starter app
- `docs/AVATAR_ASSETS.md`: requirements for a true animated human (not a photo)

## Train the mini model on GitHub
1. Open **Actions → Train Bengali Mini Model → Run workflow**.
2. The workflow fine-tunes `Qwen/Qwen2.5-0.5B-Instruct` using the small sample dataset.
3. Download the `aibondhu-lora-adapter` artifact from the completed workflow.
4. Expand the dataset with licensed, high-quality Bengali conversations before serious use.

This is **LoRA fine-tuning**, not training a foundation model from zero. For better quality and speed, use a GPU runner (self-hosted) and more training data. The default hosted CPU runner is a prototype path, not a guaranteed successful training environment.

## Android app
Open the `android/` folder in Android Studio. It is a starter UI with a model status screen and JNI integration placeholder. Real on-device inference requires building llama.cpp for Android arm64 and placing the converted/quantized GGUF model in app-private storage. The app deliberately does not fake AI responses.

## 3D human avatar
See [docs/AVATAR_ASSETS.md](docs/AVATAR_ASSETS.md). A realistic human requires a licensed rigged GLB/GLTF character, skeleton, facial blendshapes and animation clips. No static picture is presented as a working 3D human.

## License
Code in this repository: MIT. Base model, datasets and avatar assets retain their own licenses; review them before redistribution.
