// Configuración de la aplicación.
// Mientras URL_PRODUCCION / KEY_PRODUCCION contengan los placeholders, la app corre en MODO DEMO
// (datos locales en el navegador). Para producción reemplázalos con los de tu proyecto Supabase.
// La anon key es pública por diseño: la seguridad real la dan las políticas RLS (supabase/migrations).
const URL_PRODUCCION = "https://wotumvamyglfquahdnet.supabase.co";
const KEY_PRODUCCION = "sb_publishable_V0wt4JdNVXHc20_0tJWBHA_D47gcmbf";

// Entorno de pruebas: desde entorno.html se puede apuntar ESTE navegador a otro proyecto Supabase (staging)
// sin tocar el código ni afectar a nadie más. Se guarda solo en este navegador.
const OVERRIDE = (() => {
  try {
    const o = JSON.parse(localStorage.getItem("ra-entorno"));
    return o && /^https:\/\/[a-z0-9-]+\.supabase\.co$/.test(o.url) && o.key ? o : null;
  } catch { return null; }
})();
/** "produccion" o el nombre del entorno de pruebas activo en este navegador. */
export const ENTORNO = OVERRIDE ? OVERRIDE.nombre || "pruebas" : "produccion";

export const CONFIG = {
  SUPABASE_URL: OVERRIDE ? OVERRIDE.url : URL_PRODUCCION,
  SUPABASE_ANON_KEY: OVERRIDE ? OVERRIDE.key : KEY_PRODUCCION,

  APP_NAME: "Sistema de Registro Académico",
  // Enlace de descarga del APK "Mi Carnet Institucional" (se incluye en el mensaje para compartir el código). Vacío = solo el portal web.
  APK_ESTUDIANTE_URL: "https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/download/apk-latest/carnet-estudiante.apk",
  // Versión de la web. Se muestra en el menú lateral para confirmar que el APK/navegador ya cargó la última.
  APP_VERSION: "2.5.0",
  // QR del carnet: "off" = QR estático (el código del alumno) · "opcional" = el estudiante muestra un QR firmado que cambia
  // cada 30 s y el docente acepta ambos · "obligatorio" = la cámara solo acepta QR dinámicos (NFC y código manual siguen valiendo).
  QR_MODO: "off",
  // Verificación anti-bots en el registro de estudiantes (Cloudflare Turnstile). Vacío = desactivada. Requiere activarla
  // también en Supabase → Authentication → Attack Protection (ver docs/SEGURIDAD.md).
  TURNSTILE_SITEKEY: "",
  // Mensajes de aviso a apoderados. Variables: {alumno} {fecha} {instituto} {hora} {ciclo}
  PLANTILLA_FALTA: "Estimado(a) apoderado(a): le informamos que {alumno} ({ciclo}) no registró su asistencia hoy {fecha} en {instituto}. Si se trata de una ausencia justificada, por favor comuníquelo a la institución. Gracias.",
  PLANTILLA_TARDANZA: "Estimado(a) apoderado(a): le informamos que {alumno} ({ciclo}) llegó con tardanza hoy {fecha} a las {hora} a {instituto}. Gracias por su apoyo.",
  // Hora límite de ingreso (HH:MM, 24 h). Registros posteriores se marcan como "tardanza".
  HORA_LIMITE: "08:00",
  // Umbral (%) bajo el cual un alumno aparece en "Requieren atención" del dashboard.
  UMBRAL_ASISTENCIA: 85,
  ALUMNOS_POR_PAGINA: 20,
};

// Modo demo: automático con los placeholders, o forzado con ?demo=1 (queda guardado en este navegador; ?demo=0 lo quita).
// El modo demo solo usa datos locales del navegador: nunca toca la base de datos real.
function demoForzado() {
  try {
    const q = new URLSearchParams(location.search).get("demo");
    if (q === "1") localStorage.setItem("ra-force-demo", "1");
    if (q === "0") localStorage.removeItem("ra-force-demo");
    return localStorage.getItem("ra-force-demo") === "1";
  } catch { return false; }
}
export const isDemoMode = () =>
  CONFIG.SUPABASE_URL.includes("TU-PROYECTO") || CONFIG.SUPABASE_ANON_KEY.includes("TU-ANON-KEY") || demoForzado();

export const DEMO_SCHOOL_CODE = "DEMO2026";

// Programa de escritorio (.exe): el preload de Electron expone `escritorio`. Solo allí existe el modo local.
export const esEscritorio = () => !!globalThis.escritorio;
/** "online" (Supabase) o "local" (base en este equipo). Fuera del .exe siempre es "online". */
export function modoActual() {
  try { return esEscritorio() && localStorage.getItem("ra-modo") === "local" ? "local" : "online"; } catch { return "online"; }
}
export const modoLocal = () => modoActual() === "local";

export const DEMO_USER = { email: "demo@instituto.pe", password: "demo1234" };
