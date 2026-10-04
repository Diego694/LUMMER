// Cola de envíos pendientes (modo sin internet). Sin dependencias: solo localStorage.
// Cada elemento: { id, en, tipo, row, clave?, intentos, rechazado?, ultimoError? }.
// `clave` evita encolar dos veces lo mismo (p. ej. misma asistencia del mismo alumno y día).
import { uid } from "./utils.js";

// En el .exe, el modo local usa su propia cola (nunca mezcla envíos del modo online con la base local).
const KEY = "ra-cola-v1" + (() => { try { return globalThis.escritorio && localStorage.getItem("ra-modo") === "local" ? "L" : ""; } catch { return ""; } })();

function leer() {
  try { return JSON.parse(localStorage.getItem(KEY)) || []; } catch { return []; }
}
function guardar(lista) {
  try { localStorage.setItem(KEY, JSON.stringify(lista)); return true; } catch { return false; }
}

export const cola = {
  todos: leer,
  pendientes: () => leer().filter((x) => !x.rechazado),
  rechazados: () => leer().filter((x) => x.rechazado),
  /** Devuelve false si ya estaba encolado; lanza si no hay espacio (para no perder datos en silencio). */
  agregar(item) {
    const l = leer();
    if (item.clave && l.some((x) => x.clave === item.clave && !x.rechazado)) return false;
    l.push({ id: uid(), en: Date.now(), intentos: 0, ...item });
    if (!guardar(l)) throw new Error("No hay espacio en el teléfono para guardar el registro sin conexión");
    return true;
  },
  quitar(ids) {
    const s = new Set(ids);
    guardar(leer().filter((x) => !s.has(x.id)));
  },
  marcar(ids, cambios) {
    const s = new Set(ids);
    guardar(leer().map((x) => (s.has(x.id) ? { ...x, ...cambios } : x)));
  },
  vaciarRechazados() { guardar(leer().filter((x) => !x.rechazado)); },
};

/** Asistencias del día aún no enviadas (para que "ya registrado hoy" funcione también sin red). */
export const asistenciasPendientesDe = (fecha) =>
  leer().filter((x) => x.tipo === "asistencia" && !x.rechazado && x.row.fecha === fecha).map((x) => x.row);

/* ---- Copia local de los datos (para poder abrir y trabajar sin red) ---- */
const SNAP = (cid) => `ra-snap-${cid}`;
export function guardarSnapshot(cid, datos) {
  try { localStorage.setItem(SNAP(cid), JSON.stringify({ en: Date.now(), ...datos })); } catch { /* cuota llena: se trabaja sin copia */ }
}
export function leerSnapshot(cid) {
  try { return JSON.parse(localStorage.getItem(SNAP(cid))); } catch { return null; }
}
export function guardarPerfil(user, perfil) {
  try { localStorage.setItem("ra-perfil", JSON.stringify({ user: { id: user.id, email: user.email }, perfil, en: Date.now() })); } catch { /* ok */ }
}
export function leerPerfil() {
  try { return JSON.parse(localStorage.getItem("ra-perfil")); } catch { return null; }
}
export function borrarPerfil() { try { localStorage.removeItem("ra-perfil"); } catch { /* ok */ } }
