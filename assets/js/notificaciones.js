// @ts-check
// Avisos al teléfono: en la app Android, los comunicados nuevos aparecen como notificación aunque la app esté cerrada.
// La web solo entrega a la app nativa la dirección del servidor y el token de avisos del instituto; la app consulta
// los comunicados en segundo plano (ver MainActivity/AvisosJob). Fuera de Android no hace nada.
import { CONFIG } from "./config.js";

/** @typedef {import('./tipos.d.ts').AndroidBridge} AndroidBridge */

const puente = () => globalThis.AndroidBridge;

/**
 * Llamar tras iniciar sesión (personal o estudiante). Falla en silencio: los avisos nunca bloquean la app.
 * @param {{ tokenAvisos?: () => Promise<string | null> | string | null }} api
 * @returns {Promise<void>}
 */
export async function activarAvisos(api) {
  try {
    if (!puente()?.configurarAvisos || !api.tokenAvisos) return;
    const token = await api.tokenAvisos();
    if (token) puente()?.configurarAvisos?.(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, token);
  } catch (e) {
    const err = /** @type {any} */ (e);
    console.warn("Avisos no activados:", err?.message);
  }
}

/** Al cerrar sesión: este teléfono deja de recibir avisos de ese instituto. */
export function desactivarAvisos() {
  try { puente()?.detenerAvisos?.(); } catch { /* sin puente */ }
}

/* ------------------------------ Aprobación del registro del estudiante ------------------------------ */
/** @type {ReturnType<typeof setInterval> | null} */
let timerAprob = null;

/**
 * Muestra una notificación del sistema (si el usuario dio permiso) además del aviso dentro de la app.
 * @param {string} titulo
 * @param {string} cuerpo
 */
function notificarSistema(titulo, cuerpo) {
  try { if ("Notification" in globalThis && Notification.permission === "granted") new Notification(titulo, { body: cuerpo, icon: "../assets/icons/icon-192.png" }); } catch { /* sin notificaciones */ }
}

/**
 * Mientras el registro está pendiente: (1) en la app Android se encarga a la app nativa, que consulta en segundo plano
 * aunque esté cerrada; (2) con la página abierta se consulta cada 30 s. Al aprobarse llama a `alAprobar()`.
 * `token` es el token privado del estudiante (solo él lo conoce).
 * @param {{ estadoSolicitud?: (t: string) => Promise<{ aprobado?: boolean } | null> }} api
 * @param {string} token
 * @param {() => void} alAprobar
 */
export function vigilarAprobacion(api, token, alAprobar) {
  detenerVigilancia();
  if (!token || !api.estadoSolicitud) return;
  try { globalThis.AndroidBridge?.configurarAprobacion?.(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, token); } catch { /* sin puente */ }
  const revisar = async () => {
    try {
      const r = await api.estadoSolicitud?.(token);
      if (r?.aprobado === true) {
        detenerVigilancia();
        notificarSistema("¡Tu registro fue aprobado!", "Ya puedes usar tu carnet QR para registrar tu asistencia.");
        alAprobar();
      }
    } catch { /* sin red: se reintenta */ }
  };
  timerAprob = setInterval(revisar, 30000);
  document.addEventListener("visibilitychange", alVolver);
  function alVolver() { if (!document.hidden) revisar(); }
  /** @type {any} */ (vigilarAprobacion)._alVolver = alVolver;
  revisar();
}

export function detenerVigilancia() {
  if (timerAprob) { clearInterval(timerAprob); timerAprob = null; }
  const alVolver = /** @type {any} */ (vigilarAprobacion)._alVolver;
  if (alVolver) document.removeEventListener("visibilitychange", alVolver);
  /** @type {any} */ (vigilarAprobacion)._alVolver = null;
}

/** En la web (no en la app Android): pide permiso para mostrar la notificación. Devuelve true si quedó concedido. */
export async function pedirPermisoNotificacion() {
  if (!("Notification" in globalThis)) return false;
  if (Notification.permission === "granted") return true;
  if (Notification.permission === "denied") return false;
  return (await Notification.requestPermission()) === "granted";
}

/** @returns {NotificationPermission | 'unsupported'} */
export const permisoNotificacion = () => ("Notification" in globalThis ? Notification.permission : "unsupported");
