// @ts-check
// Pasar lista por curso: el docente elige su curso (los que el administrador le asignó), y marca a cada alumno con un toque
// o escaneando su carnet. Opcionalmente marca a la vez el ingreso al instituto si el alumno aún no ingresó hoy.
import { api } from "../api.js";
import { cola } from "../cola.js";
import { CONFIG } from "../config.js";
import { DB } from "../state.js";
import { cursosParaPasarLista, resumenLista } from "../api-aula.js";
import { esAdmin } from "../permisos.js";
import { esTardanza, perteneceACurso } from "../stats.js";
import { badge, confirmDialog, emptyState, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { censurarNombre, cicloCorto, esc, initials, norm, todayStr } from "../utils.js";
import { cargarCursoHoy, cursosActivos, registrarEnCurso } from "./cursos.js";
import { registrarHoy, resolverAlumno } from "./registro.js";
import { abrirEstudiantesCurso } from "../estudiantes-curso.js";

/** @typedef {import('../tipos.d.ts').Curso} Curso */

/** @type {{ cursoId: string, filtro: "todos" | "faltan" | "presentes", q: string, tambienIngreso: boolean }} */
const st = { cursoId: "", filtro: "todos", q: "", tambienIngreso: true };
/** @type {Map<string, { hora: string }>} alumno_id → hora de su asistencia en este curso, hoy */
let marcados = new Map();
/** @type {any[]} */
let alumnos = [];
/** @type {Curso[]} */
let mios = [];
/** @type {MediaStream | null} */
let stream = null;
/** @type {number | null} */
let raf = null;
let ocupado = false;

const cursoActual = () => mios.find((c) => c.id === st.cursoId);

/** @param {string | undefined} [cursoId] */
const manualesDeCurso = (cursoId) => new Set(cursoId ? (DB.curso_alumnos || []).filter((ca) => ca.curso_id === cursoId).map((ca) => ca.alumno_id) : []);

/** Alumnos activos y aprobados del curso, por orden alfabético. @param {Curso} c */
const alumnosDe = (c) => DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && perteneceACurso(a, c, manualesDeCurso(c.id))).sort((x, y) => x.nombre.localeCompare(y.nombre, "es"));

export const pasarListaPage = {
  id: "pasar-lista", title: "Pasar lista", icon: "listCheck", group: "Registro",
  /** @param {HTMLElement} root */
  async render(root) {
    root.innerHTML = `${pageHead("Pasar lista", "Elige tu curso y marca a quienes están presentes en esta clase.")}<div id="pl-cuerpo">${skeleton(4)}</div>`;
    try {
      const asignados = esAdmin() ? [] : await api.aulaCursosDe(String(DB.userId || (await api.userId())));
      mios = cursosParaPasarLista(cursosActivos(), esAdmin(), asignados);
    } catch (/** @type {any} */ e) {
      cuerpo().innerHTML = emptyState("No se pudieron cargar tus cursos", e.message || "Revisa tu conexión e inténtalo de nuevo.", "alert");
      return;
    }
    if (st.cursoId && cursoActual()) await abrirCurso(st.cursoId); else lista();
  },
  onLeave() { apagarCamara(); },
};

const cuerpo = () => /** @type {HTMLElement} */ (document.getElementById("pl-cuerpo"));

/* ------------------------------ 1. Elegir curso ------------------------------ */
function lista() {
  apagarCamara(); st.cursoId = "";
  if (!mios.length) {
    cuerpo().innerHTML = `<div class="card">${emptyState("Aún no tienes cursos asignados", esAdmin() ? "Crea cursos en Gestión → Cursos y asigna a los docentes." : "Pide al administrador que te asigne tus cursos (Aula virtual → Docentes del curso).", "book")}</div>`;
    return;
  }
  const porCarrera = [...new Set(mios.map((c) => c.nivel))].sort((a, b) => a.localeCompare(b, "es"));
  cuerpo().innerHTML = porCarrera.map((carrera) => `<section class="pl-grupo"><h2 class="pl-grupo-t">${esc(carrera)}</h2><div class="pl-cursos">
    ${mios.filter((c) => c.nivel === carrera).sort((a, b) => String(a.grado || "").localeCompare(String(b.grado || ""), "es") || a.nombre.localeCompare(b.nombre, "es")).map((c) => `<div class="pl-curso-card" style="display:flex;flex-direction:column;gap:8px"><button type="button" class="pl-curso" style="flex:1;width:100%" data-action="pl-curso" data-id="${esc(String(c.id))}">
      <span class="pl-curso-ciclo">${esc(c.grado ? cicloCorto(c.grado, c.nivel) : "Todos los ciclos")}</span><strong>${esc(c.nombre)}</strong>
      <small>${alumnosDe(c).length} estudiantes</small><span class="pl-curso-go">Pasar lista ${icon("listCheck", 15)}</span></button>
      <button type="button" class="btn btn-outline btn-sm" data-action="pl-estudiantes" data-id="${esc(String(c.id))}" aria-label="Estudiantes de ${esc(c.nombre)}">${icon("users", 14)} Estudiantes</button></div>`).join("")}</div></section>`).join("");
}

/* ------------------------------ 2. Pantalla de la clase ------------------------------ */
/** @param {string} id */
async function abrirCurso(id) {
  const c = mios.find((x) => x.id === id);
  if (!c) return lista();
  apagarCamara(); st.cursoId = id; st.filtro = "todos"; st.q = "";
  cuerpo().innerHTML = skeleton(5);
  alumnos = alumnosDe(c);
  try {
    marcados = new Map((await api.asistenciasCursoPorFecha(String(DB.cid), String(c.id), todayStr())).map((r) => [r.alumno_id, { hora: String(r.hora).slice(0, 5) }]));
    cola.pendientes().filter((x) => x.tipo === "curso" && x.row.curso_id === c.id && x.row.fecha === todayStr()).forEach((x) => marcados.set(x.row.alumno_id, { hora: String(x.row.hora).slice(0, 5) }));
    await cargarCursoHoy(c);
  } catch (/** @type {any} */ e) {
    cuerpo().innerHTML = emptyState("No se pudo cargar la lista", e.message || "Inténtalo de nuevo.", "alert"); return;
  }
  pintarClase();
}

function pintarClase() {
  const c = cursoActual();
  if (!c) return lista();
  cuerpo().innerHTML = `<div class="pl-clase">
    <div class="pl-top"><button type="button" class="btn btn-outline btn-sm" data-action="pl-volver">${icon("book", 15)} Mis cursos</button>
      <div class="pl-titulo"><h2>${esc(c.nombre)}</h2><p class="muted">${esc(c.nivel)} · ${esc(c.grado ? cicloCorto(c.grado, c.nivel) : "todos los ciclos")} · ${esc(new Date().toLocaleDateString("es-PE", { weekday: "long", day: "numeric", month: "long" }))}</p></div>
      <button type="button" class="btn btn-outline btn-sm" style="margin-left:auto" data-action="pl-estudiantes" data-id="${esc(String(c.id))}">${icon("users", 15)} Estudiantes</button></div>
    <div class="pl-resumen" id="pl-resumen" role="status" aria-live="polite"></div>
    <div class="pl-acciones">
      <button type="button" class="btn btn-primary" data-action="pl-cam" id="pl-cam-btn">${icon("camera", 16)} Escanear carnets</button>
      <button type="button" class="btn btn-outline" data-action="pl-todos">${icon("check", 16)} Marcar a todos presentes</button>
      <label class="check-row pl-ingreso"><input type="checkbox" id="pl-ingreso" ${st.tambienIngreso ? "checked" : ""}> Marcar también su ingreso al instituto si aún no ingresó hoy</label>
    </div>
    <div class="pl-scan" id="pl-scan" hidden><div class="qr-frame"><video id="pl-video" playsinline muted></video><div class="qr-guide" aria-hidden="true"></div></div>
      <div id="pl-scan-res" class="scan-result" role="status" aria-live="polite" hidden></div></div>
    <form class="inline-form pl-manual" id="pl-manual"><label class="sr-only" for="pl-codigo">Código del alumno</label><input class="input" id="pl-codigo" placeholder="¿Sin cámara? Escribe el código del alumno" autocomplete="off"><button class="btn btn-teal" type="submit">Marcar</button></form>
    <div class="pl-filtros"><div class="pills" role="group" aria-label="Filtrar lista" id="pl-filtros"></div>
      <label class="sr-only" for="pl-buscar">Buscar estudiante</label><input class="input pl-buscar" id="pl-buscar" type="search" placeholder="Buscar estudiante" value="${esc(st.q)}" autocomplete="off"></div>
    <ul class="pl-lista" id="pl-lista"></ul></div>`;
  const $ = (/** @type {string} */ s) => /** @type {HTMLElement} */ (cuerpo().querySelector(s));
  /** @type {HTMLInputElement} */ ($("#pl-ingreso")).addEventListener("change", (e) => { st.tambienIngreso = /** @type {HTMLInputElement} */ (e.target).checked; });
  /** @type {HTMLInputElement} */ ($("#pl-buscar")).addEventListener("input", (e) => { st.q = /** @type {HTMLInputElement} */ (e.target).value; pintarFilas(); });
  $("#pl-manual").addEventListener("submit", async (e) => {
    e.preventDefault();
    const inp = /** @type {HTMLInputElement} */ ($("#pl-codigo")), code = inp.value.trim();
    if (code && (await procesar(code, "manual"))) inp.value = "";
  });
  pintarFilas();
}

function pintarFilas() {
  const lista = document.getElementById("pl-lista");
  const res = document.getElementById("pl-resumen");
  const filtros = document.getElementById("pl-filtros");
  if (!lista || !res || !filtros) return;
  const r = resumenLista(alumnos, marcados);
  res.innerHTML = `<div class="pl-cifras"><div><b>${r.presentes}</b><span>presentes</span></div><div><b>${r.faltan}</b><span>sin marcar</span></div><div><b>${r.total}</b><span>en el curso</span></div></div>
    <div class="pl-barra" role="progressbar" aria-valuemin="0" aria-valuemax="100" aria-valuenow="${r.pct}" aria-label="Asistencia de la clase"><span style="width:${r.pct}%"></span></div>`;
  filtros.innerHTML = [["todos", `Todos (${r.total})`], ["faltan", `Sin marcar (${r.faltan})`], ["presentes", `Presentes (${r.presentes})`]]
    .map(([v, t]) => `<button type="button" class="pill ${st.filtro === v ? "active" : ""}" aria-pressed="${st.filtro === v}" data-action="pl-filtro" data-filtro="${v}">${t}</button>`).join("");
  const q = norm(st.q);
  const filas = alumnos.filter((a) => (st.filtro === "todos" || (st.filtro === "presentes") === marcados.has(a.id)) && (!q || norm(a.nombre).includes(q) || norm(String(a.codigo || "")).includes(q)));
  lista.innerHTML = filas.length ? filas.map((a) => {
    const m = marcados.get(a.id);
    const estado = !m ? badge("Sin marcar", "neutral") : esTardanza(m.hora, CONFIG.HORA_LIMITE, a.nivel) ? badge(`Tarde · ${m.hora}`, "amber") : badge(`Presente · ${m.hora}`, "green");
    return `<li class="pl-fila ${m ? "marcado" : ""}"><span class="avatar" aria-hidden="true">${esc(initials(a.nombre))}</span><span class="pl-nombre"><strong>${esc(a.nombre)}</strong><small>${esc(String(a.codigo || ""))}</small></span>${estado}
      ${m ? `<span class="pl-ok" aria-hidden="true">${icon("check", 18)}</span>` : `<button type="button" class="btn btn-teal pl-marcar" data-action="pl-marcar" data-id="${esc(a.id)}" aria-label="Marcar presente a ${esc(a.nombre)}">Presente</button>`}</li>`;
  }).join("") : `<li>${emptyState(alumnos.length ? "Nada que mostrar" : "Sin estudiantes", alumnos.length ? "Cambia el filtro o la búsqueda." : "No hay estudiantes activos y aprobados en este curso.", "users")}</li>`;
}

/* ------------------------------ Marcar ------------------------------ */
/**
 * @param {any} a alumno
 * @param {string} origen
 * @returns {Promise<{ clase: "ok" | "warn" | "err", texto: string }>}
 */
async function marcar(a, origen) {
  const c = cursoActual();
  if (!c) return { clase: "err", texto: "Elige un curso." };
  const res = await registrarEnCurso(a, c, origen);
  if (res === "dup") return { clase: "warn", texto: "Ya estaba marcado en este curso" };
  if (res === "no_pertenece") return { clase: "err", texto: "No pertenece a este curso o ciclo" };
  if (res === "inactivo") return { clase: "err", texto: "Alumno inactivo" };
  if (res === "pendiente") return { clase: "err", texto: "Su registro aún no fue aprobado" };
  marcados.set(a.id, { hora: String(res.hora).slice(0, 5) });
  let extra = "";
  if (st.tambienIngreso) {
    try { const ing = await registrarHoy(a, origen); if (typeof ing === "object") extra = " · ingreso al instituto marcado"; } catch { /* el ingreso no bloquea la asistencia del curso */ }
  }
  return { clase: "ok", texto: `Presente a las ${String(res.hora).slice(0, 5)}${extra}${"offline" in res && res.offline ? " (se enviará al volver la red)" : ""}` };
}

/**
 * @param {string} texto lo leído (QR, NFC o código)
 * @param {"qr" | "manual"} origen
 * @returns {Promise<boolean>}
 */
async function procesar(texto, origen) {
  if (ocupado) return false;
  ocupado = true;
  try {
    const r = await resolverAlumno(texto, origen);
    if (r.rechazo) { mostrarResultado(null, { clase: "err", texto: r.rechazo.mensaje }); return false; }
    const m = await marcar(r.alumno, origen);
    mostrarResultado(r.alumno, m); pintarFilas();
    return m.clase !== "err";
  } catch (/** @type {any} */ e) { mostrarResultado(null, { clase: "err", texto: e.message || "No se pudo registrar" }); return false; }
  finally { setTimeout(() => { ocupado = false; }, 700); }
}

/** @param {any} a @param {{ clase: string, texto: string }} m */
function mostrarResultado(a, m) {
  const box = document.getElementById("pl-scan-res");
  if (!box) { toast(m.texto, m.clase === "ok" ? "success" : "error"); return; }
  box.hidden = false; box.className = `scan-result scan-${m.clase}`;
  box.innerHTML = a ? `<span class="avatar">${esc(initials(a.nombre))}</span><div><strong>${esc(censurarNombre(a))}</strong><em>${esc(m.texto)}</em></div>` : `<div><em>${esc(m.texto)}</em></div>`;
}

/* ------------------------------ Cámara ------------------------------ */
async function encenderCamara() {
  const marco = document.getElementById("pl-scan"), video = /** @type {HTMLVideoElement | null} */ (document.getElementById("pl-video"));
  if (!marco || !video) return;
  if (!navigator.mediaDevices?.getUserMedia) { toast("Este navegador no permite usar la cámara (requiere HTTPS). Escribe el código del alumno.", "error"); return; }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } });
  } catch { toast("No se pudo abrir la cámara. Permite el acceso o escribe el código del alumno.", "error"); return; }
  marco.hidden = false; video.srcObject = stream; await video.play();
  document.getElementById("pl-cam-btn")?.setAttribute("aria-pressed", "true");
  const canvas = document.createElement("canvas"), ctx = /** @type {CanvasRenderingContext2D} */ (canvas.getContext("2d", { willReadFrequently: true }));
  let ultimo = "", t0 = 0;
  const bucle = () => {
    if (!stream) return;
    if (video.readyState === video.HAVE_ENOUGH_DATA) {
      canvas.width = video.videoWidth; canvas.height = video.videoHeight; ctx.drawImage(video, 0, 0);
      const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = /** @type {any} */ (window).jsQR?.(img.data, img.width, img.height);
      const ms = Date.now();
      if (code?.data && (code.data !== ultimo || ms - t0 > 4000) && ms - t0 > 1200) { ultimo = code.data; t0 = ms; procesar(code.data, "qr"); }
    }
    raf = requestAnimationFrame(bucle);
  };
  bucle();
}
function apagarCamara() {
  if (raf) cancelAnimationFrame(raf); raf = null;
  stream?.getTracks().forEach((t) => t.stop()); stream = null;
  const marco = document.getElementById("pl-scan"); if (marco) marco.hidden = true;
  document.getElementById("pl-cam-btn")?.setAttribute("aria-pressed", "false");
}

registerActions({
  "pl-curso": (/** @type {HTMLElement} */ b) => abrirCurso(b.dataset.id || ""),
  "pl-volver": () => lista(),
  "pl-estudiantes": (/** @type {HTMLElement} */ el) => {
    const c = mios.find((x) => x.id === el.dataset.id) || cursoActual();
    if (!c) return;
    abrirEstudiantesCurso(c, () => {
      alumnos = alumnosDe(c);
      if (st.cursoId) pintarFilas();
      else lista();
    });
  },
  "pl-filtro": (/** @type {HTMLElement} */ b) => { st.filtro = /** @type {any} */ (b.dataset.filtro || "todos"); pintarFilas(); },
  "pl-cam": () => (stream ? apagarCamara() : encenderCamara()),
  "pl-marcar": async (/** @type {HTMLElement} */ b) => {
    const a = alumnos.find((x) => x.id === b.dataset.id);
    if (!a) return;
    /** @type {HTMLButtonElement} */ (b).disabled = true;
    try { const m = await marcar(a, "manual"); if (m.clase !== "ok") toast(m.texto, m.clase === "warn" ? "info" : "error"); }
    catch (/** @type {any} */ e) { toast("No se pudo marcar: " + e.message, "error"); }
    pintarFilas();
  },
  "pl-todos": async () => {
    const pendientes = alumnos.filter((a) => !marcados.has(a.id));
    if (!pendientes.length) { toast("Todos ya están marcados.", "info"); return; }
    if (!(await confirmDialog({ title: "Marcar a todos presentes", message: `Se marcará como presentes a los <b>${pendientes.length}</b> estudiantes que aún no están marcados. Una marca no se puede deshacer desde aquí (solo el administrador puede corregirla).`, confirmLabel: "Marcar a todos", danger: false }))) return;
    let n = 0, fallos = 0;
    for (const a of pendientes) { try { if ((await marcar(a, "manual")).clase === "ok") n++; } catch { fallos++; } }
    pintarFilas(); toast(`${n} marcados${fallos ? ` · ${fallos} con error` : ""}`, fallos ? "error" : "success");
  },
});
