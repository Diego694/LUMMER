// @ts-check
// Aula: material y actividades por curso (migración 013). Funciones sobre un cliente Supabase (`sb`) que usan tanto el
// panel (api.js) como el portal del estudiante (estudiante/api.js); el modo demo guarda solo los metadatos en el navegador.
import { uid } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Curso} Curso */
/** @typedef {import('./tipos.d.ts').CursoMaterial} CursoMaterial */
/** @typedef {import('./tipos.d.ts').CursoActividad} CursoActividad */
/** @typedef {import('./tipos.d.ts').ArchivoAula} ArchivoAula */
/** @typedef {import('./tipos.d.ts').CursoEntrega} CursoEntrega */

export const BUCKET_AULA = "cursos";
export const MAX_ARCHIVO_AULA = 10 * 1024 * 1024;   // 10 MB (mismo límite que el bucket)
const EXT_PERMITIDAS = ["pdf", "txt", "zip", "doc", "docx", "ppt", "pptx", "xls", "xlsx", "jpg", "jpeg", "png", "webp"];

/**
 * @param {string} message
 * @param {any} [code]
 * @returns {Error & { code?: any }}
 */
const err = (message, code) => Object.assign(new Error(message), { code });

/**
 * Valida el archivo antes de subirlo. Devuelve el mensaje de error o null si es válido.
 * @param {{ name: string, size: number }} f
 * @returns {string | null}
 */
export function validarArchivoAula(f) {
  const ext = (f.name.split(".").pop() || "").toLowerCase();
  if (!EXT_PERMITIDAS.includes(ext)) return `Tipo de archivo no permitido (.${ext || "?"}). Usa PDF, Word, PowerPoint, Excel, imágenes, TXT o ZIP.`;
  if (f.size > MAX_ARCHIVO_AULA) return `El archivo pesa ${(f.size / 1048576).toFixed(1)} MB; el máximo es 10 MB.`;
  if (f.size === 0) return "El archivo está vacío.";
  return null;
}

/**
 * Ruta segura dentro del bucket: <colegio>/<curso>/<id>-<nombre-limpio>.<ext>
 * @param {string} colegioId
 * @param {string} cursoId
 * @param {string} nombre
 */
export function rutaArchivoAula(colegioId, cursoId, nombre) {
  const ext = (nombre.split(".").pop() || "bin").toLowerCase().replace(/[^a-z0-9]/g, "");
  const base = nombre.replace(/\.[^.]+$/, "").normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/[^a-zA-Z0-9]+/g, "-").replace(/^-|-$/g, "").slice(0, 50) || "archivo";
  return `${colegioId}/${cursoId}/${uid()}-${base}.${ext}`;
}

/**
 * Ruta del archivo de una entrega: <colegio>/<curso>/entregas/<user_id>/<id>-<nombre>.<ext> (privada para el autor y el docente).
 * @param {string} colegioId
 * @param {string} cursoId
 * @param {string} userId
 * @param {string} nombre
 */
export function rutaEntrega(colegioId, cursoId, userId, nombre) {
  const archivo = rutaArchivoAula(colegioId, cursoId, nombre).split("/")[2];
  return `${colegioId}/${cursoId}/entregas/${userId}/${archivo}`;
}

/**
 * Valida la nota contra el puntaje máximo. Devuelve el mensaje de error o null.
 * @param {number} nota
 * @param {number} max
 * @returns {string | null}
 */
export function validarNota(nota, max) {
  if (!Number.isFinite(nota)) return "Escribe una nota válida.";
  if (nota < 0 || nota > max) return `La nota debe estar entre 0 y ${max}.`;
  return null;
}

/**
 * @param {any} sb
 * @param {string} cursoId
 * @returns {Promise<CursoMaterial[]>}
 */
export async function materialesDe(sb, cursoId) {
  const { data, error } = await sb.from("curso_materiales").select("*").eq("curso_id", cursoId).order("creado_en", { ascending: false });
  if (error) throw err(error.message, error.code);
  return data;
}

/**
 * @param {any} sb
 * @param {string} cursoId
 * @returns {Promise<CursoActividad[]>}
 */
export async function actividadesDe(sb, cursoId) {
  const { data, error } = await sb.from("curso_actividades").select("*").eq("curso_id", cursoId).order("fecha_limite", { ascending: true, nullsFirst: false });
  if (error) throw err(error.message, error.code);
  return data;
}

/**
 * Sube un archivo y devuelve sus datos para guardarlos en la fila del material o la actividad.
 * @param {any} sb
 * @param {string} colegioId
 * @param {string} cursoId
 * @param {File} file
 * @returns {Promise<ArchivoAula>}
 */
export async function subirArchivoAula(sb, colegioId, cursoId, file) {
  const problema = validarArchivoAula(file);
  if (problema) throw err(problema);
  const path = rutaArchivoAula(colegioId, cursoId, file.name);
  const { error } = await sb.storage.from(BUCKET_AULA).upload(path, file, { contentType: file.type || undefined, upsert: false });
  if (error) throw err(error.message);
  return { archivo_path: path, archivo_nombre: file.name, archivo_bytes: file.size };
}

/**
 * Enlace temporal (10 min) para abrir o descargar un archivo del aula.
 * @param {any} sb
 * @param {string} path
 * @returns {Promise<string>}
 */
export async function urlArchivoAula(sb, path) {
  const { data, error } = await sb.storage.from(BUCKET_AULA).createSignedUrl(path, 600);
  if (error || !data?.signedUrl) throw err(error?.message || "No se pudo abrir el archivo.");
  return data.signedUrl;
}

/**
 * Guarda (crea o edita) una fila de `curso_materiales` o `curso_actividades`.
 * @param {any} sb
 * @param {"curso_materiales" | "curso_actividades"} tabla
 * @param {Record<string, any>} row
 * @returns {Promise<void>}
 */
export async function guardarFila(sb, tabla, row) {
  const { id, ...resto } = row;
  const q = id ? sb.from(tabla).update(resto).eq("id", id) : sb.from(tabla).insert(resto);
  const { error } = await q;
  if (error) throw err(error.message, error.code);
}

/**
 * Elimina la fila y, si tenía archivo, también el archivo del bucket.
 * @param {any} sb
 * @param {"curso_materiales" | "curso_actividades"} tabla
 * @param {{ id: string, archivo_path?: string | null }} fila
 * @returns {Promise<void>}
 */
export async function eliminarFila(sb, tabla, fila) {
  const { error, count } = await sb.from(tabla).delete({ count: "exact" }).eq("id", fila.id);
  if (error) throw err(error.message, error.code);
  if (count === 0) throw err("No tienes permiso para eliminarlo (solo el docente del curso o el administrador).");
  if (fila.archivo_path) await sb.storage.from(BUCKET_AULA).remove([fila.archivo_path]).catch(() => { /* el archivo huérfano no bloquea */ });
}

/**
 * Entregas de una actividad con el nombre del alumno (solo las ve quien gestiona el curso).
 * @param {any} sb
 * @param {string} actividadId
 * @returns {Promise<CursoEntrega[]>}
 */
export async function entregasDe(sb, actividadId) {
  const { data, error } = await sb.from("curso_entregas").select("*, alumnos(nombre, codigo)").eq("actividad_id", actividadId).order("enviado_en", { ascending: true });
  if (error) throw err(error.message, error.code);
  return data;
}

/**
 * Entrega del estudiante actual a una actividad (la política RLS solo le deja ver la suya).
 * @param {any} sb
 * @param {string} actividadId
 * @returns {Promise<CursoEntrega | null>}
 */
export async function miEntregaDe(sb, actividadId) {
  const { data, error } = await sb.from("curso_entregas").select("*").eq("actividad_id", actividadId).limit(1);
  if (error) throw err(error.message, error.code);
  return data[0] || null;
}

/**
 * El estudiante entrega: sube el archivo (si hay) a su carpeta y registra la entrega con la función SQL.
 * @param {any} sb
 * @param {{ id: string }} user
 * @param {{ id?: string, colegio_id?: string, curso_id: string }} actividad
 * @param {string} texto
 * @param {File} [file]
 * @returns {Promise<void>}
 */
export async function entregarSb(sb, user, actividad, texto, file) {
  if (!texto.trim() && !file) throw err("Escribe una respuesta o adjunta un archivo.");
  /** @type {Partial<ArchivoAula>} */
  let adjunto = {};
  if (file) {
    const problema = validarArchivoAula(file);
    if (problema) throw err(problema);
    const path = rutaEntrega(String(actividad.colegio_id), actividad.curso_id, user.id, file.name);
    const { error } = await sb.storage.from(BUCKET_AULA).upload(path, file, { contentType: file.type || undefined, upsert: false });
    if (error) throw err(error.message);
    adjunto = { archivo_path: path, archivo_nombre: file.name, archivo_bytes: file.size };
  }
  const { error } = await sb.rpc("entregar_actividad", { p_actividad: actividad.id, p_texto: texto, p_archivo_path: adjunto.archivo_path ?? null, p_archivo_nombre: adjunto.archivo_nombre ?? null, p_archivo_bytes: adjunto.archivo_bytes ?? null });
  if (error) {
    if (adjunto.archivo_path) await sb.storage.from(BUCKET_AULA).remove([adjunto.archivo_path]).catch(() => { /* huérfano: no bloquea */ });
    throw err(error.message, error.code);
  }
}

/**
 * @param {any} sb
 * @param {string} entregaId
 * @param {number | null} nota null = quitar la calificación
 * @param {string} comentario
 * @returns {Promise<void>}
 */
export async function calificarSb(sb, entregaId, nota, comentario) {
  const { error } = await sb.rpc("calificar_entrega", { p_entrega: entregaId, p_nota: nota, p_comentario: comentario });
  if (error) throw err(error.message, error.code);
}

/* ------------------------------ Supabase (panel) ------------------------------ */
export const aulaSupabase = {
  /** @this {any} @param {string} cursoId */
  aulaMateriales(cursoId) { return materialesDe(this.sb, cursoId); },
  /** @this {any} @param {string} cursoId */
  aulaActividades(cursoId) { return actividadesDe(this.sb, cursoId); },
  /** @this {any} @param {"curso_materiales" | "curso_actividades"} tabla @param {Record<string, any>} row @param {File} [archivo] */
  async aulaGuardar(tabla, row, archivo) {
    const datos = { ...row };
    if (archivo) Object.assign(datos, await subirArchivoAula(this.sb, row.colegio_id, row.curso_id, archivo));
    await guardarFila(this.sb, tabla, datos);
  },
  /** @this {any} @param {"curso_materiales" | "curso_actividades"} tabla @param {{ id: string, archivo_path?: string | null }} fila */
  aulaEliminar(tabla, fila) { return eliminarFila(this.sb, tabla, fila); },
  /** @this {any} @param {string} path */
  aulaUrlArchivo(path) { return urlArchivoAula(this.sb, path); },
  /** @this {any} @param {string} actividadId */
  aulaEntregas(actividadId) { return entregasDe(this.sb, actividadId); },
  /** @this {any} @param {string} id @param {number | null} nota @param {string} comentario */
  aulaCalificar(id, nota, comentario) { return calificarSb(this.sb, id, nota, comentario); },
  /** @this {any} @param {string} cursoId @returns {Promise<{ user_id: string }[]>} */
  async aulaDocentes(cursoId) {
    const { data, error } = await this.sb.from("curso_docentes").select("user_id").eq("curso_id", cursoId);
    if (error) throw err(error.message, error.code);
    return data;
  },
  /** @this {any} @param {string} cursoId @param {string} userId */
  async aulaAsignarDocente(cursoId, userId) {
    const { error } = await this.sb.from("curso_docentes").insert({ curso_id: cursoId, user_id: userId });
    if (error && error.code !== "23505") throw err(error.message, error.code);
  },
  /** @this {any} @param {string} cursoId @param {string} userId */
  async aulaQuitarDocente(cursoId, userId) {
    const { error } = await this.sb.from("curso_docentes").delete().eq("curso_id", cursoId).eq("user_id", userId);
    if (error) throw err(error.message, error.code);
  },
};

/* ------------------------------ Demo / local (metadatos en el navegador) ------------------------------ */
const tabla = (/** @type {any} */ db, /** @type {string} */ n) => (db[n] ||= []);

export const aulaDemo = {
  /** @this {any} @param {string} cursoId */
  async aulaMateriales(cursoId) { this.reload(); return tabla(this.db, "curso_materiales").filter((/** @type {any} */ m) => m.curso_id === cursoId); },
  /** @this {any} @param {string} cursoId */
  async aulaActividades(cursoId) { this.reload(); return tabla(this.db, "curso_actividades").filter((/** @type {any} */ a) => a.curso_id === cursoId); },
  /** @this {any} @param {"curso_materiales" | "curso_actividades"} t @param {Record<string, any>} row @param {File} [archivo] */
  async aulaGuardar(t, row, archivo) {
    this.reload();
    const datos = { ...row };
    if (archivo) {
      const problema = validarArchivoAula(archivo);
      if (problema) throw err(problema);
      Object.assign(datos, { archivo_path: null, archivo_nombre: archivo.name, archivo_bytes: archivo.size });   // demo: sin bytes
    }
    const l = tabla(this.db, t);
    const i = datos.id ? l.findIndex((/** @type {any} */ x) => x.id === datos.id) : -1;
    if (i >= 0) l[i] = { ...l[i], ...datos };
    else l.unshift({ id: uid(), creado_en: new Date().toISOString(), publicado: true, ...datos });
    this.persist();
  },
  /** @this {any} @param {"curso_materiales" | "curso_actividades"} t @param {{ id: string }} fila */
  async aulaEliminar(t, fila) { this.db[t] = tabla(this.db, t).filter((/** @type {any} */ x) => x.id !== fila.id); this.persist(); },
  async aulaUrlArchivo() { throw err("En el modo demostración los archivos no se guardan."); },
  /** @this {any} @param {string} actividadId */
  async aulaEntregas(actividadId) {
    this.reload();
    return tabla(this.db, "curso_entregas").filter((/** @type {any} */ e) => e.actividad_id === actividadId)
      .map((/** @type {any} */ e) => ({ ...e, alumnos: (this.db.alumnos || []).find((/** @type {any} */ a) => a.id === e.alumno_id) || { nombre: "Estudiante", codigo: "" } }));
  },
  /** @this {any} @param {string} id @param {number | null} nota @param {string} comentario */
  async aulaCalificar(id, nota, comentario) {
    this.reload();
    const e = tabla(this.db, "curso_entregas").find((/** @type {any} */ x) => x.id === id);
    if (!e) throw err("Entrega no encontrada.");
    const a = tabla(this.db, "curso_actividades").find((/** @type {any} */ x) => x.id === e.actividad_id);
    const problema = nota == null ? null : validarNota(nota, a?.puntaje_max ?? 20);
    if (problema) throw err(problema);
    Object.assign(e, { nota, comentario, calificado_en: nota == null ? null : new Date().toISOString() });
    this.persist();
  },
  /** @this {any} @param {string} cursoId */
  async aulaDocentes(cursoId) { this.reload(); return tabla(this.db, "curso_docentes").filter((/** @type {any} */ d) => d.curso_id === cursoId); },
  /** @this {any} @param {string} cursoId @param {string} userId */
  async aulaAsignarDocente(cursoId, userId) {
    this.reload(); const l = tabla(this.db, "curso_docentes");
    if (!l.some((/** @type {any} */ d) => d.curso_id === cursoId && d.user_id === userId)) l.push({ curso_id: cursoId, user_id: userId });
    this.persist();
  },
  /** @this {any} @param {string} cursoId @param {string} userId */
  async aulaQuitarDocente(cursoId, userId) {
    this.db.curso_docentes = tabla(this.db, "curso_docentes").filter((/** @type {any} */ d) => !(d.curso_id === cursoId && d.user_id === userId)); this.persist();
  },
};

/* ------------------------------ Estudiante: entregas (demo) ------------------------------ */
/**
 * Entrega en modo demo (localStorage compartido con el panel): solo metadatos. Modifica `db`; quien llama lo guarda.
 * @param {any} db
 * @param {{ id: string }} alumno
 * @param {{ id?: string, curso_id: string, fecha_limite?: string | null }} actividad
 * @param {string} texto
 * @param {File} [file]
 */
export function entregarDemo(db, alumno, actividad, texto, file) {
  if (!texto.trim() && !file) throw err("Escribe una respuesta o adjunta un archivo.");
  if (file) { const problema = validarArchivoAula(file); if (problema) throw err(problema); }
  const l = tabla(db, "curso_entregas");
  const previa = l.find((/** @type {any} */ e) => e.actividad_id === actividad.id && e.alumno_id === alumno.id);
  if (previa?.nota != null) throw err("La entrega ya fue calificada y no se puede cambiar.");
  const datos = { texto, archivo_path: null, archivo_nombre: file?.name ?? null, archivo_bytes: file?.size ?? null, enviado_en: new Date().toISOString(),
    tardia: !!actividad.fecha_limite && Date.now() > new Date(actividad.fecha_limite).getTime() };
  if (previa) Object.assign(previa, datos);
  else l.push({ id: uid(), curso_id: actividad.curso_id, actividad_id: actividad.id, alumno_id: alumno.id, nota: null, comentario: "", ...datos });
}
