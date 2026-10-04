// Pasar los datos del modo LOCAL (programa de PC) a la base ONLINE. Lógica pura: arma el plan para revisarlo antes de enviar.
// Regla: nunca se pisa nada que ya exista en la base online; solo se agrega lo que falta.

/**
 * @param local   { niveles, grados, alumnos, asistencias, comunicados, cursos, justificaciones } de la base local
 * @param remoto  { niveles, grados, alumnos, comunicados, cursos } lo que ya hay online
 */
export function planFusion(local, remoto) {
  const L = (k) => local?.[k] || [], R = (k) => remoto?.[k] || [];
  const nombresN = new Set(R("niveles").map((n) => n.nombre ?? n));
  const clavesG = new Set(R("grados").map((g) => `${g.nivel}|${g.nombre}`));
  const codigos = new Set(R("alumnos").map((a) => a.codigo));
  const clavesC = new Set(R("comunicados").map((c) => `${c.fecha}|${c.titulo}`));
  const clavesCu = new Set(R("cursos").map((c) => `${c.nivel}|${c.grado || ""}|${String(c.nombre).toLowerCase()}`));
  const plan = {
    niveles: L("niveles").filter((n) => !nombresN.has(n.nombre)),
    grados: L("grados").filter((g) => !clavesG.has(`${g.nivel}|${g.nombre}`)),
    alumnos: L("alumnos").filter((a) => !codigos.has(a.codigo)),
    alumnosExistentes: L("alumnos").filter((a) => codigos.has(a.codigo)).length,
    comunicados: L("comunicados").filter((c) => !clavesC.has(`${c.fecha}|${c.titulo}`)),
    cursos: L("cursos").filter((c) => !clavesCu.has(`${c.nivel}|${c.grado || ""}|${String(c.nombre).toLowerCase()}`)),
    asistencias: L("asistencias"),
  };
  plan.resumen = {
    niveles: plan.niveles.length, grados: plan.grados.length, alumnos: plan.alumnos.length, alumnosExistentes: plan.alumnosExistentes,
    asistencias: plan.asistencias.length, comunicados: plan.comunicados.length, cursos: plan.cursos.length,
  };
  return plan;
}

/** Convierte las asistencias locales (con alumno_id local) a filas online usando el código del alumno como llave. */
export function asistenciasParaOnline(asistenciasLocal, alumnosLocal, alumnosOnline, colegioId, userId) {
  const codigoLocal = new Map(alumnosLocal.map((a) => [a.id, a.codigo]));
  const idOnline = new Map(alumnosOnline.map((a) => [a.codigo, a.id]));
  const filas = [];
  let sinAlumno = 0;
  asistenciasLocal.forEach((x) => {
    const id = idOnline.get(codigoLocal.get(x.alumno_id));
    if (!id) { sinAlumno++; return; }
    filas.push({ colegio_id: colegioId, alumno_id: id, fecha: x.fecha, hora: x.hora, hora_salida: x.hora_salida || null, registrado_por: userId, origen: x.origen || "local" });
  });
  return { filas, sinAlumno };
}
