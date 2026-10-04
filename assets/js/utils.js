// Utilidades puras (sin dependencias del DOM salvo downloadFile) — probadas en tests/.

export function esc(s) {
  return (s ?? "").toString().replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

const pad = (n) => String(n).padStart(2, "0");

/** Fecha local en formato YYYY-MM-DD (evita el desfase UTC de toISOString). */
export function dateStr(d = new Date()) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
export const todayStr = () => dateStr(new Date());

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
export function nowHHMM(d = new Date()) {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
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
