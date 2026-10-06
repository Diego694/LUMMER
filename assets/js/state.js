// @ts-check
// Estado global en memoria de la sesión actual.
import { api } from "./api.js";
import { asistenciasPendientesDe, guardarSnapshot, leerSnapshot, salidasPendientesDe } from "./cola.js";
import { CONFIG } from "./config.js";
import { mapaNoLectivos, tablaLimites } from "./calendario.js";
import { configurarLimites } from "./stats.js";
import { cicloCorto, compararCiclos, esErrorRed, todayStr } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Grado} Grado */
/** @typedef {import('./tipos.d.ts').Nivel} Nivel */
/** @typedef {import('./tipos.d.ts').Asistencia} Asistencia */
/** @typedef {import('./tipos.d.ts').DBState} DBState */

/** @type {(() => void) | null} */
let alCargar = null;
/**
 * Registra una función que se ejecuta cada vez que se cargan los datos (p. ej. para el aviso de solicitudes).
 * @param {(() => void) | null} f
 */
export const alCargarDatos = (f) => { alCargar = f; };

/** @type {DBState} */
export const DB = {
  cid: null, rol: null, perfil: null, userId: null,
  alumnos: [], niveles: [], nivelesRaw: [], grados: [], comunicados: [], docentes: [], cursos: [],
  periodos: [], calendario: [], horarios: [], noLectivos: new Map(),
  hoy: [], hoyFecha: null,
  sinConexion: false,   // true cuando los datos vienen de la copia local (sin red)
};

/**
 * @param {any} d
 */
function aplicar(d) {
  DB.alumnos = d.alumnos;
  DB.nivelesRaw = d.niveles;
  DB.niveles = d.niveles.map((/** @type {Nivel} */ n) => n.nombre);
  DB.grados = d.grados;
  DB.comunicados = d.comunicados;
  DB.docentes = d.docentes;
  DB.cursos = d.cursos || [];
  const aj = d.ajustes || {};
  DB.periodos = aj.periodos || []; DB.calendario = aj.calendario || []; DB.horarios = aj.horarios || [];
  DB.noLectivos = mapaNoLectivos(DB.calendario);
  // La hora límite de puntualidad sale del horario general; cada carrera puede tener el suyo (ver stats.esTardanza).
  const lim = tablaLimites(DB.horarios, CONFIG.HORA_LIMITE_BASE);
  configurarLimites(lim);
  CONFIG.HORA_LIMITE = lim.general;
}

/** Carga los datos del instituto. Sin red usa la última copia local para poder seguir registrando asistencia. */
export async function loadAll() {
  try {
    const d = await api.loadAll(DB.cid);
    aplicar(d);
    DB.sinConexion = false;
    guardarSnapshot(DB.cid, { alumnos: d.alumnos, niveles: d.niveles, grados: d.grados, comunicados: d.comunicados, docentes: d.docentes, cursos: d.cursos || [], ajustes: d.ajustes || {} });
  } catch (e) {
    if (!esErrorRed(e)) throw e;
    const s = leerSnapshot(DB.cid);
    if (!s) throw new Error("Sin conexión y sin datos guardados en este teléfono. Conéctate una vez para descargarlos.");
    aplicar(s);
    DB.sinConexion = true;
  }
  await refreshHoy();
  try { alCargar?.(); } catch { /* el aviso nunca bloquea la carga */ }
}

/** Asistencias de hoy: las del servidor + las guardadas sin conexión aún por enviar (así el duplicado se detecta igual). */
export async function refreshHoy() {
  const fecha = todayStr();
  /** @type {Asistencia[]} */
  let servidor;
  try {
    servidor = await api.asistenciasPorFecha(DB.cid, fecha);
  } catch (e) {
    if (!esErrorRed(e)) throw e;
    servidor = DB.hoyFecha === fecha ? DB.hoy.filter((x) => !x._pendiente) : [];
  }
  const ya = new Set(servidor.map((x) => x.alumno_id));
  const pendientes = asistenciasPendientesDe(fecha).filter((r) => !ya.has(r.alumno_id)).map((r) => ({ ...r, _pendiente: true }));
  const salidas = new Map(salidasPendientesDe(fecha).map((r) => [r.alumno_id, r.hora]));
  DB.hoy = [...servidor, ...pendientes].map((x) => (salidas.has(x.alumno_id) && !x.hora_salida ? { ...x, hora_salida: salidas.get(x.alumno_id), _salidaPendiente: true } : x));
  DB.hoyFecha = fecha;
}

/** Garantiza que la caché de "hoy" sea del día en curso (la app puede quedar abierta pasada la medianoche). */
export async function asegurarHoy() {
  if (DB.hoyFecha !== todayStr()) await refreshHoy();
}

/**
 * @param {string} [nivel]
 * @returns {Grado[]}
 */
export const gradosDe = (nivel) => DB.grados.filter((g) => !nivel || g.nivel === nivel).sort((a, b) => a.nivel.localeCompare(b.nivel, "es") || compararCiclos(a.nombre, b.nombre));

/**
 * @param {string} id
 * @returns {Alumno | undefined}
 */
export const alumnoPorId = (id) => DB.alumnos.find((a) => a.id === id);

/**
 * @param {string} c
 * @returns {Alumno | undefined}
 */
export const alumnoPorCodigo = (c) => DB.alumnos.find((a) => a.codigo === c);

/**
 * @param {boolean} [conTodos]
 * @returns {{ value: string; label: string }[]}
 */
export const opcionesNivel = (conTodos) => [...(conTodos ? [{ value: "", label: "Todas las carreras" }] : []), ...DB.niveles.map((n) => ({ value: n, label: n }))];

/**
 * @param {string} [nivel]
 * @param {boolean} [conTodos]
 * @returns {{ value: string; label: string }[]}
 */
export const opcionesGrado = (nivel, conTodos) => [...(conTodos ? [{ value: "", label: "Todos los ciclos" }] : []), ...gradosDe(nivel).map((g) => ({ value: g.nombre, label: cicloCorto(g.nombre, nivel || "") }))];
