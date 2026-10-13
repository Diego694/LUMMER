// @ts-check
// QR rotativo de ingreso al instituto (migración 022).
//  · El quiosco (o personal autorizado) abre una sesión: el servidor firma un token que rota cada 15 o 30 s.
//  · El estudiante escanea el QR proyectado desde la app LUMMER (Escanear) y el servidor registra su ingreso.
// Las funciones de Supabase llaman a RPCs (SECURITY DEFINER); el backend demo/local lanza error controlado
// porque la rotación con secreto criptográfico requiere conexión con el servidor.

/**
 * @param {string} message
 * @param {any} [code]
 * @returns {Error & { code?: any }}
 */
const err = (message, code) => Object.assign(new Error(message), { code });

export const VENTANA_QR_INGRESO_DEFAULT = 30;

/**
 * Descompone el texto de un QR rotativo de ingreso ("LI.<sesión32hex>.<ventana>.<firma12>").
 * Devuelve null si el texto no tiene ese formato (p. ej. carnet de alumno o QR de curso LQ).
 * @param {unknown} texto
 * @returns {{ sesion: string, ventana: number, firma: string } | null}
 */
export function parsearTokenQrIngreso(texto) {
  const p = String(texto ?? "").trim().split(".");
  if (p.length !== 4 || p[0] !== "LI" || !/^[0-9a-f]{32}$/.test(p[1]) || !/^\d{1,12}$/.test(p[2]) || !p[3]) return null;
  return { sesion: p[1], ventana: Number(p[2]), firma: p[3] };
}

/**
 * Llama a un RPC y devuelve sus datos.
 * @param {any} sb @param {string} nombre @param {Record<string, unknown>} args
 */
async function rpc(sb, nombre, args) {
  const { data, error } = await sb.rpc(nombre, args);
  if (error) throw err(error.message, error.code);
  return data;
}

/**
 * Marca del estudiante en Supabase mediante el QR rotativo de ingreso.
 * @param {any} sb
 * @param {string} token
 */
export function marcarQrIngresoSb(sb, token) {
  return rpc(sb, "marcar_asistencia_qr_ingreso", { p_token: token });
}

export const qrIngresoSupabase = {
  /**
   * Abre o reemplaza una sesión de QR de ingreso general.
   * @this {any}
   * @param {number} [segundos] Intervalo de rotación (15 o 30 s).
   * @param {number} [minutos] Duración de la sesión en minutos (por defecto 600 min).
   * @returns {Promise<{ sesion: string, id: string, intervalo: number, expira_en: string }>}
   */
  qrIngresoAbrir(segundos = 30, minutos = 600) {
    return rpc(this.sb, "qr_ingreso_abrir", { p_segundos: segundos, p_minutos: minutos });
  },

  /**
   * Obtiene el token vigente de la sesión activa de QR de ingreso.
   * @this {any}
   * @param {string} sesionId
   * @returns {Promise<{ token: string, segundos: number, expira_en: string, cerrada?: boolean }>}
   */
  qrIngresoToken(sesionId) {
    return rpc(this.sb, "qr_ingreso_token", { p_sesion: sesionId });
  },

  /**
   * Cierra la sesión activa de QR de ingreso.
   * @this {any}
   * @param {string} sesionId
   * @returns {Promise<void>}
   */
  async qrIngresoCerrar(sesionId) {
    await rpc(this.sb, "qr_ingreso_cerrar", { p_sesion: sesionId });
  },
};

export const qrIngresoDemo = {
  /**
   * @this {any}
   * @param {number} [_segundos]
   * @param {number} [_minutos]
   * @returns {Promise<never>}
   */
  async qrIngresoAbrir(_segundos = 30, _minutos = 600) {
    throw err("Disponible solo con conexión al servidor");
  },

  /**
   * @this {any}
   * @param {string} _sesionId
   * @returns {Promise<never>}
   */
  async qrIngresoToken(_sesionId) {
    throw err("Disponible solo con conexión al servidor");
  },

  /**
   * @this {any}
   * @param {string} _sesionId
   * @returns {Promise<never>}
   */
  async qrIngresoCerrar(_sesionId) {
    throw err("Disponible solo con conexión al servidor");
  },
};
