// Cursos y asistencia por curso/hora: además de la asistencia diaria, se puede pasar lista en cada curso.
import { api } from "../api.js";
import { cola } from "../cola.js";
import { CONFIG } from "../config.js";
import { DB, asegurarHoy, opcionesGrado, opcionesNivel } from "../state.js";
import { emitir, registrarEnvio } from "../sync.js";
import { badge, emptyState, formModal, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { perteneceACurso, esTardanza } from "../stats.js";
import { cicloCorto, downloadFile, esErrorRed, esc, etiquetaCiclo, initials, nowHHMM, todayStr, toCSV } from "../utils.js";
import { red } from "../sync.js";

registrarEnvio("curso", (rows) => api.registrarMasivoCurso(rows));

/* ----------------------- Registro por curso (usado desde el escáner) ----------------------- */
let cursoHoy = { cursoId: null, fecha: null, ids: new Set() };

export async function cargarCursoHoy(curso) {
  const fecha = todayStr();
  let ids = new Set();
  try { ids = new Set((await api.asistenciasCursoPorFecha(DB.cid, curso.id, fecha)).map((a) => a.alumno_id)); }
  catch (e) { if (!esErrorRed(e)) throw e; if (cursoHoy.cursoId === curso.id && cursoHoy.fecha === fecha) ids = new Set(cursoHoy.ids); }
  cola.pendientes().filter((x) => x.tipo === "curso" && x.row.curso_id === curso.id && x.row.fecha === fecha).forEach((x) => ids.add(x.row.alumno_id));
  cursoHoy = { cursoId: curso.id, fecha, ids };
}

/** Devuelve 'no_pertenece' | 'inactivo' | 'pendiente' | 'dup' | { hora, offline? }. */
export async function registrarEnCurso(alumno, curso, origen = "qr") {
  if (alumno.estado !== "ACTIVO") return "inactivo";
  if (alumno.aprobado === false) return "pendiente";
  if (!perteneceACurso(alumno, curso)) return "no_pertenece";
  if (cursoHoy.cursoId !== curso.id || cursoHoy.fecha !== todayStr()) await cargarCursoHoy(curso);
  if (cursoHoy.ids.has(alumno.id)) return "dup";
  const fecha = todayStr(), hora = nowHHMM();
  const row = { colegio_id: DB.cid, alumno_id: alumno.id, curso_id: curso.id, fecha, hora, registrado_por: DB.userId || (await api.userId()), origen };
  const aCola = () => { cola.agregar({ tipo: "curso", row, clave: `c|${alumno.id}|${curso.id}|${fecha}` }); cursoHoy.ids.add(alumno.id); emitir(); return { hora, offline: true }; };
  if (!red.online()) return aCola();
  try { await api.registrarAsistenciaCurso(row); }
  catch (e) {
    if (e.code === "duplicate") { cursoHoy.ids.add(alumno.id); return "dup"; }
    if (esErrorRed(e)) return aCola();
    throw e;
  }
  cursoHoy.ids.add(alumno.id);
  return { hora };
}

export const cursosActivos = () => DB.cursos.filter((c) => c.activo !== false);
export const etiquetaCurso = (c) => `${c.nombre} · ${c.grado ? cicloCorto(c.grado, c.nivel) : c.nivel}`;

/* ============================ Gestión de cursos ============================ */
export const cursosPage = {
  id: "cursos", title: "Cursos", icon: "book", group: "Gestión", soloAdmin: true,
  render(el) {
    el.innerHTML = `${pageHead("Cursos", "Cursos o asignaturas por carrera (y ciclo). Permiten pasar lista por curso además de la asistencia diaria.",
      `<button class="btn btn-primary" data-action="curso-new">${icon("plus", 16)} Agregar curso</button>`)}<div class="card flush" id="tbl"></div>`;
    el._repaint = () => {
      el.querySelector("#tbl").innerHTML = DB.cursos.length ? `<div class="table-wrap"><table><thead><tr><th>Curso</th><th>Carrera · Ciclo</th><th>Docente</th><th>Estado</th><th></th></tr></thead><tbody>
        ${DB.cursos.map((c) => `<tr><td><strong>${esc(c.nombre)}</strong></td><td>${esc(c.grado ? etiquetaCiclo(c.nivel, c.grado) : c.nivel + " · todos los ciclos")}</td><td>${esc(c.docente || "—")}</td>
          <td>${badge(c.activo === false ? "Inactivo" : "Activo", c.activo === false ? "neutral" : "green")}</td>
          <td class="t-right nowrap"><button class="icon-only" title="Editar" aria-label="Editar ${esc(c.nombre)}" data-action="curso-edit" data-id="${c.id}">${icon("edit", 16)}</button>
          <button class="icon-only danger" title="Eliminar" aria-label="Eliminar ${esc(c.nombre)}" data-action="curso-del" data-id="${c.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("Sin cursos", "Agrega los cursos para poder pasar lista por asignatura.", "book");
    };
    el._repaint();
  },
};

async function guardarCurso(v, id) {
  const { loadAll } = await import("../state.js");
  try { await api.save("cursos", { colegio_id: DB.cid, nombre: v.nombre, nivel: v.nivel, grado: v.grado || null, docente: v.docente, activo: v.activo === "SI" }, id); }
  catch (e) { throw new Error(e.code === "duplicate" ? "Ya existe un curso con ese nombre en esa carrera/ciclo." : e.message); }
  await loadAll();
  document.getElementById("page-root")._repaint?.();
}
function formCurso(c) {
  if (!DB.niveles.length) { toast("Primero crea una carrera.", "error"); return; }
  const nivel = c?.nivel || DB.niveles[0];
  formModal({
    title: c ? "Editar curso" : "Agregar curso",
    fields: [
      { name: "nombre", label: "Nombre del curso", required: true, value: c?.nombre, placeholder: "Ej: Matemática aplicada" },
      { name: "nivel", label: "Carrera", type: "select", half: true, options: opcionesNivel(), value: nivel, onChange: (v, ctl) => ctl.setOptions("grado", opcionesGrado(v, true), "") },
      { name: "grado", label: "Ciclo", type: "select", half: true, options: opcionesGrado(nivel, true), value: c?.grado || "" },
      { name: "docente", label: "Docente (opcional)", value: c?.docente },
      { name: "activo", label: "Estado", type: "pills", options: [{ value: "SI", label: "Activo" }, { value: "NO", label: "Inactivo" }], value: c?.activo === false ? "NO" : "SI" },
    ],
    onSubmit: async (v) => { await guardarCurso(v, c?.id); toast("Curso guardado", "success"); },
  });
}

/* ========================= Asistencia por curso ========================= */
let ac = { cursoId: "", fecha: "" };
export const asistCursoPage = {
  id: "asist-curso", title: "Asistencia por Curso", icon: "listCheck", group: "Consultas",
  async render(root) {
    ac.fecha = ac.fecha || todayStr();
    const cursos = cursosActivos();
    ac.cursoId = ac.cursoId || cursos[0]?.id || "";
    root.innerHTML = `${pageHead("Asistencia por Curso", "Quién asistió a cada curso en la fecha elegida. Para pasar lista, usa «Registro por QR» y elige el curso.",
      `<button class="btn btn-outline" data-action="ac-export">${icon("download", 16)} CSV</button>`)}
      ${cursos.length ? `<div class="toolbar"><select class="filter" id="ac-curso" aria-label="Curso">${cursos.map((c) => `<option value="${c.id}" ${c.id === ac.cursoId ? "selected" : ""}>${esc(etiquetaCurso(c))}</option>`).join("")}</select>
        <input class="filter" type="date" id="ac-fecha" max="${todayStr()}" value="${ac.fecha}" aria-label="Fecha"></div><div class="card flush" id="ac-tabla">${skeleton(4)}</div>`
        : `<div class="card">${emptyState("Sin cursos", "El administrador debe crear cursos en Gestión → Cursos.", "book")}</div>`}`;
    if (!cursos.length) return;
    root.querySelector("#ac-curso").addEventListener("change", (e) => { ac.cursoId = e.target.value; pintar(root); });
    root.querySelector("#ac-fecha").addEventListener("change", (e) => { ac.fecha = e.target.value || todayStr(); pintar(root); });
    await pintar(root);
  },
};
let filasAc = [];
async function pintar(root) {
  const box = root.querySelector("#ac-tabla");
  const curso = DB.cursos.find((c) => c.id === ac.cursoId);
  if (!curso) return;
  box.innerHTML = skeleton(4);
  try {
    const regs = await api.asistenciasCursoPorFecha(DB.cid, curso.id, ac.fecha);
    if (!box.isConnected) return;
    const por = new Map(regs.map((r) => [r.alumno_id, r]));
    filasAc = DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && perteneceACurso(a, curso)).sort((a, b) => a.nombre.localeCompare(b.nombre, "es")).map((a) => ({ a, reg: por.get(a.id) }));
    const pres = filasAc.filter((x) => x.reg).length;
    box.innerHTML = filasAc.length ? `<div class="card-head pad"><span class="muted">${pres} de ${filasAc.length} presentes</span></div><div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Ciclo</th><th>Estado</th><th>Hora</th></tr></thead><tbody>
      ${filasAc.map(({ a, reg }) => `<tr><td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td><td>${esc(cicloCorto(a.grado, a.nivel))}</td>
        <td>${!reg ? badge("Ausente", "red") : esTardanza(reg.hora, CONFIG.HORA_LIMITE, a.nivel) ? badge("Tardanza", "amber") : badge("Presente", "green")}</td><td class="mono">${reg ? esc(String(reg.hora).slice(0, 5)) : "—"}</td></tr>`).join("")}</tbody></table></div>`
      : emptyState("Sin alumnos", "No hay alumnos activos que pertenezcan a este curso.", "users");
  } catch (e) { box.innerHTML = emptyState("No se pudo cargar", e.message, "alert"); }
}

registerActions({
  "curso-new": () => formCurso(),
  "curso-edit": (el) => formCurso(DB.cursos.find((c) => c.id === el.dataset.id)),
  "curso-del": async (el) => {
    const c = DB.cursos.find((x) => x.id === el.dataset.id);
    const { confirmDialog } = await import("../ui.js");
    if (!(await confirmDialog({ title: "Eliminar curso", message: `¿Eliminar el curso <b>${esc(c.nombre)}</b>? También se borrará su historial de asistencia.` }))) return;
    try { await api.remove("cursos", c.id); const { loadAll } = await import("../state.js"); await loadAll(); document.getElementById("page-root")._repaint?.(); toast("Curso eliminado", "success"); }
    catch (e) { toast("No se pudo eliminar: " + e.message, "error"); }
  },
  "ac-export": async () => {
    const curso = DB.cursos.find((c) => c.id === ac.cursoId);
    if (!curso || !filasAc.length) { toast("No hay datos para exportar"); return; }
    await downloadFile(`asistencia_${curso.nombre.replace(/\s+/g, "-")}_${ac.fecha}.csv`, toCSV(filasAc, [
      { label: "Fecha", value: () => ac.fecha }, { label: "Curso", value: () => curso.nombre }, { label: "Código", value: (x) => x.a.codigo }, { label: "Alumno", value: (x) => x.a.nombre },
      { label: "Estado", value: (x) => (!x.reg ? "Ausente" : esTardanza(x.reg.hora, CONFIG.HORA_LIMITE, x.a.nivel) ? "Tardanza" : "Presente") }, { label: "Hora", value: (x) => (x.reg ? String(x.reg.hora).slice(0, 5) : "") }]));
  },
});
