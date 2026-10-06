// @ts-check
// Avisos a apoderados: lista de faltas y tardanzas del día con un botón de WhatsApp (o correo) ya redactado.
// Sin costo ni cuentas externas: abre WhatsApp con el mensaje listo y registra que el aviso se hizo.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, opcionesGrado, opcionesNivel } from "../state.js";
import { badge, emptyState, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { enlaceWhatsApp, esTardanza, mensajeAviso } from "../stats.js";
import { esc, etiquetaCiclo, fmtDate, initials, todayStr } from "../utils.js";

let f = { nivel: "", grado: "", tipo: "todos" };
/** @type {{ a: any, tipo: string, hora: string }[]} */
let filas = [];   // filas actuales para las acciones

/**
 * @param {{ value: string, label: string }[]} list
 * @param {string} sel
 */
const options = (list, sel) => list.map((o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");

export const avisosPage = {
  id: "avisos", title: "Avisos a apoderados", icon: "megaphone", group: "Consultas",
  /** @param {HTMLElement} root */
  async render(root) {
    root.innerHTML = `${pageHead("Avisos a apoderados", `Faltas y tardanzas de hoy (${esc(fmtDate(todayStr()))}). Pulsa WhatsApp: se abre con el mensaje ya redactado.`)}
      <div class="toolbar">
        <select class="filter" id="av-nivel" aria-label="Carrera">${options(opcionesNivel(true), f.nivel)}</select>
        <select class="filter" id="av-grado" aria-label="Ciclo">${options(opcionesGrado(f.nivel, true), f.grado)}</select>
        <select class="filter" id="av-tipo" aria-label="Tipo"><option value="todos">Faltas y tardanzas</option><option value="falta">Solo faltas</option><option value="tardanza">Solo tardanzas</option></select></div>
      <div class="card flush" id="av-lista">${skeleton(4)}</div>`;
    const nv = /** @type {HTMLSelectElement} */ (root.querySelector("#av-nivel")), gr = /** @type {HTMLSelectElement} */ (root.querySelector("#av-grado")), tp = /** @type {HTMLSelectElement} */ (root.querySelector("#av-tipo"));
    tp.value = f.tipo;
    nv.addEventListener("change", () => { f.nivel = nv.value; f.grado = ""; gr.innerHTML = options(opcionesGrado(f.nivel, true), ""); pintar(root); });
    gr.addEventListener("change", () => { f.grado = gr.value; pintar(root); });
    tp.addEventListener("change", () => { f.tipo = tp.value; pintar(root); });
    await pintar(root);
  },
};

/** @param {HTMLElement} root */
async function pintar(root) {
  const box = /** @type {HTMLElement} */ (root.querySelector("#av-lista"));
  box.innerHTML = skeleton(4);
  const hoy = todayStr();
  /** @type {any[]} */
  let asis, avisos = [], just = [];
  try {
    asis = DB.hoyFecha === hoy ? DB.hoy : await api.asistenciasPorFecha(DB.cid, hoy);
    avisos = await api.avisosPorFecha(DB.cid, hoy).catch(() => []);
    just = await api.justificacionesRango(DB.cid, hoy, hoy).catch(() => []);
  } catch (/** @type {any} */ e) { box.innerHTML = emptyState("No se pudo cargar", e.message, "alert"); return; }
  if (!box.isConnected) return;
  const porAlumno = new Map(asis.map((x) => [x.alumno_id, x]));
  const justificados = new Set(just.map((j) => j.alumno_id));
  const avisado = new Set(avisos.map((a) => `${a.alumno_id}|${a.tipo}`));
  filas = [];
  DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && (!f.nivel || a.nivel === f.nivel) && (!f.grado || a.grado === f.grado)).forEach((a) => {
    const r = porAlumno.get(a.id);
    if (!r && !justificados.has(a.id) && f.tipo !== "tardanza") filas.push({ a, tipo: "Falta", hora: "" });
    else if (r && esTardanza(r.hora, CONFIG.HORA_LIMITE, a.nivel) && f.tipo !== "falta") filas.push({ a, tipo: "Tardanza", hora: String(r.hora).slice(0, 5) });
  });
  filas.sort((x, y) => x.tipo.localeCompare(y.tipo) || x.a.nombre.localeCompare(y.a.nombre, "es"));
  box.innerHTML = filas.length ? `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Ciclo</th><th>Situación</th><th>Apoderado</th><th></th></tr></thead><tbody>
    ${filas.map((x, i) => {
      const a = x.a, ya = avisado.has(`${a.id}|${x.tipo}`);
      return `<tr><td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td>
        <td>${esc(etiquetaCiclo(a.nivel, a.grado))}</td><td>${x.tipo === "Falta" ? badge("Falta", "red") : badge(`Tardanza ${x.hora}`, "amber")}</td>
        <td>${esc(a.apoderado || "—")}${a.apoderado_telefono ? `<br><small class="mono muted">${esc(a.apoderado_telefono)}</small>` : ""}</td>
        <td class="t-right nowrap">${ya ? badge("Avisado ✓", "green") + " " : ""}
          ${a.apoderado_telefono ? `<button class="btn btn-teal btn-sm" data-action="aviso-wa" data-i="${i}">${icon("megaphone", 14)} WhatsApp</button>` : ""}
          ${a.apoderado_email ? `<button class="btn btn-outline btn-sm" data-action="aviso-mail" data-i="${i}">Correo</button>` : ""}
          ${!a.apoderado_telefono && !a.apoderado_email ? '<span class="muted">Sin contacto</span>' : ""}</td></tr>`;
    }).join("")}</tbody></table></div>` : emptyState("Sin avisos pendientes", "No hay faltas ni tardanzas para los filtros elegidos.", "check");
}

/** @param {{ a: any, tipo: string, hora: string }} x */
const mensaje = (x) => mensajeAviso(x.tipo === "Falta" ? CONFIG.PLANTILLA_FALTA : CONFIG.PLANTILLA_TARDANZA,
  { alumno: x.a.nombre, fecha: fmtDate(todayStr()), instituto: DB.perfil?.colegio || "el instituto", hora: x.hora, ciclo: etiquetaCiclo(x.a.nivel, x.a.grado) });

/**
 * @param {{ a: any, tipo: string, hora: string }} x
 * @param {string} canal
 */
async function registrar(x, canal) {
  try { await api.registrarAviso({ colegio_id: /** @type {string} */ (DB.cid), alumno_id: x.a.id, fecha: todayStr(), tipo: x.tipo, canal, enviado_por: DB.userId || (await api.userId()) || undefined }); }
  catch (/** @type {any} */ e) { toast("Se abrió el mensaje, pero no se pudo anotar el aviso: " + e.message, "error"); return; }
  pintar(/** @type {HTMLElement} */ (document.getElementById("page-root")));
}

registerActions({
  "aviso-wa": (/** @type {HTMLElement} */ el) => {
    const x = filas[Number(el.dataset.i)];
    const url = enlaceWhatsApp(x.a.apoderado_telefono, mensaje(x));
    if (!url) { toast("El teléfono del apoderado no es válido", "error"); return; }
    window.open(url, "_blank", "noopener");
    registrar(x, "WhatsApp");
  },
  "aviso-mail": (/** @type {HTMLElement} */ el) => {
    const x = filas[Number(el.dataset.i)];
    window.open(`mailto:${encodeURIComponent(x.a.apoderado_email)}?subject=${encodeURIComponent("Aviso de asistencia")}&body=${encodeURIComponent(mensaje(x))}`, "_blank");
    registrar(x, "Correo");
  },
});
