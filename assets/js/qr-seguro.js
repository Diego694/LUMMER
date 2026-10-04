// QR dinámico (opcional, CONFIG.QR_MODO): el QR del carnet cambia cada 30 s y lleva una firma HMAC-SHA256
// calculada con un secreto que solo conocen el estudiante y el instituto. Una captura de pantalla o foto del QR
// deja de servir en ~1,5 minutos y no se puede fabricar un QR válido sin el secreto.
//   formato:  <codigo>.<ventana en base 36>.<firma 10 hex>
export const VENTANA_MS = 30000;
const TOLERANCIA = 2;   // ventanas aceptadas a cada lado (cubre relojes algo desfasados y el tiempo de lectura)

const ENC = new TextEncoder();
async function firma(secreto, mensaje) {
  const key = await crypto.subtle.importKey("raw", ENC.encode(secreto), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, ENC.encode(mensaje)));
  return [...sig.slice(0, 5)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export const ventana = (ms) => Math.floor(ms / VENTANA_MS);
export const segundosRestantes = (ms) => Math.ceil((VENTANA_MS - (ms % VENTANA_MS)) / 1000);

export async function generarQR(codigo, secreto, ms) {
  const w = ventana(ms);
  return `${codigo}.${w.toString(36)}.${await firma(secreto, `${codigo}.${w}`)}`;
}

/**
 * Verifica el texto leído. Devuelve:
 *  { estatico: true }                       → es un código normal (sin firma)
 *  { ok: true, alumno }                     → QR dinámico válido
 *  { ok: false, motivo, alumno? }           → 'desconocido' | 'vencido' | 'firma' | 'sinsecreto'
 */
export async function verificarQR(texto, buscarPorCodigo, ms) {
  if (buscarPorCodigo(texto)) return { estatico: true };           // un código existente (aunque contenga puntos) es estático
  const p = String(texto).split(".");
  if (p.length !== 3) return { estatico: true };
  const [codigo, t36, sig] = p;
  const alumno = buscarPorCodigo(codigo);
  if (!alumno) return { ok: false, motivo: "desconocido" };
  if (!alumno.qr_secreto) return { ok: false, motivo: "sinsecreto", alumno };
  const w = parseInt(t36, 36);
  if (!Number.isFinite(w) || Math.abs(ventana(ms) - w) > TOLERANCIA) return { ok: false, motivo: "vencido", alumno };
  return (await firma(alumno.qr_secreto, `${codigo}.${w}`)) === sig ? { ok: true, alumno } : { ok: false, motivo: "firma", alumno };
}
