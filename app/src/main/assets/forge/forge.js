// Website Forge: the "Forge Reactor" scene. Colours come only from the theme JSON given to Forge.init.
// No network access: everything is drawn here. The page talks to Android through ForgeBridge, which has
// exactly three methods: onReady, onRevealFinished, onWebGLFailed.
import * as THREE from 'three';
import { EffectComposer } from './jsm/postprocessing/EffectComposer.js';
import { RenderPass } from './jsm/postprocessing/RenderPass.js';
import { UnrealBloomPass } from './jsm/postprocessing/UnrealBloomPass.js';
import { ShaderPass } from './jsm/postprocessing/ShaderPass.js';
import { OutputPass } from './jsm/postprocessing/OutputPass.js';

const bridge = (name) => {
  try { if (window.ForgeBridge && window.ForgeBridge[name]) window.ForgeBridge[name](); } catch (e) { /* ignore */ }
};

const clamp = (v, a, b) => Math.max(a, Math.min(b, v));
const lerp = (a, b, t) => a + (b - a) * t;
const ease = (t) => { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); };
const GLYPHS = ['{', '}', '<', '>', '/', '=', ';', '(', ')', '0', '1', '#', '$', '*', '&', '%'];
const MAX_SLABS = 10;
const MAX_BEAMS = 22;
const MAX_WAVES = 4;
const MAX_CARDS = 4;
const PARTICLES = 1800;
const NODE_COUNT = 6;
const FLOOR_Y = -1.5;

let G = null; // everything that must be disposed
let ready = false;

// ---------------------------------------------------------------- shaders
const NOISE = `
float hash(vec3 p){ p=fract(p*0.3183099+.1); p*=17.0; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float noise(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.0-2.0*f);
  return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
             mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z); }
float fbm(vec3 p){ float a=.5,s=0.; for(int i=0;i<4;i++){ s+=a*noise(p); p*=2.02; a*=.5; } return s; }
`;

const BACKDROP_VS = `varying vec3 vDir;
void main(){ vDir = normalize(position); gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
const BACKDROP_FS = `uniform vec3 uTop, uBottom, uTint1, uTint2; uniform float uTime, uEnergy; varying vec3 vDir;
${NOISE}
void main(){
  vec3 d = normalize(vDir);
  float h = d.y * .5 + .5;
  vec3 c = mix(uBottom, uTop, smoothstep(0., 1., h));
  float n = fbm(d * 2.2 + vec3(0., uTime * .01, 0.));
  c += mix(uTint1, uTint2, n) * pow(n, 2.2) * (.35 + .4 * uEnergy);
  vec3 g = d * 140.; vec3 id = floor(g); float r = hash(id);
  float star = step(.9965, r) * smoothstep(.5, .0, length(fract(g) - .5)) * (.6 + .4 * sin(uTime * 2. + r * 40.));
  c += vec3(star);
  gl_FragColor = vec4(c, 1.);
}`;

const FLOOR_VS = `varying vec2 vXZ;
void main(){ vXZ = (modelMatrix * vec4(position, 1.)).xz; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.); }`;
const FLOOR_FS = `uniform vec3 uLine, uGlow; uniform float uTime, uEnergy; uniform vec4 uRip[6]; varying vec2 vXZ;
void main(){
  vec2 q = vXZ * 0.8;
  vec2 g = abs(fract(q - .5) - .5) / fwidth(q);
  float line = 1. - min(min(g.x, g.y), 1.);
  float dist = length(vXZ);
  float fade = smoothstep(18., 2., dist);
  float rip = 0.;
  for (int i = 0; i < 6; i++) {
    float age = uTime - uRip[i].z;
    if (uRip[i].w > 0. && age > 0. && age < 3.) {
      float rad = age * 4.;
      float d = abs(length(vXZ - uRip[i].xy) - rad);
      rip += smoothstep(.5, 0., d) * (1. - age / 3.) * uRip[i].w;
    }
  }
  vec3 c = uLine * line * fade * (.35 + .5 * uEnergy) + uGlow * rip * fade * 1.4;
  float a = clamp(line * fade * .8 + rip * fade, 0., 1.);
  gl_FragColor = vec4(c, a);
}`;

const FRESNEL_VS = `varying vec3 vN; varying vec3 vV;
void main(){ vec4 mv = modelViewMatrix * vec4(position, 1.); vN = normalize(normalMatrix * normal); vV = normalize(-mv.xyz); gl_Position = projectionMatrix * mv; }`;
const FRESNEL_FS = `uniform vec3 uA, uB; uniform float uEnergy, uTime, uFlash; varying vec3 vN; varying vec3 vV;
void main(){
  float f = pow(1. - abs(dot(normalize(vN), normalize(vV))), 2.5);
  float pulse = .85 + .15 * sin(uTime * 3.);
  vec3 c = mix(uA, uB, f) * (.5 + uEnergy * 1.1) * pulse + uB * uFlash * 1.5;
  gl_FragColor = vec4(c, clamp(.5 + f * .5 + uFlash, 0., 1.));
}`;

const GLYPH_VS = `attribute vec4 aSeed; attribute float aGlyph; attribute float aRnd;
uniform float uTime, uDensity, uBurst, uScale, uCoreY, uSpeed; uniform vec3 uColA, uColB;
varying float vGlyph; varying vec3 vCol; varying float vAlpha;
void main(){
  float visible = step(aRnd, uDensity);
  float u = fract(aSeed.x + uTime * uSpeed * (.6 + aRnd * .8));
  float e = pow(u, 1.6);
  float r = mix(aSeed.z, .4, e);
  float ang = aSeed.y + e * 7.0 + uTime * .1;
  float y = mix(aSeed.w, uCoreY, e);
  vec3 p = vec3(cos(ang) * r, y, sin(ang) * r);
  p += normalize(vec3(cos(aSeed.y * 3.), (aRnd - .5) * 1.2, sin(aSeed.y * 3.))) * uBurst * (2. + aRnd * 6.);
  vec4 mv = modelViewMatrix * vec4(p, 1.);
  gl_Position = projectionMatrix * mv;
  float fade = smoothstep(0., .1, u) * smoothstep(1., .85, u);
  vAlpha = fade * visible * (1. - uBurst * .6);
  vGlyph = aGlyph; vCol = mix(uColA, uColB, aRnd);
  gl_PointSize = visible * (uScale * (.6 + aRnd * .9)) / max(.1, -mv.z);
}`;
const GLYPH_FS = `uniform sampler2D uAtlas; varying float vGlyph; varying vec3 vCol; varying float vAlpha;
void main(){
  float g = floor(vGlyph + .5);
  vec2 cell = vec2(mod(g, 8.), floor(g / 8.));
  vec2 uv = vec2((cell.x + gl_PointCoord.x) / 8., 1. - (cell.y + gl_PointCoord.y) / 2.);
  float a = texture2D(uAtlas, uv).a;
  gl_FragColor = vec4(vCol, a * vAlpha);
}`;

const BEAM_VS = `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.); }`;
const BEAM_FS = `uniform vec3 uColor; uniform float uT; varying vec2 vUv;
void main(){
  float d = abs(vUv.y - uT);
  float a = smoothstep(.3, 0., d) * (1. - uT * .3);
  gl_FragColor = vec4(uColor * (.6 + a), a);
}`;

const WAVE_FS = `uniform vec3 uColor; uniform float uAge; varying vec2 vUv;
void main(){
  vec2 p = vUv - .5;
  float r = length(p) * 2.;
  float ring = smoothstep(.06, 0., abs(r - uAge));
  float a = ring * (1. - uAge);
  gl_FragColor = vec4(uColor, a);
}`;

const SLAB_FS = `uniform vec3 uRim, uFill; uniform float uBirth, uBars, uShimmer, uDots, uAlpha, uTime, uSeed; varying vec2 vUv;
float h2(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float sdRound(vec2 p, vec2 b, float r){ vec2 q = abs(p) - b + r; return length(max(q, 0.)) + min(max(q.x, q.y), 0.) - r; }
void main(){
  vec2 p = (vUv - .5) * vec2(1.5, 2.0);
  float d = sdRound(p, vec2(.75, 1.0), .12);
  float body = smoothstep(.01, -.01, d);
  float rim = smoothstep(.05, 0., abs(d));
  float edge = 1. - uBirth;
  float shown = step(edge, vUv.y);
  float scan = smoothstep(.025, 0., abs(vUv.y - edge)) * step(uBirth, .999) * step(.001, uBirth);
  float row = floor(vUv.y * 14.);
  float hh = h2(vec2(row, uSeed));
  float inRow = step(.2, fract(vUv.y * 14.));
  float bar = inRow * step(.12, vUv.x) * step(vUv.x, .12 + .2 + .6 * hh) * step(row, 12.) * step(1., row);
  float dots = 0.;
  for (int i = 0; i < 3; i++) { dots += smoothstep(.04, .03, length(p - vec2(-.6 + float(i) * .13, .9))); }
  float s = sin((vUv.x + vUv.y) * 6. - uTime * 2.5);
  float shimmer = smoothstep(.85, 1., s) * uShimmer * .35;
  float grad = .12 + .1 * vUv.y;
  vec3 col = uFill * grad + uRim * (rim * 1.2 + bar * .35 * uBars + dots * uDots * .8 + shimmer + scan * 1.5);
  float alpha = (body * (.28 + bar * .25 * uBars) + rim * .6 + shimmer * .4) * shown * uAlpha + scan * body;
  gl_FragColor = vec4(col, clamp(alpha, 0., 1.));
}`;

const CARD_FS = `uniform sampler2D uMap; uniform vec3 uRim; uniform float uAlpha; varying vec2 vUv;
float sdRound(vec2 p, vec2 b, float r){ vec2 q = abs(p) - b + r; return length(max(q, 0.)) + min(max(q.x, q.y), 0.) - r; }
void main(){
  vec2 p = (vUv - .5) * vec2(2.2, .55);
  float d = sdRound(p, vec2(1.1, .275), .1);
  float body = smoothstep(.01, -.01, d);
  float rim = smoothstep(.03, 0., abs(d));
  vec4 t = texture2D(uMap, vUv);
  vec3 col = uRim * (.12 + rim * 1.1) + t.rgb * t.a;
  float alpha = (body * .42 + rim * .6 + t.a * .9) * uAlpha;
  gl_FragColor = vec4(col, clamp(alpha, 0., 1.));
}`;

const ARC_VS = `varying vec2 vL; void main(){ vL = position.xy; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.); }`;
const ARC_FS = `uniform vec3 uBase, uEnergy; uniform float uProgress, uTime; varying vec2 vL;
void main(){
  float a = fract(atan(vL.y, vL.x) / 6.2831853);
  float on = step(a, uProgress);
  float head = smoothstep(.04, 0., abs(a - uProgress)) * step(.001, uProgress);
  float pulse = .75 + .25 * sin(uTime * 4. - a * 30.);
  vec3 c = uBase * .35 + uEnergy * on * pulse + uEnergy * head * 2.;
  gl_FragColor = vec4(c, .35 + on * .5 + head);
}`;

const POST_VS = `varying vec2 vUv; void main(){ vUv = uv; gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0); }`;
const POST_FS = `uniform sampler2D tDiffuse; uniform float uAmount; varying vec2 vUv;
void main(){
  vec2 c = vUv - .5; float d = dot(c, c);
  vec2 off = c * d * uAmount * .06;
  float r = texture2D(tDiffuse, vUv + off).r;
  float g = texture2D(tDiffuse, vUv).g;
  float b = texture2D(tDiffuse, vUv - off).b;
  vec3 col = vec3(r, g, b);
  col *= 1. - smoothstep(.25, .85, d * 1.6) * .55;
  gl_FragColor = vec4(col, 1.);
}`;

// ---------------------------------------------------------------- helpers
const hex = (h, fallback) => new THREE.Color(typeof h === 'string' && /^#[0-9a-fA-F]{6}$/.test(h) ? h : fallback);

function makeMaterial(vs, fs, uniforms, opts) {
  return new THREE.ShaderMaterial(Object.assign({
    vertexShader: vs, fragmentShader: fs, uniforms, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending,
  }, opts || {}));
}

function glyphAtlas() {
  const canvas = document.createElement('canvas');
  canvas.width = 512; canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, 512, 128);
  ctx.fillStyle = '#fff';
  ctx.font = 'bold 44px monospace';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  GLYPHS.forEach((g, i) => ctx.fillText(g, (i % 8) * 64 + 32, Math.floor(i / 8) * 64 + 34));
  const tex = new THREE.CanvasTexture(canvas);
  tex.generateMipmaps = false; tex.minFilter = THREE.LinearFilter; tex.magFilter = THREE.LinearFilter;
  return tex;
}

function haloTexture() {
  const c = document.createElement('canvas'); c.width = c.height = 128;
  const x = c.getContext('2d');
  const g = x.createRadialGradient(64, 64, 0, 64, 64, 64);
  g.addColorStop(0, 'rgba(255,255,255,1)'); g.addColorStop(.35, 'rgba(255,255,255,.35)'); g.addColorStop(1, 'rgba(255,255,255,0)');
  x.fillStyle = g; x.fillRect(0, 0, 128, 128);
  return new THREE.CanvasTexture(c);
}

function textTexture(width, height) {
  const canvas = document.createElement('canvas'); canvas.width = width; canvas.height = height;
  const tex = new THREE.CanvasTexture(canvas);
  tex.generateMipmaps = false; tex.minFilter = THREE.LinearFilter;
  return { canvas, tex, ctx: canvas.getContext('2d') };
}

// ---------------------------------------------------------------- build
function build(theme, opts) {
  const canvas = document.getElementById('c');
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: false, alpha: false, powerPreference: 'high-performance' });
  } catch (e) { throw new Error('webgl'); }
  if (!renderer || !renderer.getContext()) throw new Error('webgl');

  const colors = {
    bg: hex(theme.background, '#06050e'), top: hex(theme.backgroundTop, '#15113a'),
    accent: hex(theme.accent, '#ffb547'), accent2: hex(theme.accentSecondary, '#ff8a3d'),
    lagoon: hex(theme.lagoon, '#3fe0d0'), lotus: hex(theme.lotus, '#ff6fae'),
    text: hex(theme.textPrimary, '#eee9ff'), text2: hex(theme.textSecondary, '#a7a0cc'), text3: hex(theme.textTertiary, '#7d77a8'),
    error: hex(theme.error, '#ff6b7d'), success: hex(theme.success, '#4ade9b'),
  };
  document.body.style.background = theme.background || '#06050e';

  const pixelCap = opts.lowRam ? 1.5 : 2;
  let pixelRatio = Math.min(window.devicePixelRatio || 1, pixelCap);
  renderer.setPixelRatio(pixelRatio);
  const w = window.innerWidth, h = window.innerHeight;
  renderer.setSize(w, h, false);
  renderer.setClearColor(colors.bg, 1);

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(52, w / h, 0.1, 200);

  const disposables = [];
  const track = (o) => { disposables.push(o); return o; };

  // backdrop
  const backdropU = {
    uTop: { value: colors.top }, uBottom: { value: colors.bg }, uTint1: { value: colors.lagoon.clone().multiplyScalar(.5) },
    uTint2: { value: colors.lotus.clone().multiplyScalar(.5) }, uTime: { value: 0 }, uEnergy: { value: 0 },
  };
  const backdrop = new THREE.Mesh(track(new THREE.SphereGeometry(80, 32, 20)),
    track(makeMaterial(BACKDROP_VS, BACKDROP_FS, backdropU, { side: THREE.BackSide, transparent: false, blending: THREE.NormalBlending })));
  backdrop.renderOrder = -10;
  scene.add(backdrop);

  // floor grid with ripples
  const ripples = []; for (let i = 0; i < 6; i++) ripples.push(new THREE.Vector4(0, 0, -100, 0));
  const floorU = { uLine: { value: colors.lagoon.clone().multiplyScalar(.7) }, uGlow: { value: colors.accent }, uTime: { value: 0 }, uEnergy: { value: 0 }, uRip: { value: ripples } };
  const floor = new THREE.Mesh(track(new THREE.PlaneGeometry(60, 60)), track(makeMaterial(FLOOR_VS, FLOOR_FS, floorU)));
  floor.rotation.x = -Math.PI / 2; floor.position.y = FLOOR_Y;
  scene.add(floor);
  let rippleAt = 0;
  const addRipple = (time, strength) => { ripples[rippleAt % 6].set(0, 0, time, strength); rippleAt++; };

  // reactor core
  const core = new THREE.Group(); core.position.y = 0.6; scene.add(core);
  const coreU = { uA: { value: colors.lagoon }, uB: { value: colors.lotus }, uEnergy: { value: 0 }, uTime: { value: 0 }, uFlash: { value: 0 } };
  const coreSphere = new THREE.Mesh(track(new THREE.SphereGeometry(1, 48, 32)), track(makeMaterial(FRESNEL_VS, FRESNEL_FS, coreU)));
  core.add(coreSphere);
  const wireGeo = track(new THREE.WireframeGeometry(new THREE.IcosahedronGeometry(1.25, 1)));
  const wireMat = track(new THREE.LineBasicMaterial({ color: colors.accent, transparent: true, opacity: .5, blending: THREE.AdditiveBlending, depthWrite: false }));
  const wire = new THREE.LineSegments(wireGeo, wireMat); core.add(wire);
  const rings = [];
  for (let i = 0; i < 3; i++) {
    const m = new THREE.Mesh(track(new THREE.TorusGeometry(1.8 + i * .25, .012, 8, 96)),
      track(new THREE.MeshBasicMaterial({ color: i === 1 ? colors.lagoon : colors.accent, transparent: true, opacity: .8, blending: THREE.AdditiveBlending, depthWrite: false })));
    m.rotation.set(i * 1.1, i * .6, 0); core.add(m); rings.push(m);
  }
  const haloTex = track(haloTexture());
  const halo = new THREE.Sprite(track(new THREE.SpriteMaterial({ map: haloTex, color: colors.lotus, transparent: true, opacity: .55, blending: THREE.AdditiveBlending, depthWrite: false })));
  halo.scale.set(6, 6, 1); core.add(halo);

  // glyph particles
  const atlas = track(glyphAtlas());
  const pos = new Float32Array(PARTICLES * 3);
  const seed = new Float32Array(PARTICLES * 4);
  const glyph = new Float32Array(PARTICLES);
  const rnd = new Float32Array(PARTICLES);
  for (let i = 0; i < PARTICLES; i++) {
    seed[i * 4] = Math.random(); seed[i * 4 + 1] = Math.random() * Math.PI * 2;
    seed[i * 4 + 2] = 3.5 + Math.random() * 6; seed[i * 4 + 3] = FLOOR_Y + Math.random() * 6;
    glyph[i] = Math.floor(Math.random() * GLYPHS.length); rnd[i] = Math.random();
  }
  const pg = track(new THREE.BufferGeometry());
  pg.setAttribute('position', new THREE.BufferAttribute(pos, 3));
  pg.setAttribute('aSeed', new THREE.BufferAttribute(seed, 4));
  pg.setAttribute('aGlyph', new THREE.BufferAttribute(glyph, 1));
  pg.setAttribute('aRnd', new THREE.BufferAttribute(rnd, 1));
  const glyphU = {
    uAtlas: { value: atlas }, uTime: { value: 0 }, uDensity: { value: 0.1 }, uBurst: { value: 0 }, uScale: { value: 70 * pixelRatio },
    uCoreY: { value: 0.6 }, uSpeed: { value: 0.07 }, uColA: { value: colors.lagoon }, uColB: { value: colors.accent },
  };
  const particles = new THREE.Points(pg, track(makeMaterial(GLYPH_VS, GLYPH_FS, glyphU)));
  particles.frustumCulled = false; scene.add(particles);

  // dust
  const dustPos = new Float32Array(300 * 3);
  for (let i = 0; i < 300; i++) { dustPos[i * 3] = (Math.random() - .5) * 20; dustPos[i * 3 + 1] = FLOOR_Y + Math.random() * 7; dustPos[i * 3 + 2] = (Math.random() - .5) * 20; }
  const dg = track(new THREE.BufferGeometry()); dg.setAttribute('position', new THREE.BufferAttribute(dustPos, 3));
  const dust = new THREE.Points(dg, track(new THREE.PointsMaterial({ color: colors.text3, size: .035, transparent: true, opacity: .5, blending: THREE.AdditiveBlending, depthWrite: false })));
  dust.frustumCulled = false; scene.add(dust);

  // beams (pooled)
  const beamGeo = track(new THREE.CylinderGeometry(.02, .02, 1, 6, 1, true)); beamGeo.translate(0, .5, 0);
  const beams = [];
  for (let i = 0; i < MAX_BEAMS; i++) {
    const u = { uColor: { value: i % 2 ? colors.lagoon : colors.accent }, uT: { value: 0 } };
    const m = new THREE.Mesh(beamGeo, track(makeMaterial(BEAM_VS, BEAM_FS, u)));
    m.visible = false; m.frustumCulled = false; scene.add(m);
    beams.push({ mesh: m, u, age: 0, life: 1, on: false });
  }
  // shockwaves (pooled)
  const waveGeo = track(new THREE.PlaneGeometry(1, 1));
  const waves = [];
  for (let i = 0; i < MAX_WAVES; i++) {
    const u = { uColor: { value: colors.accent }, uAge: { value: 0 } };
    const m = new THREE.Mesh(waveGeo, track(makeMaterial(BEAM_VS, WAVE_FS, u)));
    m.rotation.x = -Math.PI / 2; m.position.y = FLOOR_Y + .02; m.visible = false; m.frustumCulled = false; scene.add(m);
    waves.push({ mesh: m, u, age: 0, life: 1.6, size: 14, on: false });
  }

  // glass slabs
  const slabGeo = track(new THREE.PlaneGeometry(1.5, 2.0));
  const slabs = [];
  const slabGroup = new THREE.Group(); scene.add(slabGroup);

  // stage ring: arc + nodes + labels
  const stageRadius = 3.4;
  const arcU = { uBase: { value: colors.text3 }, uEnergy: { value: colors.accent }, uProgress: { value: 0 }, uTime: { value: 0 } };
  const arc = new THREE.Mesh(track(new THREE.RingGeometry(stageRadius - .035, stageRadius + .035, 192)), track(makeMaterial(ARC_VS, ARC_FS, arcU, { side: THREE.DoubleSide })));
  arc.rotation.x = -Math.PI / 2; arc.position.y = FLOOR_Y + .03; scene.add(arc);
  const labels = (opts.stageLabels && opts.stageLabels.length === NODE_COUNT) ? opts.stageLabels : ['Planning', 'Structure', 'Styling', 'Visuals', 'Motion', 'Finishing'];
  const nodes = [];
  for (let i = 0; i < NODE_COUNT; i++) {
    const ang = ((i + .5) / NODE_COUNT) * Math.PI * 2;
    const mat = track(new THREE.MeshBasicMaterial({ color: colors.text3, transparent: true, opacity: .9 }));
    const mesh = new THREE.Mesh(track(new THREE.SphereGeometry(.1, 16, 12)), mat);
    mesh.position.set(Math.cos(ang) * stageRadius, FLOOR_Y + .12, -Math.sin(ang) * stageRadius);
    scene.add(mesh);
    const t = textTexture(256, 64);
    const sprite = new THREE.Sprite(track(new THREE.SpriteMaterial({ map: track(t.tex), transparent: true, depthWrite: false })));
    sprite.scale.set(1.3, .325, 1); sprite.position.set(mesh.position.x, FLOOR_Y + .55, mesh.position.z);
    scene.add(sprite);
    nodes.push({ mesh, mat, sprite, t, label: labels[i], state: -1 });
  }

  // floating log cards (pooled)
  const cardGeo = track(new THREE.PlaneGeometry(2.2, .55));
  const cards = [];
  for (let i = 0; i < MAX_CARDS; i++) {
    const t = textTexture(512, 128);
    const u = { uMap: { value: track(t.tex) }, uRim: { value: colors.lagoon }, uAlpha: { value: 0 } };
    const m = new THREE.Mesh(cardGeo, track(makeMaterial(BEAM_VS, CARD_FS, u)));
    m.visible = false; m.frustumCulled = false; scene.add(m);
    cards.push({ mesh: m, u, t, age: 0, life: 4.5, on: false, slot: i });
  }

  // post
  const composer = new EffectComposer(renderer);
  composer.setPixelRatio(pixelRatio); composer.setSize(w, h);
  composer.addPass(new RenderPass(scene, camera));
  const bloom = new UnrealBloomPass(new THREE.Vector2(w, h), 0.34, 0.42, 0.55);
  composer.addPass(bloom);
  const post = new ShaderPass({ uniforms: { tDiffuse: { value: null }, uAmount: { value: 1 } }, vertexShader: POST_VS, fragmentShader: POST_FS });
  composer.addPass(post);
  composer.addPass(new OutputPass());

  return {
    renderer, scene, camera, composer, bloom, post, canvas, colors, disposables, pixelCap, pixelRatio,
    backdropU, floorU, coreU, core, coreSphere, wire, rings, halo, glyphU, particles, dust, beams, waves, slabs, slabGroup, slabGeo,
    arcU, nodes, cards, addRipple, labels, haloTex,
  };
}

// ---------------------------------------------------------------- runtime state
const freshState = () => ({
  reduced: false, paused: false, raf: 0, last: 0, time: 0, dirty: true,
  phase: 'idle', stage: 0, progress: 0, shownProgress: 0, chars: 0, energy: 0, shownEnergy: 0,
  flyIn: 0, orbit: 0.6, yaw: 0, pitch: 0, yawVel: 0, tiltX: 0, tiltY: 0, kick: 0, flash: 0, burst: 0,
  rev: null, failed: false, ema: 0.016, frames: 0, beamCredit: 0, lastChars: 0, cardNext: 0, dragging: false, px: 0, py: 0,
});
const R = freshState();
let theme = {}; let options = {};
let listeners = [];

function on(target, type, fn, opt) { target.addEventListener(type, fn, opt); listeners.push([target, type, fn, opt]); }

function spawnBeam() {
  const b = G.beams.find((x) => !x.on); if (!b) return;
  const a = Math.random() * Math.PI * 2, r = 7 + Math.random() * 3;
  const start = new THREE.Vector3(Math.cos(a) * r, FLOOR_Y + 1 + Math.random() * 4, Math.sin(a) * r);
  const end = G.core.position.clone();
  const dir = end.clone().sub(start); const len = dir.length(); dir.normalize();
  b.mesh.position.copy(start); b.mesh.scale.set(1, len, 1);
  b.mesh.quaternion.setFromUnitVectors(new THREE.Vector3(0, 1, 0), dir);
  b.age = 0; b.life = .8 + Math.random() * .6; b.on = true; b.mesh.visible = true;
}

function spawnWave(size, life) {
  const w = G.waves.find((x) => !x.on); if (!w) return;
  w.age = 0; w.life = life || 1.6; w.size = size || 14; w.on = true; w.mesh.visible = true; w.mesh.scale.set(w.size, w.size, 1);
}

function drawLabel(n, state) {
  const { ctx, canvas } = n.t;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const c = state === 1 ? G.colors.accent : state === 0 ? G.colors.text : G.colors.text3;
  ctx.fillStyle = '#' + c.getHexString();
  ctx.font = (state === 0 ? 'bold ' : '') + '28px sans-serif'; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillText(n.label, 128, 32);
  n.t.tex.needsUpdate = true;
}

function refreshNodes() {
  G.nodes.forEach((n, i) => {
    const state = i < R.stage ? 1 : i === R.stage ? 0 : 2; // done, current, future
    if (state !== n.state) {
      n.state = state; drawLabel(n, state);
      n.mat.color.copy(state === 1 ? G.colors.accent : state === 0 ? G.colors.lagoon : G.colors.text3);
    }
  });
}

function addSlab(name) {
  if (G.slabs.length >= MAX_SLABS) return;
  const i = G.slabs.length;
  const u = {
    uRim: { value: i % 2 ? G.colors.lagoon : G.colors.accent }, uFill: { value: G.colors.top }, uBirth: { value: R.reduced ? 1 : 0 },
    uBars: { value: R.stage >= 2 ? 1 : 0 }, uShimmer: { value: R.stage >= 4 ? 1 : 0 }, uDots: { value: i === 0 ? 1 : 0 },
    uAlpha: { value: 1 }, uTime: { value: 0 }, uSeed: { value: Math.random() * 50 },
  };
  const mesh = new THREE.Mesh(G.slabGeo, makeMaterial(BEAM_VS, SLAB_FS, u, { side: THREE.DoubleSide }));
  mesh.frustumCulled = false;
  const ang = -1.9 + i * (3.8 / (MAX_SLABS - 1));
  const home = new THREE.Vector3(Math.sin(ang) * 4.6, 0.7 + Math.sin(i * 1.7) * .35, -Math.cos(ang) * 4.6 + 0.6);
  const far = home.clone().multiplyScalar(1.6);
  mesh.position.copy(R.reduced ? home : far);
  G.slabGroup.add(mesh);
  G.slabs.push({ mesh, u, home, far, name, age: R.reduced ? 1 : 0, vel: new THREE.Vector3((Math.random() - .5), (Math.random() - .2), (Math.random() - .5)) });
  G.disposables.push(mesh.material);
}

function addCard(text) {
  const c = G.cards[R.cardNext % MAX_CARDS]; R.cardNext++;
  const { ctx, canvas, tex } = c.t;
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#' + G.colors.accent.getHexString(); ctx.fillRect(22, 34, 6, 60);
  ctx.fillStyle = '#' + G.colors.text.getHexString();
  ctx.font = '38px sans-serif'; ctx.textAlign = 'left'; ctx.textBaseline = 'middle';
  let t = String(text || ''); while (t.length > 3 && ctx.measureText(t).width > 430) t = t.slice(0, -2);
  ctx.fillText(t, 46, 64); tex.needsUpdate = true;
  c.age = 0; c.on = true; c.mesh.visible = true; c.u.uRim.value = R.cardNext % 2 ? G.colors.lagoon : G.colors.lotus;
  const side = (c.slot % 2 === 0) ? 1 : -1;
  c.mesh.userData.base = new THREE.Vector3(side * (2.8 + (c.slot >> 1) * .4), 2.0 + (c.slot >> 1) * .9, 1.4 - c.slot * .5);
}

// ---------------------------------------------------------------- update
function update(dt) {
  R.time += dt;
  const t = R.time;
  const reduced = R.reduced;
  const k = reduced ? 1 : 1 - Math.exp(-dt * 3);
  R.shownProgress = lerp(R.shownProgress, R.progress, reduced ? 1 : 1 - Math.exp(-dt * 2.5));
  const targetEnergy = R.failed ? 0.1 : clamp(0.25 + R.shownProgress * 1.5, 0, 1.8);
  R.shownEnergy = lerp(R.shownEnergy, targetEnergy, k);
  R.kick = Math.max(0, R.kick - dt * 1.6);
  R.flash = Math.max(0, R.flash - dt * 2.2);
  const energy = R.shownEnergy;

  G.backdropU.uTime.value = t; G.backdropU.uEnergy.value = energy;
  G.floorU.uTime.value = t; G.floorU.uEnergy.value = energy;
  G.coreU.uTime.value = t; G.coreU.uEnergy.value = energy; G.coreU.uFlash.value = R.flash;
  G.glyphU.uTime.value = t;
  G.glyphU.uDensity.value = R.failed ? 0.04 : clamp(0.1 + R.shownProgress * 0.9 + Math.min(R.chars / 60000, .2), 0.05, 1);
  G.glyphU.uSpeed.value = R.failed ? 0.01 : 0.05 + energy * .04;
  G.arcU.uTime.value = t; G.arcU.uProgress.value = R.shownProgress;

  const pulse = 1 + (reduced ? 0 : Math.sin(t * 3) * .04) + R.kick * .12 + R.flash * .5;
  G.coreSphere.scale.setScalar(.8 + energy * .25 * pulse);
  G.wire.rotation.y = reduced ? 0 : t * .3; G.wire.rotation.x = reduced ? 0 : t * .17;
  G.wire.material.opacity = .25 + Math.min(energy, 1) * .3;
  G.rings.forEach((r, i) => { if (!reduced) { r.rotation.x += dt * (.4 + i * .25); r.rotation.y += dt * (.3 + i * .2); } });
  G.halo.material.opacity = .15 + Math.min(energy, 1.2) * .3 + R.flash * .6;
  G.halo.scale.setScalar(5 + energy * 1.5 + R.flash * 3);

  // beams from data coming in
  if (!reduced && R.phase === 'building') {
    R.beamCredit += dt * (1.5 + R.shownProgress * 5);
    while (R.beamCredit >= 1) { R.beamCredit -= 1; spawnBeam(); }
  }
  G.beams.forEach((b) => {
    if (!b.on) return;
    b.age += dt; const f = b.age / b.life; b.u.uT.value = clamp(f, 0, 1);
    if (f >= 1) { b.on = false; b.mesh.visible = false; }
  });
  G.waves.forEach((w) => {
    if (!w.on) return;
    w.age += dt; const f = w.age / w.life; w.u.uAge.value = clamp(f, 0, 1);
    if (f >= 1) { w.on = false; w.mesh.visible = false; }
  });

  // slabs
  const revT = R.rev ? R.rev.t : -1;
  G.slabs.forEach((s, i) => {
    s.age += dt; s.u.uTime.value = t;
    s.u.uBirth.value = reduced ? 1 : clamp(s.age / 0.9, 0, 1);
    const arrive = ease(s.age / 1.2);
    let px = lerp(s.far.x, s.home.x, arrive), py = lerp(s.far.y, s.home.y, arrive), pz = lerp(s.far.z, s.home.z, arrive);
    if (!reduced) { py += Math.sin(t * .8 + i) * .08; }
    if (R.failed) { s.home.addScaledVector(s.vel, dt * .25); px = s.home.x; py = s.home.y; pz = s.home.z; }
    s.mesh.position.set(px, py, pz);
    s.mesh.lookAt(0, py, 0); s.mesh.rotateY(Math.PI);
    s.mesh.scale.setScalar(.85);
    s.u.uBars.value = lerp(s.u.uBars.value, R.stage >= 2 ? 1 : 0, k);
    s.u.uShimmer.value = lerp(s.u.uShimmer.value, R.stage >= 4 ? 1 : 0, k);
    s.u.uAlpha.value = R.failed ? lerp(s.u.uAlpha.value, .35, k) : 1;
  });

  // log cards
  G.cards.forEach((c) => {
    if (!c.on) return;
    c.age += dt;
    const born = clamp(c.age / .5, 0, 1), out = clamp((c.life - c.age) / 1.0, 0, 1);
    c.u.uAlpha.value = ease(born) * out;
    const b = c.mesh.userData.base || new THREE.Vector3();
    c.mesh.position.set(b.x, b.y + (1 - ease(born)) * -.5 + (reduced ? 0 : Math.sin(t + c.slot) * .05), b.z);
    c.mesh.lookAt(G.camera.position);
    if (c.age >= c.life) { c.on = false; c.mesh.visible = false; }
  });

  // reveal timeline
  let fov = 52;
  if (R.rev) {
    const r = R.rev; r.t += dt; const rt = r.t;
    if (rt < .2) R.flash = Math.max(R.flash, rt / .2);
    if (rt >= .15 && !r.waved) { r.waved = true; spawnWave(20, 1.4); G.addRipple(R.time, 1.5); R.burst = 1; }
    R.burst = Math.max(0, R.burst - dt * .9);
    G.glyphU.uBurst.value = R.burst;
    const fuse = ease((rt - .2) / .7), fly = ease((rt - .9) / .5);
    const fused = new THREE.Vector3(0, 0.9, 0.2);
    const toCam = G.camera.position.clone().lerp(G.core.position, .82);
    G.slabs.forEach((s) => {
      const p = s.mesh.position.clone().lerp(fused, fuse).lerp(toCam, fly);
      s.mesh.position.copy(p);
      s.mesh.lookAt(G.camera.position);
      s.mesh.scale.setScalar(lerp(.85, 2.6, fuse) * lerp(1, 1.9, fly));
      s.u.uAlpha.value = 1 - fly * .2;
    });
    fov += fly * 24;
    if (rt >= 1.5 && !r.done) { r.done = true; bridge('onRevealFinished'); }
  } else {
    G.glyphU.uBurst.value = 0;
  }

  // camera
  const flyT = reduced ? 1 : ease(R.flyIn);
  if (!reduced) R.flyIn = Math.min(1, R.flyIn + dt / 2.0);
  if (!reduced && !R.dragging) { R.orbit += dt * .06; R.yaw += R.yawVel; R.yawVel *= .94; }
  R.pitch = clamp(R.pitch, -.35, .35);
  const radius = lerp(16, 7.6, flyT) - R.kick * .6;
  const height = lerp(6, 2.3, flyT) + R.pitch * 4;
  const ang = R.orbit + R.yaw;
  const shake = reduced ? 0 : R.kick * .05;
  G.camera.position.set(
    Math.sin(ang) * radius + R.tiltX * .5 + (Math.random() - .5) * shake,
    height + R.tiltY * .35 + (Math.random() - .5) * shake,
    Math.cos(ang) * radius,
  );
  G.camera.fov = fov + R.kick * 6 + (1 - flyT) * 8;
  G.camera.updateProjectionMatrix();
  G.camera.lookAt(0, .4, 0);

  G.bloom.strength = .34 + Math.min(energy, 1.8) * .14 + R.flash * .5;
  G.post.uniforms.uAmount.value = 1 + R.kick * 1.5 + R.flash;
}

// ---------------------------------------------------------------- loop
function frame(now) {
  R.raf = requestAnimationFrame(frame);
  const dt = clamp((now - (R.last || now)) / 1000, 0, .05); R.last = now;
  update(dt);
  G.composer.render(dt);
  adapt(dt);
}

function renderOnce() {
  R.raf = 0;
  const now = performance.now();
  const dt = clamp((now - (R.last || now)) / 1000, 0, .05); R.last = now;
  update(Math.max(dt, .016));
  G.composer.render();
  // keep settling for a short time, then stop
  if (R.settle > 0) { R.settle--; R.raf = requestAnimationFrame(renderOnce); }
}

function requestRender() {
  if (!G || R.paused) return;
  if (!R.reduced) { if (!R.raf) R.raf = requestAnimationFrame(frame); return; }
  R.settle = 20;
  if (!R.raf) R.raf = requestAnimationFrame(renderOnce);
}

function adapt(dt) {
  R.ema = lerp(R.ema, dt, .05); R.frames++;
  if (R.frames % 90 !== 0) return;
  if (R.ema > .034 && G.pixelRatio > 1) setPixelRatio(Math.max(1, G.pixelRatio - .25));
  else if (R.ema < .018 && G.pixelRatio < G.pixelCap) setPixelRatio(Math.min(G.pixelCap, G.pixelRatio + .25));
}

function setPixelRatio(v) {
  G.pixelRatio = v; G.renderer.setPixelRatio(v); G.composer.setPixelRatio(v);
  G.glyphU.uScale.value = 70 * v; resize();
}

function resize() {
  if (!G) return;
  const w = window.innerWidth, h = window.innerHeight;
  G.renderer.setSize(w, h, false); G.composer.setSize(w, h);
  G.bloom.resolution.set(w, h);
  G.camera.aspect = w / h; G.camera.updateProjectionMatrix();
  R.dirty = true; requestRender();
}

// ---------------------------------------------------------------- public API
function init(themeJson, opts) {
  try {
    theme = typeof themeJson === 'string' ? JSON.parse(themeJson) : (themeJson || {});
    options = opts || {};
    Object.assign(R, freshState());
    R.reduced = !!options.reducedMotion;
    G = build(theme, options);
    on(window, 'resize', resize);
    on(document, 'visibilitychange', () => { setPaused(document.hidden); });
    on(G.canvas, 'webglcontextlost', (e) => { e.preventDefault(); bridge('onWebGLFailed'); });
    on(G.canvas, 'pointerdown', (e) => { R.dragging = true; R.px = e.clientX; R.py = e.clientY; try { G.canvas.setPointerCapture(e.pointerId); } catch (x) { /* ignore */ } });
    on(G.canvas, 'pointermove', (e) => {
      if (!R.dragging) return;
      const dx = e.clientX - R.px, dy = e.clientY - R.py; R.px = e.clientX; R.py = e.clientY;
      R.yaw -= dx * .006; R.yawVel = -dx * .0004; R.pitch = clamp(R.pitch + dy * .003, -.35, .35); R.dirty = true; requestRender();
    });
    const up = () => { R.dragging = false; };
    on(G.canvas, 'pointerup', up); on(G.canvas, 'pointercancel', up);
    on(window, 'deviceorientation', (e) => {
      if (R.reduced) return;
      R.tiltX = lerp(R.tiltX, clamp((e.gamma || 0) / 45, -1, 1), .1);
      R.tiltY = lerp(R.tiltY, clamp(((e.beta || 45) - 45) / 45, -1, 1), .1);
    });
    refreshNodes();
    R.flyIn = R.reduced ? 1 : 0;
    G.addRipple(0, 1);
    requestRender();
  } catch (err) {
    bridge('onWebGLFailed');
  }
}

function applyUpdate(d) {
  if (!G || !d) return;
  if (d.reset) {
    R.failed = false; R.rev = null; R.stage = 0; R.progress = 0; R.shownProgress = 0; R.chars = 0; R.lastChars = 0;
    G.slabs.forEach((s) => { G.slabGroup.remove(s.mesh); s.mesh.material.dispose(); }); G.slabs.length = 0;
    G.canvas.style.filter = 'none'; refreshNodes();
  }
  if (typeof d.phase === 'string') {
    R.phase = d.phase;
    if (d.phase === 'failed') failVisuals();
    if (d.phase === 'cancelled') { R.failed = false; R.progress = Math.min(R.progress, .2); }
    if (d.phase === 'building') { R.failed = false; G.canvas.style.filter = 'none'; }
  }
  if (typeof d.stage === 'number' && d.stage !== R.stage) {
    const up = d.stage > R.stage; R.stage = d.stage; refreshNodes();
    if (up && !R.reduced) { R.kick = 1; spawnWave(12, 1.2); G.addRipple(R.time, 1); }
  }
  if (typeof d.progress === 'number') R.progress = clamp(d.progress, 0, 1);
  if (typeof d.chars === 'number') R.chars = d.chars;
  if (Array.isArray(d.newSections)) d.newSections.forEach((n) => addSlab(n));
  if (Array.isArray(d.log)) d.log.forEach((line) => { addCard(line); if (!R.reduced) spawnBeam(); });
  requestRender();
}

function failVisuals() {
  R.failed = true; R.progress = Math.min(R.progress, .6);
  G.canvas.style.filter = 'grayscale(1) brightness(.85)';
}

function reveal() {
  if (!G || R.rev || R.failed) return;
  R.progress = 1;
  if (R.reduced) { setTimeout(() => bridge('onRevealFinished'), 150); return; }
  R.rev = { t: 0, waved: false, done: false };
  requestRender();
}

function setPaused(p) {
  R.paused = !!p;
  if (R.paused) { if (R.raf) cancelAnimationFrame(R.raf); R.raf = 0; R.last = 0; }
  else { R.last = 0; requestRender(); }
}

function dispose() {
  if (!G) return;
  if (R.raf) cancelAnimationFrame(R.raf); R.raf = 0;
  listeners.forEach(([t, ty, fn, o]) => { try { t.removeEventListener(ty, fn, o); } catch (e) { /* ignore */ } }); listeners = [];
  try {
    G.slabs.forEach((s) => s.mesh.material.dispose());
    G.beams.forEach((b) => b.mesh.material.dispose());
    G.disposables.forEach((o) => { try { o.dispose(); } catch (e) { /* ignore */ } });
    G.composer.passes.forEach((p) => { if (p.dispose) p.dispose(); });
    if (G.composer.dispose) G.composer.dispose();
    G.renderer.dispose(); G.renderer.forceContextLoss();
  } catch (e) { /* ignore */ }
  G = null;
}

window.Forge = { init, update: applyUpdate, reveal, fail: failVisuals, setPaused, dispose, get ready() { return !!G; } };
ready = true;
bridge('onReady');
