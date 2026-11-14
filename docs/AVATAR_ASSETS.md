# Real 3D Human Avatar Asset

A photograph or AI-generated portrait is not an animated 3D human. The app needs a real, licensed 3D character asset.

## Required asset
- GLB or glTF 2.0, preferably under 20–40 MB for a mid-range phone
- Humanoid skeleton with retargetable joints
- Facial blendshapes/morph targets: blink, jawOpen, smile, visemes (A/E/I/O/U)
- Idle, listening, speaking and simple hand gesture animation clips
- PBR textures with mobile-friendly resolution (1K–2K)
- Explicit redistribution and commercial-use license compatible with the app

## Runtime integration
Use Filament gltfio or another maintained Android renderer. Load the GLB from app-private files, render with a surface, and drive:
1. idle animation while waiting,
2. jaw/mouth morph weights from audio amplitude or phoneme timing,
3. blink and gaze animation,
4. gestures synchronized to speech state.

## Important
Do not put an unlicensed character or copyrighted game/movie character in the repository. Add the actual GLB only after its license and file size are verified. The current Android starter has an avatar placeholder, not a rendered 3D model.
