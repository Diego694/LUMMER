// Configuración de la aplicación.
// Mientras SUPABASE_URL / SUPABASE_ANON_KEY contengan los placeholders, la app corre en MODO DEMO
// (datos locales en el navegador). Para producción reemplázalos con los de tu proyecto Supabase.
// La anon key es pública por diseño: la seguridad real la dan las políticas RLS (supabase/schema.sql).
export const CONFIG = {
  SUPABASE_URL: "https://wotumvamyglfquahdnet.supabase.co",
  SUPABASE_ANON_KEY: "sb_publishable_V0wt4JdNVXHc20_0tJWBHA_D47gcmbf",

  APP_NAME: "Sistema de Registro Académico",
  // Versión de la web. Se muestra en el menú lateral para confirmar que el APK/navegador ya cargó la última.
  APP_VERSION: "2.1.0",
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

export const DEMO_USER = { email: "demo@colegio.pe", password: "demo1234" };
