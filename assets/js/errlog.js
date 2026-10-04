// Registro de errores del cliente: si algo falla en el teléfono de un docente o estudiante, el administrador lo ve
// (Sistema → Errores) sin que nadie tenga que explicarlo. No bloquea nada, limita el volumen y no envía datos personales.
import { esErrorRed } from "./utils.js";

const COLA = "ra-errq" + (() => { try { return globalThis.escritorio && localStorage.getItem("ra-modo") === "local" ? "L" : ""; } catch { return ""; } })();
const MAX_POR_SESION = 15;
let enviadosSesion = 0, activo = false, apiRef = null;
const vistos = new Set();

const leer = () => { try { return JSON.parse(localStorage.getItem(COLA)) || []; } catch { return []; } };
const guardar = (l) => { try { localStorage.setItem(COLA, JSON.stringify(l.slice(-30))); } catch { /* sin espacio */ } };

/** Ruido que no vale la pena registrar: cortes de red (esperados), extensiones y avisos del navegador. */
export const esRuido = (m) => !m || /ResizeObserver|^Script error\.?$|Non-Error promise rejection|Failed to fetch|NetworkError|Load failed|AbortError|signal is aborted|sin conexi/i.test(String(m));

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
  const resto = [];
  for (const row of l) {
    try { await apiRef.registrarError(row); }
    catch (e) { if (esErrorRed(e) || /jwt|permission|row-level|401|not authenticated|autenticado/i.test(String(e.message))) resto.push(row); }
  }
  guardar(resto);
}

export function iniciarLogErrores(app, api) {
  if (activo) return;
  activo = true; apiRef = api;
  addEventListener("error", (e) => registrarError(app, e.message, e.error?.stack || `${e.filename}:${e.lineno}`));
  addEventListener("unhandledrejection", (e) => registrarError(app, e.reason?.message || String(e.reason), e.reason?.stack));
  addEventListener("online", enviarPendientes);
}
