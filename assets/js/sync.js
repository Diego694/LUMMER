// Sincronización: envía lo guardado sin conexión cuando vuelve la red, sin duplicar ni perder registros.
import { api } from "./api.js";
import { cola } from "./cola.js";
import { esErrorRed, esErrorSesion, sincronizarReloj } from "./utils.js";

/** ¿Hay conexión? (`__simOffline` permite simular la falta de red en pruebas.) */
export const red = { online: () => !globalThis.__simOffline && globalThis.navigator?.onLine !== false };

const oyentes = new Set();
const estado = { enCurso: false, error: null };
export const onEstado = (f) => { oyentes.add(f); return () => oyentes.delete(f); };
export const estadoSync = () => ({ pendientes: cola.pendientes().length, rechazados: cola.rechazados().length, online: red.online(), ...estado });
export const emitir = () => oyentes.forEach((f) => { try { f(estadoSync()); } catch { /* un oyente no debe romper la sincronización */ } });

/** Cómo se envía cada tipo de elemento (se amplía en otros módulos con registrarEnvio). */
const ENVIAR = { asistencia: (rows) => api.registrarMasivo(rows) };
export const registrarEnvio = (tipo, fn) => { ENVIAR[tipo] = fn; };

let alTerminar = null;                 // callback para refrescar datos después de sincronizar
export const alSincronizar = (f) => { alTerminar = f; };
let reautenticar = null;               // callback para recuperar la sesión si se inició sin red
export const alNecesitarSesion = (f) => { reautenticar = f; };

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
              if (esErrorRed(e2)) { estado.error = "red"; throw e2; }
              if (esErrorSesion(e2)) { estado.error = "sesion"; throw e2; }
              const intentos = (it.intentos || 0) + 1;
              cola.marcar([it.id], { intentos, ultimoError: String(e2.message || e2).slice(0, 200), rechazado: intentos >= 3 });
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
