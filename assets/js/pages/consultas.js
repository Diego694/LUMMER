// Consultas de asistencia: por grado (con fecha) y por alumno (historial).
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, alumnoPorId, opcionesGrado, opcionesNivel } from "../state.js";
import { badge, emptyState, icon, kpi, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { esTardanza, resumenAlumno } from "../stats.js";
import { debounce, downloadFile, esc, fmtDate, initials, lastWeekdays, norm, pct, todayStr, toCSV } from "../utils.js";

const options = (list, sel) => list.map((o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");

/* ========================== Por grado ========================== */
let ag = { nivel: "", grado: "", fecha: "", filas: [] };

export const asistGradoPage = {
  id: "asist-grado", title: "Asistencia por Grado", icon: "table", group: "Consultas",
  async render(root) {
    ag.fecha = ag.fecha || todayStr();
    root.innerHTML = `
      ${pageHead("Asistencia por Grado", "Estado de ingreso de cada alumno en la fecha elegida.", `<button class="btn btn-outline" data-action="ag-export">${icon("download", 16)} Exportar CSV</button>`)}
      <div class="toolbar">
        <select class="filter" id="ag-nivel" aria-label="Nivel">${options(opcionesNivel(true), ag.nivel)}</select>
        <select class="filter" id="ag-grado" aria-label="Grado">${options(opcionesGrado(ag.nivel, true), ag.grado)}</select>
        <input class="filter" type="date" id="ag-fecha" max="${todayStr()}" value="${ag.fecha}" aria-label="Fecha"></div>
      <div id="ag-kpis" class="kpi-grid kpi-3"></div>
      <div class="card flush" id="ag-table"></div>`;
    const nivel = root.querySelector("#ag-nivel"), grado = root.querySelector("#ag-grado"), fecha = root.querySelector("#ag-fecha");
    nivel.addEventListener("change", () => { ag.nivel = nivel.value; ag.grado = ""; grado.innerHTML = options(opcionesGrado(ag.nivel, true), ""); pintar(); });
    grado.addEventListener("change", () => { ag.grado = grado.value; pintar(); });
    fecha.addEventListener("change", () => { ag.fecha = fecha.value || todayStr(); pintar(); });
    await pintar();

    async function pintar() {
      const el = root.querySelector("#ag-table");
      const alumnos = DB.alumnos.filter((a) => (!ag.nivel || a.nivel === ag.nivel) && (!ag.grado || a.grado === ag.grado));
      if (!alumnos.length) { ag.filas = []; root.querySelector("#ag-kpis").innerHTML = ""; el.innerHTML = emptyState("Sin alumnos", "Selecciona un nivel y grado con alumnos registrados.", "users"); return; }
      el.innerHTML = skeleton(5);
      let dia = [];
      try { dia = ag.fecha === todayStr() ? DB.hoy : await api.asistenciasPorFecha(DB.cid, ag.fecha); } catch (e) { toast("No se pudo cargar: " + e.message, "error"); }
      if (!el.isConnected) return; // el usuario ya cambió de página
      const por = new Map(dia.map((x) => [x.alumno_id, x]));
      ag.filas = alumnos.map((a) => ({ a, reg: por.get(a.id) }));
      const activos = ag.filas.filter((f) => f.a.estado === "ACTIVO");
      const pres = activos.filter((f) => f.reg).length;
      const tardes = activos.filter((f) => f.reg && esTardanza(f.reg.hora, CONFIG.HORA_LIMITE)).length;
      root.querySelector("#ag-kpis").innerHTML =
        kpi({ label: "Presentes", value: pres, hint: `${tardes} con tardanza`, ic: "userCheck", tone: "teal" }) +
        kpi({ label: "Ausentes", value: activos.length - pres, ic: "userX", tone: "red" }) +
        kpi({ label: "% asistencia", value: pct(pres, activos.length) + "%", hint: `${activos.length} alumnos activos`, ic: "percent", tone: "amber" });
      el.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Código</th><th>Grado</th><th>Estado</th><th>Hora</th></tr></thead><tbody>
        ${ag.filas.map(({ a, reg }) => `<tr><td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td>
          <td class="mono">${esc(a.codigo)}</td><td>${esc(a.grado)}</td>
          <td>${a.estado !== "ACTIVO" ? badge("Inactivo", "neutral") : !reg ? badge("Ausente", "red") : esTardanza(reg.hora, CONFIG.HORA_LIMITE) ? badge("Tardanza", "amber") : badge("Presente", "green")}</td>
          <td class="mono">${reg ? esc(reg.hora.slice(0, 5)) : "—"}</td></tr>`).join("")}</tbody></table></div>`;
    }
  },
};

/* ========================== Por alumno ========================== */
let aa = { q: "", sel: null };

export const asistAlumnoPage = {
  id: "asist-alumno", title: "Asistencia por Alumno", icon: "history", group: "Consultas",
  render(root, params = {}) {
    if (params.id) aa.sel = alumnoPorId(params.id) || aa.sel;
    root.innerHTML = `
      ${pageHead("Asistencia por Alumno", "Historial de ingresos y porcentaje de asistencia (últimos 30 días hábiles).")}
      <div class="grid-2 split">
        <section class="card flush"><div class="pad"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="aa-q" placeholder="Buscar alumno…" value="${esc(aa.q)}" aria-label="Buscar alumno"></div></div><div id="aa-list" class="pick-list"></div></section>
        <section class="card" id="aa-detail"></section>
      </div>`;
    root.querySelector("#aa-q").addEventListener("input", debounce((e) => { aa.q = e.target.value; lista(); }, 150));
    lista(); detalle();
    function lista() {
      const q = norm(aa.q);
      const l = DB.alumnos.filter((a) => !q || norm(`${a.nombre} ${a.codigo}`).includes(q)).slice(0, 80);
      root.querySelector("#aa-list").innerHTML = l.length ? l.map((a) => `<button class="pick ${aa.sel?.id === a.id ? "active" : ""}" data-action="aa-pick" data-id="${a.id}">
        <span class="avatar">${esc(initials(a.nombre))}</span><span><strong>${esc(a.nombre)}</strong><small>${esc(a.grado)} · ${esc(a.nivel)}</small></span></button>`).join("") : emptyState("Sin alumnos", "Ajusta tu búsqueda.", "search");
    }
    async function detalle() {
      const el = root.querySelector("#aa-detail");
      if (!aa.sel) { el.innerHTML = emptyState("Selecciona un alumno", "Verás su historial y sus indicadores.", "history"); return; }
      const a = aa.sel;
      el.innerHTML = skeleton(5);
      try {
        const dias = lastWeekdays(30);
        const hist = (await api.asistenciasAlumno(a.id, 200)).filter((h) => h.fecha >= dias[0]);
        const diasClase = new Set((await api.asistenciasRango(DB.cid, dias[0], todayStr())).map((x) => x.fecha)).size;
        if (!el.isConnected || aa.sel !== a) return; // página cambiada o se eligió otro alumno
        const r = resumenAlumno(hist, diasClase, CONFIG.HORA_LIMITE);
        root._hist = { a, hist };
        el.innerHTML = `
          <header class="card-head"><div class="person lg"><span class="avatar avatar-lg">${esc(initials(a.nombre))}</span><div><h3>${esc(a.nombre)}</h3><small class="muted mono">${esc(a.codigo)} · ${esc(a.nivel)} · ${esc(a.grado)}</small></div></div>
          <button class="btn btn-outline btn-sm" data-action="aa-export">${icon("download", 14)} CSV</button></header>
          <div class="mini-kpis"><div><b>${r.pct}%</b><span>Asistencia</span></div><div><b>${r.presentes}</b><span>Presentes</span></div><div><b>${r.tardes}</b><span>Tardanzas</span></div><div><b>${r.ausentes}</b><span>Ausencias</span></div></div>
          ${hist.length ? `<div class="table-wrap"><table><thead><tr><th>Fecha</th><th>Hora</th><th>Estado</th></tr></thead><tbody>${hist.map((h) => `<tr><td>${esc(fmtDate(h.fecha, { weekday: "short", day: "2-digit", month: "short", year: "numeric" }))}</td><td class="mono">${esc(h.hora.slice(0, 5))}</td><td>${esTardanza(h.hora, CONFIG.HORA_LIMITE) ? badge("Tardanza", "amber") : badge("Puntual", "green")}</td></tr>`).join("")}</tbody></table></div>`
            : emptyState("Sin registros", "Este alumno no tiene asistencias en el periodo.", "calendar")}`;
      } catch (e) { el.innerHTML = emptyState("No se pudo cargar", e.message, "alert"); }
    }
    root._repaint = () => { lista(); detalle(); };
  },
};

registerActions({
  "aa-pick": (el) => { aa.sel = alumnoPorId(el.dataset.id); document.getElementById("page-root")._repaint(); },
  "aa-export": () => {
    const h = document.getElementById("page-root")._hist; if (!h) return;
    downloadFile(`historial_${h.a.codigo}.csv`, toCSV(h.hist, [{ label: "Fecha", key: "fecha" }, { label: "Hora", value: (r) => r.hora.slice(0, 5) }, { label: "Estado", value: (r) => (esTardanza(r.hora, CONFIG.HORA_LIMITE) ? "Tardanza" : "Puntual") }]));
  },
  "ag-export": () => {
    if (!ag.filas.length) { toast("No hay datos para exportar"); return; }
    downloadFile(`asistencia_${ag.fecha}.csv`, toCSV(ag.filas, [
      { label: "Fecha", value: () => ag.fecha }, { label: "Código", value: (f) => f.a.codigo }, { label: "Alumno", value: (f) => f.a.nombre },
      { label: "Nivel", value: (f) => f.a.nivel }, { label: "Grado", value: (f) => f.a.grado },
      { label: "Estado", value: (f) => (f.a.estado !== "ACTIVO" ? "Inactivo" : !f.reg ? "Ausente" : esTardanza(f.reg.hora, CONFIG.HORA_LIMITE) ? "Tardanza" : "Presente") },
      { label: "Hora", value: (f) => f.reg?.hora.slice(0, 5) ?? "" },
    ]));
    toast("Archivo exportado", "success");
  },
});
