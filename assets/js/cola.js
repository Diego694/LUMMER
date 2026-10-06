// @ts-check
// Cola de envíos pendientes (modo sin internet). Sin dependencias: solo localStorage.
// Cada elemento: { id, en, tipo, row, clave?, intentos, rechazado?, ultimoError? }.
// `clave` evita encolar dos veces lo mismo (p. ej. misma asistencia del mismo alumno y día).
import { uid } from "./utils.js";

/** @typedef {import('./tipos.d.ts').ItemCola} ItemCola */
/** @typedef {import('./tipos.d.ts').ItemColaInput} ItemColaInput */
/** @typedef {import('./tipos.d.ts').Perfil} Perfil */
/** @typedef {import('./tipos.d.ts').PerfilGuardado} PerfilGuardado */

// En el .exe, el modo local usa su propia cola (nunca mezcla envíos del modo online con la base local).
const KEY = "ra-cola-v1" + (() => { try { return globalThis.escritorio && localStorage.getItem("ra-modo") === "local" ? "L" : ""; } catch { return ""; } })();

/**
 * @returns {ItemCola[]}
 */
function leer() {
  try { return JSON.parse(localStorage.getItem(KEY) || "[]") || []; } catch { return []; }
}

/**
 * @param {ItemCola[]} lista
 * @returns {boolean}
 */
function guardar(lista) {
  try { localStorage.setItem(KEY, JSON.stringify(lista)); return true; } catch { return false; }
}

export const cola = {
  todos: leer,
  pendientes: () => leer().filter((x) => !x.rechazado),
  rechazados: () => leer().filter((x) => x.rechazado),
  /**
   * Devuelve false si ya estaba encolado; lanza si no hay espacio (para no perder datos en silencio).
   * @param {ItemColaInput} item
   * @returns {boolean}
   */
  agregar(item) {
    const l = leer();
    if (item.clave && l.some((x) => x.clave === item.clave && !x.rechazado)) return false;
    l.push({ id: uid(), en: Date.now(), intentos: 0, ...item });
    if (!guardar(l)) throw new Error("No hay espacio en el teléfono para guardar el registro sin conexión");
    return true;
  },
  /**
   * @param {string[]} ids
   */
  quitar(ids) {
    const s = new Set(ids);
    guardar(leer().filter((x) => !s.has(x.id)));
  },
  /**
   * @param {string[]} ids
   * @param {Partial<ItemCola>} cambios
   */
  marcar(ids, cambios) {
    const s = new Set(ids);
    guardar(leer().map((x) => (s.has(x.id) ? { ...x, ...cambios } : x)));
  },
  vaciarRechazados() { guardar(leer().filter((x) => !x.rechazado)); },
};

/**
 * Salidas del día aún no enviadas (para mostrarlas aunque no haya red).
 * @param {string} fecha
 * @returns {any[]}
 */
export const salidasPendientesDe = (fecha) =>
  leer().filter((x) => x.tipo === "salida" && !x.rechazado && x.row && x.row.fecha === fecha).map((x) => x.row);

/**
 * Asistencias del día aún no enviadas (para que "ya registrado hoy" funcione también sin red).
 * @param {string} fecha
 * @returns {any[]}
 */
export const asistenciasPendientesDe = (fecha) =>
  leer().filter((x) => x.tipo === "asistencia" && !x.rechazado && x.row && x.row.fecha === fecha).map((x) => x.row);

/* ---- Copia local de los datos (para poder abrir y trabajar sin red) ---- */
/** @param {string | null | undefined} cid */
const SNAP = (cid) => `ra-snap-${cid || ""}`;

/**
 * @param {string | null | undefined} cid
 * @param {Record<string, unknown>} datos
 */
export function guardarSnapshot(cid, datos) {
  try { localStorage.setItem(SNAP(cid), JSON.stringify({ en: Date.now(), ...datos })); } catch { /* cuota llena: se trabaja sin copia */ }
}

/**
 * @param {string | null | undefined} cid
 * @returns {any}
 */
export function leerSnapshot(cid) {
  try { return JSON.parse(localStorage.getItem(SNAP(cid)) || "null"); } catch { return null; }
}

/**
 * @param {{ id: string; email?: string | null }} user
 * @param {Perfil} perfil
 */
export function guardarPerfil(user, perfil) {
  try { localStorage.setItem("ra-perfil", JSON.stringify({ user: { id: user.id, email: user.email }, perfil, en: Date.now() })); } catch { /* ok */ }
}

/**
 * @returns {PerfilGuardado | null}
 */
export function leerPerfil() {
  try { return JSON.parse(localStorage.getItem("ra-perfil") || "null"); } catch { return null; }
}

export function borrarPerfil() { try { localStorage.removeItem("ra-perfil"); } catch { /* ok */ } }
