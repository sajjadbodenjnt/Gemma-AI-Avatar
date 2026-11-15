import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';

(() => {
  const host = document.getElementById('avatar');
  const status = document.getElementById('status');
  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xf0f4f8);
  const camera = new THREE.PerspectiveCamera(32, 1, 0.01, 100);
  camera.position.set(0, 1.45, 3.2);
  const renderer = new THREE.WebGLRenderer({ antialias: true, alpha: false });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5));
  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.0;
  host.appendChild(renderer.domElement);
  scene.add(new THREE.HemisphereLight(0xffffff, 0x687080, 2.0));
  const key = new THREE.DirectionalLight(0xffffff, 2.2); key.position.set(2, 4, 3); scene.add(key);
  const fill = new THREE.DirectionalLight(0xdce9ff, 0.7); fill.position.set(-3, 2, -2); scene.add(fill);
  let mixer = null, last = 0;
  const morphMeshes = [];
  const targets = { CLOSED: 0, TEETH: 0, OPEN: 0, WIDE: 0, ROUND: 0, NARROW: 0 };
  const current = { ...targets };
  function resize() {
    const w = Math.max(1, host.clientWidth), h = Math.max(1, host.clientHeight);
    renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
  }
  new ResizeObserver(resize).observe(host); resize();
  function indexOf(dict, names) {
    if (!dict) return -1;
    for (const n of names) if (dict[n] !== undefined) return dict[n];
    return -1;
  }
  function updateFace(dt) {
    const blend = 1 - Math.exp(-Math.max(0.001, dt) * 18);
    for (const k of Object.keys(current)) current[k] += (targets[k] - current[k]) * blend;
    for (const mesh of morphMeshes) {
      const dict = mesh.morphTargetDictionary || {}, values = mesh.morphTargetInfluences;
      if (!values) continue;
      for (let i = 0; i < values.length; i++) values[i] = 0;
      const set = (names, v) => { const i = indexOf(dict, names); if (i >= 0) values[i] = Math.max(values[i], v); };
      set(['jawOpen'], current.OPEN * 0.85 + current.NARROW * 0.2);
      set(['mouthClose'], current.CLOSED);
      set(['mouthFunnel', 'mouthPucker'], current.ROUND);
      set(['mouthSmileLeft', 'mouthSmileRight', 'mouthStretchLeft', 'mouthStretchRight'], current.WIDE * 0.45);
      set(['mouthPressLeft', 'mouthPressRight'], current.TEETH * 0.35);
    }
  }
  window.aibondhuSetViseme = (name, weight) => {
    for (const k of Object.keys(targets)) targets[k] = 0;
    if (targets[name] !== undefined) targets[name] = Math.max(0, Math.min(1, Number(weight) || 0));
  };
  new GLTFLoader().load('./sophia.glb', (gltf) => {
    const avatar = gltf.scene;
    avatar.traverse(obj => {
      if (obj.isMesh) {
        obj.frustumCulled = false;
        if (obj.morphTargetInfluences) morphMeshes.push(obj);
      }
    });
    scene.add(avatar);
    let bounds = new THREE.Box3().setFromObject(avatar);
    const originalSize = bounds.getSize(new THREE.Vector3());
    const maxDim = Math.max(originalSize.x, originalSize.y, originalSize.z);
    if (!Number.isFinite(maxDim) || maxDim <= 0) {
      status.textContent = '3D মডেলের মাপ সঠিক নয়';
      return;
    }
    avatar.scale.setScalar(1.85 / maxDim);
    bounds = new THREE.Box3().setFromObject(avatar);
    const center = bounds.getCenter(new THREE.Vector3());
    avatar.position.x -= center.x;
    avatar.position.y -= center.y;
    avatar.position.z -= center.z;
    bounds = new THREE.Box3().setFromObject(avatar);
    avatar.position.y -= bounds.min.y;
    bounds = new THREE.Box3().setFromObject(avatar);
    const fittedSize = bounds.getSize(new THREE.Vector3());
    const target = bounds.getCenter(new THREE.Vector3());
    const halfFov = THREE.MathUtils.degToRad(camera.fov * 0.5);
    const fitHeight = (fittedSize.y * 0.5) / Math.tan(halfFov);
    const fitWidth = (fittedSize.x * 0.5) / (Math.tan(halfFov) * camera.aspect);
    const distance = Math.max(fitHeight, fitWidth, fittedSize.z) * 1.45;
    camera.near = 0.01;
    camera.far = Math.max(100, distance * 10);
    camera.position.set(0, target.y, distance);
    camera.lookAt(0, target.y, 0);
    camera.updateProjectionMatrix();
    if (gltf.animations?.length) {
      mixer = new THREE.AnimationMixer(avatar);
      const idle = gltf.animations.find(a => /idle|stand|breath/i.test(a.name)) || gltf.animations[0];
      mixer.clipAction(idle).play();
    }
    status.textContent = '3D মানব অবতার লোড হয়েছে';
  }, undefined, error => {
    console.error('Sophia GLB load failed:', error);
    status.textContent = '3D মডেল লোড হয়নি — GLB ফাইল পরীক্ষা করুন';
  });
  function frame(now) {
    requestAnimationFrame(frame);
    const dt = Math.min(0.05, (now - (last || now)) / 1000); last = now;
    if (mixer) mixer.update(dt); updateFace(dt); renderer.render(scene, camera);
  }
  requestAnimationFrame(frame);
})();