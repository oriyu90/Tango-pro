// The staging script replaces this token with a content-derived build ID so a
// new worker is installed whenever any published app asset changes.
const CACHE = "tango-pro-web-__BUILD_ID__";
const BASE = "/projects/tango-pro/web/";
const APP_SHELL = [BASE, `${BASE}index.html`, `${BASE}manifest.webmanifest`, `${BASE}browser-bridge.js`];

self.addEventListener("install", event => {
  event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    try {
      const response = await fetch(`${BASE}build-info.json`, { cache: "no-store" });
      if (!response.ok) throw new Error(`build-info HTTP ${response.status}`);
      const build = await response.json();
      const assets = Array.isArray(build.assets) ? build.assets.map(path => `${BASE}${path}`) : [];
      const queryAssets = Array.isArray(build.queryAssets) ? build.queryAssets.flatMap(path => [
        `${BASE}${path}?vfs=opfs`,
        `${BASE}${path}?vfs=opfs-wl`
      ]) : [];
      await cache.addAll([...new Set([...APP_SHELL, `${BASE}build-info.json`, ...assets, ...queryAssets])]);
    } catch (error) {
      console.warn("Full offline precache was unavailable; caching the app shell only.", error);
      await cache.addAll(APP_SHELL);
    }
  })());
  self.skipWaiting();
});

self.addEventListener("activate", event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(key => key !== CACHE).map(key => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", event => {
  if (event.request.method !== "GET" || !event.request.url.startsWith(self.location.origin + BASE)) return;
  event.respondWith(
    fetch(event.request).then(response => {
      if (response.ok) caches.open(CACHE).then(cache => cache.put(event.request, response.clone()));
      return response;
    }).catch(() => caches.match(event.request).then(cached =>
      cached || caches.match(event.request, { ignoreSearch: true }).then(fallback => fallback || caches.match(BASE))
    ))
  );
});
