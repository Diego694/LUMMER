// Lógica de negocio pura: estadísticas de asistencia e importación CSV. Sin DOM ni red.
import { pct } from "./utils.js";

/** ¿El ingreso es tardanza? Compara HH:MM (24 h) contra el límite. */
export function esTardanza(hora, limite) {
  if (!hora) return false;
  return hora.slice(0, 5) > limite;
}

/** Resumen del día: presentes, tardanzas, ausentes y % sobre alumnos activos. */
export function resumenDia(alumnos, asistencias, limite) {
  const activos = alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false);
  const ids = new Set(activos.map((a) => a.id));
  const delDia = asistencias.filter((x) => ids.has(x.alumno_id));
  const presentes = delDia.length;
  const tardes = delDia.filter((x) => esTardanza(x.hora, limite)).length;
  return {
    activos: activos.length,
    presentes,
    tardes,
    puntuales: presentes - tardes,
    ausentes: Math.max(0, activos.length - presentes),
    pct: pct(presentes, activos.length),
  };
}

/** Serie diaria para el gráfico de tendencia. */
export function serieDiaria(dias, asistencias, totalActivos, limite) {
  return dias.map((fecha) => {
    const del = asistencias.filter((a) => a.fecha === fecha);
    const tardes = del.filter((a) => esTardanza(a.hora, limite)).length;
    return { fecha, presentes: del.length, tardes, pct: pct(del.length, totalActivos) };
  });
}

/** Asistencia del día agrupada por grado (orden alfabético natural). */
export function porGrado(alumnos, asistencias) {
  const presentes = new Set(asistencias.map((a) => a.alumno_id));
  const map = new Map();
  alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false).forEach((a) => {
    const key = `${a.nivel} · ${a.grado}`;
    const g = map.get(key) || { key, nivel: a.nivel, grado: a.grado, total: 0, presentes: 0 };
    g.total++;
    if (presentes.has(a.id)) g.presentes++;
    map.set(key, g);
  });
  return [...map.values()]
    .map((g) => ({ ...g, pct: pct(g.presentes, g.total) }))
    .sort((a, b) => a.key.localeCompare(b.key, "es", { numeric: true }));
}

export function porNivel(alumnos, niveles) {
  return niveles.map((n) => ({ nivel: n, total: alumnos.filter((a) => a.nivel === n && a.estado === "ACTIVO" && a.aprobado !== false).length }));
}

/**
 * Alumnos activos con menor asistencia en el rango. Los "días de clase" son los días en que
 * hubo al menos un registro (así feriados o días sin actividad no penalizan).
 */
export function bajaAsistencia(alumnos, asistencias, umbral, limiteResultados = 8) {
  const diasClase = new Set(asistencias.map((a) => a.fecha));
  if (diasClase.size === 0) return [];
  const conteo = new Map();
  asistencias.forEach((a) => conteo.set(a.alumno_id, (conteo.get(a.alumno_id) || 0) + 1));
  return alumnos
    .filter((a) => a.estado === "ACTIVO" && a.aprobado !== false)
    .map((a) => {
      const presentes = conteo.get(a.id) || 0;
      return { alumno: a, presentes, dias: diasClase.size, pct: pct(presentes, diasClase.size) };
    })
    .filter((r) => r.pct < umbral)
    .sort((a, b) => a.pct - b.pct || a.alumno.nombre.localeCompare(b.alumno.nombre, "es"))
    .slice(0, limiteResultados);
}

/** Estadísticas del historial de un alumno sobre los días de clase del periodo. */
export function resumenAlumno(historial, diasClase, limite) {
  const presentes = historial.length;
  const tardes = historial.filter((h) => esTardanza(h.hora, limite)).length;
  return { presentes, tardes, ausentes: Math.max(0, diasClase - presentes), pct: pct(presentes, diasClase) };
}

/** Normaliza y valida las filas de un CSV de importación de alumnos. */
export function normalizarFilasImport(rows, niveles = [], grados = []) {
  const validas = [];
  const errores = [];
  const vistos = new Set();
  rows.forEach((row, i) => {
    const n = {};
    Object.keys(row).forEach((k) => (n[k.trim().toLowerCase()] = (row[k] ?? "").toString().trim()));
    const linea = i + 2; // +1 por la cabecera, +1 por base 1
    if (!n.nombre || !n.codigo) { errores.push({ linea, motivo: "Falta nombre o código" }); return; }
    if (vistos.has(n.codigo)) { errores.push({ linea, motivo: `Código repetido en el archivo: ${n.codigo}` }); return; }
    vistos.add(n.codigo);
    const aviso = [];
    if (n.nivel && niveles.length && !niveles.includes(n.nivel)) aviso.push(`Nivel "${n.nivel}" no existe`);
    if (n.grado && grados.length && !grados.some((g) => g.nombre === n.grado && (!n.nivel || g.nivel === n.nivel))) aviso.push(`Grado "${n.grado}" no existe`);
    validas.push({
      nombre: n.nombre, codigo: n.codigo, nivel: n.nivel || "", grado: n.grado || "", apoderado: n.apoderado || "",
      estado: (n.estado || "ACTIVO").toUpperCase() === "INACTIVO" ? "INACTIVO" : "ACTIVO", aviso,
    });
  });
  return { validas, errores };
}
