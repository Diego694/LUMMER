// Estado global en memoria de la sesión actual.
import { api } from "./api.js";
import { todayStr } from "./utils.js";

export const DB = {
  cid: null, rol: null, perfil: null,
  alumnos: [], niveles: [], nivelesRaw: [], grados: [], comunicados: [], docentes: [],
  hoy: [], hoyFecha: null,
};

export async function loadAll() {
  const d = await api.loadAll(DB.cid);
  DB.alumnos = d.alumnos;
  DB.nivelesRaw = d.niveles;
  DB.niveles = d.niveles.map((n) => n.nombre);
  DB.grados = d.grados;
  DB.comunicados = d.comunicados;
  DB.docentes = d.docentes;
  await refreshHoy();
}

export async function refreshHoy() {
  const fecha = todayStr();
  DB.hoy = await api.asistenciasPorFecha(DB.cid, fecha);
  DB.hoyFecha = fecha;
}

/** Garantiza que la caché de "hoy" sea del día en curso (la app puede quedar abierta pasada la medianoche). */
export async function asegurarHoy() {
  if (DB.hoyFecha !== todayStr()) await refreshHoy();
}

export const gradosDe = (nivel) => DB.grados.filter((g) => !nivel || g.nivel === nivel);
export const alumnoPorId = (id) => DB.alumnos.find((a) => a.id === id);
export const alumnoPorCodigo = (c) => DB.alumnos.find((a) => a.codigo === c);
export const opcionesNivel = (conTodos) => [...(conTodos ? [{ value: "", label: "Todas las carreras" }] : []), ...DB.niveles.map((n) => ({ value: n, label: n }))];
export const opcionesGrado = (nivel, conTodos) => [...(conTodos ? [{ value: "", label: "Todos los ciclos" }] : []), ...gradosDe(nivel).map((g) => ({ value: g.nombre, label: g.nombre }))];
