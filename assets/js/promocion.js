// @ts-check
// Cambio de ciclo al cerrar un periodo: cada alumno activo pasa al ciclo siguiente (mismo salón); los del VI egresan.
// Lógica pura: arma el plan para que el administrador lo revise antes de aplicarlo.
import { CICLOS, nombreCiclo, parsearCiclo } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Grado} Grado */
/** @typedef {import('./tipos.d.ts').PlanPromocion} PlanPromocion */
/** @typedef {import('./tipos.d.ts').CambioPromocion} CambioPromocion */

/**
 * @param {Alumno[]} alumnos  lista de alumnos
 * @param {Grado[]} [grados]
 * @param {string[]} [carreras] (opcional) solo estas carreras; vacío = todas
 * @returns {PlanPromocion}
 */
export function planPromocion(alumnos, grados = [], carreras = []) {
  const filtro = new Set(carreras);
  const existentes = new Set(grados.map((g) => `${g.nivel}|${g.nombre}`));
  /** @type {PlanPromocion} */
  const plan = { mover: [], egresan: [], sinCiclo: [], gradosNuevos: [] };
  const nuevos = new Set();
  alumnos.filter((a) => a.estado === "ACTIVO" && (!filtro.size || filtro.has(a.nivel))).forEach((a) => {
    const p = parsearCiclo(a.grado);
    if (!p) { plan.sinCiclo.push(a); return; }
    const i = p.ciclo ? CICLOS.indexOf(p.ciclo) : -1;
    if (i < 0) { plan.sinCiclo.push(a); return; }
    if (i === CICLOS.length - 1) { plan.egresan.push(a); return; }
    const a2 = nombreCiclo(a.nivel, CICLOS[i + 1], p.seccion || "");
    plan.mover.push({ alumno: a, de: a.grado, a: a2, carrera: a.nivel, ciclo: CICLOS[i + 1] });
    const k = `${a.nivel}|${a2}`;
    if (!existentes.has(k) && !nuevos.has(k)) { nuevos.add(k); plan.gradosNuevos.push({ nivel: a.nivel, nombre: a2 }); }
  });
  return plan;
}

/**
 * Cambios por alumno a escribir (pasos de la promoción ya confirmada).
 * @param {PlanPromocion} plan
 * @returns {CambioPromocion[]}
 */
export function cambiosPromocion(plan) {
  return [
    ...plan.mover.map((m) => ({ id: m.alumno.id, cambios: { grado: m.a } })),
    ...plan.egresan.map((a) => ({ id: a.id, cambios: { estado: "EGRESADO" } })),
  ];
}
