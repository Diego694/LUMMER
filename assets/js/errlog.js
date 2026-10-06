// @ts-check
// Registro de errores del cliente: si algo falla en el teléfono de un docente o estudiante, el administrador lo ve
// (Sistema → Errores) sin que nadie tenga que explicarlo. No bloquea nada, limita el volumen y no envía datos personales.
import { esErrorRed } from "./utils.js";

/** @typedef {import('./tipos.d.ts').ErrorLogItem} ErrorLogItem */
/** @typedef {import('./tipos.d.ts').ApiErrLog} ApiErrLog */

const COLA = "ra-errq" + (() => { try { return globalThis.escritorio && localStorage.getItem("ra-modo") === "local" ? "L" : ""; } catch { return ""; } })();
const MAX_POR_SESION = 15;
let enviadosSesion = 0, activo = false;
/** @type {ApiErrLog | null} */
let apiRef = null;
/** @type {Set<string>} */
const vistos = new Set();

/**
 * @returns {ErrorLogItem[]}
 */
const leer = () => { try { return JSON.parse(localStorage.getItem(COLA) || "[]") || []; } catch { return []; } };

/**
 * @param {ErrorLogItem[]} l
 */
const guardar = (l) => { try { localStorage.setItem(COLA, JSON.stringify(l.slice(-30))); } catch { /* sin espacio */ } };

/**
 * Ruido que no vale la pena registrar: cortes de red (esperados), extensiones y avisos del navegador.
 * @param {any} m
 * @returns {boolean}
 */
export const esRuido = (m) => !m || /ResizeObserver|^Script error\.?$|Non-Error promise rejection|Failed to fetch|NetworkError|Load failed|AbortError|signal is aborted|sin conexi/i.test(String(m));

/**
 * @param {string} app
 * @param {any} mensaje
 * @param {any} [detalle]
 * @returns {boolean}
 */
export function registrarError(app, mensaje, detalle = "") {
  if (esRuido(mensaje) || enviadosSesion >= MAX_POR_SESION) return false;
  const clave = String(mensaje).slice(0, 120);
  if (vistos.has(clave)) return false;
  vistos.add(clave); enviadosSesion++;
  const l = leer();
  l.push({ app, mensaje: String(mensaje).slice(0, 500), detalle: String(detalle || "").slice(0, 4000), url: String(location.href).replace(/[?#].*$/, "").slice(0, 300), agente: navigator.userAgent.slice(0, 300) });
  guardar(l);
  enviarPendientes();
  return true;
}

/** Envía los errores guardados. Los que fallan por red o sesión se conservan para el próximo intento. */
export async function enviarPendientes() {
  if (!apiRef?.registrarError || globalThis.__simOffline) return;
  const l = leer();
  if (!l.length) return;
  /** @type {ErrorLogItem[]} */
  const resto = [];
  for (const row of l) {
    try { await apiRef.registrarError(row); }
    catch (e) {
      const err = /** @type {any} */ (e);
      if (esErrorRed(err) || /jwt|permission|row-level|401|not authenticated|autenticado/i.test(String(err?.message))) resto.push(row);
    }
  }
  guardar(resto);
}

/**
 * @param {string} app
 * @param {ApiErrLog} api
 */
export function iniciarLogErrores(app, api) {
  if (activo) return;
  activo = true; apiRef = api;
  addEventListener("error", (e) => {
    const ev = /** @type {ErrorEvent} */ (e);
    registrarError(app, ev.message, ev.error?.stack || `${ev.filename}:${ev.lineno}`);
  });
  addEventListener("unhandledrejection", (e) => {
    const ev = /** @type {PromiseRejectionEvent} */ (e);
    registrarError(app, ev.reason?.message || String(ev.reason), ev.reason?.stack);
  });
  addEventListener("online", enviarPendientes);
}
