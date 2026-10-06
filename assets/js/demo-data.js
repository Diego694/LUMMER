// @ts-check
// Generador determinista de datos de ejemplo para el MODO DEMO.
import { lastWeekdays, todayStr, uid } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Colegio} Colegio */
/** @typedef {import('./tipos.d.ts').Nivel} Nivel */
/** @typedef {import('./tipos.d.ts').Grado} Grado */
/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').Asistencia} Asistencia */
/** @typedef {import('./tipos.d.ts').Docente} Docente */
/** @typedef {import('./tipos.d.ts').Comunicado} Comunicado */
/** @typedef {import('./tipos.d.ts').Curso} Curso */
/** @typedef {import('./tipos.d.ts').DemoDB} DemoDB */

/**
 * @param {number} seed
 * @returns {() => number}
 */
function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296);
}

const NOMBRES = ["Lucía", "Mateo", "Valentina", "Santiago", "Camila", "Sebastián", "Isabella", "Diego", "Sofía", "Adrián", "Mía", "Joaquín", "Renata", "Gael", "Antonella", "Thiago", "Emma", "Bruno", "Alessia", "Lucas"];
const APELLIDOS = ["Quispe", "Flores", "Huamán", "Rojas", "Mendoza", "Vargas", "Castillo", "Ramos", "Torres", "Chávez", "Gutiérrez", "Salazar", "Paredes", "Cárdenas", "Delgado", "Ríos"];
const APODERADOS = ["María", "José", "Carmen", "Luis", "Rosa", "Jorge", "Ana", "Pedro"];

// Cada carrera tiene sus ciclos; el nombre del ciclo lleva la carrera para distinguirlos de un vistazo.
export const CARRERAS_DEMO = ["MECANICA ELECTRICA", "APSTI"];
/**
 * @param {string} carrera
 * @param {string} c
 * @returns {string}
 */
const ciclo = (carrera, c) => `${carrera} · ${c} CICLO`;
/** @type {[string, string][]} */
export const GRADOS_DEMO = [
  ["MECANICA ELECTRICA", ciclo("MECANICA ELECTRICA", "I")], ["MECANICA ELECTRICA", ciclo("MECANICA ELECTRICA", "III")], ["MECANICA ELECTRICA", ciclo("MECANICA ELECTRICA", "V")],
  ["APSTI", ciclo("APSTI", "II")], ["APSTI", ciclo("APSTI", "IV")], ["APSTI", ciclo("APSTI", "VI")],
];

/** @returns {DemoDB} */
export function buildDemoDB() {
  const r = rng(2026);
  /** @template T @param {T[]} a @returns {T} */
  const pick = (a) => a[Math.floor(r() * a.length)];
  const colegioId = "demo-colegio";
  /** @type {DemoDB} */
  const db = {
    colegio: { id: colegioId, nombre: "Instituto Demo San Martín", codigo_registro: "DEMO2026", qr_modo: "obligatorio" },
    niveles: CARRERAS_DEMO.map((nombre) => ({ id: uid(), colegio_id: colegioId, nombre })),
    grados: GRADOS_DEMO.map(([nivel, nombre]) => ({ id: uid(), colegio_id: colegioId, nivel, nombre })),
    alumnos: [], asistencias: [], docentes: [], comunicados: [],
  };

  let n = 1000;
  GRADOS_DEMO.forEach(([nivel, grado]) => {
    const cantidad = 9 + Math.floor(r() * 5);
    for (let i = 0; i < cantidad; i++) {
      const ap1 = pick(APELLIDOS), ap2 = pick(APELLIDOS);
      db.alumnos.push({
        id: uid(), colegio_id: colegioId, codigo: `a${++n}`, codigo_apoderado: Array.from({ length: 12 }, () => "0123456789ABCDEF"[Math.floor(r() * 16)]).join(""), qr_secreto: Array.from({ length: 32 }, () => "0123456789abcdef"[Math.floor(r() * 16)]).join(""),
        nombre: `${pick(NOMBRES)} ${ap1} ${ap2}`, nivel, grado,
        apoderado: `${pick(APODERADOS)} ${ap1}`, estado: r() < 0.94 ? "ACTIVO" : "INACTIVO",
      });
    }
  });

  // Historial: 30 días hábiles; cada alumno tiene su propia propensión a asistir y a llegar tarde.
  const dias = lastWeekdays(30);
  const hoy = todayStr();
  db.alumnos.filter((a) => a.estado === "ACTIVO").forEach((a) => {
    const asiste = 0.72 + r() * 0.27;
    const tarde = 0.05 + r() * 0.2;
    dias.forEach((fecha) => {
      if (fecha === hoy && r() < 0.15) return; // aún no llegan todos hoy
      if (r() > asiste) return;
      const min = r() < tarde ? 481 + Math.floor(r() * 50) : 420 + Math.floor(r() * 60);
      const hora = `${String(Math.floor(min / 60)).padStart(2, "0")}:${String(min % 60).padStart(2, "0")}`;
      db.asistencias.push({ id: uid(), colegio_id: colegioId, alumno_id: a.id, fecha, hora, registrado_por: "demo-user" });
    });
  });

  // Contacto de apoderados (≈85 % tiene teléfono) y algunos cursos de ejemplo
  db.alumnos.forEach((a) => {
    if (r() < 0.85) a.apoderado_telefono = `9${String(10000000 + Math.floor(r() * 89999999))}`;
    if (r() < 0.3) a.apoderado_email = `apoderado.${a.codigo}@ejemplo.com`;
  });
  db.cursos = [
    ["MECANICA ELECTRICA", "Circuitos eléctricos", ""], ["MECANICA ELECTRICA", "Mecánica de máquinas", ciclo("MECANICA ELECTRICA", "III")],
    ["APSTI", "Soporte técnico", ""], ["APSTI", "Redes de computadoras", ciclo("APSTI", "IV")],
  ].map(([nivel, nombre, grado]) => ({ id: uid(), colegio_id: colegioId, nivel, grado: grado || null, nombre, docente: "", activo: true }));

  db.docentes = [
    ["Rosa Mendoza Ruiz", "Profesora de Comunicación", "Docente"],
    ["Carlos Ortega Pinto", "Licenciado en Matemática", "Coordinador"],
    ["Elena Vidal Soto", "Psicóloga institucional", "Administrativo"],
    ["Jorge Paz Lara", "Auxiliar de educación", "Auxiliar"],
  ].map(([nombre, profesion, rol]) => ({ id: uid(), colegio_id: colegioId, nombre, profesion, rol, estado: "ACTIVO" }));

  db.comunicados = [
    ["Reunión de padres de familia", "Se convoca a todos los apoderados a la reunión general del viernes a las 6:00 p. m. en el auditorio."],
    ["Simulacro de sismo", "El próximo martes se realizará el simulacro nacional. Se solicita puntualidad en el ingreso."],
    ["Entrega de carnets", "Los carnets con código QR ya están disponibles. Es obligatorio presentarlos para registrar la asistencia."],
  ].map(([titulo, mensaje], i) => ({
    id: uid(), colegio_id: colegioId, titulo, mensaje, fecha: lastWeekdays(5 + i * 3).at(0) || todayStr(),
  }));

  return db;
}
