// Avisos al teléfono: en la app Android, los comunicados nuevos aparecen como notificación aunque la app esté cerrada.
// La web solo entrega a la app nativa la dirección del servidor y el token de avisos del instituto; la app consulta
// los comunicados en segundo plano (ver MainActivity/AvisosJob). Fuera de Android no hace nada.
import { CONFIG } from "./config.js";

const puente = () => globalThis.AndroidBridge;

/** Llamar tras iniciar sesión (personal o estudiante). Falla en silencio: los avisos nunca bloquean la app. */
export async function activarAvisos(api) {
  try {
    if (!puente()?.configurarAvisos || !api.tokenAvisos) return;
    const token = await api.tokenAvisos();
    if (token) puente().configurarAvisos(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, token);
  } catch (e) { console.warn("Avisos no activados:", e.message); }
}

/** Al cerrar sesión: este teléfono deja de recibir avisos de ese instituto. */
export function desactivarAvisos() {
  try { puente()?.detenerAvisos?.(); } catch { /* sin puente */ }
}
