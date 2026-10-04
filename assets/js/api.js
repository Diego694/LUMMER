// Capa de datos. Dos adaptadores con la MISMA interfaz:
//  - SupabaseBackend: producción (Postgres + Auth + RLS).
//  - DemoBackend:     localStorage, para probar sin servidor.
// El resto de la app solo habla con `api`.
import { CONFIG, DEMO_USER, isDemoMode } from "./config.js";
import { buildDemoDB } from "./demo-data.js";
import { uid } from "./utils.js";

const TABLAS = ["alumnos", "niveles", "grados", "docentes", "comunicados"];
const err = (message, code) => Object.assign(new Error(message), { code });

/* ----------------------------- DEMO ----------------------------- */
class DemoBackend {
  mode = "demo";
  KEY = "ra-demo-db-v1";
  SESSION = "ra-demo-session";

  constructor() {
    try { this.db = JSON.parse(localStorage.getItem(this.KEY)); } catch { this.db = null; }
    if (!this.db) { this.db = buildDemoDB(); this.persist(); }
  }
  persist() { try { localStorage.setItem(this.KEY, JSON.stringify(this.db)); } catch { /* cuota llena: se ignora */ } }
  reset() { this.db = buildDemoDB(); this.persist(); }

  async init() { return localStorage.getItem(this.SESSION) ? { id: "demo-user", email: DEMO_USER.email } : null; }
  async signIn(email, password) {
    if (email.trim().toLowerCase() !== DEMO_USER.email || password !== DEMO_USER.password) throw err("Credenciales inválidas", "auth");
    localStorage.setItem(this.SESSION, "1");
    return { id: "demo-user", email: DEMO_USER.email };
  }
  async signOut() { localStorage.removeItem(this.SESSION); }
  async getProfile() { return { colegio_id: this.db.colegio.id, rol: "admin", nombre: "Administrador demo", colegio: this.db.colegio.nombre }; }
  async userId() { return "demo-user"; }

  async loadAll() {
    const { alumnos, niveles, grados, comunicados, docentes } = this.db;
    const sort = (a, k) => [...a].sort((x, y) => String(x[k]).localeCompare(String(y[k]), "es", { numeric: true }));
    return {
      alumnos: sort(alumnos, "nombre"), niveles: sort(niveles, "nombre"), grados: sort(grados, "nombre"),
      docentes: sort(docentes, "nombre"), comunicados: [...comunicados].sort((a, b) => b.fecha.localeCompare(a.fecha)),
    };
  }
  async asistenciasRango(_c, desde, hasta) { return this.db.asistencias.filter((a) => a.fecha >= desde && a.fecha <= hasta); }
  async asistenciasPorFecha(_c, fecha) { return this.db.asistencias.filter((a) => a.fecha === fecha); }
  async asistenciasAlumno(alumnoId, limite = 100) {
    return this.db.asistencias.filter((a) => a.alumno_id === alumnoId).sort((a, b) => b.fecha.localeCompare(a.fecha)).slice(0, limite);
  }

  async registrarAsistencia(row) {
    if (this.db.asistencias.some((a) => a.alumno_id === row.alumno_id && a.fecha === row.fecha)) throw err("Ya registrado", "duplicate");
    this.db.asistencias.push({ id: uid(), ...row });
    this.persist();
  }
  async registrarMasivo(rows) {
    let n = 0;
    rows.forEach((row) => {
      if (!this.db.asistencias.some((a) => a.alumno_id === row.alumno_id && a.fecha === row.fecha)) { this.db.asistencias.push({ id: uid(), ...row }); n++; }
    });
    this.persist();
    return n;
  }

  async save(tabla, data, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    const list = this.db[tabla];
    if (tabla === "alumnos" && list.some((x) => x.codigo === data.codigo && x.id !== id)) throw err("Código duplicado", "duplicate");
    if (id) Object.assign(list.find((x) => x.id === id), data);
    else list.push({ id: uid(), colegio_id: this.db.colegio.id, ...data });
    this.persist();
  }
  async remove(tabla, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    this.db[tabla] = this.db[tabla].filter((x) => x.id !== id);
    if (tabla === "alumnos") this.db.asistencias = this.db.asistencias.filter((a) => a.alumno_id !== id);
    this.persist();
  }
  async upsertAlumnos(rows) {
    rows.forEach((r) => {
      const cur = this.db.alumnos.find((a) => a.codigo === r.codigo);
      if (cur) Object.assign(cur, r); else this.db.alumnos.push({ id: uid(), colegio_id: this.db.colegio.id, ...r });
    });
    this.persist();
    return rows.length;
  }
}

/* --------------------------- SUPABASE --------------------------- */
class SupabaseBackend {
  mode = "supabase";

  constructor() {
    if (!window.supabase) throw new Error("No se pudo cargar la librería de Supabase (¿sin conexión?)");
    this.sb = window.supabase.createClient(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY);
  }
  async init() { const { data } = await this.sb.auth.getSession(); return data.session?.user ?? null; }
  async signIn(email, password) {
    const { data, error } = await this.sb.auth.signInWithPassword({ email, password });
    if (error) throw err(error.message, "auth");
    return data.user;
  }
  async signOut() { await this.sb.auth.signOut(); }
  async userId() { return (await this.sb.auth.getUser()).data.user?.id; }
  async getProfile(user) {
    const { data, error } = await this.sb.from("perfiles").select("*, colegios(nombre)").eq("id", user.id).single();
    if (error || !data) throw err("No se encontró un perfil de colegio para esta cuenta.", "profile");
    return { colegio_id: data.colegio_id, rol: data.rol, nombre: data.nombre, colegio: data.colegios?.nombre ?? "" };
  }

  /** Lee todas las filas paginando de a 1000 (límite por defecto de PostgREST). */
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

  async loadAll(cid) {
    const q = (t, col, asc = true) => this.#todo(() => this.sb.from(t).select("*").eq("colegio_id", cid).order(col, { ascending: asc }));
    const [alumnos, niveles, grados, comunicados, docentes] = await Promise.all([
      q("alumnos", "nombre"), q("niveles", "nombre"), q("grados", "nombre"), q("comunicados", "fecha", false), q("docentes", "nombre"),
    ]);
    return { alumnos, niveles, grados, comunicados, docentes };
  }
  asistenciasRango(cid, desde, hasta) {
    return this.#todo(() => this.sb.from("asistencias").select("*").eq("colegio_id", cid).gte("fecha", desde).lte("fecha", hasta).order("id"));
  }
  asistenciasPorFecha(cid, fecha) { return this.asistenciasRango(cid, fecha, fecha); }
  async asistenciasAlumno(alumnoId, limite = 100) {
    const { data, error } = await this.sb.from("asistencias").select("*").eq("alumno_id", alumnoId).order("fecha", { ascending: false }).limit(limite);
    if (error) throw err(error.message, error.code);
    return data;
  }
  async registrarAsistencia(row) {
    const { error } = await this.sb.from("asistencias").insert(row);
    if (error) throw err(error.message, error.code === "23505" ? "duplicate" : error.code);
  }
  async registrarMasivo(rows) {
    const { data, error } = await this.sb.from("asistencias").upsert(rows, { onConflict: "alumno_id,fecha", ignoreDuplicates: true }).select("id");
    if (error) throw err(error.message, error.code);
    return data?.length ?? 0;
  }
  async save(tabla, data, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    const cid = data.colegio_id;
    const { error } = id ? await this.sb.from(tabla).update(data).eq("id", id) : await this.sb.from(tabla).insert({ colegio_id: cid, ...data });
    if (error) throw err(error.message, error.code === "23505" ? "duplicate" : error.code);
  }
  async remove(tabla, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    const { error } = await this.sb.from(tabla).delete().eq("id", id);
    if (error) throw err(error.message, error.code);
  }
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

function crearBackend() {
  try { return isDemoMode() ? new DemoBackend() : new SupabaseBackend(); }
  catch (error) { return { mode: "error", error, init: async () => { throw error; } }; }
}
export const api = crearBackend();
