// @ts-check
// Alerta temprana de inasistencias: quién se acerca (o pasó) el límite de faltas del periodo, con aviso al apoderado.
import { api } from "../api.js";
import { diasLectivos } from "../calendario.js";
import { CONFIG } from "../config.js";
import { DB, opcionesNivel } from "../state.js";
import { calcularRiesgo, faltasRestantes } from "../riesgo.js";
import { enlaceWhatsApp, mensajeAviso } from "../stats.js";
import { badge, emptyState, icon, pageHead, skeleton, toast } from "../ui.js";
import { addDays, cicloCorto, esc, fmtDate, initials, todayStr } from "../utils.js";

let f = { nivel: "", solo: "riesgo" };

export const alertasPage = {
  id: "alertas", title: "Alertas de inasistencia", icon: "alert", group: "Consultas",
  /** @param {HTMLElement} root */
  async render(root) {
    const per = DB.periodos.find((p) => p.activo);
    const desde = per?.inicio || addDays(todayStr(), -60);
    root.innerHTML = `${pageHead("Alertas de inasistencia", `Quién se acerca al límite de ${CONFIG.LIMITE_FALTAS_PCT} % de faltas. Cuenta desde ${fmtDate(desde, { day: "2-digit", month: "long", year: "numeric" })}${per ? ` (periodo ${esc(per.nombre)})` : " (últimos 60 días; define el periodo en «Periodos»)"}, sin feriados ni días sin clases.`)}
      <div class="toolbar"><select class="filter" id="al-nivel" aria-label="Carrera">${opcionesNivel(true).map((o) => `<option value="${esc(o.value)}" ${o.value === f.nivel ? "selected" : ""}>${esc(o.label)}</option>`).join("")}</select>
        <select class="filter" id="al-solo" aria-label="Mostrar"><option value="riesgo" ${f.solo === "riesgo" ? "selected" : ""}>Solo en riesgo</option><option value="todos" ${f.solo === "todos" ? "selected" : ""}>Todos</option></select></div>
      <div class="card flush" id="al-lista">${skeleton(5)}</div>`;
    const lista = /** @type {HTMLElement} */ (root.querySelector("#al-lista"));
    const pintar = async () => {
      lista.innerHTML = skeleton(5);
      const hoy = todayStr();
      const alumnos = DB.alumnos.filter((a) => !f.nivel || a.nivel === f.nivel);
      /** @type {any} */
      let asis, just;
      try { [asis, just] = await Promise.all([api.asistenciasRango(DB.cid, desde, hoy), api.justificacionesRango(DB.cid, desde, hoy).catch(() => [])]); }
      catch (/** @type {any} */ e) { lista.innerHTML = emptyState("No se pudo calcular", e.message, "alert"); return; }
      if (!lista.isConnected) return;
      let r = calcularRiesgo({ alumnos, asistencias: asis, justificaciones: just, noLectivos: DB.noLectivos, desde, hasta: hoy, hoy, limite: CONFIG.LIMITE_FALTAS_PCT });
      if (f.solo === "riesgo") r = r.filter((x) => x.nivel !== "ok");
      const dias = diasLectivos(desde, hoy, DB.noLectivos).length;
      if (!r.length) { lista.innerHTML = emptyState(dias ? "Nadie en riesgo" : "Aún no hay días de clase", dias ? "Ningún alumno está cerca del límite de faltas." : "Cuando empiecen las clases, aquí verás las alertas.", "userCheck"); return; }
      lista.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Ciclo</th><th>Faltas</th><th>% faltas</th><th>Margen</th><th>Estado</th><th></th></tr></thead><tbody>
        ${r.slice(0, 200).map((x) => {
          const a = x.alumno, msg = mensajeAviso(CONFIG.PLANTILLA_RIESGO, { alumno: a.nombre, ciclo: cicloCorto(a.grado, a.nivel), faltas: x.faltas, porcentaje: x.pctFaltas, limite: CONFIG.LIMITE_FALTAS_PCT, instituto: DB.perfil?.colegio || "" });
          const wa = a.apoderado_telefono ? enlaceWhatsApp(a.apoderado_telefono, msg) : "";
          return `<tr><td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td><td>${esc(cicloCorto(a.grado, a.nivel))}</td>
            <td>${x.faltas} de ${x.dias}${x.justificadas ? ` <small class="muted">(+${x.justificadas} just.)</small>` : ""}</td><td><strong>${x.pctFaltas}%</strong></td>
            <td>${x.nivel === "critico" ? "—" : `${faltasRestantes(x, CONFIG.LIMITE_FALTAS_PCT)} falta(s)`}</td>
            <td>${x.nivel === "critico" ? badge("Límite superado", "red") : x.nivel === "alerta" ? badge("En riesgo", "amber") : badge("Normal", "green")}</td>
            <td class="t-right">${wa ? `<a class="btn btn-teal btn-sm" target="_blank" rel="noopener" href="${esc(wa)}">${icon("check", 14)} Avisar</a>` : `<small class="muted">sin teléfono</small>`}</td></tr>`;
        }).join("")}</tbody></table></div>${r.length > 200 ? `<p class="muted pad">Mostrando 200 de ${r.length}.</p>` : ""}`;
    };
    /** @type {HTMLSelectElement} */ (root.querySelector("#al-nivel")).addEventListener("change", (e) => { f.nivel = /** @type {HTMLSelectElement} */ (e.target).value; pintar(); });
    /** @type {HTMLSelectElement} */ (root.querySelector("#al-solo")).addEventListener("change", (e) => { f.solo = /** @type {HTMLSelectElement} */ (e.target).value; pintar(); });
    await pintar();
  },
};
void toast;
