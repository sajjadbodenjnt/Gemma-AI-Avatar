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
    const avatar = gltf.scene; scene.add(avatar);
    const box = new THREE.Box3().setFromObject(avatar), size = box.getSize(new THREE.Vector3()), center = box.getCenter(new THREE.Vector3());
    const maxDim = Math.max(size.x, size.y, size.z);
    if (maxDim > 0) avatar.scale.setScalar(2.0 / maxDim);
    avatar.position.sub(center.multiplyScalar(2.0 / maxDim));
    const scaledBox = new THREE.Box3().setFromObject(avatar), scaledCenter = scaledBox.getCenter(new THREE.Vector3());
    avatar.position.y -= scaledBox.min.y;
    camera.position.set(0, Math.max(1.0, size.y / maxDim), 3.1); camera.lookAt(0, scaledCenter.y + 0.05, 0);
    avatar.traverse(obj => { if (obj.isMesh && obj.morphTargetInfluences) morphMeshes.push(obj); });
    if (gltf.animations?.length) {
      mixer = new THREE.AnimationMixer(avatar);
      const idle = gltf.animations.find(a => /idle|stand|breath/i.test(a.name)) || gltf.animations[0];
      mixer.clipAction(idle).play();
    }
    status.textContent = '3D মানব অবতার প্রস্তুত';
  }, undefined, error => { console.error(error); status.textContent = '3D মডেল লোড হয়নি'; });
  function frame(now) {
    requestAnimationFrame(frame);
    const dt = Math.min(0.05, (now - (last || now)) / 1000); last = now;
    if (mixer) mixer.update(dt); updateFace(dt); renderer.render(scene, camera);
  }
  requestAnimationFrame(frame);
})();