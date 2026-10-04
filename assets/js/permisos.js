// Roles y permisos de la interfaz. La seguridad REAL está en la base de datos (políticas RLS de la migración 004);
// esto solo evita ofrecer botones que fallarían y da mensajes claros.
//   administrador | admin → todo
//   coordinador           → como docente, pero solo ve su carrera (la base lo impone)
//   docente / otros       → leer; registrar asistencia, justificaciones y avisos
import { DB } from "./state.js";

export function rolActual() {
  const r = String(DB.perfil?.rol || "").trim().toLowerCase();
  if (r === "administrador" || r === "admin") return "admin";
  if (r === "coordinador") return "coordinador";
  return "docente";
}
export const esAdmin = () => rolActual() === "admin";

/** Acciones (data-action) que solo puede hacer el administrador. */
export const ACCIONES_ADMIN = [
  "nivel-new", "nivel-del", "grado-new", "grado-del", "ciclos-new", "al-new", "al-edit", "al-del", "al-import", "al-revisar",
  "co-del", "cd-generar", "cd-regenerar", "pers-crear", "pers-existente", "pers-pass", "pers-edit", "pers-del", "curso-new", "curso-edit", "curso-del",
  "just-del", "err-borrar", "respaldo-json", "respaldo-csv-alumnos", "respaldo-csv-asistencias",
];
const SET = new Set(ACCIONES_ADMIN);
export const puede = (accion) => esAdmin() || !SET.has(accion);

/** Oculta con CSS los controles de administrador cuando el rol no lo es (clase en <body>). */
export function instalarEstiloPermisos() {
  if (document.getElementById("estilo-permisos")) return;
  const st = document.createElement("style");
  st.id = "estilo-permisos";
  st.textContent = ACCIONES_ADMIN.map((a) => `body.rol-limitado [data-action="${a}"]`).join(",") + ",body.rol-limitado .solo-admin{display:none!important}";
  document.head.appendChild(st);
}
export function aplicarPermisos() {
  document.body.classList.toggle("rol-limitado", !esAdmin());
  document.body.dataset.rol = rolActual();
}

export const ETIQUETA_ROL = { admin: "Administrador", coordinador: "Coordinador", docente: "Docente" };
