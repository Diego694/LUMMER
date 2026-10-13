// @ts-check
// Bitácora de registros: coordinación, administración y administrador superior ven CADA registro con su hora exacta
// (hora:minuto:segundo, en la zona del instituto), quién lo hizo y por qué medio. Los datos los entrega la función
// bitacora_registros (migración 019), que aplica el alcance: el coordinador solo ve su carrera.
import { api } from "../api.js";
import { etiquetaOrigen, momentoExacto, resumenBitacora } from "../api-qrcurso.js";
import { badge, emptyState, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { downloadFile, esc, norm, todayStr, toCSV } from "../utils.js";

/** @typedef {import('../tipos.d.ts').BitacoraFila} BitacoraFila */

/** @type {Record<string, [string, string]>} */
const TIPOS = {
  ingreso: ["Ingreso al instituto", "green"],
  curso: ["Asistencia en curso", "teal"],
  qr_abierto: ["Docente abrió QR", "amber"],
  justificacion: ["Justificación", "neutral"],
  aviso: ["Aviso a apoderado", "neutral"],
};

const hoy = () => todayStr();
const f = { desde: "", hasta: "", tipo: "", q: "" };
/** @type {BitacoraFila[]} */
let filas = [];

/** @param {BitacoraFila} x */
const lugar = (x) => [x.carrera, x.ciclo].filter(Boolean).join(" · ");

/** Filas visibles según la búsqueda (persona, curso o quien lo registró). */
function filtradas() {
  const q = norm(f.q);
  return q ? filas.filter((x) => norm([x.persona, x.curso, x.actor, x.carrera].filter(Boolean).join(" ")).includes(q)) : filas;
}

function pintarTabla() {
  const caja = document.getElementById("bt-lista");
  const resumen = document.getElementById("bt-resumen");
  if (!caja || !resumen) return;
  const vis = filtradas();
  const r = resumenBitacora(vis);
  resumen.innerHTML = Object.entries(TIPOS).map(([k, [t]]) => `<span class="bt-chip"><b>${r[k] || 0}</b> ${esc(t)}</span>`).join("");
  caja.innerHTML = vis.length
    ? `<div class="table-wrap"><table class="bt-tabla"><thead><tr><th>Fecha</th><th>Hora exacta</th><th>Qué</th><th>Persona</th><th>Carrera · ciclo</th><th>Curso</th><th>Lo registró</th><th>Medio</th></tr></thead><tbody>
      ${vis.map((x) => {
        const m = momentoExacto(x.cuando), [etq, tono] = TIPOS[x.tipo] || [x.tipo, "neutral"];
        return `<tr><td class="nowrap">${esc(m.fecha)}</td><td class="nowrap bt-hora">${esc(m.hora)}</td><td>${badge(etq, tono)}${x.tipo === "justificacion" || x.tipo === "aviso" ? ` <small class="muted">${esc(x.hora || "")}</small>` : ""}</td>
          <td>${esc(x.persona || "—")}</td><td>${esc(lugar(x) || "—")}</td><td>${esc(x.curso || "—")}</td>
          <td><strong>${esc(x.actor)}</strong><small class="muted"> · ${esc(x.actor_rol)}</small></td><td>${esc(etiquetaOrigen(x.origen))}</td></tr>`;
      }).join("")}</tbody></table></div>`
    : emptyState("Sin registros en este rango", "Cambia las fechas o el tipo para ver más.", "history");
}

async function cargar() {
  const caja = document.getElementById("bt-lista");
  if (!caja) return;
  caja.innerHTML = skeleton(6);
  try {
    filas = await api.bitacoraRegistros({ desde: f.desde, hasta: f.hasta, tipo: f.tipo, limite: 1000 });
  } catch (/** @type {any} */ e) {
    caja.innerHTML = emptyState("No se pudo cargar la bitácora", /does not exist|schema cache|function/i.test(e.message) ? "Falta aplicar la migración 019 (supabase/migrations/019_qr_asistencia_curso.sql)." : e.message, "alert");
    return;
  }
  if (!caja.isConnected) return;
  pintarTabla();
}

export const bitacoraPage = {
  id: "bitacora", title: "Bitácora de registros", icon: "history", group: "Sistema", noDocente: true,
  /** @param {HTMLElement} root */
  async render(root) {
    if (!f.desde) { f.desde = hoy(); f.hasta = hoy(); }
    root.innerHTML = `${pageHead("Bitácora de registros", "Cada registro con su hora, minuto y segundo exactos, quién lo hizo y por qué medio. La hora la pone el servidor: nadie puede alterarla.",
      `<button type="button" class="btn btn-outline" data-action="bt-actualizar">${icon("history", 16)} Actualizar</button><button type="button" class="btn btn-outline" data-action="bt-csv">${icon("download", 16)} Exportar CSV</button>`)}
      <div class="toolbar bt-filtros">
        <label class="bt-campo">Desde<input class="filter" type="date" id="bt-desde" value="${esc(f.desde)}" max="${hoy()}"></label>
        <label class="bt-campo">Hasta<input class="filter" type="date" id="bt-hasta" value="${esc(f.hasta)}" max="${hoy()}"></label>
        <label class="bt-campo">Tipo<select class="filter" id="bt-tipo"><option value="">Todos</option>${Object.entries(TIPOS).map(([k, [t]]) => `<option value="${k}" ${f.tipo === k ? "selected" : ""}>${esc(t)}</option>`).join("")}</select></label>
        <label class="bt-campo bt-buscar">Buscar<input class="filter" type="search" id="bt-q" placeholder="Estudiante, docente o curso" value="${esc(f.q)}" autocomplete="off"></label>
      </div>
      <div class="bt-resumen" id="bt-resumen" role="status" aria-live="polite"></div>
      <div class="card flush" id="bt-lista">${skeleton(6)}</div>`;
    const campo = (/** @type {string} */ id) => /** @type {HTMLInputElement} */ (root.querySelector(id));
    campo("#bt-desde").addEventListener("change", (e) => { f.desde = /** @type {HTMLInputElement} */ (e.target).value || hoy(); if (f.hasta < f.desde) { f.hasta = f.desde; campo("#bt-hasta").value = f.hasta; } cargar(); });
    campo("#bt-hasta").addEventListener("change", (e) => { f.hasta = /** @type {HTMLInputElement} */ (e.target).value || hoy(); if (f.desde > f.hasta) { f.desde = f.hasta; campo("#bt-desde").value = f.desde; } cargar(); });
    campo("#bt-tipo").addEventListener("change", (e) => { f.tipo = /** @type {HTMLSelectElement} */ (e.target).value; cargar(); });
    campo("#bt-q").addEventListener("input", (e) => { f.q = /** @type {HTMLInputElement} */ (e.target).value; pintarTabla(); });
    await cargar();
  },
};

registerActions({
  "bt-actualizar": () => { cargar(); },
  "bt-csv": async () => {
    const vis = filtradas();
    if (!vis.length) { toast("No hay registros para exportar.", "info"); return; }
    const cols = [
      { label: "Fecha", value: (/** @type {BitacoraFila} */ x) => momentoExacto(x.cuando).fecha },
      { label: "Hora exacta", value: (/** @type {BitacoraFila} */ x) => momentoExacto(x.cuando).hora },
      { label: "Qué", value: (/** @type {BitacoraFila} */ x) => (TIPOS[x.tipo] || [x.tipo])[0] },
      { label: "Persona", key: "persona" }, { label: "Carrera", key: "carrera" }, { label: "Ciclo", key: "ciclo" }, { label: "Curso", key: "curso" },
      { label: "Lo registró", key: "actor" }, { label: "Rol", key: "actor_rol" },
      { label: "Medio", value: (/** @type {BitacoraFila} */ x) => etiquetaOrigen(x.origen) },
    ];
    await downloadFile(`bitacora_${f.desde}_${f.hasta}.csv`, toCSV(vis, cols));
  },
});
