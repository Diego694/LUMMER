// @ts-check
// Capa de datos del portal de estudiantes. Dos adaptadores con la misma interfaz:
//  - SupabaseEstudiante: producción. Solo usa Auth, el bucket privado 'fotos-alumnos' y las funciones SQL
//    de supabase/migrations/002_estudiantes.sql (el estudiante nunca escribe tablas directamente).
//  - DemoEstudiante: localStorage compartido con el panel del docente (mismo navegador) para probar sin servidor.
import { CONFIG, DEMO_SCHOOL_CODE, isDemoMode } from "../config.js";
import { buildDemoDB } from "../demo-data.js";
import { uid } from "../utils.js";

/** @typedef {import('../tipos.d.ts').Alumno} Alumno */
/** @typedef {import('../tipos.d.ts').BackendEstudiante} BackendEstudiante */
/** @typedef {import('../tipos.d.ts').DatosRegistroEstudiante} DatosRegistroEstudiante */
/** @typedef {import('../tipos.d.ts').InfoColegioResult} InfoColegioResult */
/** @typedef {import('../tipos.d.ts').MiRegistroResult} MiRegistroResult */
/** @typedef {import('../tipos.d.ts').EstadoSolicitudResult} EstadoSolicitudResult */
/** @typedef {import('../tipos.d.ts').LogCliente} LogCliente */
/** @typedef {import('../tipos.d.ts').ErrorLogItem} ErrorLogItem */
/** @typedef {import('../tipos.d.ts').DemoDB} DemoDB */

/**
 * @param {string} message
 * @param {string} [code]
 * @returns {Error & { code?: string }}
 */
const err = (message, code) => Object.assign(new Error(message), { code });

/**
 * Los mensajes de las funciones SQL usan "colegio"/"nivel o grado"; aquí se presentan como instituto/carrera/ciclo.
 * @param {unknown} [msg]
 * @returns {string}
 */
export function vocabulario(msg = "") {
  return String(msg).replace(/Nivel o grado/g, "Carrera o ciclo").replace(/\bcolegio\b/g, "instituto").replace(/\bColegio\b/g, "Instituto");
}

/**
 * fetch con tiempo de espera (mismo criterio que el panel del docente; no se importa api.js para no crear un segundo cliente de sesión).
 * @param {string | URL | Request} url
 * @param {RequestInit} [opts]
 * @returns {Promise<Response>}
 */
function fetchConTimeout(url, opts = {}) {
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), String(url).includes("/storage/") ? 60000 : 15000);
  if (opts.signal) opts.signal.addEventListener("abort", () => ctl.abort());
  return fetch(url, { ...opts, signal: ctl.signal }).finally(() => clearTimeout(t));
}

/**
 * @param {Blob} blob
 * @returns {Promise<string>}
 */
function blobADataUrl(blob) {
  return new Promise((res, rej) => {
    const r = new FileReader();
    r.onload = () => res(/** @type {string} */ (r.result));
    r.onerror = () => rej(r.error);
    r.readAsDataURL(blob);
  });
}

/* ------------------------------- DEMO ------------------------------- */
/**
 * @implements {BackendEstudiante}
 */
class DemoEstudiante {
  mode = "demo";
  KEY = "ra-demo-db-v1";
  USERS = "ra-demo-students";
  SESSION = "ra-demo-student-session";

  /** @returns {DemoDB} */
  load() {
    let db = null;
    try {
      const raw = localStorage.getItem(this.KEY);
      if (raw) db = JSON.parse(raw);
    } catch { /* se regenera */ }
    if (!db) { db = buildDemoDB(); this.save(db); }
    return db;
  }
  /** @param {DemoDB} db */
  save(db) { localStorage.setItem(this.KEY, JSON.stringify(db)); }
  /** @returns {Record<string, { pass: string; id: string }>} */
  users() {
    try {
      const raw = localStorage.getItem(this.USERS);
      return (raw ? JSON.parse(raw) : null) || {};
    } catch {
      return {};
    }
  }

  async init() {
    const id = localStorage.getItem(this.SESSION);
    const us = this.users();
    const email = id ? Object.keys(us).find((e) => us[e].id === id) : null;
    return id && email ? { id, email } : null;
  }
  async horaServidor() { return Date.now() + (Number(localStorage.getItem("ra-sim-desfase-ms")) || 0); }
  /** @param {Partial<LogCliente> | ErrorLogItem} row */
  async registrarError(row) {
    const db = this.load();
    (db.logs_cliente ||= []).unshift(/** @type {any} */ ({ id: uid(), creado_en: new Date().toISOString(), colegio_id: db.colegio.id, ...row }));
    db.logs_cliente = db.logs_cliente.slice(0, 200);
    this.save(db);
  }
  async solicitarRecuperacion() { throw err("En modo demo no se envían correos."); }
  async cambiarPassword() { throw err("En modo demo no hay recuperación de contraseña."); }
  /** @param {() => void} [_cb] */
  alRecuperar(_cb) { /* sin eventos en demo */ }
  /** @param {{ id: string }} user */
  async eliminarMiCuenta(user) {
    const db = this.load();
    db.alumnos = db.alumnos.filter((a) => a.user_id !== user.id);
    this.save(db);
    const us = this.users(); const email = Object.keys(us).find((e) => us[e].id === user.id);
    if (email) { delete us[email]; localStorage.setItem(this.USERS, JSON.stringify(us)); }
    localStorage.removeItem(this.SESSION);
  }
  /**
   * @param {string} email
   * @param {string} password
   */
  async signUp(email, password) {
    const e = email.trim().toLowerCase(), us = this.users();
    if (us[e]) throw err("Ese correo ya está registrado", "exists");
    us[e] = { pass: password, id: uid() };
    localStorage.setItem(this.USERS, JSON.stringify(us));
    localStorage.setItem(this.SESSION, us[e].id);
    return { id: us[e].id, email: e };
  }
  /**
   * @param {string} email
   * @param {string} password
   */
  async signIn(email, password) {
    const e = email.trim().toLowerCase(), u = this.users()[e];
    if (!u || u.pass !== password) throw err("Credenciales inválidas", "auth");
    localStorage.setItem(this.SESSION, u.id);
    return { id: u.id, email: e };
  }
  async signOut() { localStorage.removeItem(this.SESSION); }
  async tokenAvisos() { return null; }
  /** @param {string} token */
  async estadoSolicitud(token) { const a = this.load().alumnos.find((x) => x.notif_token === token); return a ? { aprobado: a.aprobado !== false, nombre: a.nombre.split(" ")[0] } : null; }

  /** @param {string} codigo */
  async infoColegio(codigo) {
    const db = this.load();
    if (String(codigo).trim().toUpperCase() !== String(db.colegio.codigo_registro || DEMO_SCHOOL_CODE).toUpperCase()) return null;
    return { nombre: db.colegio.nombre, niveles: db.niveles.map((n) => n.nombre).sort(), grados: db.grados.map((g) => ({ nivel: g.nivel, nombre: g.nombre })) };
  }
  /**
   * @param {{ id: string }} user
   * @param {DatosRegistroEstudiante} f
   */
  async registrar(user, f) {
    const db = this.load();
    if (db.alumnos.some((a) => a.user_id === user.id)) throw err("Ya tienes un registro");
    if (!(await this.infoColegio(f.codigoColegio))) throw err("Código de instituto inválido");
    if (!db.grados.some((g) => g.nivel === f.nivel && g.nombre === f.grado)) throw err("Carrera o ciclo inválido");
    /** @type {string} */
    let codigo;
    do { codigo = "e" + uid().replace(/-/g, "").slice(0, 10); } while (db.alumnos.some((a) => a.codigo === codigo));
    /** @type {Alumno} */
    const a = {
      id: uid(), colegio_id: db.colegio.id, codigo, nombre: `${f.nombres.trim()} ${f.apellidos.trim()}`, nivel: f.nivel, grado: f.grado,
      apoderado: f.apoderado || "", estado: "ACTIVO", user_id: user.id, nombres: f.nombres.trim(), apellidos: f.apellidos.trim(),
      dni: f.dni || null, notif_token: Array.from({ length: 32 }, () => "0123456789abcdef"[Math.floor(Math.random() * 16)]).join(""), aprobado: false, consentimiento_en: new Date().toISOString(), registrado_en: new Date().toISOString(),
      apoderado_telefono: f.apoderadoTel || null, apoderado_email: (f.apoderadoEmail || "").toLowerCase() || null, qr_secreto: uid().replace(/-/g, ""), codigo_apoderado: Array.from({ length: 12 }, () => "0123456789ABCDEF"[Math.floor(Math.random() * 16)]).join(""),
    };
    db.alumnos.push(a);
    this.save(db);
    return a;
  }
  /** @param {{ id: string }} [user] */
  async miRegistro(user) {
    if (!user) return null;
    const db = this.load();
    const a = db.alumnos.find((x) => x.user_id === user.id);
    return a ? { alumno: a, colegio: db.colegio.nombre, qr_modo: db.colegio.qr_modo || "obligatorio" } : null;
  }
  /**
   * @param {{ id: string }} user
   * @param {Blob} blob
   */
  async subirFoto(user, blob) {
    const db = this.load();
    const a = db.alumnos.find((x) => x.user_id === user.id);
    if (!a) throw err("Primero completa tu registro");
    a.foto_data = await blobADataUrl(blob);
    a.foto_path = `${user.id}/foto-${Date.now()}.jpg`;
    this.save(db);
    return a;
  }
  /** @param {{ id?: string; foto_data?: string | null }} alumno */
  async fotoUrl(alumno) { return this.load().alumnos.find((x) => x.id === alumno.id)?.foto_data || null; }
}

/* ----------------------------- SUPABASE ----------------------------- */
/**
 * @implements {BackendEstudiante}
 */
class SupabaseEstudiante {
  mode = "supabase";
  /** @type {any} */
  sb;

  constructor() {
    if (typeof window === "undefined" || !window.supabase) throw new Error("No se pudo cargar la librería de Supabase (¿sin conexión?)");
    // storageKey propio: la sesión del estudiante no debe mezclarse con la del panel del docente (mismo origen).
    this.sb = window.supabase.createClient(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, { auth: { storageKey: "ra-estudiante-auth" }, global: { fetch: (/** @type {any} */ u, /** @type {any} */ o) => fetchConTimeout(u, o) } });
  }
  async init() { const { data } = await this.sb.auth.getSession(); return data.session?.user ?? null; }
  async horaServidor() {
    const { data, error } = await this.sb.rpc("hora_servidor");
    if (error) throw err(error.message, error.code);
    return new Date(data).getTime();
  }
  /** @param {Partial<LogCliente> | ErrorLogItem} row */
  async registrarError(row) {
    const { error } = await this.sb.from("logs_cliente").insert(row);
    if (error) throw err(error.message, error.code);
  }
  /**
   * @param {string} email
   * @param {string} [redirectTo]
   */
  async solicitarRecuperacion(email, redirectTo) {
    const { error } = await this.sb.auth.resetPasswordForEmail(email.trim(), { redirectTo });
    if (error) throw err(/rate limit/i.test(error.message) ? "Se enviaron demasiados correos. Espera unos minutos e inténtalo de nuevo." : error.message, error.code);
  }
  /** @param {string} password */
  async cambiarPassword(password) {
    const { error } = await this.sb.auth.updateUser({ password });
    if (error) throw err(error.message, error.code);
  }
  /** @param {() => void} cb */
  alRecuperar(cb) { this.sb.auth.onAuthStateChange((/** @type {string} */ evento) => { if (evento === "PASSWORD_RECOVERY") cb(); }); }
  /**
   * Derecho de supresión: borra las fotos propias, el registro (con su historial) y la cuenta.
   * @param {{ id: string }} user
   */
  async eliminarMiCuenta(user) {
    const bucket = this.sb.storage.from("fotos-alumnos");
    const { data } = await bucket.list(user.id);
    const rutas = (data || []).map((/** @type {{ name: string }} */ f) => `${user.id}/${f.name}`);
    if (rutas.length) await bucket.remove(rutas);
    await this.#rpc("eliminar_mi_registro", {});
    await this.sb.auth.signOut();
  }
  /**
   * @param {string} email
   * @param {string} password
   * @param {string} [captchaToken]
   */
  async signUp(email, password, captchaToken) {
    const { data, error } = await this.sb.auth.signUp({ email: email.trim(), password, options: captchaToken ? { captchaToken } : undefined });
    if (error) throw err(/registered|already/i.test(error.message) ? "Ese correo ya está registrado" : error.message, "auth");
    if (!data.session) throw err("Te enviamos un correo para confirmar tu cuenta. Confírmalo y luego inicia sesión.", "confirm");
    return data.user;
  }
  /**
   * @param {string} email
   * @param {string} password
   */
  async signIn(email, password) {
    const { data, error } = await this.sb.auth.signInWithPassword({ email: email.trim(), password });
    if (error) throw err(error.message, "auth");
    return data.user;
  }
  async signOut() { await this.sb.auth.signOut(); }

  /**
   * @param {string} nombre
   * @param {Record<string, unknown>} [args]
   * @returns {Promise<any>}
   */
  async #rpc(nombre, args) {
    const { data, error } = await this.sb.rpc(nombre, args);
    if (error) throw err(vocabulario(error.message), error.code);
    return data;
  }
  /** @param {string} codigo */
  infoColegio(codigo) { return this.#rpc("info_colegio", { p_codigo: codigo }); }
  /**
   * @param {unknown} _u
   * @param {DatosRegistroEstudiante} f
   */
  async registrar(_u, f) {
    const r = await this.#rpc("registrar_estudiante", {
      p_codigo_colegio: f.codigoColegio, p_nombres: f.nombres, p_apellidos: f.apellidos, p_nivel: f.nivel, p_grado: f.grado,
      p_apoderado: f.apoderado || "", p_dni: f.dni || null, p_apoderado_tel: f.apoderadoTel || null, p_apoderado_email: f.apoderadoEmail || null,
    });
    if (r?.error === "codigo_invalido") throw err("Código de instituto inválido");
    return r;
  }
  miRegistro() { return this.#rpc("mi_registro", {}); }
  async tokenAvisos() { try { return await this.#rpc("token_avisos", {}); } catch { return null; } }
  /** @param {string} token */
  async estadoSolicitud(token) { const { data, error } = await this.sb.rpc("estado_solicitud", { p_token: token }); return error ? null : data; }
  /**
   * @param {{ id: string }} user
   * @param {Blob} blob
   */
  async subirFoto(user, blob) {
    const path = `${user.id}/foto-${Date.now()}.jpg`;
    const { error } = await this.sb.storage.from("fotos-alumnos").upload(path, blob, { contentType: "image/jpeg", upsert: true });
    if (error) throw err(error.message, error.code);
    await this.#rpc("actualizar_mi_foto", { p_path: path });
    // limpia fotos anteriores del propio estudiante (best effort)
    const { data } = await this.sb.storage.from("fotos-alumnos").list(user.id);
    const viejas = (data || []).filter((/** @type {{ name: string }} */ f) => `${user.id}/${f.name}` !== path).map((/** @type {{ name: string }} */ f) => `${user.id}/${f.name}`);
    if (viejas.length) await this.sb.storage.from("fotos-alumnos").remove(viejas);
    return { foto_path: path };
  }
  /** @param {{ foto_path?: string | null }} alumno */
  async fotoUrl(alumno) {
    if (!alumno?.foto_path) return null;
    const { data, error } = await this.sb.storage.from("fotos-alumnos").createSignedUrl(alumno.foto_path, 3600);
    return error ? null : data?.signedUrl ?? null;
  }
}

/** @returns {BackendEstudiante} */
function crear() {
  try { return isDemoMode() ? new DemoEstudiante() : new SupabaseEstudiante(); }
  catch (error) { return /** @type {BackendEstudiante} */ (/** @type {unknown} */ ({ mode: "error", error, init: async () => { throw error; } })); }
}
export const api = crear();

