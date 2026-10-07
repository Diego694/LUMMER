// @ts-check
// Alcance del docente: solo ve a los alumnos, carreras, ciclos y cursos de los cursos que el administrador le asignó.
// Es un filtro de la interfaz; el aislamiento por curso en la base de datos requiere políticas RLS propias.

/**
 * Recorta los datos del instituto a lo que alcanzan los cursos asignados. Sin cursos asignados no queda nada.
 * @template {{ id?: string, nivel: string, grado?: string | null }} A
 * @template {{ nombre: string }} N
 * @template {{ nivel: string, nombre: string }} G
 * @template {{ id?: string, nivel: string, grado?: string | null }} C
 * @param {{ alumnos: A[], niveles: N[], grados: G[], cursos: C[] }} datos
 * @param {Iterable<string>} asignados ids de curso asignados a este usuario
 * @param {{ curso_id: string, alumno_id: string }[]} [cursoAlumnos]
 */
export function alcanceDocente(datos, asignados, cursoAlumnos = []) {
  const ids = new Set(asignados);
  const cursos = datos.cursos.filter((c) => c.id != null && ids.has(c.id));
  const manualesIds = new Set((cursoAlumnos || []).filter((ca) => ids.has(ca.curso_id)).map((ca) => ca.alumno_id));
  const alumnos = datos.alumnos.filter((a) => (a.id && manualesIds.has(a.id)) || cursos.some((c) => c.nivel === a.nivel && (!c.grado || c.grado === a.grado)));
  const grados = datos.grados.filter((g) => cursos.some((c) => c.nivel === g.nivel && (!c.grado || c.grado === g.nombre)) || alumnos.some((a) => a.nivel === g.nivel && a.grado === g.nombre));
  const niveles = datos.niveles.filter((n) => cursos.some((c) => c.nivel === n.nombre) || alumnos.some((a) => a.nivel === n.nombre));
  return { alumnos, niveles, grados, cursos };
}

const clave = (/** @type {string} */ uid) => `ra-asignados-${uid}`;
/** @param {string} uid @param {string[]} ids */
export function recordarAsignados(uid, ids) { try { localStorage.setItem(clave(uid), JSON.stringify(ids)); } catch { /* sin almacenamiento */ } }
/** @param {string} uid @returns {string[]} */
export function asignadosGuardados(uid) { try { return JSON.parse(localStorage.getItem(clave(uid)) || "[]"); } catch { return []; } }
