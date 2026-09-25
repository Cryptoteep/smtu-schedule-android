/*
 * Кэш оболочки приложения: страница открывается и без сети, а расписание
 * приходит из localStorage (его пишет app.js).
 */
const CACHE = 'smtu-shell-v12';
const NETWORK_GRACE_MS = 4000;
const SHELL = ['./', 'index.html', 'app.js?v=12', 'parser.js?v=12', 'manifest.webmanifest', 'icon-192.png', 'icon-512.png'];

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
  // Сеть вперёд, кэш — запасной путь: так обновление приходит сразу, а без
  // сети приложение всё равно открывается. Но «сеть недоступна» и «сеть
  // висит» — разные вещи: под VPN или в метро запрос может не падать, а ждать
  // минуту. Поэтому, когда копия есть, у сети фора 4 секунды, дальше
  // открываем копию, а сеть в фоне обновит кэш к следующему разу.
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    const network = fetch(e.request).then(res => {
      if (res.ok && url.origin === location.origin) cache.put(e.request, res.clone());
      return res;
    });
    const copy = await cache.match(e.request);
    if (!copy)
      return network.catch(() => cache.match('index.html').then(hit => hit || Response.error()));
    const late = new Promise(resolve => setTimeout(() => resolve(copy), NETWORK_GRACE_MS));
    return Promise.race([network.catch(() => copy), late]);
  })());
});
