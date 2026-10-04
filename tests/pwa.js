// Pruebas de sw.js (lógica real, entorno simulado) y del puente Android (AndroidBridge simulado).
const out = [];
const check = async (name, fn) => {
  try { await fn(); out.push({ name, ok: true }); } catch (e) { out.push({ name, ok: false, msg: e.message }); }
};
const assert = (c, m = "aserción fallida") => { if (!c) throw new Error(m); };

/* ---- Entorno simulado para ejecutar sw.js tal cual ---- */
async function cargarSW({ red }) {
  const src = await (await fetch("../sw.js")).text();
  const listeners = {};
  const store = new Map();
  const cache = {
    put: async (req, res) => store.set(typeof req === "string" ? req : req.url, res),
    match: async (req) => store.get(typeof req === "string" ? new URL(req, "http://app.test/").href : req.url),
    addAll: async () => {},
  };
  const fakeCaches = { open: async () => cache, keys: async () => [], delete: async () => true };
  const self = { location: { origin: "http://app.test" }, addEventListener: (t, f) => (listeners[t] = f), skipWaiting: () => {}, clients: { claim: () => {} } };
  const fetchSim = async (url, opts) => red(String(url), opts);
  new Function("self", "caches", "fetch", "Response", "URL", "AbortController", "setTimeout", "clearTimeout", src)(
    self, fakeCaches, fetchSim, Response, URL, AbortController, setTimeout, clearTimeout);
  const pedir = async (url, { method = "GET", mode = "cors" } = {}) => {
    let respondido;
    listeners.fetch({ request: { url, method, mode }, respondWith: (p) => (respondido = p) });
    return respondido ? await respondido : undefined; // undefined = no interceptado
  };
  return { pedir, store };
}
const ok200 = (t) => new Response(t, { status: 200 });

await check("SW: archivos propios → red primero (devuelve lo nuevo y lo guarda)", async () => {
  const sw = await cargarSW({ red: () => ok200("version-nueva") });
  const r = await sw.pedir("http://app.test/assets/js/config.js");
  assert((await r.text()) === "version-nueva" && sw.store.size === 1);
});

await check("SW: revalida contra el servidor (cache:'no-cache') para no servir JS viejo", async () => {
  let opts;
  const sw = await cargarSW({ red: (u, o) => { opts = o; return ok200("x"); } });
  await sw.pedir("http://app.test/index.html");
  assert(opts.cache === "no-cache", JSON.stringify(opts));
});

await check("SW: sin red sirve la copia en cache (la app abre sin conexión)", async () => {
  let online = true;
  const sw = await cargarSW({ red: () => { if (!online) throw new TypeError("offline"); return ok200("copia-guardada"); } });
  await sw.pedir("http://app.test/assets/js/main.js");
  online = false;
  assert((await (await sw.pedir("http://app.test/assets/js/main.js")).text()) === "copia-guardada");
});

await check("SW: navegación sin red cae a index.html cacheado", async () => {
  let online = true;
  const sw = await cargarSW({ red: () => { if (!online) throw new TypeError("offline"); return ok200("<html>shell</html>"); } });
  await sw.pedir("http://app.test/index.html");
  online = false;
  const r = await sw.pedir("http://app.test/otra-ruta", { mode: "navigate" });
  assert((await r.text()) === "<html>shell</html>");
});

await check("SW: CDN → cache primero (no vuelve a la red si ya lo tiene)", async () => {
  let n = 0;
  const sw = await cargarSW({ red: () => { n++; return ok200("lib"); } });
  await sw.pedir("https://cdn.jsdelivr.net/npm/x.js");
  await sw.pedir("https://cdn.jsdelivr.net/npm/x.js");
  assert(n === 1, `peticiones de red: ${n}`);
});

await check("SW: NO intercepta Supabase, otros orígenes ni peticiones no-GET", async () => {
  const sw = await cargarSW({ red: () => ok200("x") });
  assert((await sw.pedir("https://abc.supabase.co/rest/v1/alumnos")) === undefined, "interceptó Supabase");
  assert((await sw.pedir("https://ejemplo.com/a.js")) === undefined, "interceptó un origen ajeno");
  assert((await sw.pedir("http://app.test/assets/js/api.js", { method: "POST" })) === undefined, "interceptó un POST");
});

/* ---- Puente Android simulado ---- */
await check("Puente: downloadFile entrega el archivo a AndroidBridge.saveFile en base64", async () => {
  const { downloadFile, toCSV } = await import("../assets/js/utils.js");
  let got;
  globalThis.AndroidBridge = { saveFile: (n, m, b) => { got = { n, m, b }; return true; } };
  const res = await downloadFile("datos.csv", toCSV([{ a: "ñ" }], [{ label: "A", key: "a" }]));
  delete globalThis.AndroidBridge;
  const bytes = Uint8Array.from(atob(got.b), (c) => c.charCodeAt(0));
  const texto = new TextDecoder().decode(bytes);
  assert(res === true && got.n === "datos.csv" && /^text\/csv/.test(got.m) && texto.includes("ñ"), JSON.stringify({ n: got.n, m: got.m, texto }));
});

await check("Puente: sin AndroidBridge la descarga usa el navegador (<a download>)", async () => {
  const { downloadFile } = await import("../assets/js/utils.js");
  let clicked = null;
  const orig = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () { clicked = this.download; };
  const res = await downloadFile("x.csv", "a,b");
  HTMLAnchorElement.prototype.click = orig;
  assert(res === true && clicked === "x.csv", String(clicked));
});

/* ---- QR dinámico ---- */
const { generarQR, verificarQR, ventana, VENTANA_MS } = await import("../assets/js/qr-seguro.js");
const alumnoQR = { id: "1", codigo: "e1234567890", qr_secreto: "s3creto-de-prueba" };
const buscar = (c) => (c === alumnoQR.codigo ? alumnoQR : null);
const T0 = Date.UTC(2026, 9, 5, 12, 0, 0);

await check("QR dinámico: un QR recién generado se verifica y la firma depende del secreto", async () => {
  const q = await generarQR(alumnoQR.codigo, alumnoQR.qr_secreto, T0);
  assert(q.startsWith(alumnoQR.codigo + ".") && q.split(".").length === 3, q);
  const r = await verificarQR(q, buscar, T0 + 5000);
  assert(r.ok === true && r.alumno.id === "1", JSON.stringify(r));
  const otro = await generarQR(alumnoQR.codigo, "otro-secreto", T0);
  assert((await verificarQR(otro, buscar, T0)).motivo === "firma", "un secreto distinto debe fallar");
});

await check("QR dinámico: vence (captura de pantalla inútil) pero tolera pequeños desfases", async () => {
  const q = await generarQR(alumnoQR.codigo, alumnoQR.qr_secreto, T0);
  assert((await verificarQR(q, buscar, T0 + 60000)).ok === true, "a 60 s todavía vale (2 ventanas)");
  const tarde = await verificarQR(q, buscar, T0 + 5 * VENTANA_MS);
  assert(tarde.ok === false && tarde.motivo === "vencido", JSON.stringify(tarde));
  assert((await verificarQR(q, buscar, T0 - 5 * VENTANA_MS)).motivo === "vencido", "tampoco sirve uno 'del futuro'");
});

await check("QR dinámico: manipular el código, la ventana o la firma lo invalida; los estáticos pasan", async () => {
  const q = await generarQR(alumnoQR.codigo, alumnoQR.qr_secreto, T0);
  const [c, t, s] = q.split(".");
  assert((await verificarQR(`${c}.${t}.${s.replace(/./, (x) => (x === "a" ? "b" : "a"))}`, buscar, T0)).ok === false, "firma alterada");
  assert((await verificarQR(`${c}.${(ventana(T0) + 1).toString(36)}.${s}`, buscar, T0)).motivo === "firma", "ventana alterada con la firma vieja");
  assert((await verificarQR("noexiste.abc.123", buscar, T0)).motivo === "desconocido");
  assert((await verificarQR(alumnoQR.codigo, buscar, T0)).estatico === true && (await verificarQR("lo-que-sea", buscar, T0)).estatico === true);
  const sinSecreto = { ...alumnoQR, qr_secreto: undefined };
  assert((await verificarQR(q, (x) => (x === c ? sinSecreto : null), T0)).motivo === "sinsecreto");
});

const fail = out.filter((r) => !r.ok);
document.body.insertAdjacentHTML("beforeend", `<h2>PWA y puente Android (simulados)</h2><p id="summary-pwa" data-failed="${fail.length}">${out.length - fail.length}/${out.length} correctos</p><ul>${out.map((r) => `<li style="color:${r.ok ? "#127a4f" : "#b3261e"}">${r.ok ? "✔" : "✘"} ${r.name}${r.msg ? " — " + r.msg : ""}</li>`).join("")}</ul>`);
