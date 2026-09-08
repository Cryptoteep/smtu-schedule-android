/*
 * Кэш оболочки приложения: страница открывается и без сети, а расписание
 * приходит из localStorage (его пишет app.js).
 */
const CACHE = 'smtu-shell-v7';
const SHELL = ['./', 'index.html', 'app.js?v=7', 'parser.js?v=7', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', e => {
  const url = new URL(e.request.url);
  if (e.request.method !== 'GET') return;
  // страницы расписания всегда берём из сети: их кэширует само приложение
  if (url.pathname.includes('/data/')) return;   // расписание кэширует само приложение
  // сеть вперёд, кэш — запасной путь: так обновление приходит сразу,
  // а без сети приложение всё равно открывается
  e.respondWith(
    fetch(e.request).then(res => {
      if (res.ok && url.origin === location.origin)
        caches.open(CACHE).then(c => c.put(e.request, res.clone()));
      return res;
    }).catch(() => caches.match(e.request).then(hit => hit || caches.match('index.html')))
  );
});
