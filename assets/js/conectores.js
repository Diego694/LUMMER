// @ts-check
// Conectores de bases de datos externas: validan llaves, prueban la conexión y copian TODOS los datos de LUMMER a otra base.
// Solo se aceptan llaves PÚBLICAS (anon/publishable/API key web). Las llaves secretas (service_role, claves privadas) se rechazan:
// nunca deben pegarse en una web ni guardarse en la base.
//   supabase  → Supabase u otro Postgres con API PostgREST
//   rest      → API REST propia (puerta de enlace) para MySQL, MariaDB, MongoDB u otra; contrato en docs/CONEXIONES.md
//   firestore → Firebase Firestore (API REST)

/** @typedef {'supabase' | 'rest' | 'firestore'} TipoConexion */
/** @typedef {{ tipo: TipoConexion, url: string, llave: string }} Destino */
/** @typedef {(url: string, init?: RequestInit) => Promise<Response>} FetchFn */

/** @type {Record<TipoConexion, { etiqueta: string, urlEtiqueta: string, urlEjemplo: string, llaveEtiqueta: string }>} */
export const TIPOS = {
  supabase: { etiqueta: "Supabase / Postgres (PostgREST)", urlEtiqueta: "URL del proyecto", urlEjemplo: "https://xxxx.supabase.co", llaveEtiqueta: "Llave pública (publishable / anon)" },
  rest: { etiqueta: "API REST (MySQL, MariaDB, MongoDB u otra)", urlEtiqueta: "URL base de la API", urlEjemplo: "https://api.tu-dominio.com/lummer", llaveEtiqueta: "Token de acceso de la API" },
  firestore: { etiqueta: "Firebase / Firestore", urlEtiqueta: "ID del proyecto Firebase", urlEjemplo: "mi-instituto-12345", llaveEtiqueta: "API key web de Firebase" },
};

/** Tablas de LUMMER en orden de dependencias (primero las que otras referencian). */
export const TABLAS = [
  "colegios", "niveles", "grados", "docentes", "alumnos", "comunicados", "cursos", "curso_docentes",
  "asistencias", "justificaciones", "avisos_apoderados", "asistencias_curso",
  "curso_materiales", "curso_actividades", "curso_entregas",
];

/** Clave que identifica una fila cuando no es `id`. */
const CLAVE_ID = { curso_docentes: ["curso_id", "user_id"] };

/** @param {string} b64 @returns {any} */
function jwtPayload(b64) {
  try { return JSON.parse(atob(b64.replace(/-/g, "+").replace(/_/g, "/"))); } catch { return null; }
}

/**
 * ¿Se puede guardar esta llave? Rechaza llaves secretas.
 * @param {string} llave
 * @returns {{ ok: boolean, motivo?: string }}
 */
export function clasificarLlave(llave) {
  const k = String(llave || "").trim();
  if (!k) return { ok: false, motivo: "Falta la llave." };
  if (/^sb_secret_/i.test(k)) return { ok: false, motivo: "Es una llave SECRETA de Supabase (sb_secret_). Usa solo la publishable/anon." };
  if (/BEGIN (RSA |EC )?PRIVATE KEY|"private_key"|"type"\s*:\s*"service_account"/i.test(k)) return { ok: false, motivo: "Es una credencial privada (cuenta de servicio). Nunca la pegues aquí." };
  const partes = k.split(".");
  if (partes.length === 3) {
    const p = jwtPayload(partes[1]);
    if (p && /service_role|supabase_admin/i.test(String(p.role || ""))) return { ok: false, motivo: "Es la llave service_role, que salta toda la seguridad. Usa la anon/publishable." };
  }
  if (/\s/.test(k)) return { ok: false, motivo: "La llave no debe contener espacios." };
  return { ok: true };
}

/**
 * Normaliza y valida la URL o el ID según el tipo.
 * @param {TipoConexion} tipo
 * @param {string} url
 * @returns {{ ok: boolean, valor: string, motivo?: string }}
 */
export function normalizarDestino(tipo, url) {
  const v = String(url || "").trim();
  if (tipo === "firestore") {
    return /^[a-z][a-z0-9-]{4,29}$/.test(v) ? { ok: true, valor: v } : { ok: false, valor: v, motivo: "El ID del proyecto Firebase son 6–30 letras minúsculas, números o guiones." };
  }
  const sinBarra = v.replace(/\/+$/, "");
  if (!/^https:\/\/[^\s/]+(\/[^\s]*)?$/.test(sinBarra) && !/^http:\/\/(localhost|127\.0\.0\.1)(:\d+)?(\/[^\s]*)?$/.test(sinBarra)) {
    return { ok: false, valor: v, motivo: "La URL debe empezar con https:// (http solo para localhost)." };
  }
  return { ok: true, valor: sinBarra };
}

/** @param {string} id */
const baseFirestore = (id) => `https://firestore.googleapis.com/v1/projects/${id}/databases/(default)/documents`;

/**
 * Cabeceras de autenticación de Supabase. Las llaves nuevas (sb_publishable_…) no son JWT: solo van en `apikey`.
 * @param {string} llave
 * @returns {Record<string, string>}
 */
const cabecerasSupabase = (llave) => (llave.startsWith("sb_") ? { apikey: llave } : { apikey: llave, Authorization: `Bearer ${llave}` });

/**
 * Prueba que el destino responda y acepte la llave.
 * @param {Destino} d
 * @param {FetchFn} [f]
 * @returns {Promise<{ ok: boolean, ms: number, detalle: string }>}
 */
export async function probarConexion(d, f = (u, i) => fetch(u, i)) {
  const t0 = Date.now();
  try {
    if (d.tipo === "supabase") {
      const r = await f(`${d.url}/rest/v1/`, { headers: cabecerasSupabase(d.llave) });
      if (r.ok) return { ok: true, ms: Date.now() - t0, detalle: "Conexión correcta." };
      if (r.status === 401 || r.status === 403) {
        // Supabase restringe el listado del esquema (/rest/v1/) a la llave secreta: eso no significa que la llave pública sea mala.
        // Solo se rechaza si el mensaje dice que la llave no es válida.
        const cuerpo = await r.text?.().catch(() => "") ?? "";
        if (/invalid (api key|jwt)|no api key/i.test(cuerpo) || !cuerpo) return { ok: false, ms: Date.now() - t0, detalle: "El servidor respondió, pero rechazó la llave (revisa que sea la llave publishable/anon de ESTE proyecto, completa y sin espacios)." };
        return { ok: true, ms: Date.now() - t0, detalle: "Llave aceptada (el servidor no permite listar el esquema con una llave pública, es normal)." };
      }
      return { ok: false, ms: Date.now() - t0, detalle: `El servidor respondió ${r.status}.` };
    }
    if (d.tipo === "rest") {
      const r = await f(`${d.url}/salud`, { headers: { Authorization: `Bearer ${d.llave}` } });
      if (r.ok) return { ok: true, ms: Date.now() - t0, detalle: "La API responde en /salud." };
      if (r.status === 401 || r.status === 403) return { ok: false, ms: Date.now() - t0, detalle: "La API respondió, pero rechazó el token." };
      return { ok: false, ms: Date.now() - t0, detalle: `La API respondió ${r.status} en /salud.` };
    }
    const r = await f(`${baseFirestore(d.url)}?pageSize=1&key=${encodeURIComponent(d.llave)}`);
    if (r.ok) return { ok: true, ms: Date.now() - t0, detalle: "Firestore responde y las reglas permiten leer." };
    if (r.status === 403) return { ok: true, ms: Date.now() - t0, detalle: "Firestore responde; sus reglas bloquean lectura anónima (revisa las reglas para poder importar)." };
    if (r.status === 400) return { ok: false, ms: Date.now() - t0, detalle: "API key de Firebase inválida." };
    return { ok: false, ms: Date.now() - t0, detalle: `Firestore respondió ${r.status}.` };
  } catch (e) {
    return { ok: false, ms: Date.now() - t0, detalle: `No se pudo conectar (${/** @type {any} */ (e)?.message || "error de red"}). ¿Está bien la URL y permite CORS?` };
  }
}

/**
 * Convierte un valor JS al formato tipado de Firestore.
 * @param {any} v
 * @returns {any}
 */
export function aFirestore(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === "boolean") return { booleanValue: v };
  if (typeof v === "number") return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === "string") return { stringValue: v };
  if (Array.isArray(v)) return { arrayValue: { values: v.map(aFirestore) } };
  return { mapValue: { fields: Object.fromEntries(Object.entries(v).map(([k, x]) => [k, aFirestore(x)])) } };
}

/** @template T @param {T[]} lista @param {number} n @returns {T[][]} */
export function lotes(lista, n) {
  /** @type {T[][]} */ const r = [];
  for (let i = 0; i < lista.length; i += n) r.push(lista.slice(i, i + n));
  return r;
}

/**
 * Identificador de documento de una fila.
 * @param {string} tabla @param {Record<string, any>} fila @param {number} i
 */
export function idDeFila(tabla, fila, i) {
  const claves = /** @type {Record<string, string[]>} */ (CLAVE_ID)[tabla];
  if (claves) return claves.map((c) => String(fila[c])).join("_");
  return fila.id != null ? String(fila.id) : `${tabla}-${i}`;
}

/**
 * Paquete portable con todos los datos. `leerTabla` devuelve las filas de una tabla.
 * @param {(tabla: string) => Promise<Record<string, any>[]>} leerTabla
 * @param {(msg: string) => void} [alProgresar]
 */
export async function crearPaquete(leerTabla, alProgresar = () => {}) {
  /** @type {Record<string, Record<string, any>[]>} */ const tablas = {};
  /** @type {string[]} */ const avisos = [];
  for (const t of TABLAS) {
    alProgresar(`Leyendo ${t}…`);
    try { tablas[t] = await leerTabla(t); }
    catch (/** @type {any} */ e) { tablas[t] = []; avisos.push(`${t}: no se pudo leer (${e?.message || "error"})`); }
  }
  const conteos = Object.fromEntries(Object.entries(tablas).map(([k, v]) => [k, v.length]));
  return { formato: "lummer-paquete", version: 1, creado: new Date().toISOString(), tablas, conteos, avisos };
}

/**
 * Valida la forma de un paquete antes de importarlo.
 * @param {any} p
 * @returns {{ ok: boolean, motivo?: string }}
 */
export function validarPaquete(p) {
  if (!p || p.formato !== "lummer-paquete" || typeof p.tablas !== "object") return { ok: false, motivo: "No es un paquete de LUMMER." };
  for (const t of Object.keys(p.tablas)) if (!Array.isArray(p.tablas[t])) return { ok: false, motivo: `La tabla ${t} no es una lista.` };
  return { ok: true };
}

/**
 * Copia el paquete al destino. Siempre «upsert»: se puede repetir sin duplicar.
 * @param {Destino} d
 * @param {{ tablas: Record<string, Record<string, any>[]> }} paquete
 * @param {{ alProgresar?: (msg: string, hecho: number, total: number) => void, f?: FetchFn, lote?: number }} [op]
 * @returns {Promise<{ tabla: string, enviadas: number, error?: string }[]>}
 */
export async function importarPaquete(d, paquete, { alProgresar = () => {}, f = (u, i) => fetch(u, i), lote = 400 } = {}) {
  const tablas = TABLAS.filter((t) => (paquete.tablas[t] || []).length);
  /** @type {{ tabla: string, enviadas: number, error?: string }[]} */ const informe = [];
  let hecho = 0;
  for (const t of tablas) {
    const filas = paquete.tablas[t];
    let enviadas = 0;
    /** @type {string | undefined} */ let error;
    alProgresar(`Copiando ${t} (${filas.length})…`, hecho, tablas.length);
    try {
      for (const grupo of lotes(filas, lote)) {
        let r;
        if (d.tipo === "supabase") {
          r = await f(`${d.url}/rest/v1/${t}`, { method: "POST", headers: { ...cabecerasSupabase(d.llave), "Content-Type": "application/json", Prefer: "resolution=merge-duplicates,return=minimal" }, body: JSON.stringify(grupo) });
        } else if (d.tipo === "rest") {
          r = await f(`${d.url}/importar/${t}`, { method: "POST", headers: { Authorization: `Bearer ${d.llave}`, "Content-Type": "application/json" }, body: JSON.stringify({ filas: grupo }) });
        } else {
          const base = baseFirestore(d.url);
          const writes = grupo.map((fila, i) => ({ update: { name: `projects/${d.url}/databases/(default)/documents/${t}/${idDeFila(t, fila, enviadas + i)}`, fields: Object.fromEntries(Object.entries(fila).map(([k, v]) => [k, aFirestore(v)])) } }));
          r = await f(`${base}:commit?key=${encodeURIComponent(d.llave)}`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ writes }) });
        }
        if (!r.ok) {
          error = r.status === 401 || r.status === 403
            ? "El destino rechazó la escritura (permisos/RLS). Crea el esquema en el destino y habilita la escritura para esta llave."
            : `El destino respondió ${r.status}.`;
          break;
        }
        enviadas += grupo.length;
      }
    } catch (/** @type {any} */ e) { error = `Sin conexión con el destino (${e?.message || "error de red"}).`; }
    informe.push({ tabla: t, enviadas, ...(error ? { error } : {}) });
    hecho++;
    if (error) break;   // no seguir si falló: evita un destino a medias sin saberlo
  }
  alProgresar("Terminado", hecho, tablas.length);
  return informe;
}

/**
 * Cuenta filas en el destino para comparar con el paquete.
 * @param {Destino} d @param {string} tabla @param {FetchFn} [f]
 * @returns {Promise<number | null>} null = el tipo no permite contar
 */
export async function contarEnDestino(d, tabla, f = (u, i) => fetch(u, i)) {
  try {
    if (d.tipo === "supabase") {
      const r = await f(`${d.url}/rest/v1/${tabla}?select=*`, { method: "HEAD", headers: { ...cabecerasSupabase(d.llave), Prefer: "count=exact", Range: "0-0" } });
      const m = /\/(\d+|\*)$/.exec(r.headers.get("content-range") || "");
      return m && m[1] !== "*" ? Number(m[1]) : null;
    }
    if (d.tipo === "rest") {
      const r = await f(`${d.url}/conteo/${tabla}`, { headers: { Authorization: `Bearer ${d.llave}` } });
      if (!r.ok) return null;
      const j = await r.json();
      return typeof j.total === "number" ? j.total : null;
    }
  } catch { /* se informa como no verificable */ }
  return null;
}
