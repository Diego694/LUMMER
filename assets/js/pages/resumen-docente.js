// @ts-check
// Resumen del docente: de un vistazo, por cada curso que tiene asignado, quiénes ya marcaron hoy, cuántas entregas esperan nota
// y cuál es el próximo vencimiento. Usa los mismos datos que «Pasar lista» y el aula; no requiere migración.
import { api } from "../api.js";
import { DB } from "../state.js";
import { bajaAsistenciaCurso, cursosParaPasarLista, resumenCursoDocente } from "../api-aula.js";
import { esAdmin } from "../permisos.js";
import { perteneceACurso } from "../stats.js";
import { emptyState, icon, kpi, pageHead, skeleton } from "../ui.js";
import { cicloCorto, esc, fmtDate, lastWeekdays, todayStr } from "../utils.js";
import { cursosActivos } from "./cursos.js";

let ver = 0;

/** @param {string} cursoId */
const manuales = (cursoId) => new Set((DB.curso_alumnos || []).filter((ca) => ca.curso_id === cursoId).map((ca) => ca.alumno_id));

async function cargar() {
  const asignados = esAdmin() ? [] : await api.aulaCursosDe(String(DB.userId || (await api.userId())));
  const cursos = cursosParaPasarLista(cursosActivos(), esAdmin(), asignados);
  const hoy = todayStr();
  const semana = lastWeekdays(5, hoy);
  return Promise.all(cursos.map(async (c) => {
    const alumnos = DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && perteneceACurso(a, c, manuales(String(c.id))));
    const cid = String(c.id);
    const [dias, acts, ents] = await Promise.all([
      Promise.all(semana.map((f) => api.asistenciasCursoPorFecha(DB.cid, cid, f).catch(() => []))),
      api.aulaActividades(cid).catch(() => []),
      api.aulaEntregasCurso(cid).catch(() => []),
    ]);
    const sets = dias.map((d) => new Set(d.map((m) => m.alumno_id)));
    return { c, r: resumenCursoDocente(alumnos, sets[sets.length - 1], acts, ents), baja: bajaAsistenciaCurso(alumnos, sets).slice(0, 5) };
  }));
}

export const resumenDocentePage = {
  id: "resumen-docente", title: "Mi resumen", icon: "dashboard", group: "Principal",
  /** @param {HTMLElement} root */
  async render(root) {
    const mia = ++ver;
    root.innerHTML = `${pageHead("Mi resumen", "Tus cursos de hoy: quiénes ya marcaron asistencia, quiénes faltan seguido y qué entregas esperan tu nota.")}<div id="rd-cuerpo">${skeleton(4)}</div>`;
    let filas;
    try { filas = await cargar(); }
    catch (/** @type {any} */ e) {
      if (mia === ver) /** @type {HTMLElement} */ (document.getElementById("rd-cuerpo")).innerHTML = emptyState("No se pudo cargar tu resumen", e.message || "Revisa tu conexión e inténtalo de nuevo.", "alert");
      return;
    }
    if (mia !== ver) return;
    const cuerpo = /** @type {HTMLElement} */ (document.getElementById("rd-cuerpo"));
    if (!filas.length) { cuerpo.innerHTML = `<div class="card">${emptyState("Aún no tienes cursos asignados", "El administrador te asigna cursos desde Gestión → Cursos.", "listCheck")}</div>`; return; }
    const tot = filas.reduce((s, { r }) => ({ est: s.est + r.total, pres: s.pres + r.presentes, cal: s.cal + r.porCalificar }), { est: 0, pres: 0, cal: 0 });
    cuerpo.innerHTML = `<div class="kpi-grid">
      ${kpi({ label: "Cursos", value: String(filas.length), ic: "listCheck" })}
      ${kpi({ label: "Presentes hoy", value: `${tot.pres}/${tot.est}`, ic: "check", tone: "green" })}
      ${kpi({ label: "Por calificar", value: String(tot.cal), ic: "clock", tone: tot.cal ? "amber" : "navy" })}</div>
      <div class="pl-cursos rd-cursos">${filas.map(({ c, r, baja }) => `<article class="card rd-curso">
        <span class="pl-curso-ciclo">${esc(c.grado ? cicloCorto(c.grado, c.nivel) : c.nivel)}</span><strong>${esc(c.nombre)}</strong>
        <div class="rd-barra" role="img" aria-label="${r.pct}% presentes hoy"><span style="width:${r.pct}%"></span></div>
        <small>${r.presentes} de ${r.total} presentes hoy (${r.pct}%) · faltan ${r.faltan}</small>
        <small>${r.porCalificar} por calificar · ${r.abiertas} ${r.abiertas === 1 ? "actividad abierta" : "actividades abiertas"}${r.proxima ? ` · vence «${esc(r.proxima.titulo)}» ${esc(fmtDate(r.proxima.fecha_limite))}` : ""}</small>
        ${baja.length ? `<div class="rd-baja"><strong>Baja asistencia esta semana</strong>${baja.map((b) => `<span>${esc(b.nombre)} · ${b.presentes}/${b.de}</span>`).join("")}</div>` : ""}
        <div class="rd-acc"><a class="btn btn-primary btn-sm" href="#pasar-lista">${icon("listCheck", 15)} Pasar lista</a><a class="btn btn-outline btn-sm" href="#aula">Aula</a></div></article>`).join("")}</div>`;
  },
};

