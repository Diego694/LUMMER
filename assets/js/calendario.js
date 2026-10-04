// Calendario del instituto: días sin clases (feriados, suspensiones) y horarios. Lógica pura, sin DOM ni red.
import { addDays, dateStr, isWeekend } from "./utils.js";

/** Mapa fecha → {tipo, nombre} de los días NO lectivos (Feriado y Sin clases; los «Evento» sí tienen clases). */
export function mapaNoLectivos(calendario = []) {
  const m = new Map();
  calendario.filter((c) => c.tipo === "Feriado" || c.tipo === "Sin clases").forEach((c) => m.set(c.fecha, c));
  return m;
}

export const esDiaLectivo = (fecha, noLectivos) => !isWeekend(fecha) && !noLectivos.has(fecha);

/** Días lectivos (lun–vie, sin feriados) entre dos fechas YYYY-MM-DD, ambas incluidas. */
export function diasLectivos(desde, hasta, noLectivos = new Map()) {
  const out = [];
  for (let f = desde; f <= hasta; f = addDays(f, 1)) if (esDiaLectivo(f, noLectivos)) out.push(f);
  return out;
}

/** Feriados nacionales de Perú de un año (referenciales: el Gobierno puede trasladarlos o decretar días no laborables). */
export function feriadosPeru(anio) {
  // Pascua (algoritmo de Meeus/Jones/Butcher) → Jueves y Viernes Santo
  const a = anio % 19, b = Math.floor(anio / 100), c = anio % 100, d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451);
  const mes = Math.floor((h + l - 7 * m + 114) / 31), dia = ((h + l - 7 * m + 114) % 31) + 1;
  const pascua = dateStr(new Date(anio, mes - 1, dia));
  const fijos = [
    ["01-01", "Año Nuevo"], ["05-01", "Día del Trabajo"], ["06-07", "Batalla de Arica y Día de la Bandera"], ["06-29", "San Pedro y San Pablo"],
    ["07-23", "Día de la Fuerza Aérea del Perú"], ["07-28", "Fiestas Patrias"], ["07-29", "Fiestas Patrias"], ["08-06", "Batalla de Junín"],
    ["08-30", "Santa Rosa de Lima"], ["10-08", "Combate de Angamos"], ["11-01", "Todos los Santos"], ["12-08", "Inmaculada Concepción"],
    ["12-09", "Batalla de Ayacucho"], ["12-25", "Navidad"],
  ].map(([md, nombre]) => ({ fecha: `${anio}-${md}`, tipo: "Feriado", nombre }));
  return [...fijos, { fecha: addDays(pascua, -3), tipo: "Feriado", nombre: "Jueves Santo" }, { fecha: addDays(pascua, -2), tipo: "Feriado", nombre: "Viernes Santo" }]
    .sort((x, y) => x.fecha.localeCompare(y.fecha));
}

const aMin = (hhmm) => { const [h, m] = String(hhmm).split(":").map(Number); return h * 60 + m; };
const aHHMM = (min) => `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}`;

/**
 * Límite de puntualidad (HH:MM) de una carrera: ingreso + tolerancia de su horario; si no tiene, el general; si no, `porDefecto`.
 * `horarios` = filas de la tabla horarios ({nivel|null, hora_ingreso, tolerancia_min}).
 */
export function limiteDeHorario(horarios = [], nivel, porDefecto = "08:00") {
  const h = horarios.find((x) => x.nivel && x.nivel === nivel) || horarios.find((x) => !x.nivel);
  return h ? aHHMM(aMin(h.hora_ingreso) + Number(h.tolerancia_min || 0)) : porDefecto;
}

/** Mapa carrera → límite, más `general`. Es lo que usa `esTardanza` (ver stats.js). */
export function tablaLimites(horarios = [], porDefecto = "08:00") {
  const porNivel = {};
  horarios.filter((h) => h.nivel).forEach((h) => { porNivel[h.nivel] = limiteDeHorario(horarios, h.nivel, porDefecto); });
  return { general: limiteDeHorario(horarios, null, porDefecto), porNivel };
}

/** Horario efectivo de una carrera: el suyo, o el general, o los valores por defecto. */
export function horarioDe(horarios = [], nivel, porDefecto = { limite: "08:00", permanencia: 120 }) {
  const h = horarios.find((x) => x.nivel && x.nivel === nivel) || horarios.find((x) => !x.nivel);
  if (!h) return { definido: false, ingreso: null, tolerancia: 0, limite: porDefecto.limite, salida: null, desde: null, hasta: null, permanencia: porDefecto.permanencia };
  return {
    definido: true, ingreso: h.hora_ingreso, tolerancia: Number(h.tolerancia_min || 0), limite: aHHMM(aMin(h.hora_ingreso) + Number(h.tolerancia_min || 0)),
    salida: h.hora_salida || null, desde: h.ingreso_desde || null, hasta: h.ingreso_hasta || null, permanencia: Number(h.permanencia_min ?? porDefecto.permanencia),
  };
}

/**
 * ¿Cómo es un ingreso a esta hora? 'temprano' (aún no se abre el ingreso) · 'puntual' · 'tarde' (pasó el límite de puntualidad)
 * · 'cerrado' (pasó la hora de cierre del ingreso).
 */
export function estadoIngreso(h, hhmm) {
  if (h.desde && hhmm < h.desde) return "temprano";
  if (h.hasta && hhmm > h.hasta) return "cerrado";
  return hhmm > h.limite ? "tarde" : "puntual";
}

/** ¿Ya puede marcar su salida? Pasada la permanencia mínima desde su ingreso. Devuelve { ok, desde:'HH:MM' }. */
export function salidaPermitida(horaIngreso, ahoraHHMM, permanencia = 120) {
  const desde = aHHMM(Math.min(aMin(horaIngreso) + permanencia, 24 * 60 - 1));
  return { ok: ahoraHHMM >= desde, desde };
}
