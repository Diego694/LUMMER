// @ts-check
// Periodos académicos y cambio de ciclo (administrador): cierra el periodo, pasa a cada alumno al ciclo siguiente
// (mismo salón), da por egresados a los del VI ciclo y abre el nuevo periodo. El historial de asistencia se conserva.
import { api } from "../api.js";
import { planPromocion } from "../promocion.js";
import { DB, loadAll } from "../state.js";
import { badge, emptyState, formModal, icon, openModal, pageHead, registerActions, toast } from "../ui.js";
import { esc, fmtDate, todayStr } from "../utils.js";

const root = () => /** @type {HTMLElement} */ (document.getElementById("page-root"));
const activo = () => DB.periodos.find((p) => p.activo) || null;

/** @param {HTMLElement} el */
function pintar(el) {
  const act = activo();
  const pasados = DB.periodos.filter((p) => !p.activo).sort((a, b) => b.inicio.localeCompare(a.inicio));
  /** @type {HTMLElement} */ (el.querySelector("#per-actual")).innerHTML = act
    ? `<div class="per-actual"><div><span class="muted">Periodo vigente</span><h2 style="margin:2px 0">${esc(act.nombre)}</h2><small class="muted">Desde ${esc(fmtDate(act.inicio, { day: "2-digit", month: "long", year: "numeric" }))}</small></div>
       <button class="btn btn-primary" data-action="per-cerrar">${icon("flip", 16)} Cerrar periodo y pasar de ciclo</button></div>`
    : `${emptyState("Sin periodo vigente", "Define el periodo actual (por ejemplo «2026-II»): los reportes y las alertas de inasistencia cuentan desde su inicio.", "calendar")}
       <div class="btn-row center"><button class="btn btn-primary" data-action="per-nuevo">${icon("plus", 16)} Definir periodo actual</button></div>`;
  /** @type {HTMLElement} */ (el.querySelector("#per-hist")).innerHTML = pasados.length
    ? `<div class="table-wrap"><table><thead><tr><th>Periodo</th><th>Inicio</th><th>Cierre</th><th>Pasaron de ciclo</th><th>Egresaron</th></tr></thead><tbody>
       ${pasados.map((p) => `<tr><td><strong>${esc(p.nombre)}</strong></td><td>${esc(fmtDate(p.inicio))}</td><td>${p.cerrado_en ? esc(fmtDate(String(p.cerrado_en).slice(0, 10))) : "—"}</td>
         <td>${p.resumen ? badge(String(p.resumen.movidos ?? 0), "green") : "—"}</td><td>${p.resumen ? badge(String(p.resumen.egresados ?? 0), "navy") : "—"}</td></tr>`).join("")}</tbody></table></div>`
    : emptyState("Aún no hay periodos cerrados", "Aquí quedará el historial de cada cierre.", "history");
}

export const periodosPage = {
  id: "periodos", title: "Periodos y cambio de ciclo", icon: "history", group: "Gestión", soloAdmin: true,
  /** @param {HTMLElement} el */
  render(el) {
    el.innerHTML = `${pageHead("Periodos y cambio de ciclo", "Cierra el periodo y pasa a los alumnos al ciclo siguiente, sin perder el historial.")}
      <section class="card" id="per-actual"></section>
      <section class="card flush" style="margin-top:16px"><header class="card-head pad"><h3>Periodos anteriores</h3></header><div id="per-hist"></div></section>
      <section class="card" style="margin-top:16px"><h3 style="margin-top:0">Cómo funciona el cambio de ciclo</h3>
        <ul class="muted" style="margin:6px 0 0;padding-left:20px;line-height:1.7"><li>Cada alumno <b>activo</b> pasa del ciclo I al II, del II al III… manteniendo su salón (A, B…). Si el ciclo nuevo no existe, se crea.</li>
        <li>Los del <b>VI ciclo egresan</b>: quedan como «EGRESADO» (no cuentan como activos), con su historial intacto.</li>
        <li>Alumnos con un ciclo que no sigue el formato (I–VI) <b>no se tocan</b>; se te avisa.</li>
        <li>Se cierra el periodo vigente y se abre el nuevo. <b>Haz antes un respaldo</b> (Sistema → Respaldo).</li></ul></section>`;
    pintar(el);
  },
};

function modalCierre() {
  const act = activo();
  const m = openModal({
    title: "Cerrar periodo y pasar de ciclo", wide: true,
    body: `<div class="form-grid">
      <div class="field half"><label for="pc-carrera">Carrera</label><select id="pc-carrera"><option value="">Todas las carreras</option>${DB.niveles.map((n) => `<option>${esc(n)}</option>`).join("")}</select></div>
      <div class="field half"><label for="pc-nombre">Nombre del nuevo periodo <span class="req">*</span></label><input id="pc-nombre" placeholder="Ej: 2027-I" autocomplete="off"></div>
      <div class="field half"><label for="pc-inicio">Inicio del nuevo periodo <span class="req">*</span></label><input id="pc-inicio" type="date" value="${todayStr()}"></div></div>
      <div id="pc-plan" class="card" style="margin:14px 0"></div>
      <label class="check-row"><input type="checkbox" id="pc-resp"> Ya descargué un respaldo (Sistema → Respaldo).</label>
      <p class="err-msg" id="pc-err" role="alert" hidden></p>`,
    footer: `<button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-primary" id="pc-aplicar">Aplicar cambio de ciclo</button>`,
  });
  /** @param {string} s */
  const $ = (s) => /** @type {any} */ (m.el.querySelector(s));
  $("[data-close2]").addEventListener("click", m.close);
  const plan = () => planPromocion(DB.alumnos, DB.grados, $("#pc-carrera").value ? [$("#pc-carrera").value] : []);
  const mostrar = () => {
    const p = plan();
    $("#pc-plan").innerHTML = `<div class="kpi-grid kpi-3"><div class="kpi kpi-teal"><div class="kpi-body"><span class="kpi-label">Pasan de ciclo</span><span class="kpi-value">${p.mover.length}</span></div></div>
      <div class="kpi kpi-navy"><div class="kpi-body"><span class="kpi-label">Egresan (VI)</span><span class="kpi-value">${p.egresan.length}</span></div></div>
      <div class="kpi"><div class="kpi-body"><span class="kpi-label">Sin cambio (ciclo libre)</span><span class="kpi-value">${p.sinCiclo.length}</span></div></div></div>
      ${p.gradosNuevos.length ? `<p class="muted" style="margin:10px 0 0">Se crearán ${p.gradosNuevos.length} ciclo(s) nuevo(s): ${esc(p.gradosNuevos.slice(0, 6).map((g) => g.nombre).join(", "))}${p.gradosNuevos.length > 6 ? "…" : ""}</p>` : ""}`;
  };
  $("#pc-carrera").addEventListener("change", mostrar); mostrar();
  $("#pc-aplicar").addEventListener("click", async () => {
    /** @param {string} t */
    const err = (t) => { $("#pc-err").textContent = t; $("#pc-err").hidden = !t; };
    const nombre = $("#pc-nombre").value.trim(), inicio = $("#pc-inicio").value;
    if (!nombre || !inicio) return err("Escribe el nombre y la fecha de inicio del nuevo periodo.");
    if (DB.periodos.some((p) => p.nombre === nombre)) return err("Ya existe un periodo con ese nombre.");
    if (!$("#pc-resp").checked) return err("Confirma que descargaste un respaldo antes de continuar.");
    const p = plan();
    if (!p.mover.length && !p.egresan.length) return err("No hay alumnos para pasar de ciclo.");
    const btn = $("#pc-aplicar"); btn.disabled = true; btn.textContent = "Aplicando…";
    try {
      for (const g of p.gradosNuevos) await api.save("grados", { colegio_id: DB.cid, nivel: g.nivel, nombre: g.nombre });
      const filas = [
        ...p.mover.map((x) => ({ ...x.alumno, grado: x.a })),
        ...p.egresan.map((a) => ({ ...a, estado: "EGRESADO" })),
      ];
      await api.upsertAlumnos(filas);
      const act = activo();
      if (act) await api.save("periodos", { colegio_id: DB.cid, activo: false, fin: todayStr(), cerrado_en: new Date().toISOString(), resumen: { movidos: p.mover.length, egresados: p.egresan.length, carrera: $("#pc-carrera").value || "todas" } }, act.id);
      await api.save("periodos", { colegio_id: DB.cid, nombre, inicio, activo: true });
      await loadAll();
      m.close(); pintar(root());
      toast(`Listo: ${p.mover.length} pasaron de ciclo y ${p.egresan.length} egresaron.`, "success");
    } catch (/** @type {any} */ e) { err("No se pudo completar: " + e.message); btn.disabled = false; btn.textContent = "Aplicar cambio de ciclo"; }
  });
  void act;
}

registerActions({
  "per-nuevo": () => formModal({
    title: "Definir periodo actual", fields: [
      { name: "nombre", label: "Nombre", required: true, placeholder: "Ej: 2026-II", half: true },
      { name: "inicio", label: "Fecha de inicio", type: "date", required: true, value: todayStr(), half: true },
    ],
    onSubmit: async (v) => {
      try { await api.save("periodos", { colegio_id: DB.cid, nombre: v.nombre, inicio: v.inicio, activo: true }); }
      catch (/** @type {any} */ e) { throw new Error(e.code === "duplicate" ? "Ya existe un periodo con ese nombre." : e.message); }
      await loadAll(); pintar(root()); toast("Periodo definido", "success");
    },
  }),
  "per-cerrar": () => modalCierre(),
});
