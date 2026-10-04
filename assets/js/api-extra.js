// Operaciones adicionales de la capa de datos (justificaciones, cursos, asistencia por curso, avisos, errores,
// respaldo y recuperación de contraseña). Se mezclan en DemoBackend y SupabaseBackend (ver api.js): usan `this`.
import { uid } from "./utils.js";

const err = (message, code) => Object.assign(new Error(message), { code });

/** Lee todas las filas paginando de a 1000 (límite por defecto de PostgREST). */
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
  async cursosLista(cid) {
    try { return await paginar(() => this.sb.from("cursos").select("*").eq("colegio_id", cid).order("nombre")); }
    catch (e) { if (/does not exist|relation|schema cache/i.test(e.message)) return []; throw e; }  // antes de aplicar la migración 004
  },
  justificacionesRango(cid, desde, hasta) {
    return paginar(() => this.sb.from("justificaciones").select("*").eq("colegio_id", cid).gte("fecha", desde).lte("fecha", hasta).order("id"));
  },
  async guardarJustificacion(row) {
    const { error } = await this.sb.from("justificaciones").insert(row);
    if (error) throw err(error.code === "23505" ? "Ya existe una justificación para ese día (solo el administrador puede modificarla)." : error.message, error.code === "23505" ? "duplicate" : error.code);
  },
  async eliminarJustificacion(id) {
    const { error, count } = await this.sb.from("justificaciones").delete({ count: "exact" }).eq("id", id);
    if (error) throw err(error.message, error.code);
    if (count === 0) throw err("No tienes permiso para eliminarla (solo el administrador).");
  },
  asistenciasCursoPorFecha(cid, cursoId, fecha) {
    return paginar(() => this.sb.from("asistencias_curso").select("*").eq("colegio_id", cid).eq("curso_id", cursoId).eq("fecha", fecha).order("id"));
  },
  async registrarAsistenciaCurso(row) {
    const { error } = await this.sb.from("asistencias_curso").insert(row);
    if (error) throw err(error.message, error.code === "23505" ? "duplicate" : error.code);
  },
  async registrarMasivoCurso(rows) {
    const { data, error } = await this.sb.from("asistencias_curso").upsert(rows, { onConflict: "alumno_id,curso_id,fecha", ignoreDuplicates: true }).select("id");
    if (error) throw err(error.message, error.code);
    return data?.length ?? 0;
  },
  avisosPorFecha(cid, fecha) {
    return paginar(() => this.sb.from("avisos_apoderados").select("*").eq("colegio_id", cid).eq("fecha", fecha).order("id"));
  },
  async registrarAviso(row) {
    const { error } = await this.sb.from("avisos_apoderados").insert(row);
    if (error) throw err(error.message, error.code);
  },
  async registrarError(row) {
    const { error } = await this.sb.from("logs_cliente").insert(row);
    if (error) throw err(error.message, error.code);
  },
  async erroresRecientes(cid, limite = 200) {
    const { data, error } = await this.sb.from("logs_cliente").select("*").eq("colegio_id", cid).order("creado_en", { ascending: false }).limit(limite);
    if (error) throw err(error.message, error.code);
    return data;
  },
  async borrarErrores(cid) {
    const { error } = await this.sb.from("logs_cliente").delete().eq("colegio_id", cid);
    if (error) throw err(error.message, error.code);
  },
  /** Respaldo: todas las tablas del instituto. Las que aún no existen se omiten (instalaciones sin la migración 004). */
  async exportarTodo(cid) {
    const out = {};
    for (const t of TABLAS_RESPALDO) {
      try { out[t] = await paginar(() => this.sb.from(t).select("*").eq("colegio_id", cid).order("id")); }
      catch (e) { if (!/does not exist|relation|schema cache/i.test(e.message)) throw e; out[t] = []; }
    }
    return out;
  },
  async eliminarFotoAlumno(alumno) {
    if (!alumno?.foto_path) return;
    const carpeta = alumno.foto_path.split("/")[0];
    const { data } = await this.sb.storage.from("fotos-alumnos").list(carpeta);
    const rutas = (data || []).map((f) => `${carpeta}/${f.name}`);
    if (rutas.length) await this.sb.storage.from("fotos-alumnos").remove(rutas);
  },
  async personalListar() {
    const { data, error } = await this.sb.rpc("personal_listar");
    if (error) throw err(error.message, error.code);
    return data || [];
  },
  async personalAsignar(email, rol, carrera, nombre) {
    const { error } = await this.sb.rpc("personal_asignar", { p_email: email, p_rol: rol, p_carrera: carrera || null, p_nombre: nombre || null });
    if (error) throw err(error.message, error.code);
  },
  async personalQuitar(id) {
    const { error } = await this.sb.rpc("personal_quitar", { p_id: id });
    if (error) throw err(error.message, error.code);
  },
  async tokenAvisos() {
    const { data, error } = await this.sb.rpc("token_avisos");
    return error ? null : data;   // sin la migración 005 simplemente no hay avisos
  },
  async solicitarRecuperacion(email, redirectTo) {
    const { error } = await this.sb.auth.resetPasswordForEmail(email.trim(), { redirectTo });
    if (error) throw err(/rate limit/i.test(error.message) ? "Se enviaron demasiados correos. Espera unos minutos e inténtalo de nuevo." : error.message, error.code);
  },
  async cambiarPassword(password) {
    const { error } = await this.sb.auth.updateUser({ password });
    if (error) throw err(error.message, error.code);
  },
  alRecuperar(cb) {
    this.sb.auth.onAuthStateChange((evento) => { if (evento === "PASSWORD_RECOVERY") cb(); });
  },
};

/* --------------------------------- Demo --------------------------------- */
const tabla = (db, n) => (db[n] ||= []);

export const extrasDemo = {
  async cursosLista() { this.reload(); return [...tabla(this.db, "cursos")].sort((a, b) => a.nombre.localeCompare(b.nombre, "es")); },
  async justificacionesRango(_c, desde, hasta) { this.reload(); return tabla(this.db, "justificaciones").filter((j) => j.fecha >= desde && j.fecha <= hasta); },
  async guardarJustificacion(row) {
    const l = tabla(this.db, "justificaciones");
    if (l.some((j) => j.alumno_id === row.alumno_id && j.fecha === row.fecha)) throw err("Ya existe una justificación para ese día (solo el administrador puede modificarla).", "duplicate");
    l.push({ id: uid(), creado_en: new Date().toISOString(), ...row }); this.persist();
  },
  async eliminarJustificacion(id) { this.db.justificaciones = tabla(this.db, "justificaciones").filter((j) => j.id !== id); this.persist(); },
  async asistenciasCursoPorFecha(_c, cursoId, fecha) { this.reload(); return tabla(this.db, "asistencias_curso").filter((a) => a.curso_id === cursoId && a.fecha === fecha); },
  async registrarAsistenciaCurso(row) {
    const l = tabla(this.db, "asistencias_curso");
    if (l.some((a) => a.alumno_id === row.alumno_id && a.curso_id === row.curso_id && a.fecha === row.fecha)) throw err("Ya registrado", "duplicate");
    l.push({ id: uid(), registrado_en: new Date().toISOString(), ...row }); this.persist();
  },
  async registrarMasivoCurso(rows) {
    const l = tabla(this.db, "asistencias_curso"); let n = 0;
    rows.forEach((r) => { if (!l.some((a) => a.alumno_id === r.alumno_id && a.curso_id === r.curso_id && a.fecha === r.fecha)) { l.push({ id: uid(), registrado_en: new Date().toISOString(), ...r }); n++; } });
    this.persist(); return n;
  },
  async avisosPorFecha(_c, fecha) { this.reload(); return tabla(this.db, "avisos_apoderados").filter((a) => a.fecha === fecha); },
  async registrarAviso(row) { tabla(this.db, "avisos_apoderados").push({ id: uid(), creado_en: new Date().toISOString(), ...row }); this.persist(); },
  async registrarError(row) { const l = tabla(this.db, "logs_cliente"); l.unshift({ id: uid(), creado_en: new Date().toISOString(), ...row }); this.db.logs_cliente = l.slice(0, 200); this.persist(); },
  async erroresRecientes() { this.reload(); return [...tabla(this.db, "logs_cliente")]; },
  async borrarErrores() { this.db.logs_cliente = []; this.persist(); },
  async exportarTodo() {
    this.reload();
    const map = { alumnos: "alumnos", niveles: "niveles", grados: "grados", docentes: "docentes", comunicados: "comunicados", cursos: "cursos", asistencias: "asistencias", asistencias_curso: "asistencias_curso", justificaciones: "justificaciones", avisos_apoderados: "avisos_apoderados" };
    return Object.fromEntries(Object.entries(map).map(([t, k]) => [t, JSON.parse(JSON.stringify(this.db[k] || []))]));
  },
  async eliminarFotoAlumno() { /* en demo la foto vive dentro del propio registro */ },
  async personalListar() {
    this.db.personal = this.db.personal || [{ id: "demo-user", email: "demo@instituto.pe", nombre: "Administrador", rol: "Administrador", carrera: null }];
    return JSON.parse(JSON.stringify(this.db.personal));
  },
  async personalAsignar(email, rol, carrera, nombre) {
    await this.personalListar();
    if (!/^\S+@\S+\.\S+$/.test(String(email).trim())) throw err("No existe una cuenta con ese correo. Créala primero en Supabase → Authentication → Users.");
    if (rol === "Coordinador" && !carrera) throw err("El coordinador necesita una carrera existente");
    const e = String(email).trim().toLowerCase();
    const p = this.db.personal.find((x) => x.email === e);
    if (p) Object.assign(p, { rol, carrera: rol === "Coordinador" ? carrera : null, nombre: nombre || p.nombre });
    else this.db.personal.push({ id: uid(), email: e, nombre: nombre || null, rol, carrera: rol === "Coordinador" ? carrera : null });
    this.persist();
  },
  async personalQuitar(id) {
    await this.personalListar();
    if (id === "demo-user") throw err("No puedes quitarte el acceso a ti mismo");
    this.db.personal = this.db.personal.filter((x) => x.id !== id); this.persist();
  },
  async tokenAvisos() { return null; },
  async solicitarRecuperacion() { throw err("En modo demo no se envían correos."); },
  async cambiarPassword() { throw err("En modo demo no hay recuperación de contraseña."); },
  alRecuperar() { /* sin eventos en demo */ },
};
