// @ts-check
// Roles y permisos de la interfaz. La seguridad REAL está en la base de datos (políticas RLS de la migración 004);
// esto solo evita ofrecer botones que fallarían y da mensajes claros.
//   administrador | admin → todo
//   coordinador           → como docente, pero solo ve su carrera (la base lo impone)
//   docente / otros       → leer; registrar asistencia, justificaciones y avisos
import { DB } from "./state.js";

/** @returns {'admin' | 'coordinador' | 'docente'} */
export function rolActual() {
  const r = String(DB.perfil?.rol || "").trim().toLowerCase();
  if (r === "administrador" || r === "admin") return "admin";
  if (r === "coordinador") return "coordinador";
  return "docente";
}
/** @returns {boolean} */
export const esAdmin = () => rolActual() === "admin";
/** @returns {boolean} */
export const esSuper = () => DB.perfil?.superadmin === true;

/** Acciones (data-action) que solo puede hacer el administrador. */
export const ACCIONES_ADMIN = [
  "nivel-new", "nivel-del", "grado-new", "grado-del", "ciclos-new", "al-new", "al-edit", "al-del", "al-import", "al-revisar", "sol-ver", "sol-ok", "sol-no", "sol-actualizar", "of-ver", "of-exportar", "of-importar",
  "co-del", "cd-generar", "cd-regenerar", "pers-crear", "pers-existente", "pers-pass", "pers-edit", "pers-del", "curso-new", "curso-edit", "curso-del", "aula-docentes",
  "just-del", "err-borrar", "respaldo-json", "respaldo-csv-alumnos", "respaldo-csv-asistencias", "inst-nombre-cambiar", "inst-qr-guardar",
];
const SET = new Set(ACCIONES_ADMIN);
/**
 * @param {string} accion
 * @returns {boolean}
 */
export const puede = (accion) => esAdmin() || !SET.has(accion);

/**
 * Oculta con CSS los controles de administrador cuando el rol no lo es (clase en <body>).
 * @returns {void}
 */
export function instalarEstiloPermisos() {
  if (document.getElementById("estilo-permisos")) return;
  const st = document.createElement("style");
  st.id = "estilo-permisos";
  st.textContent = ACCIONES_ADMIN.map((a) => `body.rol-limitado [data-action="${a}"]`).join(",") + ",body.rol-limitado .solo-admin{display:none!important}";
  document.head.appendChild(st);
}
/** @returns {void} */
export function aplicarPermisos() {
  document.body.classList.toggle("rol-limitado", !esAdmin());
  document.body.dataset.rol = rolActual();
}

/** @type {Record<'admin' | 'coordinador' | 'docente', string>} */
export const ETIQUETA_ROL = { admin: "Administrador", coordinador: "Coordinador", docente: "Docente" };
