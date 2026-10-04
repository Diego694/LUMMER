import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB } from "../state.js";
import { badge, emptyState, icon, kpi, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { resumenDia, serieDiaria, porGrado, porNivel, bajaAsistencia, esTardanza } from "../stats.js";
import { downloadFile, esc, fmtDate, fmtDay, greeting, initials, isWeekend, lastWeekdays, todayStr, toCSV } from "../utils.js";

let rango = 7;
let cache = null;
let charts = [];
let timer = null;
let rootEl = null;
let ver = 0; // invalida cargas en vuelo si el usuario cambia de página o de periodo

const cssVar = (n) => getComputedStyle(document.documentElement).getPropertyValue(n).trim();

export const dashboardPage = {
  id: "dashboard", title: "Dashboard", icon: "dashboard", group: "Principal",

  async render(root) {
    rootEl = root;
    const mia = ++ver;
    root.innerHTML = skeleton(6);
    await cargar();
    if (mia !== ver) return;
    pintar();
    clearInterval(timer);
    timer = setInterval(async () => {
      if (document.hidden) return;
      const m = ver;
      await cargar(true);
      if (m === ver) pintar();
    }, 60000);
  },
  onLeave() { ver++; clearInterval(timer); destruirGraficos(); },
  onTheme() { if (cache && rootEl?.isConnected) dibujarGraficos(); },
};

async function cargar(silencioso = false) {
  try {
    const dias = lastWeekdays(rango);
    const hoy = todayStr();
    const filas = await api.asistenciasRango(DB.cid, dias[0], hoy);
    cache = { dias, filas, hoy: filas.filter((f) => f.fecha === hoy), actualizado: new Date() };
  } catch (e) {
    if (!silencioso) toast("No se pudo cargar el dashboard: " + e.message, "error");
    cache = cache || { dias: [], filas: [], hoy: [], actualizado: new Date() };
  }
}

function pintar() {
  const { dias, filas, hoy, actualizado } = cache;
  const L = CONFIG.HORA_LIMITE;
  const r = resumenDia(DB.alumnos, hoy, L);
  const activosIds = new Set(DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false).map((a) => a.id));
  const serie = serieDiaria(dias, filas.filter((f) => activosIds.has(f.alumno_id)), r.activos, L);
  const previos = serie.filter((s) => s.fecha !== todayStr() && s.presentes > 0);
  const promedio = previos.length ? Math.round(previos.reduce((t, s) => t + s.pct, 0) / previos.length) : 0;
  const delta = previos.length ? r.pct - promedio : null;
  const deltaTxt = delta === null ? "Sin histórico" : `${delta >= 0 ? "▲" : "▼"} ${Math.abs(delta)} pts vs. promedio (${promedio}%)`;
  const atencion = bajaAsistencia(DB.alumnos, filas, CONFIG.UMBRAL_ASISTENCIA);
  const recientes = [...hoy].sort((a, b) => b.hora.localeCompare(a.hora)).slice(0, 8);
  const alum = new Map(DB.alumnos.map((a) => [a.id, a]));
  const inactivos = DB.alumnos.length - r.activos;
  const nombre = (DB.perfil?.nombre || "").split(" ")[0];
  const finde = isWeekend(todayStr());

  rootEl.innerHTML = `
    ${pageHead(`${greeting()}${nombre ? ", " + esc(nombre) : ""}`,
      `${esc(fmtDate(todayStr(), { weekday: "long", day: "numeric", month: "long", year: "numeric" }))} · Actualizado ${actualizado.toLocaleTimeString("es-PE", { hour: "2-digit", minute: "2-digit" })}`,
      `<label class="sr-only" for="dash-rango">Periodo</label>
       <select class="filter" id="dash-rango" aria-label="Periodo">${[7, 14, 30].map((n) => `<option value="${n}" ${n === rango ? "selected" : ""}>Últimos ${n} días hábiles</option>`).join("")}</select>
       <button class="btn btn-outline" data-action="dash-export">${icon("download", 16)} Exportar CSV</button>
       <a class="btn btn-outline" href="#/codigo">${icon("qr", 16)} Código de registro</a>
       <a class="btn btn-primary" href="#/registro-qr">${icon("qr", 16)} Registrar asistencia</a>`)}

    <section class="kpi-grid" aria-label="Indicadores del día">
      ${kpi({ label: "Alumnos activos", value: r.activos, hint: `${inactivos} inactivo(s) · ${DB.grados.length} ciclos`, ic: "users", tone: "navy" })}
      ${kpi({ label: "Presentes hoy", value: r.presentes, hint: `${r.puntuales} puntuales · ${r.tardes} tardanzas`, ic: "userCheck", tone: "teal" })}
      ${kpi({ label: "Ausentes hoy", value: finde && !r.presentes ? "—" : r.ausentes, hint: finde && !r.presentes ? "Fin de semana: sin clases" : r.ausentes ? "Sin registro de ingreso" : "¡Asistencia completa!", ic: "userX", tone: r.ausentes && !(finde && !r.presentes) ? "red" : "teal" })}
      ${kpi({ label: "% de asistencia hoy", value: r.pct + "%", hint: deltaTxt, ic: "percent", tone: "amber" })}
    </section>

    <section class="dash-grid">
      <article class="card span-2"><header class="card-head"><h3>Tendencia de asistencia</h3><span class="muted">Hora límite de ingreso: ${esc(L)}</span></header>
        <div class="chart-box"><canvas id="chart-tendencia" role="img" aria-label="Gráfico de tendencia de asistencia por día"></canvas></div></article>
      <article class="card"><header class="card-head"><h3>Estado de hoy</h3></header>
        <div class="chart-box chart-sm"><canvas id="chart-hoy" role="img" aria-label="Distribución de presentes, tardanzas y ausentes de hoy"></canvas></div></article>

      <article class="card span-2"><header class="card-head"><h3>Asistencia de hoy por ciclo</h3><span class="muted">Meta: ${CONFIG.UMBRAL_ASISTENCIA}%</span></header>
        <div class="chart-box chart-grados"><canvas id="chart-grados" role="img" aria-label="Porcentaje de asistencia de hoy por ciclo"></canvas></div></article>
      <article class="card"><header class="card-head"><h3>Alumnos por carrera</h3></header>
        <div class="chart-box chart-sm"><canvas id="chart-niveles" role="img" aria-label="Alumnos activos por carrera"></canvas></div></article>

      <article class="card span-2"><header class="card-head"><h3>Últimos ingresos</h3><a class="link" href="#/asist-grado">Ver asistencia por ciclo →</a></header>
        ${recientes.length ? `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Ciclo</th><th>Hora</th><th>Estado</th></tr></thead><tbody>
          ${recientes.map((x) => { const a = alum.get(x.alumno_id); const t = esTardanza(x.hora, L); return `<tr>
            <td><div class="person"><span class="avatar">${esc(initials(a?.nombre))}</span><span>${esc(a?.nombre ?? "—")}</span></div></td>
            <td>${esc(a ? a.grado : "—")}</td><td class="mono">${esc(x.hora.slice(0, 5))}</td><td>${t ? badge("Tardanza", "amber") : badge("Puntual", "green")}</td></tr>`; }).join("")}
          </tbody></table></div>` : emptyState("Aún no hay ingresos hoy", "Los registros aparecerán aquí en cuanto se escanee el primer carnet.", "clock")}</article>

      <article class="card"><header class="card-head"><h3>Requieren atención</h3><span class="muted">&lt; ${CONFIG.UMBRAL_ASISTENCIA}% en ${rango} días</span></header>
        ${atencion.length ? `<ul class="alert-list">${atencion.map((x) => `<li><div class="person"><span class="avatar avatar-warn">${esc(initials(x.alumno.nombre))}</span><div><strong>${esc(x.alumno.nombre)}</strong><small>${esc(x.alumno.grado)} · ${x.presentes}/${x.dias} días</small></div></div><span class="pct ${x.pct < 60 ? "pct-red" : "pct-amber"}">${x.pct}%</span></li>`).join("")}</ul>`
          : emptyState("Todo en orden", "Ningún alumno está por debajo de la meta en este periodo.", "check")}</article>

      <article class="card span-3"><header class="card-head"><h3>Últimos comunicados</h3><a class="link" href="#/comunicados">Gestionar →</a></header>
        ${DB.comunicados.length ? `<div class="news-grid">${DB.comunicados.slice(0, 3).map((c) => `<div class="news"><small>${esc(fmtDate(c.fecha))}</small><strong>${esc(c.titulo)}</strong><p>${esc(c.mensaje)}</p></div>`).join("")}</div>`
          : emptyState("Sin comunicados", "Publica el primero desde la sección Comunicados.", "megaphone")}</article>
    </section>`;

  rootEl.querySelector("#dash-rango").addEventListener("change", async (e) => {
    rango = Number(e.target.value);
    const mia = ++ver;
    rootEl.innerHTML = skeleton(6);
    await cargar();
    if (mia === ver) pintar();
  });
  cache.derived = { r, serie, porGrado: porGrado(DB.alumnos, hoy), porNivel: porNivel(DB.alumnos, DB.niveles) };
  dibujarGraficos();
}

function destruirGraficos() { charts.forEach((c) => c.destroy()); charts = []; }

function dibujarGraficos() {
  if (!window.Chart || !cache?.derived) return;
  destruirGraficos();
  const C = { text: cssVar("--ink-soft"), grid: cssVar("--line"), teal: cssVar("--teal"), amber: cssVar("--amber"), red: cssVar("--red"), navy: cssVar("--chart-navy"), panel: cssVar("--panel") };
  Chart.defaults.font.family = "Inter, sans-serif";
  Chart.defaults.color = C.text;
  const { r, serie, porGrado: grados, porNivel: niveles } = cache.derived;
  const base = { responsive: true, maintainAspectRatio: false, plugins: { legend: { labels: { usePointStyle: true, boxWidth: 8 } } } };
  const get = (id) => document.getElementById(id);

  charts.push(new Chart(get("chart-tendencia"), {
    data: {
      labels: serie.map((s) => fmtDay(s.fecha)),
      datasets: [
        { type: "line", label: "% asistencia", data: serie.map((s) => s.pct), yAxisID: "y1", borderColor: C.navy, backgroundColor: C.navy, tension: 0.35, pointRadius: 3, order: 0 },
        { type: "bar", label: "Puntuales", data: serie.map((s) => s.presentes - s.tardes), backgroundColor: C.teal, borderRadius: 4, stack: "a", yAxisID: "y", order: 1 },
        { type: "bar", label: "Tardanzas", data: serie.map((s) => s.tardes), backgroundColor: C.amber, borderRadius: 4, stack: "a", yAxisID: "y", order: 1 },
      ],
    },
    options: { ...base, interaction: { mode: "index", intersect: false }, scales: {
      x: { stacked: true, grid: { display: false } },
      y: { stacked: true, beginAtZero: true, grid: { color: C.grid }, title: { display: true, text: "Alumnos" }, ticks: { precision: 0 } },
      y1: { position: "right", min: 0, max: 100, grid: { display: false }, ticks: { callback: (v) => v + "%" } },
    } },
  }));

  charts.push(new Chart(get("chart-hoy"), {
    type: "doughnut",
    data: { labels: ["Puntuales", "Tardanzas", "Ausentes"], datasets: [{ data: [r.puntuales, r.tardes, r.ausentes], backgroundColor: [C.teal, C.amber, C.red], borderColor: C.panel, borderWidth: 3 }] },
    options: { ...base, cutout: "68%", plugins: { legend: { position: "bottom", labels: { usePointStyle: true, boxWidth: 8 } } } },
  }));

  const colorGrado = (p) => (p >= CONFIG.UMBRAL_ASISTENCIA ? C.teal : p >= 60 ? C.amber : C.red);
  charts.push(new Chart(get("chart-grados"), {
    type: "bar",
    data: { labels: grados.map((g) => g.key), datasets: [{ label: "% asistencia hoy", data: grados.map((g) => g.pct), backgroundColor: grados.map((g) => colorGrado(g.pct)), borderRadius: 4, barThickness: 16 }] },
    options: { ...base, indexAxis: "y", plugins: { legend: { display: false }, tooltip: { callbacks: { label: (c) => ` ${c.parsed.x}% (${grados[c.dataIndex].presentes}/${grados[c.dataIndex].total})` } } },
      scales: { x: { min: 0, max: 100, grid: { color: C.grid }, ticks: { callback: (v) => v + "%" } }, y: { grid: { display: false } } } },
  }));

  charts.push(new Chart(get("chart-niveles"), {
    type: "doughnut",
    data: { labels: niveles.map((n) => n.nivel), datasets: [{ data: niveles.map((n) => n.total), backgroundColor: [C.navy, C.amber, C.teal, C.red, "#7C88A8"], borderColor: C.panel, borderWidth: 3 }] },
    options: { ...base, cutout: "60%", plugins: { legend: { position: "bottom", labels: { usePointStyle: true, boxWidth: 8 } } } },
  }));
}

registerActions({
  "dash-export": () => {
    if (!cache) return;
    const alum = new Map(DB.alumnos.map((a) => [a.id, a]));
    const L = CONFIG.HORA_LIMITE;
    const rows = [...cache.filas].sort((a, b) => a.fecha.localeCompare(b.fecha) || a.hora.localeCompare(b.hora));
    downloadFile(`asistencia_${cache.dias[0]}_a_${todayStr()}.csv`, toCSV(rows, [
      { label: "Fecha", key: "fecha" }, { label: "Hora", value: (r) => r.hora.slice(0, 5) },
      { label: "Código", value: (r) => alum.get(r.alumno_id)?.codigo }, { label: "Alumno", value: (r) => alum.get(r.alumno_id)?.nombre },
      { label: "Carrera", value: (r) => alum.get(r.alumno_id)?.nivel }, { label: "Ciclo", value: (r) => alum.get(r.alumno_id)?.grado },
      { label: "Estado", value: (r) => (esTardanza(r.hora, L) ? "Tardanza" : "Puntual") },
    ]));
    toast(`${rows.length} registros exportados`, "success");
  },
});
