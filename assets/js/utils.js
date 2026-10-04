// Utilidades puras (sin dependencias del DOM salvo downloadFile) — probadas en tests/.

export function esc(s) {
  return (s ?? "").toString().replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const pad = (n) => String(n).padStart(2, "0");

/** Fecha local en formato YYYY-MM-DD (evita el desfase UTC de toISOString). */
export function dateStr(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
/* ---------- Reloj confiable ----------
 * La asistencia NUNCA debe depender del reloj ni de la zona del teléfono: se usa la hora del servidor
 * (sincronizada al iniciar y periódicamente) y la zona horaria del instituto (Lima, sin horario de verano).
 * Mientras la página está abierta se avanza con performance.now(), que no se altera si cambian el reloj del equipo. */
export const ZONA_HORARIA = "America/Lima";
let ancla = null;   // { servidor, perf }
let desfase = 0;    // ms (servidor − dispositivo); se restaura de la última sincronización si no hay red
try { desfase = Number(JSON.parse(localStorage.getItem("ra-reloj"))?.desfase) || 0; } catch { /* sin storage (Node) */ }

export function sincronizarReloj(servidorMs) {
  ancla = { servidor: servidorMs, perf: performance.now() };
  desfase = servidorMs - Date.now();
  try { localStorage.setItem("ra-reloj", JSON.stringify({ desfase, en: Date.now() })); } catch { /* sin storage */ }
}
export const desfaseReloj = () => desfase;
export const ahora = () => new Date(ancla ? ancla.servidor + (performance.now() - ancla.perf) : Date.now() + desfase);

function partesZona(d, zona = ZONA_HORARIA) {
  const p = {};
  new Intl.DateTimeFormat("en-CA", { timeZone: zona, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })
    .formatToParts(d).forEach((x) => { p[x.type] = x.value; });
  return p;
}
/** Fecha YYYY-MM-DD en la zona del instituto. */
export const fechaZona = (d = ahora()) => { const p = partesZona(d); return `${p.year}-${p.month}-${p.day}`; };
/** Hora HH:MM (24 h) en la zona del instituto. */
export const horaZona = (d = ahora()) => { const p = partesZona(d); return `${p.hour}:${p.minute}`; };
export const todayStr = () => fechaZona(ahora());

/** ¿El error es de red/tiempo de espera (y por tanto reintentable sin perder datos)? */
export const esErrorRed = (e) => !e?.code && /fetch|network|abort|timeout|timed out|load failed|conexi/i.test(String(e?.message || e || ""));
/** ¿El error indica sesión vencida o sin permisos de sesión? */
export const esErrorSesion = (e) => /jwt|expired|not authenticated|no autenticado|invalid token|401/i.test(String(e?.message || "")) || e?.status === 401;

export function parseDate(str) {
  const [y, m, d] = str.split("-").map(Number);
  return new Date(y, m - 1, d);
}
export function addDays(str, n) {
  const d = parseDate(str);
  d.setDate(d.getDate() + n);
  return dateStr(d);
}
export function isWeekend(str) {
  const w = parseDate(str).getDay();
  return w === 0 || w === 6;
}
/** Últimos n días hábiles (lun-vie) terminando en `hasta` (inclusive si es hábil), orden ascendente. */
export function lastWeekdays(n, hasta = todayStr()) {
  const out = [];
  let cur = hasta;
  while (out.length < n) {
    if (!isWeekend(cur)) out.unshift(cur);
    cur = addDays(cur, -1);
  }
  return out;
}
/** Hora actual HH:MM según el reloj confiable y la zona del instituto. */
/** Días hábiles (lun-vie) de un mes "YYYY-MM", sin pasar de `hasta` (YYYY-MM-DD). */
export function diasHabilesDelMes(mes, hasta = todayStr()) {
  const [y, m] = mes.split("-").map(Number);
  const ultimo = new Date(y, m, 0).getDate();
  const out = [];
  for (let d = 1; d <= ultimo; d++) {
    const f = `${mes}-${String(d).padStart(2, "0")}`;
    if (f > hasta) break;
    if (!isWeekend(f)) out.push(f);
  }
  return out;
}

export function nowHHMM(d = ahora()) {
  return horaZona(d);
}
export function fmtDate(str, opts = { day: "2-digit", month: "short", year: "numeric" }) {
  return parseDate(str).toLocaleDateString("es-PE", opts).replace(".", "");
}
export function fmtDay(str) {
  return parseDate(str).toLocaleDateString("es-PE", { weekday: "short", day: "2-digit" }).replace(".", "");
}
export function greeting(d = new Date()) {
  const h = d.getHours();
  return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
}
export function initials(name) {
  return (name || "?").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
}
export function debounce(fn, ms = 200) {
  let t;
  return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}
export const pct = (num, den) => (den > 0 ? Math.round((num / den) * 100) : 0);

const enmascarar = (w) => (w.length <= 2 ? w[0] + "*" : w.slice(0, 2) + "*".repeat(Math.min(w.length - 2, 6)));

/**
 * Nombre para mostrar con privacidad: nombres completos y apellidos parcialmente censurados
 * ("Lucía Quispe Flores" → "Lucía Qu**** Fl****"). Usa nombres/apellidos si existen; si no, deduce:
 * con 3+ palabras los dos últimos son apellidos; con 2, el último.
 */
export function censurarNombre(alumno) {
  let nombres = (alumno?.nombres || "").trim();
  let apellidos = (alumno?.apellidos || "").trim();
  if (!nombres || !apellidos) {
    const t = (alumno?.nombre || "").trim().split(/\s+/).filter(Boolean);
    if (t.length >= 3) { nombres = t.slice(0, -2).join(" "); apellidos = t.slice(-2).join(" "); }
    else if (t.length === 2) { nombres = t[0]; apellidos = t[1]; }
    else { nombres = t[0] || ""; apellidos = ""; }
  }
  const ap = apellidos.split(/\s+/).filter(Boolean).map(enmascarar).join(" ");
  return [nombres, ap].filter(Boolean).join(" ");
}

/** Ciclos válidos del instituto: del I al VI. */
export const CICLOS = ["I", "II", "III", "IV", "V", "VI"];

/** Nombre canónico de un ciclo/salón: "APSTI · IV CICLO" o "APSTI · IV CICLO · SECCIÓN A". */
export function nombreCiclo(carrera, ciclo, seccion = "") {
  return [carrera, `${ciclo} CICLO`, seccion ? `SECCIÓN ${seccion}` : ""].filter(Boolean).join(" · ");
}

/** Extrae ciclo (I–VI) y sección de un nombre. Nombres antiguos o libres devuelven ciclo = null. */
export function parsearCiclo(nombre) {
  const c = /\b(VI|IV|V|III|II|I)\s+CICLO\b/i.exec(nombre || "");
  const s = /SECCI[ÓO]N\s+([A-Z0-9]+)/i.exec(nombre || "");
  return { ciclo: c ? c[1].toUpperCase() : null, seccion: s ? s[1].toUpperCase() : "" };
}

/** Comparador de nombres de ciclo: I → VI, luego por salón; los nombres libres van al final, por orden alfabético. */
export function compararCiclos(a, b) {
  const pa = parsearCiclo(a), pb = parsearCiclo(b);
  const ia = pa.ciclo ? CICLOS.indexOf(pa.ciclo) : 99, ib = pb.ciclo ? CICLOS.indexOf(pb.ciclo) : 99;
  return ia - ib || pa.seccion.localeCompare(pb.seccion, "es") || String(a).localeCompare(String(b), "es", { numeric: true });
}

/** Quita el prefijo "CARRERA · " cuando ya se sabe la carrera (selectores filtrados por carrera). */
export function cicloCorto(nombre, carrera) {
  const pre = `${carrera} · `;
  return carrera && (nombre || "").toUpperCase().startsWith(pre.toUpperCase()) ? nombre.slice(pre.length) : nombre;
}

/**
 * Etiqueta legible "Carrera · Ciclo". Si el nombre del ciclo ya empieza con la carrera
 * ("MECANICA ELECTRICA" + "MECANICA ELECTRICA I") se muestra solo el ciclo, sin repetirla.
 */
export function etiquetaCiclo(carrera, ciclo) {
  if (!carrera) return ciclo || "";
  if (!ciclo) return carrera;
  return norm(ciclo).startsWith(norm(carrera)) ? ciclo : `${carrera} · ${ciclo}`;
}

/** Normaliza texto para búsqueda sin tildes ni mayúsculas. */
export function norm(s) {
  return (s ?? "").toString().normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/** Serializa filas a CSV (RFC 4180) con BOM para que Excel respete UTF-8. */
export function toCSV(rows, columns) {
  const q = (v) => {
    const s = (v ?? "").toString();
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const head = columns.map((c) => q(c.label)).join(",");
  const body = rows.map((r) => columns.map((c) => q(typeof c.value === "function" ? c.value(r) : r[c.key])).join(","));
  return "﻿" + [head, ...body].join("\r\n");
}

function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result).split(",")[1] || "");
    r.onerror = () => reject(r.error);
    r.readAsDataURL(blob);
  });
}

/**
 * Descarga un archivo. En el navegador usa <a download>; dentro del APK (WebView) las descargas de blobs no
 * funcionan, así que se entrega al puente nativo `AndroidBridge.saveFile`, que lo guarda en Descargas.
 */
export async function downloadFile(filename, content, type = "text/csv;charset=utf-8;") {
  const blob = content instanceof Blob ? content : new Blob([content], { type });
  const bridge = globalThis.AndroidBridge;
  if (bridge?.saveFile) return bridge.saveFile(filename, blob.type || type, await blobToBase64(blob));
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
  return true;
}

export const uid = () =>
  (globalThis.crypto?.randomUUID ? crypto.randomUUID() : "id-" + Date.now().toString(36) + Math.random().toString(36).slice(2, 10));

/** Recorta al centro en cuadrado y reduce a `lado`×`lado` JPEG (~30 KB): fotos de perfil ligeras. */
export async function archivoACuadrado(file, lado = 400) {
  if (!/^image\//.test(file.type)) throw new Error("El archivo debe ser una imagen.");
  const dibujar = (fuente, w, h) => {
    const m = Math.min(w, h), c = document.createElement("canvas");
    c.width = c.height = lado;
    c.getContext("2d").drawImage(fuente, (w - m) / 2, (h - m) / 2, m, m, 0, 0, lado, lado);
    return new Promise((res) => c.toBlob(res, "image/jpeg", 0.85));
  };
  if (globalThis.createImageBitmap) {
    const bm = await createImageBitmap(file, { imageOrientation: "from-image" });
    const b = await dibujar(bm, bm.width, bm.height); bm.close?.(); return b;
  }
  const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = URL.createObjectURL(file); });
  return dibujar(img, img.naturalWidth, img.naturalHeight);
}
