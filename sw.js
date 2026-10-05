// 커피트럭 포스 — 오프라인 실행용 서비스 워커
// 앱 파일: 인터넷 되면 최신 버전, 안 되면 기기에 저장된 버전으로 실행
// 서버 연결 모듈(Firebase): 한 번 받아두면 기기에 저장해서 오프라인에서도 사용
const CACHE = 'coffee-pos-v1';
const CORE = ['./', './index.html', './manifest.webmanifest', './icon-192.png', './icon-512.png'];
const FB = 'https://www.gstatic.com/firebasejs/12.19.0/';
const FB_FILES = ['firebase-app.js', 'firebase-auth.js', 'firebase-firestore.js'].map(f => FB + f);

self.addEventListener('install', e => {
  e.waitUntil((async () => {
    const c = await caches.open(CACHE);
    await c.addAll(CORE);
    await Promise.all(FB_FILES.map(u => fetch(u, { mode: 'cors' }).then(r => r.ok && c.put(u, r)).catch(() => {})));
    await self.skipWaiting();
  })());
});

self.addEventListener('activate', e => {
  e.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k)));
    await self.clients.claim();
  })());
});

function withTimeout(p, ms) { return Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), ms))]); }

self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET') return;
  const url = new URL(req.url);
  if (url.origin === self.location.origin) {
    const key = req.mode === 'navigate' ? './index.html' : req;
    e.respondWith((async () => {
      try {
        const res = await withTimeout(fetch(req), 5000);
        if (res && res.ok) { const cp = res.clone(); caches.open(CACHE).then(c => c.put(key, cp)); }
        return res;
      } catch (err) {
        const hit = await caches.match(key, { ignoreSearch: true });
        return hit || caches.match('./index.html');
      }
    })());
    return;
  }
  if (req.url.startsWith(FB)) {
    e.respondWith(caches.match(req).then(hit => hit || fetch(req).then(res => {
      if (res && res.ok) { const cp = res.clone(); caches.open(CACHE).then(c => c.put(req, cp)); }
      return res;
    })));
  }
});
