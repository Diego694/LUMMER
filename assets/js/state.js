// Estado global en memoria de la sesión actual.
import { api } from "./api.js";
import { asistenciasPendientesDe, guardarSnapshot, leerSnapshot } from "./cola.js";
import { cicloCorto, compararCiclos, esErrorRed, todayStr } from "./utils.js";

export const DB = {
  cid: null, rol: null, perfil: null, userId: null,
  alumnos: [], niveles: [], nivelesRaw: [], grados: [], comunicados: [], docentes: [], cursos: [],
  hoy: [], hoyFecha: null,
  sinConexion: false,   // true cuando los datos vienen de la copia local (sin red)
};

function aplicar(d) {
  DB.alumnos = d.alumnos;
  DB.nivelesRaw = d.niveles;
  DB.niveles = d.niveles.map((n) => n.nombre);
  DB.grados = d.grados;
  DB.comunicados = d.comunicados;
  DB.docentes = d.docentes;
  DB.cursos = d.cursos || [];
}

/** Carga los datos del instituto. Sin red usa la última copia local para poder seguir registrando asistencia. */
export async function loadAll() {
  try {
    const d = await api.loadAll(DB.cid);
    aplicar(d);
    DB.sinConexion = false;
    guardarSnapshot(DB.cid, { alumnos: d.alumnos, niveles: d.niveles, grados: d.grados, comunicados: d.comunicados, docentes: d.docentes, cursos: d.cursos || [] });
  } catch (e) {
    if (!esErrorRed(e)) throw e;
    const s = leerSnapshot(DB.cid);
    if (!s) throw new Error("Sin conexión y sin datos guardados en este teléfono. Conéctate una vez para descargarlos.");
    aplicar(s);
    DB.sinConexion = true;
  }
  await refreshHoy();
}

/** Asistencias de hoy: las del servidor + las guardadas sin conexión aún por enviar (así el duplicado se detecta igual). */
export async function refreshHoy() {
  const fecha = todayStr();
  let servidor;
  try {
    servidor = await api.asistenciasPorFecha(DB.cid, fecha);
  } catch (e) {
    if (!esErrorRed(e)) throw e;
    servidor = DB.hoyFecha === fecha ? DB.hoy.filter((x) => !x._pendiente) : [];
  }
  const ya = new Set(servidor.map((x) => x.alumno_id));
  const pendientes = asistenciasPendientesDe(fecha).filter((r) => !ya.has(r.alumno_id)).map((r) => ({ ...r, _pendiente: true }));
  DB.hoy = [...servidor, ...pendientes];
  DB.hoyFecha = fecha;
}

/** Garantiza que la caché de "hoy" sea del día en curso (la app puede quedar abierta pasada la medianoche). */
export async function asegurarHoy() {
  if (DB.hoyFecha !== todayStr()) await refreshHoy();
}

export const gradosDe = (nivel) => DB.grados.filter((g) => !nivel || g.nivel === nivel).sort((a, b) => a.nivel.localeCompare(b.nivel, "es") || compararCiclos(a.nombre, b.nombre));
export const alumnoPorId = (id) => DB.alumnos.find((a) => a.id === id);
export const alumnoPorCodigo = (c) => DB.alumnos.find((a) => a.codigo === c);
export const opcionesNivel = (conTodos) => [...(conTodos ? [{ value: "", label: "Todas las carreras" }] : []), ...DB.niveles.map((n) => ({ value: n, label: n }))];
export const opcionesGrado = (nivel, conTodos) => [...(conTodos ? [{ value: "", label: "Todos los ciclos" }] : []), ...gradosDe(nivel).map((g) => ({ value: g.nombre, label: cicloCorto(g.nombre, nivel) }))];
