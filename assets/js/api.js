// Capa de datos. Dos adaptadores con la MISMA interfaz:
//  - SupabaseBackend: producción (Postgres + Auth + RLS).
//  - DemoBackend:     localStorage, para probar sin servidor.
// El resto de la app solo habla con `api`.
import { CONFIG, DEMO_USER, isDemoMode, modoLocal } from "./config.js";
import { buildDemoDB } from "./demo-data.js";
import { extrasDemo, extrasSupabase } from "./api-extra.js";
import { uid } from "./utils.js";

const TABLAS = ["alumnos", "niveles", "grados", "docentes", "comunicados", "cursos", "periodos", "calendario", "horarios"];
const err = (message, code) => Object.assign(new Error(message), { code });

/* ----------------------------- DEMO ----------------------------- */
/** Código de 12 caracteres (como el de la base) con el que el apoderado consulta la asistencia de su hijo. */
export const codigoApoderado = () => Array.from({ length: 12 }, () => "0123456789ABCDEF"[Math.floor(Math.random() * 16)]).join("");

class DemoBackend {
  mode = "demo";
  /** Historial de cambios del demo/local (en la base real lo escriben disparadores que nadie puede saltarse). */
  _auditar(accion, tabla, id, nuevo, previo) {
    if (tabla === "auditoria") return;
    const detalle = accion === "UPDATE"
      ? Object.fromEntries(Object.keys(nuevo || {}).filter((k) => previo && previo[k] !== nuevo[k]).map((k) => [k, { de: previo[k], a: nuevo[k] }]))
      : (accion === "DELETE" ? { ...(nuevo || {}) } : { ...(nuevo || {}) });
    if (accion === "UPDATE" && !Object.keys(detalle).length) return;
    (this.db.auditoria ||= []).unshift({ id: uid(), colegio_id: this.db.colegio.id, usuario: this.mode === "local" ? "local@este-equipo" : DEMO_USER.email, accion, tabla, registro_id: id, detalle, creado_en: new Date().toISOString() });
    this.db.auditoria = this.db.auditoria.slice(0, 500);
  }
  KEY = "ra-demo-db-v1";
  SESSION = "ra-demo-session";

  constructor() {
    if (this.constructor.KEY) this.KEY = this.constructor.KEY;
    try { this.db = JSON.parse(localStorage.getItem(this.KEY)); } catch { this.db = null; }
    if (!this.db) { this.db = this.nuevaDB(); this.persist(); }
    // datos guardados por versiones anteriores: completar lo que faltaba
    let cambio = false;
    this.db.alumnos.forEach((a) => { if (!a.codigo_apoderado) { a.codigo_apoderado = codigoApoderado(); cambio = true; } });
    if (cambio) this.persist();
  }
  nuevaDB() { return buildDemoDB(); }
  persist() { try { localStorage.setItem(this.KEY, JSON.stringify(this.db)); } catch { /* cuota llena: se ignora */ } }
  reset() { this.db = this.nuevaDB(); this.persist(); }

  async init() { return localStorage.getItem(this.SESSION) ? { id: "demo-user", email: DEMO_USER.email } : null; }
  async signIn(email, password) {
    if (email.trim().toLowerCase() !== DEMO_USER.email || password !== DEMO_USER.password) throw err("Credenciales inválidas", "auth");
    localStorage.setItem(this.SESSION, "1");
    return { id: "demo-user", email: DEMO_USER.email };
  }
  async signOut() { localStorage.removeItem(this.SESSION); }
  async getProfile() { return { colegio_id: this.db.colegio.id, rol: "Administrador", nombre: this.db.perfil_nombre || "Administrador demo", carrera: null, foto_path: this.db.foto_perfil ? "local" : null, colegio: this.db.colegio.nombre, superadmin: true, qr_modo: this.db.colegio.qr_modo || "obligatorio" }; }
  async userId() { return "demo-user"; }
  /** Hora "del servidor". En demo se puede simular un reloj de teléfono desfasado con localStorage ra-sim-desfase-ms. */
  async horaServidor() { return Date.now() + (Number(localStorage.getItem("ra-sim-desfase-ms")) || 0); }

  // El portal de estudiantes (otra página, mismo navegador) escribe en el mismo localStorage: se relee antes de cargar.
  reload() { try { const d = JSON.parse(localStorage.getItem(this.KEY)); if (d) this.db = d; } catch { /* se conserva la copia en memoria */ } }
  async getCodigoRegistro() { this.reload(); return this.db.colegio.codigo_registro || null; }
  async setCodigoRegistro(_cid, codigo) { this.db.colegio.codigo_registro = codigo; this.persist(); }
  async fotoUrl(alumno) { this.reload(); return this.db.alumnos.find((a) => a.id === alumno.id)?.foto_data || null; }

  async loadAll() {
    this.reload();
    const { alumnos, niveles, grados, comunicados, docentes } = this.db;
    const cursos = [...(this.db.cursos || [])];
    const sort = (a, k) => [...a].sort((x, y) => String(x[k]).localeCompare(String(y[k]), "es", { numeric: true }));
    return {
      alumnos: sort(alumnos, "nombre"), niveles: sort(niveles, "nombre"), grados: sort(grados, "nombre"),
      docentes: sort(docentes, "nombre"), comunicados: [...comunicados].sort((a, b) => b.fecha.localeCompare(a.fecha)), cursos: sort(cursos, "nombre"),
      ajustes: { periodos: [...(this.db.periodos || [])], calendario: [...(this.db.calendario || [])], horarios: [...(this.db.horarios || [])] },
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
    const list = (this.db[tabla] ||= []);
    if (tabla === "calendario" && list.some((x) => x.id !== id && x.fecha === data.fecha)) throw err("Fecha duplicada", "duplicate");
    if (tabla === "periodos" && list.some((x) => x.id !== id && x.nombre === data.nombre)) throw err("Periodo duplicado", "duplicate");
    if (tabla === "alumnos" && list.some((x) => x.codigo === data.codigo && x.id !== id)) throw err("Código duplicado", "duplicate");
    // misma regla que el índice único de la base: curso único por carrera + ciclo + nombre (sin distinguir mayúsculas)
    if (tabla === "cursos" && list.some((x) => x.id !== id && x.nivel === data.nivel && (x.grado || "") === (data.grado || "") && x.nombre.toLowerCase() === String(data.nombre).toLowerCase())) throw err("Curso duplicado", "duplicate");
    if (id) { const prev = list.find((x) => x.id === id); this._auditar("UPDATE", tabla, id, data, prev); Object.assign(prev, data); }
    else { const nuevo = { id: uid(), colegio_id: this.db.colegio.id, ...data }; if (tabla === "alumnos" && !nuevo.codigo_apoderado) nuevo.codigo_apoderado = codigoApoderado(); list.push(nuevo); this._auditar("INSERT", tabla, nuevo.id, data); }
    this.persist();
  }
  async remove(tabla, id) {
    if (!TABLAS.includes(tabla)) throw err("Tabla inválida");
    this._auditar("DELETE", tabla, id, tabla === "alumnos" ? { codigo: (this.db.alumnos.find((x) => x.id === id) || {}).codigo } : (this.db[tabla] || []).find((x) => x.id === id));
    this.db[tabla] = (this.db[tabla] || []).filter((x) => x.id !== id);
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

// Pruebas sin conexión: con globalThis.__simOffline = true, toda llamada "de red" del demo falla como un corte real.
["loadAll", "asistenciasRango", "asistenciasPorFecha", "asistenciasAlumno", "registrarAsistencia", "registrarMasivo", "save", "remove", "upsertAlumnos", "horaServidor"].forEach((m) => {
  const original = DemoBackend.prototype[m];
  DemoBackend.prototype[m] = async function (...args) {
    if (globalThis.__simOffline) throw err("Failed to fetch (sin conexión simulada)");
    return original.apply(this, args);
  };
});

/** fetch con tiempo de espera: en redes "colgadas" (señal sin datos) falla pronto y el registro pasa a la cola local. */
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

  constructor() {
    if (!window.supabase) throw new Error("No se pudo cargar la librería de Supabase (¿sin conexión?)");
    this.sb = window.supabase.createClient(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, { global: { fetch: (u, o) => fetchConTimeout(u, o) } });
  }
  /** Hora del servidor en ms (función SQL hora_servidor, migración 004). */
  async horaServidor() {
    const { data, error } = await this.sb.rpc("hora_servidor");
    if (error) throw err(error.message, error.code);
    return new Date(data).getTime();
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
  async getCodigoRegistro(cid) {
    const { data, error } = await this.sb.from("colegios").select("codigo_registro").eq("id", cid).maybeSingle();
    return error ? null : data?.codigo_registro ?? null;
  }
  async setCodigoRegistro(cid, codigo) {
    const { error } = await this.sb.from("colegios").update({ codigo_registro: codigo }).eq("id", cid);
    if (error) throw err(error.code === "23505" ? "Ese código ya está en uso, genera otro." : error.message, error.code === "23505" ? "duplicate" : error.code);
  }
  /** URL firmada (1 h) de la foto del alumno; el bucket es privado. Cacheada por ruta. */
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
    const [alumnos, niveles, grados, comunicados, docentes, cursos] = await Promise.all([
      q("alumnos", "nombre"), q("niveles", "nombre"), q("grados", "nombre"), q("comunicados", "fecha", false), q("docentes", "nombre"), this.cursosLista(cid),
    ]);
    const ajustes = await this.ajustesLista(cid);
    return { alumnos, niveles, grados, comunicados, docentes, cursos, ajustes };
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

Object.assign(DemoBackend.prototype, extrasDemo);
Object.assign(SupabaseBackend.prototype, extrasSupabase);
// Las operaciones nuevas de red del demo también fallan con la red simulada caída
["cursosLista", "ajustesLista", "registrarSalidas", "justificacionesRango", "guardarJustificacion", "asistenciasCursoPorFecha", "registrarAsistenciaCurso", "registrarMasivoCurso", "avisosPorFecha", "registrarAviso", "exportarTodo"].forEach((m) => {
  const original = DemoBackend.prototype[m];
  DemoBackend.prototype[m] = async function (...args) {
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
  nuevaDB() {
    const id = "local-instituto";
    return {
      colegio: { id, nombre: "Mi instituto (modo local)", codigo_registro: "LOCAL", qr_modo: "obligatorio" },
      niveles: [], grados: [], alumnos: [], asistencias: [], docentes: [], comunicados: [], cursos: [],
    };
  }
  async init() { return { id: "local-user", email: "local@este-equipo" }; }
  async signIn() { return { id: "local-user", email: "local@este-equipo" }; }
  async signOut() { /* sin cuentas: nada que cerrar */ }
  async getProfile() { return { colegio_id: this.db.colegio.id, rol: "Administrador", nombre: this.db.perfil_nombre || "Administrador (local)", carrera: null, foto_path: this.db.foto_perfil ? "local" : null, colegio: this.db.colegio.nombre, superadmin: false, qr_modo: this.db.colegio.qr_modo || "obligatorio" }; }
  async esSuperadmin() { return false; }
  async userId() { return "local-user"; }
}

/** @returns {import('./tipos.d.ts').Api} */
function crearBackend() {
  try { return modoLocal() ? new LocalBackend() : isDemoMode() ? new DemoBackend() : new SupabaseBackend(); }
  catch (error) { return /** @type {import('./tipos.d.ts').Api} */ (/** @type {unknown} */ ({ mode: "error", error, init: async () => { throw error; } })); }
}
export const api = crearBackend();
export { DB } from "./state.js";
