// Service worker: la app abre sin conexión y SIEMPRE intenta traer la versión más nueva primero.
//  - Archivos propios (HTML/JS/CSS/config): red primero (revalida contra el servidor) → cache solo si no hay red.
//    Así cada cambio publicado (p. ej. la conexión a la base de datos en config.js) llega solo, sin reinstalar el APK.
//  - Librerías de CDN (versión fijada): cache primero.
//  - Cualquier otro origen (Supabase API, etc.): no se intercepta jamás.
const CACHE = "ra-shell-v1";
const SHELL = [
  "./", "index.html", "manifest.webmanifest", "assets/css/styles.css", "assets/icons/icon-192.png",
  "assets/js/theme-init.js", "assets/js/main.js", "assets/js/config.js", "assets/js/api.js", "assets/js/demo-data.js",
  "assets/js/state.js", "assets/js/stats.js", "assets/js/ui.js", "assets/js/utils.js",
  "assets/js/pages/dashboard.js", "assets/js/pages/registro.js", "assets/js/pages/consultas.js",
  "assets/js/pages/carnet.js", "assets/js/pages/mantenimiento.js",
];
const CDN = ["cdn.jsdelivr.net", "cdnjs.cloudflare.com", "fonts.googleapis.com", "fonts.gstatic.com"];

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(SHELL)).then(() => self.skipWaiting()));
});
self.addEventListener("activate", (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => self.clients.claim()));
});

async function redPrimero(req) {
  const cache = await caches.open(CACHE);
  try {
    const ctl = new AbortController();
    const t = setTimeout(() => ctl.abort(), 5000); // red lenta → cae al cache en vez de colgarse
    const res = await fetch(req.url, { cache: "no-cache", credentials: "same-origin", signal: ctl.signal });
    clearTimeout(t);
    if (res.ok) cache.put(req, res.clone());
    return res;
  } catch {
    const hit = await cache.match(req, { ignoreSearch: true });
    if (hit) return hit;
    if (req.mode === "navigate") return (await cache.match("index.html")) || Response.error();
    return Response.error();
  }
}
async function cachePrimero(req) {
  const cache = await caches.open(CACHE);
  const hit = await cache.match(req);
  if (hit) return hit;
  const res = await fetch(req);
  if (res.ok || res.type === "opaque") cache.put(req, res.clone());
  return res;
}

self.addEventListener("fetch", (e) => {
  const req = e.request;
  if (req.method !== "GET") return;
  const url = new URL(req.url);
  if (url.origin === self.location.origin) e.respondWith(redPrimero(req));
  else if (CDN.includes(url.hostname)) e.respondWith(cachePrimero(req));
});
