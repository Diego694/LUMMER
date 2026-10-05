// @ts-check
// Alerta temprana: alumnos que se acercan (o pasaron) el límite de inasistencias del periodo. Lógica pura.
import { diasLectivos } from "./calendario.js";
import { pct } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Asistencia} Asistencia */
/** @typedef {import('./tipos.d.ts').Justificacion} Justificacion */
/** @typedef {import('./tipos.d.ts').DiaCalendario} DiaCalendario */
/** @typedef {import('./tipos.d.ts').RiesgoAlumno} RiesgoAlumno */
/** @typedef {import('./tipos.d.ts').OpcionesCalcularRiesgo} OpcionesCalcularRiesgo */

/**
 * @param {OpcionesCalcularRiesgo} opciones
 * @returns {RiesgoAlumno[]} ordenado por % de faltas.
 */
export function calcularRiesgo({ alumnos, asistencias, justificaciones = [], noLectivos = new Map(), desde, hasta, limite = 30, aviso, hoy }) {
  const umbralAviso = aviso ?? Math.round(limite * 0.7);
  let dias = diasLectivos(desde, hasta, noLectivos);
  const asis = new Set(asistencias.map((a) => `${a.alumno_id}|${a.fecha}`));
  const just = new Set(justificaciones.map((j) => `${j.alumno_id}|${j.fecha}`));
  if (hoy && dias.at(-1) === hoy) dias = dias.slice(0, -1);   // el día en curso aún no cuenta como falta
  const total = dias.length;
  if (!total) return [];
  return alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false).map((alumno) => {
    let faltas = 0, justificadas = 0;
    dias.forEach((d) => {
      const k = `${alumno.id}|${d}`;
      if (asis.has(k)) return;
      if (just.has(k)) justificadas++; else faltas++;
    });
    const pctFaltas = pct(faltas, total);
    /** @type {'critico' | 'alerta' | 'ok'} */
    const nivel = pctFaltas >= limite ? "critico" : pctFaltas >= umbralAviso ? "alerta" : "ok";
    return { alumno, dias: total, faltas, justificadas, pctFaltas, nivel };
  }).sort((x, y) => y.pctFaltas - x.pctFaltas || x.alumno.nombre.localeCompare(y.alumno.nombre, "es"));
}

/**
 * Cuántas faltas más puede tener antes de llegar al límite (0 si ya lo superó).
 * @param {RiesgoAlumno} r
 * @param {number} [limite]
 * @returns {number}
 */
export const faltasRestantes = (r, limite = 30) => Math.max(0, Math.floor((limite / 100) * r.dias) - r.faltas);
