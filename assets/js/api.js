// @ts-check
// Capa de datos. Dos adaptadores con la MISMA interfaz:
//  - SupabaseBackend: producción (Postgres + Auth + RLS).
//  - DemoBackend:     localStorage, para probar sin servidor.
// El resto de la app solo habla con `api`.
import { CONFIG, DEMO_USER, isDemoMode, modoLocal } from "./config.js";
import { buildDemoDB } from "./demo-data.js";
import { extrasDemo, extrasSupabase } from "./api-extra.js";
import { aulaDemo, aulaSupabase } from "./api-aula.js";
import { uid } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Asistencia} Asistencia */
/** @typedef {import('./tipos.d.ts').AsistenciaCurso} AsistenciaCurso */
/** @typedef {import('./tipos.d.ts').Auditoria} Auditoria */
/** @typedef {import('./tipos.d.ts').AvisoApoderado} AvisoApoderado */
/** @typedef {import('./tipos.d.ts').Colegio} Colegio */
/** @typedef {import('./tipos.d.ts').Comunicado} Comunicado */
/** @typedef {import('./tipos.d.ts').Curso} Curso */
/** @typedef {import('./tipos.d.ts').DiaCalendario} DiaCalendario */
/** @typedef {import('./tipos.d.ts').Docente} Docente */
/** @typedef {import('./tipos.d.ts').ErrorLogItem} ErrorLogItem */
/** @typedef {import('./tipos.d.ts').Grado} Grado */
/** @typedef {import('./tipos.d.ts').Horario} Horario */
/** @typedef {import('./tipos.d.ts').Justificacion} Justificacion */
/** @typedef {import('./tipos.d.ts').LoadAllResult} LoadAllResult */
/** @typedef {import('./tipos.d.ts').LogCliente} LogCliente */
/** @typedef {import('./tipos.d.ts').Nivel} Nivel */
/** @typedef {import('./tipos.d.ts').Perfil} Perfil */
/** @typedef {import('./tipos.d.ts').Periodo} Periodo */
/** @typedef {import('./tipos.d.ts').Backend} Backend */
/** @typedef {import('./tipos.d.ts').SupabaseClient} SupabaseClient */
/** @typedef {import('./tipos.d.ts').DemoDB} DemoDB */

const TABLAS = ["alumnos", "niveles", "grados", "docentes", "comunicados", "cursos", "periodos", "calendario", "horarios"];
/**
 * @param {string} message
 * @param {any} [code]
 * @returns {Error & { code?: any }}
 */
const err = (message, code) => Object.assign(new Error(message), { code });

/* ----------------------------- DEMO ----------------------------- */
/** Código de 12 caracteres (como el de la base) con el que el apoderado consulta la asistencia de su hijo. */
export const codigoApoderado = () => Array.from({ length: 12 }, () => "0123456789ABCDEF"[Math.floor(Math.random() * 16)]).join("");

class DemoBackend {
  mode = "demo";
  /** @type {DemoDB} */
  db;
  KEY = "ra-demo-db-v1";
  SESSION = "ra-demo-session";

  /**
   * Historial de cambios del demo/local (en la base real lo escriben disparadores que nadie puede saltarse).
   * @param {string} accion
   * @param {string} tabla
   * @param {string | number | undefined} id
   * @param {Record<string, any>} [nuevo]
   * @param {Record<string, any>} [previo]
   */
  _auditar(accion, tabla, id, nuevo, previo) {
    if (tabla === "auditoria") return;
    const detalle = accion === "UPDATE"
      ? Object.fromEntries(Object.keys(nuevo || {}).filter((k) => previo && /** @type {any} */ (previo)[k] !== /** @type {any} */ (nuevo)[k]).map((k) => [k, { de: /** @type {any} */ (previo)?.[k], a: /** @type {any} */ (nuevo)[k] }]))
      : (accion === "DELETE" ? { ...(nuevo || {}) } : { ...(nuevo || {}) });
    if (accion === "UPDATE" && !Object.keys(detalle).length) return;
    (this.db.auditoria ||= []).unshift({ id: uid(), colegio_id: this.db.colegio.id, usuario: this.mode === "local" ? "local@este-equipo" : DEMO_USER.email, accion, tabla, registro_id: id ? String(id) : null, detalle, creado_en: new Date().toISOString() });
    this.db.auditoria = this.db.auditoria.slice(0, 500);
  }

  constructor() {
    if (/** @type {any} */ (this.constructor).KEY) this.KEY = /** @type {any} */ (this.constructor).KEY;
    try { const raw = typeof localStorage !== "undefined" ? localStorage.getItem(this.KEY) : null; this.db = raw ? JSON.parse(raw) : null; } catch { this.db = /** @type {any} */ (null); }
    if (!this.db) { this.db = this.nuevaDB(); this.persist(); }
    // datos guardados por versiones anteriores: completar lo que faltaba
    let cambio = false;
    this.db.alumnos.forEach((a) => { if (!a.codigo_apoderado) { a.codigo_apoderado = codigoApoderado(); cambio = true; } });
    if (cambio) this.persist();
  }
  /** @returns {DemoDB} */
  nuevaDB() { return buildDemoDB(); }
  /** @returns {void} */
  persist() { try { if (typeof localStorage !== "undefined") localStorage.setItem(this.KEY, JSON.stringify(this.db)); } catch { /* cuota llena: se ignora */ } }
  /** @returns {void} */
  reset() { this.db = this.nuevaDB(); this.persist(); }

  /** @returns {Promise<{ id: string, email: string } | null>} */
  async init() { return typeof localStorage !== "undefined" && localStorage.getItem(this.SESSION) ? { id: "demo-user", email: DEMO_USER.email } : null; }
  /**
   * @param {string} email
   * @param {string} password
   * @returns {Promise<{ id: string, email: string }>}
   */
  async signIn(email, password) {
    if (email.trim().toLowerCase() !== DEMO_USER.email || password !== DEMO_USER.password) throw err("Credenciales inválidas", "auth");
    if (typeof localStorage !== "undefined") localStorage.setItem(this.SESSION, "1");
    return { id: "demo-user", email: DEMO_USER.email };
  }
  /** @returns {Promise<void>} */
  async signOut() { if (typeof localStorage !== "undefined") localStorage.removeItem(this.SESSION); }
  /** @returns {Promise<Perfil>} */
  async getProfile() { return { colegio_id: this.db.colegio.id, rol: "Administrador", nombre: this.db.perfil_nombre || "Administrador demo", carrera: null, foto_path: this.db.foto_perfil ? "local" : null, colegio: this.db.colegio.nombre, superadmin: true, qr_modo: this.db.colegio.qr_modo || "obligatorio" }; }
  /** @returns {Promise<string>} */
  async userId() { return "demo-user"; }
  /**
   * Hora "del servidor". En demo se puede simular un reloj de teléfono desfasado con localStorage ra-sim-desfase-ms.
   * @returns {Promise<number>}
   */
  async horaServidor() { return Date.now() + (Number(typeof localStorage !== "undefined" ? localStorage.getItem("ra-sim-desfase-ms") : 0) || 0); }

  // El portal de estudiantes (otra página, mismo navegador) escribe en el mismo localStorage: se relee antes de cargar.
  /** @returns {void} */
  reload() { try { const raw = typeof localStorage !== "undefined" ? localStorage.getItem(this.KEY) : null; const d = raw ? JSON.parse(raw) : null; if (d) this.db = d; } catch { /* se conserva la copia en memoria */ } }
  /** @returns {Promise<string | null>} */
  async getCodigoRegistro() { this.reload(); return this.db.colegio.codigo_registro || null; }
  /**
   * @param {string | null | undefined} _cid
   * @param {string} codigo
   * @returns {Promise<void>}
   */
  async setCodigoRegistro(_cid, codigo) { this.db.colegio.codigo_registro = codigo; this.persist(); }
  /**
   * @param {{ id?: string, foto_path?: string | null, foto_data?: string | null }} alumno
   * @returns {Promise<string | null>}
   */
  async fotoUrl(alumno) { this.reload(); return this.db.alumnos.find((a) => a.id === alumno.id)?.foto_data || null; }

  /** @returns {Promise<LoadAllResult>} */
  async loadAll() {
    this.reload();
    const { alumnos, niveles, grados, comunicados, docentes } = this.db;
    const cursos = [...(this.db.cursos || [])];
    /** @template T @param {T[]} a @param {keyof T} k @returns {T[]} */
    const sort = (a, k) => [...a].sort((x, y) => String(x[k]).localeCompare(String(y[k]), "es", { numeric: true }));
    return {
      alumnos: sort(alumnos, "nombre"), niveles: sort(niveles, "nombre"), grados: sort(grados, "nombre"),
      docentes: sort(docentes, "nombre"), comunicados: [...comunicados].sort((a, b) => b.fecha.localeCompare(a.fecha)), cursos: sort(cursos, "nombre"),
      ajustes: { periodos: [...(this.db.periodos || [])], calendario: [...(this.db.calendario || [])], horarios: [...(this.db.horarios || [])] },
    };
  }
  /**
   * @param {string | null | undefined} _c
   * @param {string} desde
   * @param {string} hasta
   * @returns {Promise<Asistencia[]>}
   */
  async asistenciasRango(_c, desde, hasta) { return this.db.asistencias.filter((a) => a.fecha >= desde && a.fecha <= hasta); }
  /**
   * @param {string | null | undefined} _c
   * @param {string} fecha
   * @returns {Promise<Asistencia[]>}
   */
  async asistenciasPorFecha(_c, fecha) { return this.db.asistencias.filter((a) => a.fecha === fecha); }
  /**
   * @param {string} alumnoId
   * @param {number} [limite]
   * @returns {Promise<Asistencia[]>}
   */
  async asistenciasAlumno(alumnoId, limite = 100) {
    return this.db.asistencias.filter((a) => a.alumno_id === alumnoId).sort((a, b) => b.fecha.localeCompare(a.fecha)).slice(0, limite);
  }

  /**
   * @param {Partial<Asistencia> & { alumno_id: string, fecha: string, hora: string }} row
   * @returns {Promise<void>}
   */
  async registrarAsistencia(row) {
    if (this.db.asistencias.some((a) => a.alumno_id === row.alumno_id && a.fecha === row.fecha)) throw err("Ya registrado", "duplicate");
    this.db.asistencias.push(/** @type {any} */ ({ id: uid(), ...row }));
    this.persist();
  }
  /**
   * @param {(Partial<Asistencia> & { alumno_id: string, fecha: string })[]} rows
   * @returns {Promise<number>}
   */
  async registrarMasivo(rows) {
    let n = 0;
    rows.forEach((row) => {
      if (!this.db.asistencias.some((a) => a.alumno_id === row.alumno_id && a.fecha === row.fecha)) { this.db.asistencias.push(/** @type {any} */ ({ id: uid(), ...row })); n++; }
    });
    this.persist();
    return n;
  }

  /**
   * @param {string} tabla
   * @param {Record<string, any>} data
   * @param {string | number} [id]
   * @returns {Promise<void>}
   */
  async save(tabla, data, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    const list = (this.db[tabla] ||= []);
    if (tabla === "calendario" && list.some((/** @type {any} */ x) => x.id !== id && x.fecha === data.fecha)) throw err("Fecha duplicada", "duplicate");
    if (tabla === "periodos" && list.some((/** @type {any} */ x) => x.id !== id && x.nombre === data.nombre)) throw err("Periodo duplicado", "duplicate");
    if (tabla === "alumnos" && list.some((/** @type {any} */ x) => x.codigo === data.codigo && x.id !== id)) throw err("Código duplicado", "duplicate");
    // misma regla que el índice único de la base: curso único por carrera + ciclo + nombre (sin distinguir mayúsculas)
    if (tabla === "cursos" && list.some((/** @type {any} */ x) => x.id !== id && x.nivel === data.nivel && (x.grado || "") === (data.grado || "") && x.nombre.toLowerCase() === String(data.nombre).toLowerCase())) throw err("Curso duplicado", "duplicate");
    if (id) { const prev = list.find((/** @type {any} */ x) => x.id === id); this._auditar("UPDATE", tabla, id, data, prev); Object.assign(prev, data); }
    else { const nuevo = /** @type {any} */ ({ id: uid(), colegio_id: this.db.colegio.id, ...data }); if (tabla === "alumnos" && !nuevo.codigo_apoderado) nuevo.codigo_apoderado = codigoApoderado(); list.push(nuevo); this._auditar("INSERT", tabla, nuevo.id, data); }
    this.persist();
  }
  /**
   * @param {string} tabla
   * @param {string | number} id
   * @returns {Promise<void>}
   */
  async remove(tabla, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    this._auditar("DELETE", tabla, id, tabla === "alumnos" ? { codigo: (this.db.alumnos.find((x) => x.id === id) || {}).codigo } : (this.db[tabla] || []).find((/** @type {any} */ x) => x.id === id));
    this.db[tabla] = (this.db[tabla] || []).filter((/** @type {any} */ x) => x.id !== id);
    if (tabla === "alumnos") this.db.asistencias = this.db.asistencias.filter((a) => a.alumno_id !== id);
    this.persist();
  }
  /**
   * @param {Record<string, any>[]} rows
   * @returns {Promise<number>}
   */
  async upsertAlumnos(rows) {
    rows.forEach((r) => {
      const cur = this.db.alumnos.find((a) => a.codigo === r.codigo);
      if (cur) Object.assign(cur, r); else this.db.alumnos.push(/** @type {any} */ ({ id: uid(), colegio_id: this.db.colegio.id, ...r }));
    });
    this.persist();
    return rows.length;
  }
}

// Pruebas sin conexión: con globalThis.__simOffline = true, toda llamada "de red" del demo falla como un corte real.
["loadAll", "asistenciasRango", "asistenciasPorFecha", "asistenciasAlumno", "registrarAsistencia", "registrarMasivo", "save", "remove", "upsertAlumnos", "horaServidor"].forEach((m) => {
  const original = /** @type {any} */ (DemoBackend.prototype)[m];
  /** @type {any} */ (DemoBackend.prototype)[m] = async function (/** @type {any[]} */ ...args) {
    if (globalThis.__simOffline) throw err("Failed to fetch (sin conexión simulada)");
    return original.apply(this, args);
  };
});

/**
 * fetch con tiempo de espera: en redes "colgadas" (señal sin datos) falla pronto y el registro pasa a la cola local.
 * @param {string | URL | Request} url
 * @param {RequestInit} [opts]
 * @param {number} [ms]
 * @returns {Promise<Response>}
 */
export function fetchConTimeout(url, opts = {}, ms) {
  const limite = ms ?? (String(url).includes("/storage/") ? 60000 : 15000);
  const ctl = new AbortController();
  const t = setTimeout(() => ctl.abort(), limite);
  if (opts.signal) opts.signal.addEventListener("abort", () => ctl.abort());
  return fetch(url, { ...opts, signal: ctl.signal }).finally(() => clearTimeout(t));
}

/* --------------------------- SUPABASE --------------------------- */
class SupabaseBackend {
  mode = "supabase";
  /** @type {SupabaseClient} */
  sb;
  /** @type {Map<string, { url: string, hasta: number }> | undefined} */
  _fotos;

  constructor() {
    if (!/** @type {any} */ (window).supabase) throw new Error("No se pudo cargar la librería de Supabase (¿sin conexión?)");
    this.sb = /** @type {any} */ (window).supabase.createClient(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, { global: { fetch: (/** @type {any} */ u, /** @type {any} */ o) => fetchConTimeout(u, o) } });
  }
  /**
   * Hora del servidor en ms (función SQL hora_servidor, migración 004).
   * @returns {Promise<number>}
   */
  async horaServidor() {
    const { data, error } = await this.sb.rpc("hora_servidor");
    if (error) throw err(error.message, error.code);
    return new Date(data).getTime();
  }
  /** @returns {Promise<{ id: string, email?: string | null } | null>} */
  async init() { const { data } = await this.sb.auth.getSession(); return data.session?.user ?? null; }
  /**
   * @param {string} email
   * @param {string} password
   * @returns {Promise<any>}
   */
  async signIn(email, password) {
    const { data, error } = await this.sb.auth.signInWithPassword({ email, password });
    if (error) throw err(error.message, "auth");
    return data.user;
  }
  /** @returns {Promise<void>} */
  async signOut() { await this.sb.auth.signOut(); }
  /** @returns {Promise<string | undefined>} */
  async userId() { return (await this.sb.auth.getUser()).data.user?.id; }
  /**
   * @param {{ id: string }} user
   * @returns {Promise<Perfil>}
   */
  async getProfile(user) {
    let data;
    let qrModoFallback = false;
    const res = await this.sb.from("perfiles").select("*, colegios(nombre, qr_modo)").eq("id", user.id).single();
    if (res.error || !res.data) {
      const resFallback = await this.sb.from("perfiles").select("*, colegios(nombre)").eq("id", user.id).single();
      if (resFallback.error || !resFallback.data) throw err("No se encontró un perfil de instituto para esta cuenta.", "profile");
      data = resFallback.data;
      qrModoFallback = true;
    } else {
      data = res.data;
    }
    let superadmin = false;
    try {
      const { data: sa, error: saErr } = await this.sb.rpc("es_superadmin");
      if (!saErr && sa) superadmin = true;
    } catch { /* false ante cualquier error */ }
    return {
      colegio_id: data.colegio_id,
      rol: data.rol,
      nombre: data.nombre,
      carrera: data.carrera ?? null,
      foto_path: data.foto_path ?? null,
      colegio: data.colegios?.nombre ?? "",
      superadmin,
      // Sin migración 012 no hay secretos de alumnos, así que 'off' es el fallback correcto
      qr_modo: qrModoFallback ? "off" : (data.colegios?.qr_modo || "off"),
    };
  }

  // Portal de estudiantes. Todo es opcional: si la migración 002 aún no se aplicó, estas funciones devuelven null y la app sigue igual.
  /**
   * @param {string | null | undefined} cid
   * @returns {Promise<string | null>}
   */
  async getCodigoRegistro(cid) {
    const { data, error } = await this.sb.from("colegios").select("codigo_registro").eq("id", cid).maybeSingle();
    return error ? null : data?.codigo_registro ?? null;
  }
  /**
   * @param {string | null | undefined} cid
   * @param {string} codigo
   * @returns {Promise<void>}
   */
  async setCodigoRegistro(cid, codigo) {
    const { error } = await this.sb.from("colegios").update({ codigo_registro: codigo }).eq("id", cid);
    if (error) throw err(error.code === "23505" ? "Ese código ya está en uso, genera otro." : error.message, error.code === "23505" ? "duplicate" : error.code);
  }
  /**
   * URL firmada (1 h) de la foto del alumno; el bucket es privado. Cacheada por ruta.
   * @param {{ id?: string, foto_path?: string | null }} alumno
   * @returns {Promise<string | null>}
   */
  async fotoUrl(alumno) {
    if (!alumno?.foto_path) return null;
    this._fotos ??= new Map();
    const c = this._fotos.get(alumno.foto_path);
    if (c && c.hasta > Date.now()) return c.url;
    const { data, error } = await this.sb.storage.from("fotos-alumnos").createSignedUrl(alumno.foto_path, 3600);
    if (error || !data?.signedUrl) return null;
    this._fotos.set(alumno.foto_path, { url: data.signedUrl, hasta: Date.now() + 50 * 60 * 1000 });
    return data.signedUrl;
  }

  /**
   * Lee todas las filas paginando de a 1000 (límite por defecto de PostgREST).
   * @param {() => any} build
   * @returns {Promise<any[]>}
   */
  async #todo(build) {
    const out = [];
    for (let from = 0; ; from += 1000) {
      const { data, error } = await build().range(from, from + 999);
      if (error) throw err(error.message, error.code);
      out.push(...data);
      if (data.length < 1000) break;
    }
    return out;
  }

  /**
   * @param {string | null | undefined} cid
   * @returns {Promise<LoadAllResult>}
   */
  async loadAll(cid) {
    const q = (/** @type {string} */ t, /** @type {string} */ col, asc = true) => this.#todo(() => this.sb.from(t).select("*").eq("colegio_id", cid).order(col, { ascending: asc }));
    const [alumnos, niveles, grados, comunicados, docentes, cursos] = await Promise.all([
      q("alumnos", "nombre"), q("niveles", "nombre"), q("grados", "nombre"), q("comunicados", "fecha", false), q("docentes", "nombre"), /** @type {any} */ (this).cursosLista(cid),
    ]);
    const ajustes = await /** @type {any} */ (this).ajustesLista(cid);
    return { alumnos, niveles, grados, comunicados, docentes, cursos, ajustes };
  }
  /**
   * @param {string | null | undefined} cid
   * @param {string} desde
   * @param {string} hasta
   * @returns {Promise<Asistencia[]>}
   */
  asistenciasRango(cid, desde, hasta) {
    return this.#todo(() => this.sb.from("asistencias").select("*").eq("colegio_id", cid).gte("fecha", desde).lte("fecha", hasta).order("id"));
  }
  /**
   * @param {string | null | undefined} cid
   * @param {string} fecha
   * @returns {Promise<Asistencia[]>}
   */
  asistenciasPorFecha(cid, fecha) { return this.asistenciasRango(cid, fecha, fecha); }
  /**
   * @param {string} alumnoId
   * @param {number} [limite]
   * @returns {Promise<Asistencia[]>}
   */
  async asistenciasAlumno(alumnoId, limite = 100) {
    const { data, error } = await this.sb.from("asistencias").select("*").eq("alumno_id", alumnoId).order("fecha", { ascending: false }).limit(limite);
    if (error) throw err(error.message, error.code);
    return data;
  }
  /**
   * @param {Partial<Asistencia> & { alumno_id: string, fecha: string, hora: string }} row
   * @returns {Promise<void>}
   */
  async registrarAsistencia(row) {
    const { error } = await this.sb.from("asistencias").insert(row);
    if (error) throw err(error.message, error.code === "23505" ? "duplicate" : error.code);
  }
  /**
   * @param {(Partial<Asistencia> & { alumno_id: string, fecha: string })[]} rows
   * @returns {Promise<number>}
   */
  async registrarMasivo(rows) {
    const { data, error } = await this.sb.from("asistencias").upsert(rows, { onConflict: "alumno_id,fecha", ignoreDuplicates: true }).select("id");
    if (error) throw err(error.message, error.code);
    return data?.length ?? 0;
  }
  /**
   * @param {string} tabla
   * @param {Record<string, any>} data
   * @param {string | number} [id]
   * @returns {Promise<void>}
   */
  async save(tabla, data, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    const cid = data.colegio_id;
    const { error } = id ? await this.sb.from(tabla).update(data).eq("id", id) : await this.sb.from(tabla).insert({ colegio_id: cid, ...data });
    if (error) throw err(error.message, error.code === "23505" ? "duplicate" : error.code);
  }
  /**
   * @param {string} tabla
   * @param {string | number} id
   * @returns {Promise<void>}
   */
  async remove(tabla, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    const { error } = await this.sb.from(tabla).delete().eq("id", id);
    if (error) throw err(error.message, error.code);
  }
  /**
   * @param {Record<string, any>[]} rows
   * @returns {Promise<number>}
   */
  async upsertAlumnos(rows) {
    let n = 0;
    for (let i = 0; i < rows.length; i += 200) {
      const lote = rows.slice(i, i + 200);
      const { error } = await this.sb.from("alumnos").upsert(lote, { onConflict: "colegio_id,codigo" });
      if (error) throw err(error.message, error.code);
      n += lote.length;
    }
    return n;
  }
}

Object.assign(DemoBackend.prototype, extrasDemo);
Object.assign(SupabaseBackend.prototype, extrasSupabase);
Object.assign(DemoBackend.prototype, aulaDemo);
Object.assign(SupabaseBackend.prototype, aulaSupabase);
// Las operaciones nuevas de red del demo también fallan con la red simulada caída
["cursosLista", "ajustesLista", "registrarSalidas", "justificacionesRango", "guardarJustificacion", "asistenciasCursoPorFecha", "registrarAsistenciaCurso", "registrarMasivoCurso", "avisosPorFecha", "registrarAviso", "exportarTodo"].forEach((m) => {
  const original = /** @type {any} */ (DemoBackend.prototype)[m];
  /** @type {any} */ (DemoBackend.prototype)[m] = async function (/** @type {any[]} */ ...args) {
    if (globalThis.__simOffline) throw err("Failed to fetch (sin conexión simulada)");
    return original.apply(this, args);
  };
});

/* ----------------------------- LOCAL (programa de escritorio) -----------------------------
   Base de datos propia del equipo, sin internet ni servidor. Reutiliza el motor del demo pero parte VACÍA, no pide
   cuenta y guarda en otra clave: cambiar entre modo local y online nunca mezcla ni borra los datos de ninguno. */
class LocalBackend extends DemoBackend {
  static KEY = "ra-local-db-v1";
  mode = "local";
  /** @returns {DemoDB} */
  nuevaDB() {
    const id = "local-instituto";
    return {
      colegio: { id, nombre: "Mi instituto (modo local)", codigo_registro: "LOCAL", qr_modo: "obligatorio" },
      niveles: [], grados: [], alumnos: [], asistencias: [], docentes: [], comunicados: [], cursos: [],
    };
  }
  /** @returns {Promise<{ id: string, email: string }>} */
  async init() { return { id: "local-user", email: "local@este-equipo" }; }
  /** @returns {Promise<{ id: string, email: string }>} */
  async signIn() { return { id: "local-user", email: "local@este-equipo" }; }
  /** @returns {Promise<void>} */
  async signOut() { /* sin cuentas: nada que cerrar */ }
  /** @returns {Promise<Perfil>} */
  async getProfile() { return { colegio_id: this.db.colegio.id, rol: "Administrador", nombre: this.db.perfil_nombre || "Administrador (local)", carrera: null, foto_path: this.db.foto_perfil ? "local" : null, colegio: this.db.colegio.nombre, superadmin: false, qr_modo: this.db.colegio.qr_modo || "obligatorio" }; }
  /** @returns {Promise<boolean>} */
  async esSuperadmin() { return false; }
  /** @returns {Promise<string>} */
  async userId() { return "local-user"; }
}

/** @returns {Backend} */
function crearBackend() {
  try { return /** @type {Backend} */ (/** @type {any} */ (modoLocal() ? new LocalBackend() : isDemoMode() ? new DemoBackend() : new SupabaseBackend())); }
  catch (error) { return /** @type {Backend} */ (/** @type {unknown} */ ({ mode: "error", error, init: async () => { throw error; } })); }
}
export const api = crearBackend();
export { DB } from "./state.js";
