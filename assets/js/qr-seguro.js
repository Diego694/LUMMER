// @ts-check
// QR dinámico: el QR del carnet cambia cada 30 s y lleva una firma HMAC-SHA256
// calculada con un secreto que solo conocen el estudiante y el instituto. Una captura de pantalla o foto del QR
// deja de servir en ~60 s y no se puede fabricar un QR válido sin el secreto.
//   formato:  <codigo>.<ventana en base 36>.<firma 10 hex>
import { CONFIG } from "./config.js";

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').QrModo} QrModo */
/** @typedef {import('./tipos.d.ts').ResultadoVerificarQR} ResultadoVerificarQR */

export const VENTANA_MS = 30000;
const TOLERANCIA = 1;   // ventanas aceptadas a cada lado (cubre relojes algo desfasados y el tiempo de lectura)

const MODOS_VALIDOS = new Set(["off", "opcional", "obligatorio"]);

/**
 * Devuelve "off" | "opcional" | "obligatorio" validando entradas desconocidas
 * (utiliza CONFIG.QR_MODO como respaldo si la entrada no es válida).
 * @param {string | null | undefined} [modo]
 * @returns {QrModo}
 */
export function qrModoEfectivo(modo) {
  const m = String(modo || "").toLowerCase().trim();
  if (MODOS_VALIDOS.has(m)) return m;
  const respaldo = String(CONFIG?.QR_MODO || "").toLowerCase().trim();
  return MODOS_VALIDOS.has(respaldo) ? respaldo : "off";
}

const ENC = new TextEncoder();
/**
 * @param {string} secreto
 * @param {string} mensaje
 * @returns {Promise<string>}
 */
async function firma(secreto, mensaje) {
  if (!globalThis.crypto?.subtle) throw new Error("crypto.subtle no disponible");
  const key = await crypto.subtle.importKey("raw", ENC.encode(secreto), { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", key, ENC.encode(mensaje)));
  return [...sig.slice(0, 5)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

/**
 * @param {number} ms
 * @returns {number}
 */
export const ventana = (ms) => Math.floor(ms / VENTANA_MS);
/**
 * @param {number} ms
 * @returns {number}
 */
export const segundosRestantes = (ms) => Math.ceil((VENTANA_MS - (ms % VENTANA_MS)) / 1000);

/**
 * @param {string} codigo
 * @param {string} secreto
 * @param {number} ms
 * @returns {Promise<string>}
 */
export async function generarQR(codigo, secreto, ms) {
  const w = ventana(ms);
  return `${codigo}.${w.toString(36)}.${await firma(secreto, `${codigo}.${w}`)}`;
}

/**
 * Verifica el texto leído. Devuelve:
 *  { estatico: true }                       → es un código normal (sin firma)
 *  { ok: true, alumno }                     → QR dinámico válido
 *  { ok: false, motivo, alumno? }           → 'desconocido' | 'vencido' | 'firma' | 'sinsecreto' | 'nocrypto'
 * @param {string} texto
 * @param {(codigo: string) => Alumno | null | undefined} buscarPorCodigo
 * @param {number} ms
 * @returns {Promise<ResultadoVerificarQR>}
 */
export async function verificarQR(texto, buscarPorCodigo, ms) {
  if (buscarPorCodigo(texto)) return { estatico: true };           // un código existente (aunque contenga puntos) es estático
  const s = String(texto ?? "");
  const i2 = s.lastIndexOf(".");
  const i1 = i2 > 0 ? s.lastIndexOf(".", i2 - 1) : -1;
  if (i1 < 0) return { estatico: true };
  const codigo = s.slice(0, i1);
  const t36 = s.slice(i1 + 1, i2);
  const sig = s.slice(i2 + 1);
  if (!codigo || !t36 || !sig) return { estatico: true };

  const alumno = buscarPorCodigo(codigo);
  if (!alumno) return { ok: false, motivo: "desconocido" };
  if (!alumno.qr_secreto) return { ok: false, motivo: "sinsecreto", alumno };
  const w = parseInt(t36, 36);
  if (!Number.isFinite(w) || Math.abs(ventana(ms) - w) > TOLERANCIA) return { ok: false, motivo: "vencido", alumno };

  if (!globalThis.crypto?.subtle) return { ok: false, motivo: "nocrypto", alumno };
  try {
    const f = await firma(alumno.qr_secreto, `${codigo}.${w}`);
    return f.toLowerCase() === sig.toLowerCase() ? { ok: true, alumno } : { ok: false, motivo: "firma", alumno };
  } catch {
    return { ok: false, motivo: "nocrypto", alumno };
  }
}
