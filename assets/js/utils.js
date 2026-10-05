// @ts-check
// Utilidades puras (sin dependencias del DOM salvo downloadFile) — probadas en tests/.

/**
 * @param {string | number | null | undefined} [s]
 * @returns {string}
 */
export function esc(s) {
  /** @type {Record<string, string>} */
  const mapa = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
  return (s ?? "").toString().replace(/[&<>"']/g, (c) => mapa[c] || c);
}

/**
 * @param {number | string} n
 * @returns {string}
 */
const pad = (n) => String(n).padStart(2, "0");

/**
 * Fecha local en formato YYYY-MM-DD (evita el desfase UTC de toISOString).
 * @param {Date} [d]
 * @returns {string}
 */
export function dateStr(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/* ---------- Reloj confiable ----------
 * La asistencia NUNCA debe depender del reloj ni de la zona del teléfono: se usa la hora del servidor
 * (sincronizada al iniciar y periódicamente) y la zona horaria del instituto (Lima, sin horario de verano).
 * Mientras la página está abierta se avanza con performance.now(), que no se altera si cambian el reloj del equipo. */
export const ZONA_HORARIA = "America/Lima";
/** @type {{ servidor: number, perf: number } | null} */
let ancla = null;   // { servidor, perf }
let desfase = 0;    // ms (servidor − dispositivo); se restaura de la última sincronización si no hay red
try {
  const guardado = typeof localStorage !== "undefined" ? localStorage.getItem("ra-reloj") : null;
  desfase = Number(guardado ? JSON.parse(guardado)?.desfase : 0) || 0;
} catch { /* sin storage (Node) */ }

/**
 * @param {number} servidorMs
 * @returns {void}
 */
export function sincronizarReloj(servidorMs) {
  ancla = { servidor: servidorMs, perf: performance.now() };
  desfase = servidorMs - Date.now();
  try {
    if (typeof localStorage !== "undefined") {
      localStorage.setItem("ra-reloj", JSON.stringify({ desfase, en: Date.now() }));
    }
  } catch { /* sin storage */ }
}
/** @returns {number} */
export const desfaseReloj = () => desfase;
/** @returns {Date} */
export const ahora = () => new Date(ancla ? ancla.servidor + (performance.now() - ancla.perf) : Date.now() + desfase);

/**
 * @param {Date} d
 * @param {string} [zona]
 * @returns {Record<string, string>}
 */
function partesZona(d, zona = ZONA_HORARIA) {
  /** @type {Record<string, string>} */
  const p = {};
  new Intl.DateTimeFormat("en-CA", { timeZone: zona, hourCycle: "h23", year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" })
    .formatToParts(d).forEach((x) => { p[x.type] = x.value; });
  return p;
}

/**
 * Fecha YYYY-MM-DD en la zona del instituto.
 * @param {Date} [d]
 * @returns {string}
 */
export const fechaZona = (d = ahora()) => { const p = partesZona(d); return `${p.year}-${p.month}-${p.day}`; };

/**
 * Hora HH:MM (24 h) en la zona del instituto.
 * @param {Date} [d]
 * @returns {string}
 */
export const horaZona = (d = ahora()) => { const p = partesZona(d); return `${p.hour}:${p.minute}`; };

/** @returns {string} */
export const todayStr = () => fechaZona(ahora());

/**
 * ¿El error es de red/tiempo de espera (y por tanto reintentable sin perder datos)?
 * @param {any} e
 * @returns {boolean}
 */
export const esErrorRed = (e) => !e?.code && /fetch|network|abort|timeout|timed out|load failed|conexi/i.test(String(e?.message || e || ""));

/**
 * ¿El error indica sesión vencida o sin permisos de sesión?
 * @param {any} e
 * @returns {boolean}
 */
export const esErrorSesion = (e) => /jwt|expired|not authenticated|no autenticado|invalid token|401/i.test(String(e?.message || "")) || e?.status === 401;

/**
 * @param {string} str
 * @returns {Date}
 */
export function parseDate(str) {
  const [y, m, d] = str.split("-").map(Number);
  return new Date(y, m - 1, d);
}

/**
 * @param {string} str
 * @param {number} n
 * @returns {string}
 */
export function addDays(str, n) {
  const d = parseDate(str);
  d.setDate(d.getDate() + n);
  return dateStr(d);
}

/**
 * @param {string} str
 * @returns {boolean}
 */
export function isWeekend(str) {
  const w = parseDate(str).getDay();
  return w === 0 || w === 6;
}

/**
 * Últimos n días hábiles (lun-vie) terminando en `hasta` (inclusive si es hábil), orden ascendente.
 * @param {number} n
 * @param {string} [hasta]
 * @returns {string[]}
 */
export function lastWeekdays(n, hasta = todayStr()) {
  /** @type {string[]} */
  const out = [];
  let cur = hasta;
  while (out.length < n) {
    if (!isWeekend(cur)) out.unshift(cur);
    cur = addDays(cur, -1);
  }
  return out;
}

/**
 * Días hábiles (lun-vie) de un mes "YYYY-MM", sin pasar de `hasta` (YYYY-MM-DD).
 * @param {string} mes
 * @param {string} [hasta]
 * @param {Map<string, any>} [noLectivos]
 * @returns {string[]}
 */
export function diasHabilesDelMes(mes, hasta = todayStr(), noLectivos = new Map()) {
  const [y, m] = mes.split("-").map(Number);
  const ultimo = new Date(y, m, 0).getDate();
  /** @type {string[]} */
  const out = [];
  for (let d = 1; d <= ultimo; d++) {
    const f = `${mes}-${String(d).padStart(2, "0")}`;
    if (f > hasta) break;
    if (!isWeekend(f) && !noLectivos.has(f)) out.push(f);
  }
  return out;
}

/**
 * Hora actual HH:MM según el reloj confiable y la zona del instituto.
 * @param {Date} [d]
 * @returns {string}
 */
export function nowHHMM(d = ahora()) {
  return horaZona(d);
}

/**
 * @param {string} str
 * @param {Intl.DateTimeFormatOptions} [opts]
 * @returns {string}
 */
export function fmtDate(str, opts = { day: "2-digit", month: "short", year: "numeric" }) {
  return parseDate(str).toLocaleDateString("es-PE", opts).replace(".", "");
}

/**
 * @param {string} str
 * @returns {string}
 */
export function fmtDay(str) {
  return parseDate(str).toLocaleDateString("es-PE", { weekday: "short", day: "2-digit" }).replace(".", "");
}

/**
 * @param {Date} [d]
 * @returns {string}
 */
export function greeting(d = new Date()) {
  const h = d.getHours();
  return h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches";
}

/**
 * @param {string | null | undefined} name
 * @returns {string}
 */
export function initials(name) {
  return (name || "?").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
}

/**
 * @template {(...args: any[]) => any} T
 * @param {T} fn
 * @param {number} [ms]
 * @returns {(...args: Parameters<T>) => void}
 */
export function debounce(fn, ms = 200) {
  /** @type {any} */
  let t;
  return (...a) => { clearTimeout(t); t = setTimeout(() => fn(...a), ms); };
}

/**
 * @param {number} num
 * @param {number} den
 * @returns {number}
 */
export const pct = (num, den) => (den > 0 ? Math.round((num / den) * 100) : 0);

/**
 * @param {string} w
 * @returns {string}
 */
const enmascarar = (w) => (w.length <= 2 ? w[0] + "*" : w.slice(0, 2) + "*".repeat(Math.min(w.length - 2, 6)));

/**
 * Nombre para mostrar con privacidad: nombres completos y apellidos parcialmente censurados
 * ("Lucía Quispe Flores" → "Lucía Qu**** Fl****"). Usa nombres/apellidos si existen; si no, deduce:
 * con 3+ palabras los dos últimos son apellidos; con 2, el último.
 * @param {{ nombre?: string | null, nombres?: string | null, apellidos?: string | null } | null | undefined} alumno
 * @returns {string}
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

/**
 * Nombre canónico de un ciclo/salón: "APSTI · IV CICLO" o "APSTI · IV CICLO · SECCIÓN A".
 * @param {string} carrera
 * @param {string} ciclo
 * @param {string} [seccion]
 * @returns {string}
 */
export function nombreCiclo(carrera, ciclo, seccion = "") {
  return [carrera, `${ciclo} CICLO`, seccion ? `SECCIÓN ${seccion}` : ""].filter(Boolean).join(" · ");
}

/**
 * Extrae ciclo (I–VI) y sección de un nombre. Nombres antiguos o libres devuelven ciclo = null.
 * @param {string | null | undefined} nombre
 * @returns {{ ciclo: string | null, seccion: string }}
 */
export function parsearCiclo(nombre) {
  const c = /\b(VI|IV|V|III|II|I)\s+CICLO\b/i.exec(nombre || "");
  const s = /SECCI[ÓO]N\s+([A-Z0-9]+)/i.exec(nombre || "");
  return { ciclo: c ? c[1].toUpperCase() : null, seccion: s ? s[1].toUpperCase() : "" };
}

/**
 * Comparador de nombres de ciclo: I → VI, luego por salón; los nombres libres van al final, por orden alfabético.
 * @param {string} a
 * @param {string} b
 * @returns {number}
 */
export function compararCiclos(a, b) {
  const pa = parsearCiclo(a), pb = parsearCiclo(b);
  const ia = pa.ciclo ? CICLOS.indexOf(pa.ciclo) : 99, ib = pb.ciclo ? CICLOS.indexOf(pb.ciclo) : 99;
  return ia - ib || pa.seccion.localeCompare(pb.seccion, "es") || String(a).localeCompare(String(b), "es", { numeric: true });
}

/**
 * Quita el prefijo "CARRERA · " cuando ya se sabe la carrera (selectores filtrados por carrera).
 * @param {string} nombre
 * @param {string} carrera
 * @returns {string}
 */
export function cicloCorto(nombre, carrera) {
  const pre = `${carrera} · `;
  return carrera && (nombre || "").toUpperCase().startsWith(pre.toUpperCase()) ? (nombre || "").slice(pre.length) : nombre;
}

/**
 * Etiqueta legible "Carrera · Ciclo". Si el nombre del ciclo ya empieza con la carrera
 * ("MECANICA ELECTRICA" + "MECANICA ELECTRICA I") se muestra solo el ciclo, sin repetirla.
 * @param {string} carrera
 * @param {string} ciclo
 * @returns {string}
 */
export function etiquetaCiclo(carrera, ciclo) {
  if (!carrera) return ciclo || "";
  if (!ciclo) return carrera;
  return norm(ciclo).startsWith(norm(carrera)) ? ciclo : `${carrera} · ${ciclo}`;
}

/**
 * Normaliza texto para búsqueda sin tildes ni mayúsculas.
 * @param {string | null | undefined} s
 * @returns {string}
 */
export function norm(s) {
  return (s ?? "").toString().normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

/**
 * @typedef {{ label: string, key?: string, value?: (row: any) => any }} ColumnaCSV
 */

/**
 * Serializa filas a CSV (RFC 4180) con BOM para que Excel respete UTF-8.
 * @param {any[]} rows
 * @param {ColumnaCSV[]} columns
 * @returns {string}
 */
export function toCSV(rows, columns) {
  /** @param {any} v */
  const q = (v) => {
    const s = (v ?? "").toString();
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const head = columns.map((c) => q(c.label)).join(",");
  const body = rows.map((r) => columns.map((c) => q(typeof c.value === "function" ? c.value(r) : (c.key ? r[c.key] : ""))).join(","));
  return "\uFEFF" + [head, ...body].join("\r\n");
}

/**
 * @param {Blob} blob
 * @returns {Promise<string>}
 */
function blobToBase64(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(String(r.result || "").split(",")[1] || "");
    r.onerror = () => reject(r.error);
    r.readAsDataURL(blob);
  });
}

/**
 * Descarga un archivo. En el navegador usa <a download>; dentro del APK (WebView) las descargas de blobs no
 * funcionan, así que se entrega al puente nativo `AndroidBridge.saveFile`, que lo guarda en Descargas.
 * @param {string} filename
 * @param {Blob | string} content
 * @param {string} [type]
 * @returns {Promise<boolean>}
 */
export async function downloadFile(filename, content, type = "text/csv;charset=utf-8;") {
  const blob = content instanceof Blob ? content : new Blob([content], { type });
  const bridge = /** @type {any} */ (globalThis).AndroidBridge;
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

/** @returns {string} */
export const uid = () =>
  (typeof crypto !== "undefined" && crypto.randomUUID ? crypto.randomUUID() : "id-" + Date.now().toString(36) + Math.random().toString(36).slice(2, 10));

/**
 * Recorta al centro en cuadrado y reduce a `lado`×`lado` JPEG (~30 KB): fotos de perfil ligeras.
 * @param {File | Blob} file
 * @param {number} [lado]
 * @returns {Promise<Blob | null>}
 */
export async function archivoACuadrado(file, lado = 400) {
  if (!/^image\//.test(file.type)) throw new Error("El archivo debe ser una imagen.");
  /**
   * @param {CanvasImageSource} fuente
   * @param {number} w
   * @param {number} h
   * @returns {Promise<Blob | null>}
   */
  const dibujar = (fuente, w, h) => {
    const m = Math.min(w, h), c = document.createElement("canvas");
    c.width = c.height = lado;
    const ctx = c.getContext("2d");
    if (!ctx) throw new Error("No se pudo obtener el contexto 2D");
    ctx.drawImage(fuente, (w - m) / 2, (h - m) / 2, m, m, 0, 0, lado, lado);
    return new Promise((res) => c.toBlob(res, "image/jpeg", 0.85));
  };
  if (typeof createImageBitmap !== "undefined") {
    const bm = await createImageBitmap(file, { imageOrientation: "from-image" });
    const b = await dibujar(bm, bm.width, bm.height);
    bm.close?.();
    return b;
  }
  /** @type {HTMLImageElement} */
  const img = await new Promise((res, rej) => {
    const i = new Image();
    i.onload = () => res(i);
    i.onerror = rej;
    i.src = URL.createObjectURL(file);
  });
  return dibujar(img, img.naturalWidth, img.naturalHeight);
}
