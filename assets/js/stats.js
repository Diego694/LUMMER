// Lógica de negocio pura: estadísticas de asistencia e importación CSV. Sin DOM ni red.
import { etiquetaCiclo, pct } from "./utils.js";

/** Límites de puntualidad por carrera (se cargan desde la tabla horarios; ver calendario.js → tablaLimites). */
export const limites = { general: null, porNivel: {} };
export function configurarLimites(tabla) { limites.general = tabla?.general ?? null; limites.porNivel = tabla?.porNivel || {}; }

/** ¿El ingreso es tardanza? Compara HH:MM (24 h) contra el límite; si se indica la carrera y tiene horario propio, usa ese. */
export function esTardanza(hora, limite, nivel) {
  if (!hora) return false;
  const lim = (nivel && limites.porNivel[nivel]) || limite;
  return String(hora).slice(0, 5) > lim;
}

/**
 * Qué corresponde hacer cuando un alumno se presenta (quiosco): 'entrada' si hoy no ingresó; 'salida' si ya ingresó,
 * aún no salió y pasó la permanencia mínima; 'ya_ingreso' si vuelve a pasar muy pronto (evita registrar una salida por error);
 * 'dup_salida' si ya tenía salida. `reg` es la asistencia de hoy del alumno (o undefined).
 */
export function decidirAccion(reg, ahoraHHMM, minPermanencia = 45) {
  if (!reg) return "entrada";
  if (reg.hora_salida) return "dup_salida";
  const m = (h) => { const [a, b] = String(h).split(":").map(Number); return a * 60 + b; };
  return m(ahoraHHMM) - m(reg.hora) >= minPermanencia ? "salida" : "ya_ingreso";
}

/** Resumen del día: presentes, tardanzas, ausentes y % sobre alumnos activos. */
export function resumenDia(alumnos, asistencias, limite) {
  const activos = alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false);
  const ids = new Set(activos.map((a) => a.id));
  const delDia = asistencias.filter((x) => ids.has(x.alumno_id));
  const nivelDe = new Map(activos.map((a) => [a.id, a.nivel]));
  const presentes = delDia.length;
  const tardes = delDia.filter((x) => esTardanza(x.hora, limite, nivelDe.get(x.alumno_id))).length;
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
export function serieDiaria(dias, asistencias, totalActivos, limite, nivelDe = new Map()) {
  return dias.map((fecha) => {
    const del = asistencias.filter((a) => a.fecha === fecha);
    const tardes = del.filter((a) => esTardanza(a.hora, limite, nivelDe.get(a.alumno_id))).length;
    return { fecha, presentes: del.length, tardes, pct: pct(del.length, totalActivos) };
  });
}

/** Asistencia del día agrupada por grado (orden alfabético natural). */
export function porGrado(alumnos, asistencias) {
  const presentes = new Set(asistencias.map((a) => a.alumno_id));
  const map = new Map();
  alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false).forEach((a) => {
    const key = etiquetaCiclo(a.nivel, a.grado);
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
export function resumenAlumno(historial, diasClase, limite, nivel) {
  const presentes = historial.length;
  const tardes = historial.filter((h) => esTardanza(h.hora, limite, nivel)).length;
  return { presentes, tardes, ausentes: Math.max(0, diasClase - presentes), pct: pct(presentes, diasClase) };
}

/**
 * Matriz de asistencia (reporte mensual). Una columna por día de clase (día con al menos un registro o justificación
 * entre estos alumnos). Código por celda: P presente · T tardanza · J justificado · F falta.
 * pct = presentes (incluye tardanzas) / días de clase; pctJust = (presentes + justificados) / días de clase.
 */
export function matrizAsistencia(alumnos, asistencias, justificaciones, dias, limite) {
  const ids = new Set(alumnos.map((a) => a.id));
  const asis = new Map();   // "alumno|fecha" → hora
  const just = new Map();   // "alumno|fecha" → tipo
  asistencias.filter((x) => ids.has(x.alumno_id)).forEach((x) => asis.set(`${x.alumno_id}|${x.fecha}`, x.hora));
  justificaciones.filter((x) => ids.has(x.alumno_id)).forEach((x) => just.set(`${x.alumno_id}|${x.fecha}`, x.tipo));
  const conRegistro = new Set([...asis.keys(), ...just.keys()].map((k) => k.split("|")[1]));
  const diasClase = dias.filter((d) => conRegistro.has(d));
  const filas = alumnos.map((alumno) => {
    const celdas = {};
    let p = 0, t = 0, j = 0, f = 0;
    diasClase.forEach((d) => {
      const hora = asis.get(`${alumno.id}|${d}`);
      if (hora !== undefined) { if (esTardanza(hora, limite, alumno.nivel)) { celdas[d] = "T"; t++; } else celdas[d] = "P"; p++; }
      else if (just.has(`${alumno.id}|${d}`)) { celdas[d] = "J"; j++; }
      else { celdas[d] = "F"; f++; }
    });
    return { alumno, celdas, p, t, j, f, pct: pct(p, diasClase.length), pctJust: pct(p + j, diasClase.length) };
  });
  const total = filas.reduce((s, r) => s + r.p, 0), posibles = filas.length * diasClase.length;
  return { dias: diasClase, filas, resumen: { alumnos: filas.length, dias: diasClase.length, pct: pct(total, posibles) } };
}

/** Número para wa.me (solo dígitos, con prefijo de país). Perú por defecto: 9 dígitos que empiezan con 9 → 51XXXXXXXXX. */
export function numeroWhatsApp(tel, paisPorDefecto = "51") {
  const d = String(tel || "").replace(/[^0-9]/g, "");
  if (!d) return "";
  if (String(tel).trim().startsWith("+")) return d;
  if (d.length === 9 && d.startsWith("9")) return paisPorDefecto + d;
  return d;
}
export const enlaceWhatsApp = (tel, texto) => { const n = numeroWhatsApp(tel); return n ? `https://wa.me/${n}?text=${encodeURIComponent(texto)}` : ""; };
/** Rellena {alumno}, {fecha}, {instituto}, {hora}, {ciclo} en una plantilla de aviso. */
export const mensajeAviso = (plantilla, datos) => String(plantilla).replace(/\{(\w+)\}/g, (m, k) => (datos[k] ?? m));

/** ¿El alumno puede asistir a este curso? (misma carrera y, si el curso fija ciclo, el mismo ciclo). */
export const perteneceACurso = (alumno, curso) => alumno.nivel === curso.nivel && (!curso.grado || alumno.grado === curso.grado);

/** Normaliza y valida las filas de un CSV de importación de alumnos. */
export function normalizarFilasImport(rows, niveles = [], grados = []) {
  const validas = [];
  const errores = [];
  const vistos = new Set();
  rows.forEach((row, i) => {
    const n = {};
    Object.keys(row).forEach((k) => (n[k.trim().toLowerCase()] = (row[k] ?? "").toString().trim()));
    if (!n.nivel && n.carrera) n.nivel = n.carrera; // la plantilla usa "carrera" y "ciclo"; "nivel" y "grado" también valen
    if (!n.grado && n.ciclo) n.grado = n.ciclo;
    const linea = i + 2; // +1 por la cabecera, +1 por base 1
    if (!n.nombre || !n.codigo) { errores.push({ linea, motivo: "Falta nombre o código" }); return; }
    if (vistos.has(n.codigo)) { errores.push({ linea, motivo: `Código repetido en el archivo: ${n.codigo}` }); return; }
    vistos.add(n.codigo);
    const aviso = [];
    if (n.nivel && niveles.length && !niveles.includes(n.nivel)) aviso.push(`Carrera "${n.nivel}" no existe`);
    if (n.grado && grados.length && !grados.some((g) => g.nombre === n.grado && (!n.nivel || g.nivel === n.nivel))) aviso.push(`Ciclo "${n.grado}" no existe`);
    validas.push({
      nombre: n.nombre, codigo: n.codigo, nivel: n.nivel || "", grado: n.grado || "", apoderado: n.apoderado || "",
      estado: (n.estado || "ACTIVO").toUpperCase() === "INACTIVO" ? "INACTIVO" : "ACTIVO", aviso,
    });
  });
  return { validas, errores };
}
