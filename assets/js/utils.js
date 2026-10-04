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

export function downloadFile(filename, content, type = "text/csv;charset=utf-8;") {
  const blob = content instanceof Blob ? content : new Blob([content], { type });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(a.href), 1000);
}

export const uid = () =>
  (globalThis.crypto?.randomUUID ? crypto.randomUUID() : "id-" + Date.now().toString(36) + Math.random().toString(36).slice(2, 10));
