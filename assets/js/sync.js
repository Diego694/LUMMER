// @ts-check
// Sincronización: envía lo guardado sin conexión cuando vuelve la red, sin duplicar ni perder registros.
import { api } from "./api.js";
import { cola } from "./cola.js";
import { esErrorRed, esErrorSesion, sincronizarReloj } from "./utils.js";

/** @typedef {import('./tipos.d.ts').ItemCola} ItemCola */
/** @typedef {import('./tipos.d.ts').EstadoSync} EstadoSync */
/** @typedef {import('./tipos.d.ts').ResultadoGuardarAsistencias} ResultadoGuardarAsistencias */

/** ¿Hay conexión? (`__simOffline` permite simular la falta de red en pruebas.) */
// En modo local (.exe) los datos están en el propio equipo: siempre "hay conexión" y nunca se encola ni se sincroniza.
export const red = { online: () => api.mode === "local" || (!globalThis.__simOffline && globalThis.navigator?.onLine !== false) };

/** @type {Set<(s: EstadoSync) => void>} */
const oyentes = new Set();
/** @type {{ enCurso: boolean, error: string | null }} */
const estado = { enCurso: false, error: null };
/**
 * @param {(s: EstadoSync) => void} f
 * @returns {() => boolean}
 */
export const onEstado = (f) => { oyentes.add(f); return () => oyentes.delete(f); };
/**
 * @returns {EstadoSync}
 */
export const estadoSync = () => ({ pendientes: cola.pendientes().length, rechazados: cola.rechazados().length, online: red.online(), ...estado });
export const emitir = () => oyentes.forEach((f) => { try { f(estadoSync()); } catch { /* un oyente no debe romper la sincronización */ } });

/**
 * Cómo se envía cada tipo de elemento (se amplía en otros módulos con registrarEnvio).
 * @type {Record<string, (rows: any[]) => Promise<any>>}
 */
const ENVIAR = { asistencia: (rows) => api.registrarMasivo(rows) };
/**
 * @param {string} tipo
 * @param {(rows: any[]) => Promise<any>} fn
 */
export const registrarEnvio = (tipo, fn) => { ENVIAR[tipo] = fn; };

/** @type {(() => Promise<void> | void) | null} */
let alTerminar = null;                 // callback para refrescar datos después de sincronizar
/**
 * @param {(() => Promise<void> | void) | null} f
 */
export const alSincronizar = (f) => { alTerminar = f; };
/** @type {(() => Promise<boolean> | boolean) | null} */
let reautenticar = null;               // callback para recuperar la sesión si se inició sin red
/**
 * @param {(() => Promise<boolean> | boolean) | null} f
 */
export const alNecesitarSesion = (f) => { reautenticar = f; };

/**
 * @template T
 * @param {T[]} a
 * @param {number} n
 * @returns {T[][]}
 */
const lotes = (a, n) => Array.from({ length: Math.ceil(a.length / n) }, (_, i) => a.slice(i * n, i * n + n));

export async function sincronizar() {
  if (estado.enCurso || !red.online()) return { n: 0 };
  const pend = cola.pendientes();
  if (!pend.length) return { n: 0 };
  estado.enCurso = true; estado.error = null; emitir();
  let enviados = 0;
  try {
    if (reautenticar && !(await reautenticar())) { estado.error = "sesion"; return { n: 0 }; }
    for (const tipo of [...new Set(pend.map((p) => p.tipo))]) {
      const enviar = ENVIAR[tipo];
      if (!enviar) continue;
      for (const lote of lotes(pend.filter((p) => p.tipo === tipo), 100)) {
        try {
          await enviar(lote.map((i) => i.row));
          cola.quitar(lote.map((i) => i.id)); enviados += lote.length;
        } catch (e) {
          if (esErrorRed(e)) { estado.error = "red"; throw e; }
          if (esErrorSesion(e)) { estado.error = "sesion"; throw e; }
          // Error permanente en el lote: se aísla el elemento culpable sin frenar a los demás.
          for (const it of lote) {
            try { await enviar([it.row]); cola.quitar([it.id]); enviados++; }
            catch (e2) {
              const err2 = /** @type {any} */ (e2);
              if (esErrorRed(err2)) { estado.error = "red"; throw err2; }
              if (esErrorSesion(err2)) { estado.error = "sesion"; throw err2; }
              const intentos = (it.intentos || 0) + 1;
              cola.marcar([it.id], { intentos, ultimoError: String(err2?.message || err2).slice(0, 200), rechazado: intentos >= 3 });
            }
          }
        }
      }
    }
  } catch { /* red o sesión: se reintenta solo más tarde */ }
  finally { estado.enCurso = false; emitir(); }
  if (enviados && alTerminar) { try { await alTerminar(); } catch { /* sin red de nuevo */ } }
  return { n: enviados };
}

/** Sincroniza el reloj con el servidor (mantiene la hora de asistencia confiable). No falla si no hay red. */
export async function sincronizarRelojServidor() {
  if (!red.online() || !api.horaServidor) return false;
  try { sincronizarReloj(await api.horaServidor()); return true; } catch { return false; }
}

export function iniciarSync() {
  const reintentar = () => { emitir(); sincronizar(); sincronizarRelojServidor(); };
  addEventListener("online", reintentar);
  addEventListener("offline", emitir);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) reintentar(); });
  setInterval(() => { sincronizar(); }, 30000);
  setInterval(() => { sincronizarRelojServidor(); }, 10 * 60 * 1000);
  emitir();
}

/**
 * Guarda asistencias: directo si hay red; si no (o si la red falla a mitad), quedan en la cola del teléfono.
 * Devuelve { n, offline }. Los errores que NO son de red (permisos, datos) se propagan: no se ocultan.
 * @param {any[]} rows
 * @returns {Promise<ResultadoGuardarAsistencias>}
 */
export async function guardarAsistencias(rows) {
  if (red.online()) {
    try { return { n: await api.registrarMasivo(rows), offline: false }; }
    catch (e) { if (!esErrorRed(e)) throw e; }
  }
  rows.forEach((r) => cola.agregar({ tipo: "asistencia", row: r, clave: `a|${r.alumno_id}|${r.fecha}` }));
  emitir();
  return { n: rows.length, offline: true };
}
