// Configuración de la aplicación.
// Mientras SUPABASE_URL / SUPABASE_ANON_KEY contengan los placeholders, la app corre en MODO DEMO
// (datos locales en el navegador). Para producción reemplázalos con los de tu proyecto Supabase.
// La anon key es pública por diseño: la seguridad real la dan las políticas RLS (supabase/schema.sql).
export const CONFIG = {
  SUPABASE_URL: "https://TU-PROYECTO.supabase.co",
  SUPABASE_ANON_KEY: "TU-ANON-KEY-AQUI",

  APP_NAME: "Sistema de Registro Académico",
  // Hora límite de ingreso (HH:MM, 24 h). Registros posteriores se marcan como "tardanza".
  HORA_LIMITE: "08:00",
  // Umbral (%) bajo el cual un alumno aparece en "Requieren atención" del dashboard.
  UMBRAL_ASISTENCIA: 85,
  ALUMNOS_POR_PAGINA: 20,
};

export const isDemoMode = () =>
  CONFIG.SUPABASE_URL.includes("TU-PROYECTO") || CONFIG.SUPABASE_ANON_KEY.includes("TU-ANON-KEY");

export const DEMO_USER = { email: "demo@colegio.pe", password: "demo1234" };
