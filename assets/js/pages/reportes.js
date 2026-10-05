// Reporte mensual de asistencia (matriz por alumno y día, CSV y PDF) y Justificaciones de faltas/permisos.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, alumnoPorId, gradosDe, opcionesGrado, opcionesNivel } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { matrizAsistencia } from "../stats.js";
import { addDays, cicloCorto, diasHabilesDelMes, downloadFile, esc, etiquetaCiclo, fmtDate, norm, todayStr, toCSV } from "../utils.js";

const options = (list, sel) => list.map((o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");

/* ========================== Reporte mensual ========================== */
let rp = { nivel: "", grado: "", mes: "", m: null, titulo: "" };

export const reportePage = {
  id: "reporte", title: "Reporte mensual", icon: "table", group: "Consultas",
  async render(root) {
    rp.mes = rp.mes || todayStr().slice(0, 7);
    root.innerHTML = `${pageHead("Reporte mensual", "Asistencia de cada alumno, día por día. P presente · T tardanza · J justificado · F falta.",
      `<button class="btn btn-outline" data-action="rp-csv">${icon("download", 16)} CSV</button><button class="btn btn-primary" data-action="rp-pdf">${icon("download", 16)} PDF</button>`)}
      <div class="toolbar">
        <select class="filter" id="rp-nivel" aria-label="Carrera">${options(opcionesNivel(false).length ? [{ value: "", label: "Elige la carrera…" }, ...opcionesNivel(false)] : [], rp.nivel)}</select>
        <select class="filter" id="rp-grado" aria-label="Ciclo">${options(opcionesGrado(rp.nivel, true), rp.grado)}</select>
        <input class="filter" type="month" id="rp-mes" value="${rp.mes}" max="${todayStr().slice(0, 7)}" aria-label="Mes"></div>
      <div id="rp-kpis"></div><div class="card flush" id="rp-tabla"></div>`;
    const nv = root.querySelector("#rp-nivel"), gr = root.querySelector("#rp-grado"), mes = root.querySelector("#rp-mes");
    nv.addEventListener("change", () => { rp.nivel = nv.value; rp.grado = ""; gr.innerHTML = options(opcionesGrado(rp.nivel, true), ""); generar(root); });
    gr.addEventListener("change", () => { rp.grado = gr.value; generar(root); });
    mes.addEventListener("change", () => { rp.mes = mes.value || todayStr().slice(0, 7); generar(root); });
    await generar(root);
  },
};

async function generar(root) {
  const box = root.querySelector("#rp-tabla"), kp = root.querySelector("#rp-kpis");
  rp.m = null;
  if (!rp.nivel) { kp.innerHTML = ""; box.innerHTML = emptyState("Elige una carrera", "El reporte se arma por carrera (y opcionalmente por ciclo) y mes.", "table"); return; }
  box.innerHTML = skeleton(5);
  const alumnos = DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && a.nivel === rp.nivel && (!rp.grado || a.grado === rp.grado))
    .sort((a, b) => a.grado.localeCompare(b.grado, "es", { numeric: true }) || a.nombre.localeCompare(b.nombre, "es"));
  const dias = diasHabilesDelMes(rp.mes, todayStr(), DB.noLectivos);   // sin feriados ni días sin clases
  if (!alumnos.length || !dias.length) { kp.innerHTML = ""; box.innerHTML = emptyState("Sin datos", alumnos.length ? "El mes elegido aún no tiene días hábiles." : "No hay alumnos activos en esa carrera/ciclo.", "users"); return; }
  try {
    const [asis, just] = await Promise.all([api.asistenciasRango(DB.cid, dias[0], dias.at(-1)), api.justificacionesRango(DB.cid, dias[0], dias.at(-1)).catch(() => [])]);
    if (!box.isConnected) return;
    rp.m = matrizAsistencia(alumnos, asis, just, dias, CONFIG.HORA_LIMITE);
    rp.titulo = `${rp.nivel}${rp.grado ? " · " + cicloCorto(rp.grado, rp.nivel) : ""} — ${rp.mes}`;
  } catch (e) { box.innerHTML = emptyState("No se pudo generar", e.message, "alert"); return; }
  const m = rp.m;
  kp.innerHTML = `<div class="kpi-grid kpi-3"><div class="kpi kpi-navy"><div class="kpi-ic">${icon("users", 20)}</div><div class="kpi-body"><span class="kpi-label">Alumnos</span><span class="kpi-value">${m.resumen.alumnos}</span></div></div>
    <div class="kpi kpi-teal"><div class="kpi-ic">${icon("calendar", 20)}</div><div class="kpi-body"><span class="kpi-label">Días de clase registrados</span><span class="kpi-value">${m.resumen.dias}</span></div></div>
    <div class="kpi kpi-amber"><div class="kpi-ic">${icon("percent", 20)}</div><div class="kpi-body"><span class="kpi-label">Asistencia del grupo</span><span class="kpi-value">${m.resumen.pct}%</span></div></div></div>`;
  if (!m.dias.length) { box.innerHTML = emptyState("Sin registros en el mes", "Aún no hay asistencias registradas en este periodo.", "calendar"); return; }
  box.innerHTML = `<div class="table-wrap matriz"><table><thead><tr><th class="sticky">Alumno</th>${m.dias.map((d) => `<th class="c">${Number(d.slice(8))}</th>`).join("")}<th class="c">P</th><th class="c">T</th><th class="c">J</th><th class="c">F</th><th class="c">%</th></tr></thead><tbody>
    ${m.filas.map((r) => `<tr><td class="sticky"><strong>${esc(r.alumno.nombre)}</strong>${rp.grado ? "" : `<br><small class="muted">${esc(cicloCorto(r.alumno.grado, rp.nivel))}</small>`}</td>
      ${m.dias.map((d) => `<td class="c celda-${r.celdas[d]}">${r.celdas[d]}</td>`).join("")}
      <td class="c">${r.p}</td><td class="c">${r.t}</td><td class="c">${r.j}</td><td class="c">${r.f}</td><td class="c"><b class="${r.pct < CONFIG.UMBRAL_ASISTENCIA ? "pct-red" : ""}">${r.pct}%</b></td></tr>`).join("")}</tbody></table></div>`;
  const tw = box.querySelector(".table-wrap.matriz");
  if (tw) {
    const onScroll = () => tw.classList.toggle("is-scrolled", tw.scrollLeft > 2);
    tw.addEventListener("scroll", onScroll, { passive: true });
    onScroll();
  }
}

function pdfReporte() {
  const m = rp.m;
  const doc = new window.jspdf.jsPDF({ orientation: "landscape", unit: "pt", format: "a4" });
  const W = 842, M = 28, filaH = 17, porPagina = 24, nombreW = 150, dW = Math.min(22, (W - 2 * M - nombreW - 150) / m.dias.length);
  const per = DB.periodos.find((p) => p.activo);
  const noLect = [...DB.noLectivos.values()].filter((c) => c.fecha.startsWith(rp.mes)).sort((a, b) => a.fecha.localeCompare(b.fecha));
  const cab = () => {
    doc.setFont("helvetica", "bold"); doc.setFontSize(14); doc.text(`NÓMINA DE ASISTENCIA — ${(DB.perfil?.colegio || "").toUpperCase()}`, M, 32);
    doc.setFont("helvetica", "normal"); doc.setFontSize(10);
    doc.text(`${rp.titulo}${per ? "   ·   Periodo " + per.nombre : ""}   ·   Asistencia del grupo: ${m.resumen.pct}%`, M, 48);
    doc.setFontSize(8.5); doc.setTextColor(90);
    doc.text(`P presente  ·  T tardanza  ·  J justificado  ·  F falta${noLect.length ? "   ·   Sin clases: " + noLect.map((c) => `${Number(c.fecha.slice(8))} (${c.nombre})`).join(", ").slice(0, 150) : ""}`, M, 61);
    doc.setTextColor(0);
  };
  const encabezado = (y) => {
    doc.setFont("helvetica", "bold"); doc.setFontSize(8); doc.text("Alumno", M, y);
    m.dias.forEach((d, i) => doc.text(String(Number(d.slice(8))), M + nombreW + i * dW + dW / 2, y, { align: "center" }));
    ["P", "T", "J", "F", "%"].forEach((t, i) => doc.text(t, M + nombreW + m.dias.length * dW + 18 + i * 28, y, { align: "center" }));
    doc.line(M, y + 4, W - M, y + 4);
  };
  let y = 0, n = 0, pagina = 0;
  m.filas.forEach((r) => {
    if (n % porPagina === 0) { if (pagina) doc.addPage(); pagina++; cab(); y = 84; encabezado(y); y += 8; }
    y += filaH; n++;
    doc.setFont("helvetica", "normal"); doc.setFontSize(8);
    doc.text(`${n}. ${r.alumno.nombre}`.slice(0, 36), M, y);
    m.dias.forEach((d, i) => {
      const c = r.celdas[d];
      if (c === "F") { doc.setTextColor(190, 40, 30); doc.setFont("helvetica", "bold"); } else if (c === "T") doc.setTextColor(180, 110, 10); else if (c === "J") doc.setTextColor(40, 90, 170); else doc.setTextColor(30, 120, 90);
      doc.text(c, M + nombreW + i * dW + dW / 2, y, { align: "center" }); doc.setTextColor(0); doc.setFont("helvetica", "normal");
    });
    [r.p, r.t, r.j, r.f, r.pct + "%"].forEach((v, i) => doc.text(String(v), M + nombreW + m.dias.length * dW + 18 + i * 28, y, { align: "center" }));
  });
  // Firmas (formato de nómina): docente responsable, coordinación y dirección
  if (y > 470) { doc.addPage(); y = 60; }
  y += 54;
  doc.setFontSize(9);
  ["Docente responsable", "Coordinación académica", "Dirección"].forEach((t, i) => {
    const x = M + i * 270;
    doc.line(x, y, x + 200, y); doc.text(t, x + 100, y + 12, { align: "center" });
  });
  doc.setFontSize(7.5); doc.setTextColor(120);
  doc.text(`Generado el ${new Date().toLocaleDateString("es-PE", { timeZone: "America/Lima", day: "2-digit", month: "long", year: "numeric" })} · Registro Académico`, M, 575);
  doc.setTextColor(0);
  return doc;
}

/* ============================ Justificaciones ============================ */
export const justificacionesPage = {
  id: "justificaciones", title: "Justificaciones", icon: "calendar", group: "Gestión",
  async render(root) {
    root.innerHTML = `${pageHead("Justificaciones", "Faltas justificadas, permisos y tardanzas justificadas. Una justificada no cuenta como falta en los reportes.",
      `<button class="btn btn-primary" data-action="just-new">${icon("plus", 16)} Nueva justificación</button>`)}<div class="card flush" id="just-lista">${skeleton(4)}</div>`;
    root._repaint = () => pintarLista(root);
    await pintarLista(root);
  },
};
async function pintarLista(root) {
  const box = root.querySelector("#just-lista");
  try {
    const hoy = todayStr();
    const l = (await api.justificacionesRango(DB.cid, addDays(hoy, -90), hoy)).sort((a, b) => b.fecha.localeCompare(a.fecha));
    box.innerHTML = l.length ? `<div class="table-wrap"><table><thead><tr><th>Fecha</th><th>Alumno</th><th>Ciclo</th><th>Tipo</th><th>Motivo</th><th></th></tr></thead><tbody>
      ${l.map((j) => { const a = alumnoPorId(j.alumno_id); return `<tr><td class="nowrap">${esc(fmtDate(j.fecha))}</td><td>${esc(a?.nombre || "—")}</td><td>${esc(a ? etiquetaCiclo(a.nivel, a.grado) : "")}</td>
        <td>${badge(j.tipo, "green")}</td><td>${esc(j.motivo || "—")}</td><td class="t-right"><button class="icon-only danger" title="Eliminar" aria-label="Eliminar justificación" data-action="just-del" data-id="${j.id}">${icon("trash", 16)}</button></td></tr>`; }).join("")}</tbody></table></div>`
      : emptyState("Sin justificaciones", "Las faltas o permisos que registres aparecerán aquí (últimos 90 días).", "calendar");
  } catch (e) { box.innerHTML = emptyState("No se pudo cargar", /does not exist|schema cache/i.test(e.message) ? "Falta aplicar la migración 004 en Supabase." : e.message, "alert"); }
}

function nuevaJustificacion() {
  const alumnosDe = (nivel, grado) => DB.alumnos.filter((a) => a.estado === "ACTIVO" && (!nivel || a.nivel === nivel) && (!grado || a.grado === grado)).sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  const optAl = (n, g) => alumnosDe(n, g).map((a) => ({ value: a.id, label: `${a.nombre} (${a.codigo})` }));
  const nivel0 = DB.niveles[0] || "";
  formModal({
    title: "Nueva justificación", submitLabel: "Guardar",
    fields: [
      { name: "nivel", label: "Carrera", type: "select", half: true, options: opcionesNivel(true), value: "", onChange: (v, c) => { c.setOptions("grado", opcionesGrado(v, true), ""); c.setOptions("alumno", optAl(v, ""), undefined); } },
      { name: "grado", label: "Ciclo", type: "select", half: true, options: opcionesGrado("", true), value: "", onChange: (v, c) => { const n = document.querySelector("#modal-form").elements.nivel.value; c.setOptions("alumno", optAl(n, v), undefined); } },
      { name: "alumno", label: "Alumno", type: "select", required: true, options: optAl("", ""), value: optAl("", "")[0]?.value },
      { name: "fecha", label: "Fecha", type: "date", required: true, half: true, value: todayStr(), max: todayStr() },
      { name: "tipo", label: "Tipo", type: "select", half: true, options: ["Falta justificada", "Permiso", "Tardanza justificada"].map((t) => ({ value: t, label: t })), value: "Falta justificada" },
      { name: "motivo", label: "Motivo", type: "textarea", placeholder: "Ej: Cita médica, certificado adjunto" },
    ],
    onSubmit: async (v) => {
      if (v.fecha > todayStr()) throw new Error("La fecha no puede ser futura.");
      try { await api.guardarJustificacion({ colegio_id: DB.cid, alumno_id: v.alumno, fecha: v.fecha, tipo: v.tipo, motivo: v.motivo, registrado_por: DB.userId || (await api.userId()) }); }
      catch (e) { throw new Error(e.message); }
      toast("Justificación registrada", "success");
      document.getElementById("page-root")._repaint?.();
    },
  });
}

registerActions({
  "just-new": nuevaJustificacion,
  "just-del": async (el) => {
    if (!(await confirmDialog({ title: "Eliminar justificación", message: "¿Eliminar esta justificación? La falta volverá a contarse." }))) return;
    try { await api.eliminarJustificacion(el.dataset.id); document.getElementById("page-root")._repaint?.(); toast("Justificación eliminada", "success"); } catch (e) { toast(e.message, "error"); }
  },
  "rp-csv": async () => {
    if (!rp.m?.dias.length) { toast("Primero genera un reporte con datos"); return; }
    const cols = [{ label: "Código", value: (r) => r.alumno.codigo }, { label: "Alumno", value: (r) => r.alumno.nombre }, { label: "Carrera", value: (r) => r.alumno.nivel }, { label: "Ciclo", value: (r) => r.alumno.grado },
      ...rp.m.dias.map((d) => ({ label: d, value: (r) => r.celdas[d] })),
      { label: "Presentes", value: (r) => r.p }, { label: "Tardanzas", value: (r) => r.t }, { label: "Justificadas", value: (r) => r.j }, { label: "Faltas", value: (r) => r.f }, { label: "% asistencia", value: (r) => r.pct }];
    await downloadFile(`reporte_${rp.mes}_${norm(rp.nivel).replace(/\s+/g, "-")}.csv`, toCSV(rp.m.filas, cols));
  },
  "rp-pdf": async () => {
    if (!rp.m?.dias.length) { toast("Primero genera un reporte con datos"); return; }
    await downloadFile(`reporte_${rp.mes}_${norm(rp.nivel).replace(/\s+/g, "-")}.pdf`, pdfReporte().output("blob"));
  },
});
