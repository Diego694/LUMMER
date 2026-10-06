// @ts-check
// Historial de cambios (administrador): quién creó, modificó o borró qué y cuándo. Lo escriben disparadores de la base
// de datos: ni el personal ni la propia aplicación pueden alterarlo.
import { api } from "../api.js";
import { DB } from "../state.js";
import { badge, emptyState, pageHead, skeleton } from "../ui.js";
import { ZONA_HORARIA, esc } from "../utils.js";

/** @type {Record<string, string>} */
const TABLAS = { alumnos: "Alumnos", niveles: "Carreras", grados: "Ciclos", docentes: "Docentes", comunicados: "Comunicados", cursos: "Cursos", justificaciones: "Justificaciones", perfiles: "Personal y accesos", calendario: "Calendario", horarios: "Horarios", periodos: "Periodos", asistencias: "Asistencias (correcciones)", colegios: "Instituto" };
/** @type {Record<string, [string, string]>} */
const ACCION = { INSERT: ["Creó", "green"], UPDATE: ["Modificó", "amber"], DELETE: ["Eliminó", "red"] };
let f = { tabla: "", accion: "" };

/** @param {string} iso */
const cuando = (iso) => new Date(iso).toLocaleString("es-PE", { timeZone: ZONA_HORARIA, day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
/**
 * @param {{ accion: string, detalle?: any }} x
 * @returns {string}
 */
function resumen(x) {
  const d = x.detalle || {};
  if (d.purgado) return "Datos personales eliminados";
  if (x.accion === "UPDATE") return Object.entries(d).map(([k, v]) => `${k}: ${typeof v === "object" && v && "a" in v ? `${fmt(v.de)} → ${fmt(v.a)}` : fmt(v)}`).join(" · ");
  const nombre = d.nombre || d.titulo || d.codigo || d.fecha || "";
  return [nombre, d.nivel, d.grado].filter(Boolean).join(" · ");
}
/** @param {unknown} v */
const fmt = (v) => (v === null || v === undefined || v === "" ? "vacío" : String(v).replace(/^"|"$/g, ""));

export const historialPage = {
  id: "historial", title: "Historial de cambios", icon: "history", group: "Sistema", soloAdmin: true,
  /** @param {HTMLElement} root */
  async render(root) {
    root.innerHTML = `${pageHead("Historial de cambios", "Quién hizo qué y cuándo. No se puede editar ni borrar. Los ingresos normales de asistencia no se listan (solo sus correcciones).")}
      <div class="toolbar"><select class="filter" id="h-tabla" aria-label="Sección"><option value="">Todas las secciones</option>${Object.entries(TABLAS).map(([k, v]) => `<option value="${k}" ${f.tabla === k ? "selected" : ""}>${esc(v)}</option>`).join("")}</select>
        <select class="filter" id="h-accion" aria-label="Acción"><option value="">Todas las acciones</option>${Object.entries(ACCION).map(([k, [v]]) => `<option value="${k}" ${f.accion === k ? "selected" : ""}>${v}</option>`).join("")}</select></div>
      <div class="card flush" id="h-lista">${skeleton(6)}</div>`;
    const lista = /** @type {HTMLElement} */ (root.querySelector("#h-lista"));
    const pintar = async () => {
      lista.innerHTML = skeleton(6);
      /** @type {any[]} */
      let filas;
      try { filas = await api.auditoriaLista(DB.cid, { limite: 300, tabla: f.tabla, accion: f.accion }); }
      catch (/** @type {any} */ e) { lista.innerHTML = emptyState("No se pudo cargar el historial", /does not exist|schema cache|relation/i.test(e.message) ? "Falta aplicar la migración 007 (supabase/migrations/007_operacion_avanzada.sql)." : e.message, "alert"); return; }
      if (!lista.isConnected) return;
      lista.innerHTML = filas.length ? `<div class="table-wrap"><table><thead><tr><th>Cuándo</th><th>Quién</th><th>Acción</th><th>Sección</th><th>Detalle</th></tr></thead><tbody>
        ${filas.map((x) => { const [ac, tono] = ACCION[x.accion] || [x.accion, "neutral"]; return `<tr><td class="nowrap">${esc(cuando(x.creado_en))}</td><td>${esc(x.usuario || "—")}</td><td>${badge(ac, tono)}</td><td>${esc(TABLAS[x.tabla] || x.tabla)}</td><td class="hist-det">${esc(resumen(x).slice(0, 200))}</td></tr>`; }).join("")}</tbody></table></div>`
        : emptyState("Sin cambios registrados", "Cuando alguien cree, modifique o elimine datos, aparecerá aquí.", "history");
    };
    /** @type {HTMLSelectElement} */ (root.querySelector("#h-tabla")).addEventListener("change", (e) => { f.tabla = /** @type {HTMLSelectElement} */ (e.target).value; pintar(); });
    /** @type {HTMLSelectElement} */ (root.querySelector("#h-accion")).addEventListener("change", (e) => { f.accion = /** @type {HTMLSelectElement} */ (e.target).value; pintar(); });
    await pintar();
  },
};
