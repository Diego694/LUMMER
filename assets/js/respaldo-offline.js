// @ts-check
// Respaldo offline: un archivo (.rabackup) con las asistencias y registros hechos sin internet, que luego se importa en la
// web, la app o el programa de PC y queda en las FECHAS en que se grabaron (no en la actual). Lógica pura, sin DOM ni red.
//
// Formato (JSON de texto): { formato:"ra-respaldo", version:1, creado_en, instituto:{id,nombre}, origen:{modo,app,version},
//   rango:{desde,hasta}, conteo:{…}, datos:{ alumnos, asistencias, asistencias_curso, justificaciones }, sha256 }
// Los alumnos se identifican por su CÓDIGO (no por el id interno), así el archivo vale en cualquier equipo del mismo instituto.
// `sha256` es la huella de `datos`: detecta archivos dañados o alterados.

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Asistencia} Asistencia */
/** @typedef {import('./tipos.d.ts').Curso} Curso */
/** @typedef {import('./tipos.d.ts').Justificacion} Justificacion */
/** @typedef {import('./tipos.d.ts').AsistenciaCurso} AsistenciaCurso */
/** @typedef {import('./tipos.d.ts').RespaldoPaquete} RespaldoPaquete */
/** @typedef {import('./tipos.d.ts').RespaldoDatos} RespaldoDatos */
/** @typedef {import('./tipos.d.ts').RespaldoAsistencia} RespaldoAsistencia */
/** @typedef {import('./tipos.d.ts').RespaldoAsistenciaCurso} RespaldoAsistenciaCurso */
/** @typedef {import('./tipos.d.ts').RespaldoJustificacion} RespaldoJustificacion */
/** @typedef {import('./tipos.d.ts').RespaldoAlumno} RespaldoAlumno */
/** @typedef {import('./tipos.d.ts').ValidarPaqueteResult} ValidarPaqueteResult */
/** @typedef {import('./tipos.d.ts').PlanImportacion} PlanImportacion */
/** @typedef {import('./tipos.d.ts').PlanImportacionPorFecha} PlanImportacionPorFecha */
/** @typedef {import('./tipos.d.ts').CtxImportacion} CtxImportacion */

export const FORMATO = "ra-respaldo";
export const VERSION = 1;
export const EXTENSION = ".rabackup";
export const MAX_BYTES = 25 * 1024 * 1024;

const RE_FECHA = /^\d{4}-\d{2}-\d{2}$/;
const RE_HORA = /^([01]\d|2[0-3]):[0-5]\d(:[0-5]\d)?$/;
/** @param {string | null | undefined} h */
const hhmm = (h) => String(h || "").slice(0, 5);

/**
 * @param {string} texto
 * @returns {Promise<string>}
 */
export async function sha256Hex(texto) {
  const b = await globalThis.crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
  return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
}

/**
 * @param {string} desde
 * @param {string} [hasta]
 * @returns {string}
 */
export const nombreArchivo = (desde, hasta) => `asistencias_${desde}${hasta && hasta !== desde ? "_a_" + hasta : ""}${EXTENSION}`;

/**
 * Arma el contenido del archivo.
 * @param {Object} opts
 * @param {any} [opts.instituto]
 * @param {any} [opts.origen]
 * @param {{ desde: string, hasta: string }} opts.rango
 * @param {Alumno[]} opts.alumnos todos los alumnos conocidos (para pasar id → código)
 * @param {Asistencia[]} [opts.asistencias] filas {alumno_id, fecha, hora, hora_salida?, origen?}
 * @param {{ alumno_id: string, fecha: string, hora: string }[]} [opts.salidas] filas aún sueltas (se unen a su asistencia del día)
 * @param {Curso[]} [opts.cursos] cursos conocidos (id → nombre, carrera, ciclo)
 * @param {AsistenciaCurso[]} [opts.asistenciasCurso]
 * @param {Justificacion[]} [opts.justificaciones]
 * @param {string} [opts.ahoraIso]
 * @returns {Promise<string>}
 */
export async function construirPaquete({ instituto, origen, rango, alumnos, asistencias = [], salidas = [], cursos = [], asistenciasCurso = [], justificaciones = [], ahoraIso = new Date().toISOString() }) {
  const codigo = new Map(alumnos.map((a) => [a.id, a]));
  const curso = new Map(cursos.map((c) => [c.id, c]));
  /** @param {string} f */
  const enRango = (f) => f >= rango.desde && f <= rango.hasta;
  /** @type {Map<string, RespaldoAsistencia>} */
  const porClave = new Map();
  asistencias.filter((x) => enRango(x.fecha) && codigo.has(x.alumno_id)).forEach((x) => {
    const a = codigo.get(x.alumno_id);
    if (!a) return;
    porClave.set(`${x.alumno_id}|${x.fecha}`, { codigo: a.codigo, fecha: x.fecha, hora: hhmm(x.hora), hora_salida: x.hora_salida ? hhmm(x.hora_salida) : null, origen: x.origen || "manual" });
  });
  salidas.filter((x) => enRango(x.fecha)).forEach((x) => { const r = porClave.get(`${x.alumno_id}|${x.fecha}`); if (r && !r.hora_salida) r.hora_salida = hhmm(x.hora); });
  const asis = [...porClave.values()].sort((a, b) => a.fecha.localeCompare(b.fecha) || a.hora.localeCompare(b.hora) || a.codigo.localeCompare(b.codigo));
  const cas = asistenciasCurso.filter((x) => enRango(x.fecha) && codigo.has(x.alumno_id) && curso.has(x.curso_id)).map((x) => {
    const a = codigo.get(x.alumno_id);
    const c = curso.get(x.curso_id);
    return { codigo: a?.codigo || "", curso: { nombre: c?.nombre || "", nivel: c?.nivel || "", grado: c?.grado || null }, fecha: x.fecha, hora: hhmm(x.hora), origen: x.origen || "manual" };
  }).sort((a, b) => a.fecha.localeCompare(b.fecha) || a.hora.localeCompare(b.hora));
  const jus = justificaciones.filter((x) => enRango(x.fecha) && codigo.has(x.alumno_id))
    .map((x) => ({ codigo: codigo.get(x.alumno_id)?.codigo || "", fecha: x.fecha, tipo: x.tipo || "Falta justificada", motivo: x.motivo || "" }));
  const usados = new Set([...asis, ...cas, ...jus].map((x) => x.codigo));
  const als = [...usados].sort().map((c) => { const a = alumnos.find((x) => x.codigo === c); return { codigo: c, nombre: a?.nombre || "", nivel: a?.nivel || "", grado: a?.grado || "" }; });
  const datos = { alumnos: als, asistencias: asis, asistencias_curso: cas, justificaciones: jus };
  const dias = new Set(asis.map((x) => x.fecha));
  return JSON.stringify({
    formato: FORMATO, version: VERSION, creado_en: ahoraIso, instituto, origen, rango,
    conteo: { alumnos: als.length, asistencias: asis.length, asistencias_curso: cas.length, justificaciones: jus.length, dias: dias.size },
    datos, sha256: await sha256Hex(JSON.stringify(datos)),
  });
}

/**
 * Lee y valida un archivo. Nunca lanza: devuelve { ok, paquete?, errores:[], avisos:[] }.
 * Las filas con fecha/hora inválida se descartan y se cuentan en `avisos`.
 * @param {string} texto
 * @param {{ hoy?: string }} [opts]
 * @returns {Promise<ValidarPaqueteResult>}
 */
export async function validarPaquete(texto, { hoy = new Date().toISOString().slice(0, 10) } = {}) {
  /** @type {string[]} */
  const errores = [];
  /** @type {string[]} */
  const avisos = [];
  if (typeof texto !== "string" || !texto.trim()) return { ok: false, errores: ["El archivo está vacío."], avisos };
  if (texto.length > MAX_BYTES) return { ok: false, errores: ["El archivo es demasiado grande (máximo 25 MB)."], avisos };
  let p;
  try { p = JSON.parse(texto); } catch { return { ok: false, errores: ["El archivo no es un respaldo válido (no se puede leer)."], avisos }; }
  if (p?.formato !== FORMATO) return { ok: false, errores: ["Este archivo no es un respaldo de asistencias de Lummer."], avisos };
  if (p.version > VERSION) return { ok: false, errores: [`Este respaldo es de una versión más nueva (${p.version}). Actualiza la aplicación e inténtalo de nuevo.`], avisos };
  const d = p.datos;
  if (!d || !Array.isArray(d.asistencias) || !Array.isArray(d.alumnos)) return { ok: false, errores: ["El respaldo está incompleto."], avisos };
  d.asistencias_curso ||= []; d.justificaciones ||= [];
  try {
    if (p.sha256 && (await sha256Hex(JSON.stringify(d))) !== p.sha256) return { ok: false, errores: ["El archivo está dañado o fue modificado (la huella no coincide). Vuelve a exportarlo."], avisos };
  } catch { avisos.push("No se pudo verificar la integridad del archivo en este navegador."); }
  /** @param {any} x */
  const buena = (x) => x && typeof x.codigo === "string" && x.codigo && RE_FECHA.test(x.fecha) && x.fecha >= "2020-01-01" && x.fecha <= hoy;
  let descartadas = 0;
  d.asistencias = d.asistencias.filter((/** @type {any} */ x) => { const ok = buena(x) && RE_HORA.test(String(x.hora)) && (!x.hora_salida || RE_HORA.test(String(x.hora_salida))); if (!ok) descartadas++; return ok; });
  d.asistencias_curso = d.asistencias_curso.filter((/** @type {any} */ x) => { const ok = buena(x) && RE_HORA.test(String(x.hora)) && x.curso?.nombre; if (!ok) descartadas++; return ok; });
  d.justificaciones = d.justificaciones.filter((/** @type {any} */ x) => { const ok = buena(x); if (!ok) descartadas++; return ok; });
  if (descartadas) avisos.push(`${descartadas} registro(s) con fecha u hora inválida (o futura) se ignoraron.`);
  return { ok: true, paquete: p, errores, avisos };
}

/**
 * Qué pasaría al importar. `ctx`: { alumnos (online/del equipo), cursos, existentes:{ asistencias: Map "alumnoId|fecha" → fila, cursos:Set "alumnoId|cursoId|fecha", just:Set "alumnoId|fecha" } }.
 * Nada se pisa: lo que ya existe se cuenta como «repetido»; solo se completa la hora de salida si faltaba.
 * @param {RespaldoPaquete} paquete
 * @param {CtxImportacion} ctx
 * @returns {PlanImportacion}
 */
export function planImportacion(paquete, ctx) {
  const idPorCodigo = new Map(ctx.alumnos.map((a) => [a.codigo, a.id]));
  const d = paquete.datos;
  /** @type {Map<string, PlanImportacionPorFecha>} */
  const porFecha = new Map();
  /** @param {string} f */
  const dia = (f) => {
    let df = porFecha.get(f);
    if (!df) {
      df = { fecha: f, nuevas: 0, repetidas: 0, salidas: 0, curso: 0, justificaciones: 0, sinAlumno: 0 };
      porFecha.set(f, df);
    }
    return df;
  };
  /** @type {Asistencia[]} */
  const asistenciasPlan = [];
  /** @type {{ alumno_id: string, fecha: string, hora: string }[]} */
  const salidasPlan = [];
  /** @type {{ alumno_id: string, curso_id?: string, fecha: string, hora: string, origen: string }[]} */
  const cursosPlan = [];
  /** @type {{ alumno_id: string, fecha: string, tipo: string, motivo: string }[]} */
  const justificacionesPlan = [];
  /** @type {Set<string>} */
  const desconocidosSet = new Set();
  /** @type {Set<string>} */
  const cursosSinMatchSet = new Set();

  d.asistencias.forEach((x) => {
    const id = idPorCodigo.get(x.codigo), f = dia(x.fecha);
    if (!id) { f.sinAlumno++; desconocidosSet.add(x.codigo); return; }
    const ya = ctx.existentes.asistencias.get(`${id}|${x.fecha}`);
    if (!ya) { asistenciasPlan.push({ alumno_id: id, fecha: x.fecha, hora: hhmm(x.hora), hora_salida: x.hora_salida ? hhmm(x.hora_salida) : null, origen: "importado" }); f.nuevas++; }
    else { f.repetidas++; if (x.hora_salida && !ya.hora_salida) { salidasPlan.push({ alumno_id: id, fecha: x.fecha, hora: hhmm(x.hora_salida) }); f.salidas++; } }
  });
  /** @param {any} c */
  const cursoDe = (c) => ctx.cursos.find((k) => k.nombre.toLowerCase() === String(c.nombre).toLowerCase() && k.nivel === c.nivel && (k.grado || null) === (c.grado || null));
  d.asistencias_curso.forEach((x) => {
    const id = idPorCodigo.get(x.codigo), f = dia(x.fecha), c = cursoDe(x.curso);
    if (!id) { f.sinAlumno++; desconocidosSet.add(x.codigo); return; }
    if (!c) { cursosSinMatchSet.add(`${x.curso.nombre} (${x.curso.nivel})`); return; }
    if (ctx.existentes.cursos.has(`${id}|${c.id}|${x.fecha}`)) { f.repetidas++; return; }
    cursosPlan.push({ alumno_id: id, curso_id: c.id, fecha: x.fecha, hora: hhmm(x.hora), origen: "importado" }); f.curso++;
  });
  d.justificaciones.forEach((x) => {
    const id = idPorCodigo.get(x.codigo), f = dia(x.fecha);
    if (!id) { f.sinAlumno++; desconocidosSet.add(x.codigo); return; }
    if (ctx.existentes.just.has(`${id}|${x.fecha}`)) { f.repetidas++; return; }
    justificacionesPlan.push({ alumno_id: id, fecha: x.fecha, tipo: x.tipo, motivo: x.motivo || "" }); f.justificaciones++;
  });
  const porFechaArr = [...porFecha.values()].sort((a, b) => a.fecha.localeCompare(b.fecha));
  const desconocidosArr = [...desconocidosSet];
  const cursosSinMatchArr = [...cursosSinMatchSet];
  /** @type {PlanImportacion} */
  const plan = {
    asistencias: asistenciasPlan,
    salidas: salidasPlan,
    cursos: cursosPlan,
    justificaciones: justificacionesPlan,
    desconocidos: desconocidosArr,
    cursosSinMatch: cursosSinMatchArr,
    porFecha: porFechaArr,
    resumen: {
      asistencias: asistenciasPlan.length,
      salidas: salidasPlan.length,
      cursos: cursosPlan.length,
      justificaciones: justificacionesPlan.length,
      repetidas: porFechaArr.reduce((s, x) => s + x.repetidas, 0),
      desconocidos: desconocidosArr.length,
    },
  };
  return plan;
}
