// Programa de escritorio "Registro Académico" (Windows). Abre la web empaquetada (app/) con todas sus librerías
// incluidas, así que arranca sin internet. El modo online/local lo decide la propia web (assets/js/modo.js);
// los datos de cada modo viven en el perfil del programa (%APPDATA%) y no se borran al actualizar.
const { app, BrowserWindow, Menu, protocol, session, shell } = require("electron");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");

const SMOKE = process.argv.includes("--smoke");
const RAIZ = path.join(__dirname, "app");
const ORIGEN = "app://local";

if (SMOKE) app.setPath("userData", fs.mkdtempSync(path.join(os.tmpdir(), "ra-smoke-")));
protocol.registerSchemesAsPrivileged([
  { scheme: "app", privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true } },
]);
if (!SMOKE && !app.requestSingleInstanceLock()) app.quit();

let vendor = {};
try { vendor = JSON.parse(fs.readFileSync(path.join(RAIZ, "vendor", "map.json"), "utf8")); } catch { /* sin vendor: se usa la red */ }

/** El HTML apunta a CDN; aquí se reescribe a las copias locales (y se quitan las fuentes remotas). */
function htmlOffline(texto) {
  for (const [url, local] of Object.entries(vendor)) texto = texto.split(url).join(local);
  return texto
    .replace(/<link[^>]+(fonts\.googleapis\.com|fonts\.gstatic\.com)[^>]*>/g, "");
}

const MIME = {
  ".js": "text/javascript; charset=utf-8", ".mjs": "text/javascript; charset=utf-8", ".css": "text/css; charset=utf-8",
  ".json": "application/json", ".webmanifest": "application/manifest+json", ".png": "image/png", ".svg": "image/svg+xml",
  ".ico": "image/x-icon", ".jpg": "image/jpeg", ".woff2": "font/woff2", ".map": "application/json",
};

function servir(req) {
  const url = new URL(req.url);
  let rel = decodeURIComponent(url.pathname);
  if (rel === "/" || rel === "") rel = "/index.html";
  const archivo = path.normalize(path.join(RAIZ, rel));
  if (!archivo.startsWith(RAIZ + path.sep) || !fs.existsSync(archivo) || fs.statSync(archivo).isDirectory()) return new Response("No encontrado", { status: 404 });
  if (archivo.endsWith(".html")) {
    return new Response(htmlOffline(fs.readFileSync(archivo, "utf8")), { headers: { "content-type": "text/html; charset=utf-8" } });
  }
  // Se lee del disco directamente (net.fetch de file:// pasaría por la red y fallaría con la red simulada caída).
  const tipo = MIME[path.extname(archivo).toLowerCase()] || "application/octet-stream";
  return new Response(fs.readFileSync(archivo), { headers: { "content-type": tipo } });
}

function crearVentana() {
  const win = new BrowserWindow({
    width: 1360, height: 860, minWidth: 980, minHeight: 640, show: false, backgroundColor: "#F3F5F9",
    title: "Registro Académico", icon: path.join(__dirname, "build", "icon.png"),
    webPreferences: { preload: path.join(__dirname, "preload.js"), contextIsolation: true, nodeIntegration: false, sandbox: true },
  });
  Menu.setApplicationMenu(null);
  win.once("ready-to-show", () => win.show());
  // Enlaces externos (WhatsApp, GitHub…) al navegador del sistema; la app nunca navega fuera de sí misma.
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https?:/.test(url)) shell.openExternal(url); return { action: "deny" }; });
  win.webContents.on("will-navigate", (e, url) => { if (!url.startsWith(ORIGEN)) { e.preventDefault(); if (/^https?:/.test(url)) shell.openExternal(url); } });
  if (SMOKE) {
    win.webContents.on("console-message", (_e, nivel, msg) => { if (nivel >= 2) console.log("RENDERER", msg); });
    win.webContents.on("did-fail-load", (_e, code, desc, url) => console.log("FALLO CARGA", code, desc, url));
  }
  win.loadURL(`${ORIGEN}/index.html`);
  return win;
}

async function pruebaAutomatica(win) {
  const esperar = (ms) => new Promise((r) => setTimeout(r, ms));
  const siguienteCarga = () => new Promise((r) => win.webContents.once("did-finish-load", r));
  const evaluar = (js) => win.webContents.executeJavaScript(js, true);
  const res = { ok: false, pasos: [] };
  const paso = (nombre, cumple) => { res.pasos.push({ nombre, cumple: !!cumple }); };
  try {
    if (win.webContents.isLoading()) await siguienteCarga();
    await esperar(1500);
    paso("web cargada sin internet", await evaluar("document.title.includes('Registro')"));
    paso("librerías incluidas (Chart, QRCode, jsQR, jsPDF, Papa, supabase)", await evaluar("[typeof Chart, typeof QRCode, typeof jsQR, typeof jspdf, typeof Papa, typeof supabase].every(t => t !== 'undefined')"));
    paso("expone el programa de escritorio", await evaluar("!!window.escritorio"));
    paso("muestra el selector de modo", await evaluar("!!document.getElementById('modo-switch')"));
    let carga = siguienteCarga();
    await evaluar("localStorage.setItem('ra-modo','local'); location.reload()");
    await carga;
    await esperar(2500);
    paso("modo local entra sin cuenta", await evaluar("!document.getElementById('app').hidden"));
    paso("insignia 'Modo local'", await evaluar("document.getElementById('mode-badge').textContent === 'Modo local'"));
    // Guardar un dato local y comprobar que sobrevive al cambio de modo (clave aparte para cada modo).
    carga = siguienteCarga();
    await evaluar("(() => { const d = JSON.parse(localStorage.getItem('ra-local-db-v1')); d.niveles.push({id:'x1', colegio_id:d.colegio.id, nombre:'PRUEBA'}); localStorage.setItem('ra-local-db-v1', JSON.stringify(d)); localStorage.setItem('ra-modo','online'); location.reload(); })()");
    await carga;
    await esperar(1500);
    paso("modo online muestra el inicio de sesión", await evaluar("!document.getElementById('login-screen').hidden && !!document.getElementById('login-email')"));
    paso("los datos locales se conservan al cambiar de modo", await evaluar("JSON.parse(localStorage.getItem('ra-local-db-v1')).niveles.some(n => n.nombre === 'PRUEBA')"));
    res.ok = res.pasos.every((p) => p.cumple);
  } catch (e) { res.error = String(e && e.message || e); }
  const texto = "SMOKE " + JSON.stringify(res, null, 1);
  console.log(texto);
  if (process.env.SMOKE_OUT) fs.writeFileSync(process.env.SMOKE_OUT, texto);
  app.exit(res.ok ? 0 : 1);
}

app.whenReady().then(async () => {
  protocol.handle("app", servir);
  // La prueba corre SIN red: demuestra que el .exe no depende de internet.
  if (SMOKE) await session.defaultSession.enableNetworkEmulation({ offline: true });
  // Cámara (escáner QR) solo para la propia app.
  session.defaultSession.setPermissionRequestHandler((wc, permiso, cb) => cb(wc.getURL().startsWith(ORIGEN) && ["media", "fullscreen", "clipboard-sanitized-write"].includes(permiso)));
  session.defaultSession.setPermissionCheckHandler((wc, permiso) => ["media", "fullscreen", "clipboard-sanitized-write"].includes(permiso));
  const win = crearVentana();
  if (SMOKE) { win.show(); pruebaAutomatica(win); setTimeout(() => { console.log("SMOKE tiempo agotado"); app.exit(2); }, 90000); }
  app.on("second-instance", () => { if (win.isMinimized()) win.restore(); win.focus(); });
});
app.on("window-all-closed", () => app.quit());
