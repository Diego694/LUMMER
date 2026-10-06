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
  "assets/js/pages/carnet.js", "assets/js/pages/mantenimiento.js", "assets/js/alerta.js", "assets/js/pages/codigo.js", "assets/js/cola.js", "assets/js/sync.js", "assets/js/permisos.js", "assets/js/errlog.js", "assets/js/api-extra.js", "assets/js/api-aula.js", "assets/js/pages/aula.js", "assets/js/qr-seguro.js", "assets/js/modo.js", "assets/js/notificaciones.js", "assets/js/pages/personal.js", "assets/js/pages/perfil.js", "assets/js/calendario.js", "assets/js/promocion.js", "assets/js/fusion.js", "assets/js/riesgo.js",
  "assets/js/pages/calendario.js", "assets/js/pages/periodos.js", "assets/js/pages/alertas.js", "assets/js/pages/historial.js", "assets/js/pages/migrar.js", "assets/js/pages/quiosco.js", "assets/js/pages/solicitudes.js", "assets/js/respaldo-offline.js", "assets/js/pages/offline.js", "assets/js/compat.js", "privacidad.html", "entorno.html",
  "assets/js/pages/avisos.js", "assets/js/pages/reportes.js", "assets/js/pages/cursos.js", "assets/js/pages/sistema.js",
  "assets/js/marca.js", "assets/js/pages/instituciones.js", "assets/js/pages/instituto.js",
  // Portal del estudiante
  "estudiante/", "estudiante/manifest.webmanifest", "apoderado/", "assets/js/apoderado/main.js", "assets/css/estudiante.css", "assets/css/diseno.css",
  "assets/js/estudiante/main.js", "assets/js/estudiante/api.js", "assets/js/estudiante/aula.js",
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
    if (req.mode === "navigate") {
      const portalEstudiante = new URL(req.url).pathname.includes("/estudiante");
      return (await cache.match(portalEstudiante ? "estudiante/" : "index.html")) || Response.error();
    }
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
