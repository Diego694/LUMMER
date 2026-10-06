// @ts-check
// Pasar los datos del modo LOCAL (programa de PC) a la base ONLINE. Lógica pura: arma el plan para revisarlo antes de enviar.
// Regla: nunca se pisa nada que ya exista en la base online; solo se agrega lo que falta.

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Asistencia} Asistencia */
/** @typedef {import('./tipos.d.ts').DatosFusionLocal} DatosFusionLocal */
/** @typedef {import('./tipos.d.ts').DatosFusionRemoto} DatosFusionRemoto */
/** @typedef {import('./tipos.d.ts').PlanFusion} PlanFusion */
/** @typedef {import('./tipos.d.ts').AsistenciasOnlineResult} AsistenciasOnlineResult */

/**
 * @param {DatosFusionLocal} local   { niveles, grados, alumnos, asistencias, comunicados, cursos, justificaciones } de la base local
 * @param {DatosFusionRemoto} remoto  { niveles, grados, alumnos, comunicados, cursos } lo que ya hay online
 * @returns {PlanFusion}
 */
export function planFusion(local, remoto) {
  /** @template {keyof DatosFusionLocal} K @param {K} k @returns {NonNullable<DatosFusionLocal[K]>} */
  const L = (k) => /** @type {any} */ (local?.[k] || []);
  /** @template {keyof DatosFusionRemoto} K @param {K} k @returns {NonNullable<DatosFusionRemoto[K]>} */
  const R = (k) => /** @type {any} */ (remoto?.[k] || []);
  const nombresN = new Set(R("niveles").map((n) => (typeof n === "object" && n !== null && "nombre" in n ? n.nombre : n)));
  const clavesG = new Set(R("grados").map((g) => `${g.nivel}|${g.nombre}`));
  const codigos = new Set(R("alumnos").map((a) => a.codigo));
  const clavesC = new Set(R("comunicados").map((c) => `${c.fecha}|${c.titulo}`));
  const clavesCu = new Set(R("cursos").map((c) => `${c.nivel}|${c.grado || ""}|${String(c.nombre).toLowerCase()}`));
  const niveles = L("niveles").filter((n) => !nombresN.has(n.nombre));
  const grados = L("grados").filter((g) => !clavesG.has(`${g.nivel}|${g.nombre}`));
  const alumnos = L("alumnos").filter((a) => !codigos.has(a.codigo));
  const alumnosExistentes = L("alumnos").filter((a) => codigos.has(a.codigo)).length;
  const comunicados = L("comunicados").filter((c) => !clavesC.has(`${c.fecha}|${c.titulo}`));
  const cursos = L("cursos").filter((c) => !clavesCu.has(`${c.nivel}|${c.grado || ""}|${String(c.nombre).toLowerCase()}`));
  const asistencias = L("asistencias");
  /** @type {PlanFusion} */
  const plan = {
    niveles,
    grados,
    alumnos,
    alumnosExistentes,
    comunicados,
    cursos,
    asistencias,
    resumen: {
      niveles: niveles.length,
      grados: grados.length,
      alumnos: alumnos.length,
      alumnosExistentes,
      asistencias: asistencias.length,
      comunicados: comunicados.length,
      cursos: cursos.length,
    },
  };
  return plan;
}

/**
 * Convierte las asistencias locales (con alumno_id local) a filas online usando el código del alumno como llave.
 * @param {Asistencia[]} asistenciasLocal
 * @param {Alumno[]} alumnosLocal
 * @param {Alumno[]} alumnosOnline
 * @param {string} colegioId
 * @param {string} userId
 * @returns {AsistenciasOnlineResult}
 */
export function asistenciasParaOnline(asistenciasLocal, alumnosLocal, alumnosOnline, colegioId, userId) {
  const codigoLocal = new Map(alumnosLocal.map((a) => [a.id, a.codigo]));
  const idOnline = new Map(alumnosOnline.map((a) => [a.codigo, a.id]));
  /** @type {Asistencia[]} */
  const filas = [];
  let sinAlumno = 0;
  asistenciasLocal.forEach((x) => {
    const cod = codigoLocal.get(x.alumno_id);
    const id = cod ? idOnline.get(cod) : undefined;
    if (!id) { sinAlumno++; return; }
    filas.push({ colegio_id: colegioId, alumno_id: id, fecha: x.fecha, hora: x.hora, hora_salida: x.hora_salida || null, registrado_por: userId, origen: x.origen || "local" });
  });
  return { filas, sinAlumno };
}
