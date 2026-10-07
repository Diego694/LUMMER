// @ts-check
// Operaciones adicionales de la capa de datos (justificaciones, cursos, asistencia por curso, avisos, errores,
// respaldo y recuperación de contraseña). Se mezclan en DemoBackend y SupabaseBackend (ver api.js): usan `this`.
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
/** @typedef {import('./tipos.d.ts').LogCliente} LogCliente */
/** @typedef {import('./tipos.d.ts').Nivel} Nivel */
/** @typedef {import('./tipos.d.ts').Perfil} Perfil */
/** @typedef {import('./tipos.d.ts').Periodo} Periodo */

/**
 * @param {string} message
 * @param {any} [code]
 * @returns {Error & { code?: any }}
 */
const err = (message, code) => Object.assign(new Error(message), { code });

/**
 * Lee todas las filas paginando de a 1000 (límite por defecto de PostgREST).
 * @param {() => any} build
 * @returns {Promise<any[]>}
 */
async function paginar(build) {
  const out = [];
  for (let from = 0; ; from += 1000) {
    const { data, error } = await build().range(from, from + 999);
    if (error) throw err(error.message, error.code);
    out.push(...data);
    if (data.length < 1000) break;
  }
  return out;
}

/** Tablas incluidas en el respaldo y cómo se filtran por instituto. */
export const TABLAS_RESPALDO = ["alumnos", "niveles", "grados", "docentes", "comunicados", "cursos", "asistencias", "asistencias_curso", "justificaciones", "avisos_apoderados"];

/* ------------------------------ Supabase ------------------------------ */
export const extrasSupabase = {
  /**
   * Periodos, calendario y horarios (migración 007). Sin la migración devuelve listas vacías y la app sigue igual.
   * @this {any}
   * @param {string | null | undefined} cid
   * @returns {Promise<{ periodos: Periodo[], calendario: DiaCalendario[], horarios: Horario[] }>}
   */
  async ajustesLista(cid) {
    const leer = async (/** @type {string} */ t, /** @type {string} */ col) => {
      try { return await paginar(() => this.sb.from(t).select("*").eq("colegio_id", cid).order(col)); }
      catch (e) { if (/does not exist|relation|schema cache/i.test(/** @type {Error} */ (e).message)) return []; throw e; }
    };
    const [periodos, calendario, horarios] = await Promise.all([leer("periodos", "inicio"), leer("calendario", "fecha"), leer("horarios", "nivel")]);
    return { periodos, calendario, horarios };
  },
  /**
   * @this {any}
   * @param {{ alumno_id: string, fecha: string, hora: string }[]} rows
   * @returns {Promise<{ ok: number, dup: number, sin_entrada: number }>}
   */
  async registrarSalidas(rows) {
    const { data, error } = await this.sb.rpc("registrar_salidas", { p_rows: rows.map((r) => ({ alumno_id: r.alumno_id, fecha: r.fecha, hora: r.hora })) });
    if (error) throw err(error.message, error.code);
    return data;   // { ok, dup, sin_entrada }
  },
  /**
   * @this {any}
   * @param {string | null | undefined} cid
   * @param {{ limite?: number, tabla?: string, accion?: string }} [opts]
   * @returns {Promise<Auditoria[]>}
   */
  async auditoriaLista(cid, { limite = 200, tabla = "", accion = "" } = {}) {
    let q = this.sb.from("auditoria").select("*").eq("colegio_id", cid).order("creado_en", { ascending: false }).limit(limite);
    if (tabla) q = q.eq("tabla", tabla);
    if (accion) q = q.eq("accion", accion);
    const { data, error } = await q;
    if (error) throw err(error.message, error.code);
    return data || [];
  },
  /**
   * @this {any}
   * @param {string | null | undefined} cid
   * @returns {Promise<Curso[]>}
   */
  async cursosLista(cid) {
    try { return await paginar(() => this.sb.from("cursos").select("*").eq("colegio_id", cid).order("nombre")); }
    catch (e) { if (/does not exist|relation|schema cache/i.test(/** @type {Error} */ (e).message)) return []; throw e; }  // antes de aplicar la migración 004
  },
  /**
   * @this {any}
   * @param {string | null | undefined} cid
   * @returns {Promise<import('./tipos.d.ts').CursoAlumno[]>}
   */
  async cursoAlumnosLista(cid) {
    try { return await paginar(() => this.sb.from("curso_alumnos").select("*").eq("colegio_id", cid)); }
    catch (e) { if (/does not exist|relation|schema cache/i.test(/** @type {Error} */ (e).message)) return []; throw e; }
  },
  /**
   * @this {any}
   * @param {string | null | undefined} cid
   * @param {string} desde
   * @param {string} hasta
   * @returns {Promise<Justificacion[]>}
   */
  justificacionesRango(cid, desde, hasta) {
    return paginar(() => this.sb.from("justificaciones").select("*").eq("colegio_id", cid).gte("fecha", desde).lte("fecha", hasta).order("id"));
  },
  /**
   * @this {any}
   * @param {Partial<Justificacion> & { alumno_id: string, fecha: string }} row
   * @returns {Promise<void>}
   */
  async guardarJustificacion(row) {
    const { error } = await this.sb.from("justificaciones").insert(row);
    if (error) throw err(error.code === "23505" ? "Ya existe una justificación para ese día (solo el administrador puede modificarla)." : error.message, error.code === "23505" ? "duplicate" : error.code);
  },
  /**
   * @this {any}
   * @param {string | number} id
   * @returns {Promise<void>}
   */
  async eliminarJustificacion(id) {
    const { error, count } = await this.sb.from("justificaciones").delete({ count: "exact" }).eq("id", id);
    if (error) throw err(error.message, error.code);
    if (count === 0) throw err("No tienes permiso para eliminarla (solo el administrador).");
  },
  /**
   * @this {any}
   * @param {string | null | undefined} cid
   * @param {string} cursoId
   * @param {string} fecha
   * @returns {Promise<AsistenciaCurso[]>}
   */
  asistenciasCursoPorFecha(cid, cursoId, fecha) {
    return paginar(() => this.sb.from("asistencias_curso").select("*").eq("colegio_id", cid).eq("curso_id", cursoId).eq("fecha", fecha).order("id"));
  },
  /**
   * @this {any}
   * @param {Partial<AsistenciaCurso> & { alumno_id: string, curso_id: string, fecha: string }} row
   * @returns {Promise<void>}
   */
  async registrarAsistenciaCurso(row) {
    const { error } = await this.sb.from("asistencias_curso").insert(row);
    if (error) throw err(error.message, error.code === "23505" ? "duplicate" : error.code);
  },
  /**
   * @this {any}
   * @param {(Partial<AsistenciaCurso> & { alumno_id: string, curso_id: string, fecha: string })[]} rows
   * @returns {Promise<number>}
   */
  async registrarMasivoCurso(rows) {
    const { data, error } = await this.sb.from("asistencias_curso").upsert(rows, { onConflict: "alumno_id,curso_id,fecha", ignoreDuplicates: true }).select("id");
    if (error) throw err(error.message, error.code);
    return data?.length ?? 0;
  },
  /**
   * @this {any}
   * @param {string | null | undefined} cid
   * @param {string} fecha
   * @returns {Promise<AvisoApoderado[]>}
   */
  avisosPorFecha(cid, fecha) {
    return paginar(() => this.sb.from("avisos_apoderados").select("*").eq("colegio_id", cid).eq("fecha", fecha).order("id"));
  },
  /**
   * @this {any}
   * @param {Partial<AvisoApoderado>} row
   * @returns {Promise<void>}
   */
  async registrarAviso(row) {
    const { error } = await this.sb.from("avisos_apoderados").insert(row);
    if (error) throw err(error.message, error.code);
  },
  /**
   * @this {any}
   * @param {LogCliente | ErrorLogItem | Record<string, unknown>} row
   * @returns {Promise<void>}
   */
  async registrarError(row) {
    const { error } = await this.sb.from("logs_cliente").insert(row);
    if (error) throw err(error.message, error.code);
  },
  /**
   * @this {any}
   * @param {string | null | undefined} [cid]
   * @param {number} [limite]
   * @returns {Promise<LogCliente[]>}
   */
  async erroresRecientes(cid, limite = 200) {
    const { data, error } = await this.sb.from("logs_cliente").select("*").eq("colegio_id", cid).order("creado_en", { ascending: false }).limit(limite);
    if (error) throw err(error.message, error.code);
    return data;
  },
  /**
   * @this {any}
   * @param {string | null | undefined} [cid]
   * @returns {Promise<void>}
   */
  async borrarErrores(cid) {
    const { error } = await this.sb.from("logs_cliente").delete().eq("colegio_id", cid);
    if (error) throw err(error.message, error.code);
  },
  /**
   * Respaldo: todas las tablas del instituto. Las que aún no existen se omiten (instalaciones sin la migración 004).
   * @this {any}
   * @param {string | null | undefined} [cid]
   * @returns {Promise<Record<string, unknown[]>>}
   */
  async exportarTodo(cid) {
    /** @type {Record<string, unknown[]>} */
    const out = {};
    for (const t of TABLAS_RESPALDO) {
      try { out[t] = await paginar(() => this.sb.from(t).select("*").eq("colegio_id", cid).order("id")); }
      catch (e) { if (!/does not exist|relation|schema cache/i.test(/** @type {Error} */ (e).message)) throw e; out[t] = []; }
    }
    return out;
  },
  /**
   * @this {any}
   * @param {{ id?: string, foto_path?: string | null }} alumno
   * @returns {Promise<void>}
   */
  async eliminarFotoAlumno(alumno) {
    if (!alumno?.foto_path) return;
    const carpeta = alumno.foto_path.split("/")[0];
    const { data } = await this.sb.storage.from("fotos-alumnos").list(carpeta);
    const rutas = (data || []).map((/** @type {{ name: string }} */ f) => `${carpeta}/${f.name}`);
    if (rutas.length) await this.sb.storage.from("fotos-alumnos").remove(rutas);
  },
  /**
   * @this {any}
   * @returns {Promise<any[]>}
   */
  async personalListar() {
    const { data, error } = await this.sb.rpc("personal_listar");
    if (error) throw err(error.message, error.code);
    return data || [];
  },
  /**
   * @this {any}
   * @param {string} email
   * @param {string} rol
   * @param {string | null} [carrera]
   * @param {string | null} [nombre]
   * @returns {Promise<void>}
   */
  async personalAsignar(email, rol, carrera, nombre) {
    const { error } = await this.sb.rpc("personal_asignar", { p_email: email, p_rol: rol, p_carrera: carrera || null, p_nombre: nombre || null });
    if (error) throw err(error.message, error.code);
  },
  /**
   * Llama a la Edge Function «gestionar-personal» (crear cuenta, cambiar contraseña, eliminar).
   * @this {any}
   * @param {Record<string, any>} cuerpo
   * @returns {Promise<any>}
   */
  async _personalFn(cuerpo) {
    const { data, error } = await this.sb.functions.invoke("gestionar-personal", { body: cuerpo });
    if (error) {
      let msg = error.message;
      try { const j = await error.context?.json?.(); if (j?.error) msg = j.error; } catch { /* sin cuerpo */ }
      if (/Failed to send|NetworkError|fetch/i.test(msg)) msg = "La función de usuarios no está disponible. Revisa que «gestionar-personal» esté publicada en Supabase (docs/ROLES.md).";
      throw err(msg, error.context?.status);
    }
    if (data?.error) throw err(data.error);
    return data;
  },
  /**
   * @this {any}
   * @param {{ nombre: string, email: string, password?: string, rol: string, carrera?: string | null }} datos
   * @returns {Promise<any>}
   */
  personalCrear({ nombre, email, password, rol, carrera }) { return this._personalFn({ accion: "crear", nombre, email, password, rol, carrera }); },
  /**
   * @this {any}
   * @param {string} id
   * @param {string} [password]
   * @returns {Promise<any>}
   */
  personalPassword(id, password) { return this._personalFn({ accion: "password", id, password }); },
  /**
   * @this {any}
   * @param {string} id
   * @returns {Promise<any>}
   */
  async personalEliminar(id) {
    try { return await this._personalFn({ accion: "eliminar", id }); }
    catch (e) {
      if (!/no está disponible/.test(/** @type {Error} */ (e).message)) throw e;
      return this.personalQuitar(id);   // sin la función publicada: al menos se le quita el acceso (la cuenta queda sin rol)
    }
  },
  /**
   * @this {any}
   * @param {string} nombre
   * @returns {Promise<void>}
   */
  async actualizarMiPerfil(nombre) {
    const { error } = await this.sb.rpc("actualizar_mi_perfil", { p_nombre: nombre, p_path: null });
    if (error) throw err(error.message, error.code);
  },
  /**
   * @this {any}
   * @param {string} userId
   * @param {Blob} blob
   * @returns {Promise<string>}
   */
  async subirFotoPerfil(userId, blob) {
    const ruta = `${userId}/foto-${Date.now()}.jpg`;
    const b = this.sb.storage.from("fotos-personal");
    const { error } = await b.upload(ruta, blob, { contentType: "image/jpeg", upsert: true });
    if (error) throw err(error.message, error.code);
    const r = await this.sb.rpc("actualizar_mi_perfil", { p_nombre: null, p_path: ruta });
    if (r.error) throw err(r.error.message, r.error.code);
    const { data } = await b.list(userId);
    const viejas = (data || []).map((/** @type {{ name: string }} */ f) => `${userId}/${f.name}`).filter((/** @type {string} */ p) => p !== ruta);
    if (viejas.length) await b.remove(viejas);          // deja solo la foto vigente (mejor esfuerzo)
    return ruta;
  },
  /**
   * @this {any}
   * @param {string | null} [path]
   * @returns {Promise<string | null>}
   */
  async fotoPersonalUrl(path) {
    if (!path) return null;
    this._fotosP ??= new Map();
    const c = this._fotosP.get(path);
    if (c && c.hasta > Date.now()) return c.url;
    const { data, error } = await this.sb.storage.from("fotos-personal").createSignedUrl(path, 3600);
    if (error || !data?.signedUrl) return null;
    this._fotosP.set(path, { url: data.signedUrl, hasta: Date.now() + 50 * 60 * 1000 });
    return data.signedUrl;
  },
  /**
   * @this {any}
   * @param {string} id
   * @returns {Promise<void>}
   */
  async personalQuitar(id) {
    const { error } = await this.sb.rpc("personal_quitar", { p_id: id });
    if (error) throw err(error.message, error.code);
  },
  /**
   * Directorio de docentes y coordinadores (solo lectura, sin correos). Sin la migración 008 devuelve null.
   * @this {any}
   * @returns {Promise<any[] | null>}
   */
  async personalDirectorio() {
    const { data, error } = await this.sb.rpc("personal_directorio");
    return error ? null : data || [];
  },
  /**
   * @this {any}
   * @returns {Promise<string | null>}
   */
  async tokenAvisos() {
    const { data, error } = await this.sb.rpc("token_avisos");
    return error ? null : data;   // sin la migración 005 simplemente no hay avisos
  },
  /**
   * @this {any}
   * @param {string} email
   * @param {string} [redirectTo]
   * @returns {Promise<void>}
   */
  async solicitarRecuperacion(email, redirectTo) {
    const { error } = await this.sb.auth.resetPasswordForEmail(email.trim(), { redirectTo });
    if (error) throw err(/rate limit/i.test(error.message) ? "Se enviaron demasiados correos. Espera unos minutos e inténtalo de nuevo." : error.message, error.code);
  },
  /**
   * @this {any}
   * @param {string} password
   * @returns {Promise<void>}
   */
  async cambiarPassword(password) {
    const { error } = await this.sb.auth.updateUser({ password });
    if (error) throw err(error.message, error.code);
  },
  /**
   * @this {any}
   * @param {() => void} cb
   * @returns {void}
   */
  alRecuperar(cb) {
    this.sb.auth.onAuthStateChange((/** @type {string} */ evento) => { if (evento === "PASSWORD_RECOVERY") cb(); });
  },

  /* ------------------- Multi-institución (superadmin) ------------------- */
  /**
   * @this {any}
   * @returns {Promise<boolean>}
   */
  async esSuperadmin() {
    try {
      const { data, error } = await this.sb.rpc("es_superadmin");
      if (error) return false;
      return Boolean(data);
    } catch {
      return false;
    }
  },
  /**
   * @this {any}
   * @returns {Promise<any[]>}
   */
  async saListar() {
    const { data, error } = await this.sb.rpc("sa_listar");
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 011", error.code);
      }
      throw err(error.message, error.code);
    }
    return data || [];
  },
  /**
   * @this {any}
   * @param {string} nombre
   * @param {string | null} [codigo]
   * @returns {Promise<any>}
   */
  async saCrear(nombre, codigo) {
    const { data, error } = await this.sb.rpc("sa_crear", {
      p_nombre: nombre,
      p_codigo: codigo || null,
    });
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 011", error.code);
      }
      throw err(error.message, error.code);
    }
    return data;
  },
  /**
   * @this {any}
   * @param {string} id
   * @param {string} nombre
   * @returns {Promise<void>}
   */
  async saRenombrar(id, nombre) {
    const { error } = await this.sb.rpc("sa_renombrar", {
      p_id: id,
      p_nombre: nombre,
    });
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 011", error.code);
      }
      throw err(error.message, error.code);
    }
  },
  /**
   * @this {any}
   * @param {string} id
   * @param {boolean} desvincular
   * @returns {Promise<void>}
   */
  async saDesvincular(id, desvincular) {
    const { error } = await this.sb.rpc("sa_desvincular", { p_id: id, p_desvincular: desvincular });
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 018", error.code);
      }
      throw err(error.message, error.code);
    }
  },
  /**
   * @this {any}
   * @param {string} id
   * @param {boolean} activo
   * @returns {Promise<void>}
   */
  async saActivar(id, activo) {
    const { error } = await this.sb.rpc("sa_activar", {
      p_id: id,
      p_activo: activo,
    });
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 011", error.code);
      }
      throw err(error.message, error.code);
    }
  },
  /**
   * @this {any}
   * @param {string} id
   * @returns {Promise<any>}
   */
  async saEntrar(id) {
    const { data, error } = await this.sb.rpc("sa_entrar", {
      p_colegio: id,
    });
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 011", error.code);
      }
      throw err(error.message, error.code);
    }
    return data;
  },
  /**
   * @this {any}
   * @param {string} id
   * @param {string} email
   * @returns {Promise<any>}
   */
  async saAsignarAdmin(id, email) {
    const { data, error } = await this.sb.rpc("sa_asignar_admin", {
      p_colegio: id,
      p_email: email,
    });
    if (error) {
      if (/does not exist|schema cache|function/i.test(error.message) || error.code === "PGRST202" || error.code === "42883") {
        throw err("Falta aplicar la migración 011", error.code);
      }
      throw err(error.message, error.code);
    }
    return data;
  },
  /**
   * @this {any}
   * @param {string} cid
   * @param {string} nombre
   * @returns {Promise<void>}
   */
  async renombrarInstituto(cid, nombre) {
    const nom = String(nombre || "").trim();
    if (nom.length < 3 || nom.length > 80) throw err("El nombre debe tener entre 3 y 80 caracteres");
    const { error, count } = await this.sb.from("colegios").update({ nombre: nom }, { count: "exact" }).eq("id", cid);
    if (error) throw err(error.message, error.code);
    if (count === 0) throw err("No tienes permiso para cambiar el nombre");
  },
  /**
   * @this {any}
   * @param {string} cid
   * @param {string} modo
   * @returns {Promise<void>}
   */
  async cambiarQrModo(cid, modo) {
    const m = String(modo || "").toLowerCase().trim();
    if (!["off", "opcional", "obligatorio"].includes(m)) {
      throw err("Modo no válido (usa off, opcional u obligatorio)");
    }
    const { error, count } = await this.sb.from("colegios").update({ qr_modo: m }, { count: "exact" }).eq("id", cid);
    if (error) {
      if (/does not exist|column|schema cache/i.test(error.message) || error.code === "PGRST204" || error.code === "42703") {
        throw err("Falta aplicar la migración 012", error.code);
      }
      throw err(error.message, error.code);
    }
    if (count === 0) throw err("No tienes permiso para cambiar la seguridad del QR");
  },
};

/* --------------------------------- Demo --------------------------------- */
/**
 * @param {any} db
 * @param {string} n
 * @returns {any[]}
 */
const tabla = (db, n) => (db[n] ||= []);

export const extrasDemo = {
  /**
   * @this {any}
   * @returns {Promise<Curso[]>}
   */
  async cursosLista() { this.reload(); return [...tabla(this.db, "cursos")].sort((a, b) => a.nombre.localeCompare(b.nombre, "es")); },
  /**
   * @this {any}
   * @param {string | null | undefined} [_cid]
   * @returns {Promise<import('./tipos.d.ts').CursoAlumno[]>}
   */
  async cursoAlumnosLista(_cid) { this.reload(); return [...(this.db.curso_alumnos || [])]; },
  /**
   * @this {any}
   * @param {string | null | undefined} _c
   * @param {string} desde
   * @param {string} hasta
   * @returns {Promise<Justificacion[]>}
   */
  async justificacionesRango(_c, desde, hasta) { this.reload(); return tabla(this.db, "justificaciones").filter((j) => j.fecha >= desde && j.fecha <= hasta); },
  /**
   * @this {any}
   * @param {Partial<Justificacion> & { alumno_id: string, fecha: string }} row
   * @returns {Promise<void>}
   */
  async guardarJustificacion(row) {
    const l = tabla(this.db, "justificaciones");
    if (l.some((j) => j.alumno_id === row.alumno_id && j.fecha === row.fecha)) throw err("Ya existe una justificación para ese día (solo el administrador puede modificarla).", "duplicate");
    l.push({ id: uid(), creado_en: new Date().toISOString(), ...row }); this.persist();
  },
  /**
   * @this {any}
   * @param {string | number} id
   * @returns {Promise<void>}
   */
  async eliminarJustificacion(id) { this.db.justificaciones = tabla(this.db, "justificaciones").filter((j) => j.id !== id); this.persist(); },
  /**
   * @this {any}
   * @param {string | null | undefined} _c
   * @param {string} cursoId
   * @param {string} fecha
   * @returns {Promise<AsistenciaCurso[]>}
   */
  async asistenciasCursoPorFecha(_c, cursoId, fecha) { this.reload(); return tabla(this.db, "asistencias_curso").filter((a) => a.curso_id === cursoId && a.fecha === fecha); },
  /**
   * @this {any}
   * @param {Partial<AsistenciaCurso> & { alumno_id: string, curso_id: string, fecha: string }} row
   * @returns {Promise<void>}
   */
  async registrarAsistenciaCurso(row) {
    const l = tabla(this.db, "asistencias_curso");
    if (l.some((a) => a.alumno_id === row.alumno_id && a.curso_id === row.curso_id && a.fecha === row.fecha)) throw err("Ya registrado", "duplicate");
    l.push({ id: uid(), registrado_en: new Date().toISOString(), ...row }); this.persist();
  },
  /**
   * @this {any}
   * @param {(Partial<AsistenciaCurso> & { alumno_id: string, curso_id: string, fecha: string })[]} rows
   * @returns {Promise<number>}
   */
  async registrarMasivoCurso(rows) {
    const l = tabla(this.db, "asistencias_curso"); let n = 0;
    rows.forEach((r) => { if (!l.some((a) => a.alumno_id === r.alumno_id && a.curso_id === r.curso_id && a.fecha === r.fecha)) { l.push({ id: uid(), registrado_en: new Date().toISOString(), ...r }); n++; } });
    this.persist(); return n;
  },
  /**
   * @this {any}
   * @param {string | null | undefined} _c
   * @param {string} fecha
   * @returns {Promise<AvisoApoderado[]>}
   */
  async avisosPorFecha(_c, fecha) { this.reload(); return tabla(this.db, "avisos_apoderados").filter((a) => a.fecha === fecha); },
  /**
   * @this {any}
   * @param {Partial<AvisoApoderado>} row
   * @returns {Promise<void>}
   */
  async registrarAviso(row) { tabla(this.db, "avisos_apoderados").push({ id: uid(), creado_en: new Date().toISOString(), ...row }); this.persist(); },
  /**
   * @this {any}
   * @param {LogCliente | ErrorLogItem | Record<string, unknown>} row
   * @returns {Promise<void>}
   */
  async registrarError(row) { const l = tabla(this.db, "logs_cliente"); l.unshift({ id: uid(), creado_en: new Date().toISOString(), ...row }); this.db.logs_cliente = l.slice(0, 200); this.persist(); },
  /**
   * @this {any}
   * @returns {Promise<LogCliente[]>}
   */
  async erroresRecientes() { this.reload(); return [...tabla(this.db, "logs_cliente")]; },
  /**
   * @this {any}
   * @returns {Promise<void>}
   */
  async borrarErrores() { this.db.logs_cliente = []; this.persist(); },
  /**
   * @this {any}
   * @returns {Promise<Record<string, unknown[]>>}
   */
  async exportarTodo() {
    this.reload();
    const map = { alumnos: "alumnos", niveles: "niveles", grados: "grados", docentes: "docentes", comunicados: "comunicados", cursos: "cursos", asistencias: "asistencias", asistencias_curso: "asistencias_curso", justificaciones: "justificaciones", avisos_apoderados: "avisos_apoderados" };
    return Object.fromEntries(Object.entries(map).map(([t, k]) => [t, JSON.parse(JSON.stringify(this.db[k] || []))]));
  },
  /**
   * @this {any}
   * @returns {Promise<void>}
   */
  async eliminarFotoAlumno() { /* en demo la foto vive dentro del propio registro */ },
  /**
   * @this {any}
   * @returns {Promise<{ periodos: Periodo[], calendario: DiaCalendario[], horarios: Horario[] }>}
   */
  async ajustesLista() { return { periodos: [...(this.db.periodos || [])], calendario: [...(this.db.calendario || [])], horarios: [...(this.db.horarios || [])] }; },
  /**
   * @this {any}
   * @param {{ alumno_id: string, fecha: string, hora: string }[]} rows
   * @returns {Promise<{ ok: number, dup: number, sin_entrada: number }>}
   */
  async registrarSalidas(rows) {
    let ok = 0, dup = 0, sin = 0;
    rows.forEach((r) => {
      const a = this.db.asistencias.find((/** @type {Asistencia} */ x) => x.alumno_id === r.alumno_id && x.fecha === r.fecha);
      if (!a) sin++; else if (a.hora_salida) dup++; else { a.hora_salida = String(r.hora).slice(0, 5); ok++; }
    });
    this.persist();
    return { ok, dup, sin_entrada: sin };
  },
  /**
   * @this {any}
   * @param {string | null | undefined} [_cid]
   * @param {{ limite?: number, tabla?: string, accion?: string }} [opts]
   * @returns {Promise<Auditoria[]>}
   */
  async auditoriaLista(_cid, { limite = 200, tabla = "", accion = "" } = {}) {
    return (this.db.auditoria || []).filter((/** @type {Auditoria} */ x) => (!tabla || x.tabla === tabla) && (!accion || x.accion === accion)).slice(0, limite);
  },
  /**
   * @this {any}
   * @returns {Promise<any[]>}
   */
  async personalListar() {
    this.db.personal = this.db.personal || [{ id: "demo-user", email: "demo@instituto.pe", nombre: "Administrador", rol: "Administrador", carrera: null }];
    return JSON.parse(JSON.stringify(this.db.personal));
  },
  /**
   * @this {any}
   * @param {string} email
   * @param {string} rol
   * @param {string | null} [carrera]
   * @param {string | null} [nombre]
   * @returns {Promise<void>}
   */
  async personalAsignar(email, rol, carrera, nombre) {
    await this.personalListar();
    if (!/^\S+@\S+\.\S+$/.test(String(email).trim())) throw err("No existe una cuenta con ese correo. Créala primero en Supabase → Authentication → Users.");
    if (rol === "Coordinador" && !carrera) throw err("El coordinador necesita una carrera existente");
    const e = String(email).trim().toLowerCase();
    const p = this.db.personal.find((/** @type {{ email: string }} */ x) => x.email === e);
    if (p) Object.assign(p, { rol, carrera: rol === "Coordinador" ? carrera : null, nombre: nombre || p.nombre });
    else this.db.personal.push({ id: uid(), email: e, nombre: nombre || null, rol, carrera: rol === "Coordinador" ? carrera : null });
    this.persist();
  },
  /**
   * @this {any}
   * @param {string} id
   * @returns {Promise<void>}
   */
  async personalQuitar(id) {
    await this.personalListar();
    if (id === "demo-user") throw err("No puedes quitarte el acceso a ti mismo");
    this.db.personal = this.db.personal.filter((/** @type {{ id: string }} */ x) => x.id !== id); this.persist();
  },
  /**
   * @this {any}
   * @returns {Promise<string | null>}
   */
  async tokenAvisos() { return null; },
  /**
   * @this {any}
   * @returns {Promise<any[] | null>}
   */
  async personalDirectorio() { return (await this.personalListar()).filter((/** @type {{ rol: string }} */ p) => /^(docente|coordinador)$/i.test(p.rol)).map((/** @type {any} */ { email, ...resto }) => resto); },
  /**
   * @this {any}
   * @param {{ nombre: string, email: string, rol: string, carrera?: string | null }} datos
   * @returns {Promise<void>}
   */
  async personalCrear({ nombre, email, rol, carrera }) {
    await this.personalAsignar(email, rol, carrera, nombre);   // en demo/local no hay cuentas reales: solo la lista
  },
  /**
   * @this {any}
   * @returns {Promise<void>}
   */
  async personalPassword() { /* en demo/local no hay cuentas */ },
  /**
   * @this {any}
   * @param {string} id
   * @returns {Promise<void>}
   */
  async personalEliminar(id) { return this.personalQuitar(id); },
  /**
   * @this {any}
   * @param {string} nombre
   * @returns {Promise<void>}
   */
  async actualizarMiPerfil(nombre) { this.db.perfil_nombre = String(nombre || "").trim().slice(0, 80) || this.db.perfil_nombre; this.persist(); },
  /**
   * @this {any}
   * @param {string} _uid
   * @param {Blob} blob
   * @returns {Promise<string>}
   */
  async subirFotoPerfil(_uid, blob) {
    const dataUrl = await new Promise((res) => { const r = new FileReader(); r.onload = () => res(r.result); r.readAsDataURL(blob); });
    this.db.foto_perfil = dataUrl; this.persist(); return "local";
  },
  /**
   * @this {any}
   * @param {string | null} [path]
   * @returns {Promise<string | null>}
   */
  async fotoPersonalUrl(path) { return path ? this.db.foto_perfil || null : null; },
  /**
   * @this {any}
   * @returns {Promise<void>}
   */
  async solicitarRecuperacion() { throw err("En modo demo no se envían correos."); },
  /**
   * @this {any}
   * @returns {Promise<void>}
   */
  async cambiarPassword() { throw err("En modo demo no hay recuperación de contraseña."); },
  /**
   * @this {any}
   * @returns {void}
   */
  alRecuperar() { /* sin eventos en demo */ },

  /* ------------------- Multi-institución (superadmin demo) ------------------- */
  /**
   * @this {any}
   * @returns {Promise<boolean>}
   */
  async esSuperadmin() { return true; },

  /**
   * @this {any}
   * @returns {any[]}
   */
  _asegurarInstituciones() {
    this.reload();
    if (!this.db.instituciones || !Array.isArray(this.db.instituciones)) {
      this.db.instituciones = [{
        id: this.db.colegio.id,
        nombre: this.db.colegio.nombre,
        codigo_registro: this.db.colegio.codigo_registro || "DEMO01",
        activo: true,
        creado_en: new Date().toISOString(),
        alumnos: (this.db.alumnos || []).length,
        personal: (this.db.personal || []).length,
        actual: true,
      }];
      this.persist();
    } else {
      const act = this.db.instituciones.find((/** @type {{ id: string }} */ i) => i.id === this.db.colegio.id);
      if (act) {
        act.nombre = this.db.colegio.nombre;
        act.alumnos = (this.db.alumnos || []).length;
        act.personal = (this.db.personal || []).length;
        act.actual = true;
      }
    }
    return this.db.instituciones;
  },

  /**
   * @this {any}
   * @returns {Promise<any[]>}
   */
  async saListar() {
    const list = this._asegurarInstituciones();
    return list.map((/** @type {any} */ i) => ({
      ...i,
      actual: i.id === this.db.colegio.id,
    })).sort((/** @type {any} */ a, /** @type {any} */ b) => a.nombre.localeCompare(b.nombre, "es"));
  },

  /**
   * @this {any}
   * @param {string} nombre
   * @param {string | null} [codigo]
   * @returns {Promise<any>}
   */
  async saCrear(nombre, codigo) {
    this._asegurarInstituciones();
    const nom = String(nombre || "").trim();
    if (nom.length < 3 || nom.length > 80) throw err("El nombre debe tener entre 3 y 80 caracteres");
    let cod = String(codigo || "").trim().toUpperCase();
    if (!cod) {
      cod = "INST" + Math.floor(1000 + Math.random() * 9000);
    } else {
      if (!/^[A-Z0-9]{6,20}$/.test(cod)) throw err("El código debe tener entre 6 y 20 caracteres alfanuméricos (A-Z, 0-9)");
      if (this.db.instituciones.some((/** @type {{ codigo_registro: string }} */ i) => i.codigo_registro === cod)) {
        throw err(`Ya existe una institución con el código «${cod}»`);
      }
    }
    const nueva = {
      id: uid(),
      nombre: nom,
      codigo_registro: cod,
      activo: true,
      creado_en: new Date().toISOString(),
      alumnos: 0,
      personal: 0,
      actual: false,
    };
    this.db.instituciones.push(nueva);
    this.persist();
    return nueva;
  },

  /**
   * @this {any}
   * @param {string} id
   * @param {string} nombre
   * @returns {Promise<void>}
   */
  async saRenombrar(id, nombre) {
    this._asegurarInstituciones();
    const nom = String(nombre || "").trim();
    if (nom.length < 3 || nom.length > 80) throw err("El nombre debe tener entre 3 y 80 caracteres");
    const inst = this.db.instituciones.find((/** @type {{ id: string }} */ i) => i.id === id);
    if (!inst) throw err("No se encontró la institución");
    inst.nombre = nom;
    if (id === this.db.colegio.id) {
      this.db.colegio.nombre = nom;
    }
    this.persist();
  },

  /**
   * @this {any}
   * @param {string} id
   * @param {boolean} desvincular
   * @returns {Promise<void>}
   */
  async saDesvincular(id, desvincular) {
    this._asegurarInstituciones();
    const inst = this.db.instituciones.find((/** @type {{ id: string }} */ i) => i.id === id);
    if (!inst) throw err("No se encontró la institución");
    if (desvincular && id === this.db.colegio.id) throw err("Estás dentro de esta institución: entra primero a otra y vuelve a intentarlo");
    inst.desvinculado_en = desvincular ? new Date().toISOString() : null;
    inst.activo = !desvincular;
    this.persist();
  },
  /**
   * @this {any}
   * @param {string} id
   * @param {boolean} activo
   * @returns {Promise<void>}
   */
  async saActivar(id, activo) {
    this._asegurarInstituciones();
    const inst = this.db.instituciones.find((/** @type {{ id: string }} */ i) => i.id === id);
    if (!inst) throw err("No se encontró la institución");
    inst.activo = Boolean(activo);
    this.persist();
  },

  /**
   * @this {any}
   * @param {string} _id
   * @returns {Promise<any>}
   */
  async saEntrar(_id) {
    throw err("En modo demo hay un solo instituto");
  },

  /**
   * @this {any}
   * @param {string} _id
   * @param {string} _email
   * @returns {Promise<any>}
   */
  async saAsignarAdmin(_id, _email) {
    throw err("En modo demo hay un solo instituto");
  },

  /**
   * @this {any}
   * @param {string} cid
   * @param {string} nombre
   * @returns {Promise<void>}
   */
  async renombrarInstituto(cid, nombre) {
    this.reload();
    const nom = String(nombre || "").trim();
    if (nom.length < 3 || nom.length > 80) throw err("El nombre debe tener entre 3 y 80 caracteres");
    this.db.colegio.nombre = nom;
    if (this.db.instituciones) {
      const inst = this.db.instituciones.find((/** @type {{ id: string }} */ i) => i.id === cid || i.id === this.db.colegio.id);
      if (inst) inst.nombre = nom;
    }
    this.persist();
  },
  /**
   * @this {any}
   * @param {string} cid
   * @param {string} modo
   * @returns {Promise<void>}
   */
  async cambiarQrModo(cid, modo) {
    const m = String(modo || "").toLowerCase().trim();
    if (!["off", "opcional", "obligatorio"].includes(m)) {
      throw err("Modo no válido (usa off, opcional u obligatorio)");
    }
    this.reload();
    this.db.colegio.qr_modo = m;
    if (this.db.instituciones) {
      const inst = this.db.instituciones.find((/** @type {{ id: string }} */ i) => i.id === cid || i.id === this.db.colegio.id);
      if (inst) inst.qr_modo = m;
    }
    this.persist();
  },
};
