// @ts-check
// QR de asistencia por curso (migración 019) y bitácora de registros con segundos.
//  · El docente abre una sesión: el servidor firma un token que cambia cada 30 s y que se muestra como QR.
//  · El estudiante escanea el QR y el servidor marca su asistencia en ese curso con la hora exacta.
// Las funciones de Supabase solo llaman a RPC (SECURITY DEFINER); el demo reproduce el flujo con localStorage.
import { uid } from "./utils.js";

/**
 * @param {string} message
 * @param {any} [code]
 * @returns {Error & { code?: any }}
 */
const err = (message, code) => Object.assign(new Error(message), { code });
/** @param {any} db @param {string} n */
const tabla = (db, n) => (db[n] ||= []);

export const VENTANA_QR_CURSO_S = 30;

/**
 * Descompone el texto de un QR de asistencia por curso ("LQ.<sesión>.<ventana>.<firma>").
 * Devuelve null si el texto no tiene ese formato (p. ej. el carnet de otro alumno).
 * @param {unknown} texto
 * @returns {{ sesion: string, ventana: number, firma: string } | null}
 */
export function parsearTokenQrCurso(texto) {
  const p = String(texto ?? "").trim().split(".");
  if (p.length !== 4 || p[0] !== "LQ" || !/^[0-9a-f]{32}$/.test(p[1]) || !/^\d{1,12}$/.test(p[2]) || !p[3]) return null;
  return { sesion: p[1], ventana: Number(p[2]), firma: p[3] };
}

/** Firma del demo (NO es segura: solo evita errores de tipeo; la real la calcula el servidor). @param {string} secreto @param {string} sesion @param {number} w */
function firmaDemo(secreto, sesion, w) {
  let h = 5381;
  for (const c of `${secreto}:${sesion}:${w}`) h = ((h << 5) + h + c.charCodeAt(0)) >>> 0;
  return h.toString(16).padStart(8, "0");
}
const hex = (/** @type {string} */ id) => id.replace(/-/g, "");

/**
 * Etiqueta legible del origen de un registro.
 * @param {string | null | undefined} origen
 * @returns {string}
 */
export function etiquetaOrigen(origen) {
  /** @type {Record<string, string>} */
  const m = { qr: "Carnet QR", nfc: "NFC", manual: "Manual", alumno: "El propio estudiante", masivo: "Masivo", "qr-docente": "QR del docente", kiosco: "Quiosco", quiosco: "Quiosco" };
  return m[String(origen || "").toLowerCase()] || (origen ? String(origen) : "—");
}

/**
 * Hora exacta con segundos (HH:MM:SS) y fecha en la zona del instituto.
 * @param {string | null | undefined} iso
 * @param {string} [zona]
 * @returns {{ fecha: string, hora: string }}
 */
export function momentoExacto(iso, zona = "America/Lima") {
  if (!iso) return { fecha: "—", hora: "—" };
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return { fecha: "—", hora: "—" };
  return {
    fecha: d.toLocaleDateString("es-PE", { timeZone: zona, day: "2-digit", month: "2-digit", year: "numeric" }),
    hora: d.toLocaleTimeString("es-PE", { timeZone: zona, hour12: false, hour: "2-digit", minute: "2-digit", second: "2-digit" }),
  };
}

/**
 * Cuenta registros de la bitácora por tipo.
 * @param {{ tipo: string }[]} filas
 * @returns {Record<string, number>}
 */
export function resumenBitacora(filas) {
  /** @type {Record<string, number>} */
  const r = {};
  for (const f of filas) r[f.tipo] = (r[f.tipo] || 0) + 1;
  return r;
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

/** Marca del estudiante en Supabase. @param {any} sb @param {string} token */
export function marcarQrCursoSb(sb, token) { return rpc(sb, "marcar_asistencia_qr_curso", { p_token: token }); }

/**
 * Marca del estudiante en el demo (localStorage compartido con el panel).
 * @param {any} db @param {() => void} guardar @param {string} userId @param {string} token
 * @returns {Promise<{ estado: string, mensaje: string, curso?: string, hora?: string }>}
 */
export async function marcarQrCursoDemo(db, guardar, userId, token) {
  const p = parsearTokenQrCurso(token);
  if (!p) return { estado: "invalido", mensaje: "Ese código QR no es de asistencia." };
  const a = (db.alumnos || []).find((/** @type {any} */ x) => x.user_id === userId);
  if (!a) throw err("Esta cuenta no es de un estudiante");
  if (a.aprobado === false) throw err("Tu registro aún no fue aprobado");
  const s = tabla(db, "sesiones_qr_curso").find((/** @type {any} */ x) => hex(x.id) === p.sesion);
  if (!s || p.firma !== firmaDemo(s.secreto, s.id, p.ventana)) return { estado: "invalido", mensaje: "Código QR no válido." };
  if (s.cerrada_en || new Date(s.expira_en).getTime() <= Date.now()) return { estado: "cerrado", mensaje: "El docente ya cerró esta asistencia." };
  if (Math.abs(Math.floor(Date.now() / 30000) - p.ventana) > 1) return { estado: "vencido", mensaje: "El QR venció. Escanea el que se muestra ahora en pantalla." };
  const c = (db.cursos || []).find((/** @type {any} */ x) => x.id === s.curso_id);
  if (!c || c.nivel !== a.nivel || (c.grado && c.grado !== a.grado)) return { estado: "no_pertenece", mensaje: "Este curso no corresponde a tu carrera o ciclo." };
  const ahora = new Date();
  const fecha = ahora.toLocaleDateString("en-CA", { timeZone: "America/Lima" });
  const hora = ahora.toLocaleTimeString("en-GB", { timeZone: "America/Lima", hour12: false });
  const l = tabla(db, "asistencias_curso");
  const previa = l.find((/** @type {any} */ x) => x.alumno_id === a.id && x.curso_id === c.id && x.fecha === fecha);
  if (previa) return { estado: "ya_marcado", curso: c.nombre, hora: previa.hora, mensaje: "Ya habías marcado tu asistencia en este curso." };
  l.push({ id: uid(), colegio_id: a.colegio_id, alumno_id: a.id, curso_id: c.id, fecha, hora, registrado_por: userId, registrado_en: ahora.toISOString(), origen: "qr-docente", sesion_id: s.id });
  guardar();
  return { estado: "ok", curso: c.nombre, hora, mensaje: "Asistencia registrada." };
}

export const qrCursoSupabase = {
  /** @this {any} @param {string} cursoId @param {number} [minutos] @returns {Promise<{ id: string, expira_en: string }>} */
  qrCursoAbrir(cursoId, minutos = 20) { return rpc(this.sb, "qr_curso_abrir", { p_curso: cursoId, p_minutos: minutos }); },
  /** @this {any} @param {string} sesionId @returns {Promise<{ token?: string, segundos?: number, cerrada?: boolean }>} */
  qrCursoToken(sesionId) { return rpc(this.sb, "qr_curso_token", { p_sesion: sesionId }); },
  /** @this {any} @param {string} sesionId @returns {Promise<void>} */
  async qrCursoCerrar(sesionId) { await rpc(this.sb, "qr_curso_cerrar", { p_sesion: sesionId }); },
  /**
   * @this {any}
   * @param {{ desde?: string, hasta?: string, tipo?: string, limite?: number, colegio?: string }} [o]
   * @returns {Promise<any[]>}
   */
  async bitacoraRegistros(o = {}) {
    return (await rpc(this.sb, "bitacora_registros", { p_desde: o.desde || null, p_hasta: o.hasta || null, p_tipo: o.tipo || null, p_limite: o.limite || 500, p_colegio: o.colegio || null })) || [];
  },
};

export const qrCursoDemo = {
  /** @this {any} @param {string} cursoId @param {number} [minutos] */
  async qrCursoAbrir(cursoId, minutos = 20) {
    this.reload();
    const l = tabla(this.db, "sesiones_qr_curso");
    l.forEach((/** @type {any} */ s) => { if (s.curso_id === cursoId && !s.cerrada_en) s.cerrada_en = new Date().toISOString(); });
    const s = { id: uid(), colegio_id: this.db.colegio.id, curso_id: cursoId, abierta_por: "demo-user", secreto: uid(), creada_en: new Date().toISOString(), expira_en: new Date(Date.now() + minutos * 60000).toISOString(), cerrada_en: null };
    l.push(s); this.persist();
    return { id: s.id, expira_en: s.expira_en };
  },
  /** @this {any} @param {string} sesionId */
  async qrCursoToken(sesionId) {
    this.reload();
    const s = tabla(this.db, "sesiones_qr_curso").find((/** @type {any} */ x) => x.id === sesionId);
    if (!s || s.cerrada_en || new Date(s.expira_en).getTime() <= Date.now()) return { cerrada: true };
    const w = Math.floor(Date.now() / 30000);
    return { token: `LQ.${hex(s.id)}.${w}.${firmaDemo(s.secreto, s.id, w)}`, segundos: 30 - (Math.floor(Date.now() / 1000) % 30), expira_en: s.expira_en };
  },
  /** @this {any} @param {string} sesionId */
  async qrCursoCerrar(sesionId) {
    this.reload();
    const s = tabla(this.db, "sesiones_qr_curso").find((/** @type {any} */ x) => x.id === sesionId);
    if (s && !s.cerrada_en) { s.cerrada_en = new Date().toISOString(); this.persist(); }
  },
  /**
   * @this {any}
   * @param {{ desde?: string, hasta?: string, tipo?: string, limite?: number }} [o]
   * @returns {Promise<any[]>}
   */
  async bitacoraRegistros(o = {}) {
    this.reload();
    const hoy = new Date().toLocaleDateString("en-CA", { timeZone: "America/Lima" });
    const dentro = (/** @type {string | undefined} */ iso) => {
      if (!iso) return false;
      const f = new Date(iso).toLocaleDateString("en-CA", { timeZone: "America/Lima" });
      return f >= (o.desde || hoy) && f <= (o.hasta || hoy);
    };
    const al = (/** @type {string} */ id) => (this.db.alumnos || []).find((/** @type {any} */ a) => a.id === id);
    const cu = (/** @type {string} */ id) => (this.db.cursos || []).find((/** @type {any} */ c) => c.id === id);
    /** @type {any[]} */
    const out = [];
    for (const s of tabla(this.db, "asistencias")) {
      const a = al(s.alumno_id);
      if (a && dentro(s.registrado_en)) out.push({ tipo: "ingreso", cuando: s.registrado_en, persona: a.nombre, carrera: a.nivel, ciclo: a.grado, curso: null, hora: s.hora, origen: s.origen, actor: s.registrado_por === a.user_id ? a.nombre : "Administrador demo", actor_rol: s.registrado_por === a.user_id ? "Estudiante" : "Administrador" });
    }
    for (const k of tabla(this.db, "asistencias_curso")) {
      const a = al(k.alumno_id), c = cu(k.curso_id);
      if (a && c && dentro(k.registrado_en)) out.push({ tipo: "curso", cuando: k.registrado_en, persona: a.nombre, carrera: a.nivel, ciclo: a.grado, curso: c.nombre, hora: k.hora, origen: k.origen, actor: k.registrado_por === a.user_id ? a.nombre : "Administrador demo", actor_rol: k.registrado_por === a.user_id ? "Estudiante" : "Administrador" });
    }
    for (const q of tabla(this.db, "sesiones_qr_curso")) {
      const c = cu(q.curso_id);
      if (c && dentro(q.creada_en)) out.push({ tipo: "qr_abierto", cuando: q.creada_en, persona: null, carrera: c.nivel, ciclo: c.grado, curso: c.nombre, hora: null, origen: "qr-docente", actor: "Administrador demo", actor_rol: "Administrador" });
    }
    return out.filter((f) => !o.tipo || f.tipo === o.tipo).sort((x, y) => String(y.cuando).localeCompare(String(x.cuando))).slice(0, o.limite || 500);
  },
};
