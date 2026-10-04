// Generador determinista de datos de ejemplo para el MODO DEMO.
import { lastWeekdays, todayStr, uid } from "./utils.js";

function rng(seed) {
  let s = seed >>> 0;
  return () => ((s = (Math.imul(s, 1664525) + 1013904223) >>> 0) / 4294967296);
}

const NOMBRES = ["Lucía", "Mateo", "Valentina", "Santiago", "Camila", "Sebastián", "Isabella", "Diego", "Sofía", "Adrián", "Mía", "Joaquín", "Renata", "Gael", "Antonella", "Thiago", "Emma", "Bruno", "Alessia", "Lucas"];
const APELLIDOS = ["Quispe", "Flores", "Huamán", "Rojas", "Mendoza", "Vargas", "Castillo", "Ramos", "Torres", "Chávez", "Gutiérrez", "Salazar", "Paredes", "Cárdenas", "Delgado", "Ríos"];
const APODERADOS = ["María", "José", "Carmen", "Luis", "Rosa", "Jorge", "Ana", "Pedro"];

export const GRADOS_DEMO = [
  ["Inicial", "3 años A"], ["Inicial", "4 años A"], ["Inicial", "5 años A"],
  ["Primaria", "1er grado"], ["Primaria", "2do grado"], ["Primaria", "3er grado"],
  ["Secundaria", "1er año"], ["Secundaria", "2do año"],
];

export function buildDemoDB() {
  const r = rng(2026);
  const pick = (a) => a[Math.floor(r() * a.length)];
  const colegioId = "demo-colegio";
  const db = {
    colegio: { id: colegioId, nombre: "I.E. Demo San Martín" },
    niveles: ["Inicial", "Primaria", "Secundaria"].map((nombre) => ({ id: uid(), colegio_id: colegioId, nombre })),
    grados: GRADOS_DEMO.map(([nivel, nombre]) => ({ id: uid(), colegio_id: colegioId, nivel, nombre })),
    alumnos: [], asistencias: [], docentes: [], comunicados: [],
  };

  let n = 1000;
  GRADOS_DEMO.forEach(([nivel, grado]) => {
    const cantidad = 7 + Math.floor(r() * 4);
    for (let i = 0; i < cantidad; i++) {
      const ap1 = pick(APELLIDOS), ap2 = pick(APELLIDOS);
      db.alumnos.push({
        id: uid(), colegio_id: colegioId, codigo: `a${++n}`,
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

  db.docentes = [
    ["Rosa Mendoza Ruiz", "Profesora de Comunicación", "Docente"],
    ["Carlos Ortega Pinto", "Licenciado en Matemática", "Coordinador"],
    ["Elena Vidal Soto", "Psicóloga escolar", "Administrativo"],
    ["Jorge Paz Lara", "Auxiliar de educación", "Auxiliar"],
  ].map(([nombre, profesion, rol]) => ({ id: uid(), colegio_id: colegioId, nombre, profesion, rol, estado: "ACTIVO" }));

  db.comunicados = [
    ["Reunión de padres de familia", "Se convoca a todos los apoderados a la reunión general del viernes a las 6:00 p. m. en el auditorio."],
    ["Simulacro de sismo", "El próximo martes se realizará el simulacro nacional. Se solicita puntualidad en el ingreso."],
    ["Entrega de carnets", "Los carnets con código QR ya están disponibles. Es obligatorio presentarlos para registrar la asistencia."],
  ].map(([titulo, mensaje], i) => ({
    id: uid(), colegio_id: colegioId, titulo, mensaje, fecha: lastWeekdays(5 + i * 3).at(0),
  }));

  return db;
}
