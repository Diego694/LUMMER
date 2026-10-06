// @ts-check
// Mantenimiento (CRUD): carreras, ciclos y salones, alumnos (con importación CSV), docentes y comunicados.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, alumnoPorId, loadAll, opcionesGrado, opcionesNivel } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, openModal, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { enlaceWhatsApp, normalizarFilasImport } from "../stats.js";
import { CICLOS, debounce, downloadFile, esc, etiquetaCiclo, fmtDate, initials, nombreCiclo, norm, parsearCiclo, todayStr } from "../utils.js";

const root = () => /** @type {HTMLElement} */ (document.getElementById("page-root"));
const repaint = () => /** @type {any} */ (root())._repaint?.();
const options = (/** @type {any} */ list, /** @type {any} */ sel) => list.map((/** @type {any} */ o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");
const msgError = (/** @type {any} */ e, /** @type {any} */ dup) => (e.code === "duplicate" ? dup : e.message);

async function guardar(/** @type {any} */ tabla, /** @type {any} */ data, /** @type {any} */ id = undefined) {
  await api.save(tabla, { colegio_id: DB.cid, ...data }, id);
  await loadAll();
}
async function eliminar(/** @type {any} */ tabla, /** @type {any} */ id, /** @type {any} */ mensaje, /** @type {any} */ exito) {
  if (!(await confirmDialog({ title: "Confirmar eliminación", message: mensaje }))) return;
  try { await api.remove(tabla, id); await loadAll(); repaint(); toast(exito, "success"); }
  catch (/** @type {any} */ e) { toast("No se pudo eliminar: " + e.message, "error"); }
}

/* ============================ Carreras ============================ */
export const nivelesPage = {
  id: "niveles", title: "Carreras", icon: "layers", group: "Gestión",
  render(/** @type {any} */ el) {
    el.innerHTML = `${pageHead("Carreras", "Carreras o programas de estudio del instituto. Cada una tiene sus propios ciclos y salones.", `<button class="btn btn-primary" data-action="nivel-new">${icon("plus", 16)} Agregar carrera</button>`)}<div class="card flush" id="tbl"></div>`;
    el._repaint = () => {
      /** @type {HTMLElement} */ (el.querySelector("#tbl")).innerHTML = DB.niveles.length ? `<div class="table-wrap"><table><thead><tr><th>Carrera</th><th>Ciclos</th><th>Alumnos</th><th></th></tr></thead><tbody>
        ${DB.nivelesRaw.map((n) => `<tr><td><strong>${esc(n.nombre)}</strong></td><td>${DB.grados.filter((g) => g.nivel === n.nombre).length}</td><td>${DB.alumnos.filter((a) => a.nivel === n.nombre).length}</td>
          <td class="t-right nowrap"><button class="btn btn-outline btn-sm" data-action="ciclos-new" data-carrera="${esc(n.nombre)}">${icon("plus", 14)} Ciclos</button>
          <button class="btn btn-ghost-danger btn-sm" data-action="nivel-del" data-id="${n.id}">${icon("trash", 14)} Eliminar</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("Sin carreras", "Crea la primera carrera (por ejemplo MECANICA ELECTRICA) o usa «Crear ciclos» en Ciclos y salones.", "layers");
    };
    el._repaint();
  },
};

/* ======================== Ciclos y salones ======================== */
const NUEVA = "__nueva__";
const indiceCiclo = (/** @type {any} */ g) => { const i = CICLOS.indexOf(/** @type {any} */ (parsearCiclo(g.nombre).ciclo)); return i < 0 ? 99 : i; };
const porCiclo = (/** @type {any} */ a, /** @type {any} */ b) => indiceCiclo(a) - indiceCiclo(b) || parsearCiclo(a.nombre).seccion.localeCompare(parsearCiclo(b.nombre).seccion, "es") || a.nombre.localeCompare(b.nombre, "es", { numeric: true });

export const gradosPage = {
  id: "grados", title: "Ciclos y salones", icon: "book", group: "Gestión",
  render(/** @type {any} */ el) {
    el.innerHTML = `${pageHead("Ciclos y salones", "Cada carrera tiene sus ciclos (del I al VI) y, si hace falta, sus salones. Cada uno es independiente: sus propios alumnos, asistencia y reportes.",
      `<button class="btn btn-primary" data-action="ciclos-new">${icon("plus", 16)} Crear ciclos</button>`)}<div id="carreras-lista"></div>`;
    el._repaint = () => {
      const carreras = [...new Set([...DB.niveles, ...DB.grados.map((g) => g.nivel)])].sort((a, b) => a.localeCompare(b, "es"));
      /** @type {HTMLElement} */ (el.querySelector("#carreras-lista")).innerHTML = carreras.length ? carreras.map((c) => {
        const lista = DB.grados.filter((g) => g.nivel === c).sort(porCiclo);
        const alumnos = DB.alumnos.filter((a) => a.nivel === c).length;
        return `<section class="card flush carrera-card">
          <header class="card-head"><div><h3>${esc(c)}</h3><small class="muted">${lista.length} ciclo(s)/salón(es) · ${alumnos} alumno(s)</small></div>
            <button class="btn btn-outline btn-sm" data-action="ciclos-new" data-carrera="${esc(c)}">${icon("plus", 14)} Agregar ciclos</button></header>
          ${lista.length ? `<div class="table-wrap"><table><thead><tr><th>Ciclo</th><th>Salón</th><th>Alumnos</th><th></th></tr></thead><tbody>
            ${lista.map((g) => { const p = parsearCiclo(g.nombre);
              return `<tr><td>${p.ciclo ? `<span class="ciclo-badge">${esc(p.ciclo)}</span> <span class="muted">CICLO</span>` : `<strong>${esc(g.nombre)}</strong>`}</td>
                <td>${p.seccion ? esc(p.seccion) : '<span class="muted">—</span>'}</td>
                <td>${DB.alumnos.filter((a) => a.nivel === g.nivel && a.grado === g.nombre).length}</td>
                <td class="t-right"><button class="btn btn-ghost-danger btn-sm" data-action="grado-del" data-id="${g.id}">${icon("trash", 14)} Eliminar</button></td></tr>`; }).join("")}
          </tbody></table></div>` : `<p class="muted pad">Aún no tiene ciclos. Usa «Agregar ciclos».</p>`}
        </section>`; }).join("")
        : `<div class="card">${emptyState("Sin carreras ni ciclos", "Usa «Crear ciclos» para empezar: elige la carrera y marca los ciclos del I al VI.", "book")}</div>`;
    };
    el._repaint();
  },
};

/** Crea ciclos (I–VI) y, opcionalmente, salones de una carrera. Nombre fijo: "CARRERA · IV CICLO [· SECCIÓN A]". */
function crearCiclos(carreraInicial = "") {
  const hayCarreras = DB.niveles.length > 0;
  const ciclosSel = new Set(), seccionesSel = new Set();
  const m = openModal({
    title: "Crear ciclos y salones", wide: true,
    body: `<div class="form-grid">
        ${hayCarreras ? `<div class="field half"><label for="cc-sel">Carrera</label><select id="cc-sel">${DB.niveles.map((n) => `<option value="${esc(n)}">${esc(n)}</option>`).join("")}<option value="${NUEVA}">➕ Nueva carrera…</option></select></div>` : ""}
        <div class="field ${hayCarreras ? "half" : ""}" id="cc-nueva-box" ${hayCarreras ? "hidden" : ""}><label for="cc-nueva">${hayCarreras ? "Nombre de la nueva carrera" : "Carrera"}</label><input id="cc-nueva" placeholder="Ej: APSTI" autocomplete="off"></div>
        <div class="field"><label>Ciclos <span class="req">*</span></label>
          <div class="pill-select" id="cc-ciclos">${CICLOS.map((c) => `<button type="button" class="pill pill-roman" aria-pressed="false" data-c="${c}">${c}</button>`).join("")}</div>
          <div class="chips"><button type="button" class="link-btn" data-rapido="todos">Todos (I–VI)</button><button type="button" class="link-btn" data-rapido="impares">I, III, V</button><button type="button" class="link-btn" data-rapido="pares">II, IV, VI</button><button type="button" class="link-btn" data-rapido="ninguno">Limpiar</button></div></div>
        <div class="field"><label>Salones / secciones <span class="muted">(opcional)</span></label>
          <div class="pill-select" id="cc-secs">${["A", "B", "C", "D", "E"].map((s) => `<button type="button" class="pill pill-roman" aria-pressed="false" data-s="${s}">${s}</button>`).join("")}</div>
          <p class="muted" style="margin:6px 0 0">Déjalo vacío si cada ciclo tiene un solo salón.</p></div>
      </div>
      <div class="cc-prev" id="cc-prev" aria-live="polite"></div>
      <p class="err-msg" id="cc-err" role="alert" hidden></p>`,
    footer: `<button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-primary" id="cc-go" disabled>Crear</button>`,
  });
  const q = (/** @type {any} */ s) => m.el.querySelector(s);
  /** @type {HTMLElement} */ (m.el.querySelector("[data-close2]")).addEventListener("click", m.close);
  const sel = q("#cc-sel");
  if (sel && carreraInicial) sel.value = carreraInicial;

  const carrera = () => (sel && sel.value !== NUEVA ? sel.value : q("#cc-nueva").value.trim().replace(/\s+/g, " ").toUpperCase());
  const nombres = () => {
    const c = carrera(); if (!c) return [];
    const secs = [...seccionesSel].sort();
    return CICLOS.filter((ci) => ciclosSel.has(ci)).flatMap((ci) => (secs.length ? secs.map((s) => nombreCiclo(c, ci, s)) : [nombreCiclo(c, ci)]));
  };
  const pintarPills = () => {
    m.el.querySelectorAll("[data-c]").forEach((/** @type {any} */ b) => { const on = ciclosSel.has(b.dataset.c); b.classList.toggle("active", on); b.setAttribute("aria-pressed", String(on)); });
    m.el.querySelectorAll("[data-s]").forEach((/** @type {any} */ b) => { const on = seccionesSel.has(b.dataset.s); b.classList.toggle("active", on); b.setAttribute("aria-pressed", String(on)); });
  };
  const actualizar = () => {
    if (sel) q("#cc-nueva-box").hidden = sel.value !== NUEVA;
    pintarPills();
    const c = carrera(), lista = nombres();
    const existen = new Set(DB.grados.filter((g) => norm(g.nivel) === norm(c)).map((g) => norm(g.nombre)));
    const nuevos = lista.filter((n) => !existen.has(norm(n)));
    q("#cc-prev").innerHTML = lista.length
      ? `<strong>${nuevos.length}</strong> por crear${lista.length > nuevos.length ? ` · ${lista.length - nuevos.length} ya existen y se omiten` : ""}:
         <div class="chips">${lista.map((n) => `<span class="badge ${existen.has(norm(n)) ? "badge-neutral" : "badge-green"}">${esc(n)}</span>`).join("")}</div>`
      : `<span class="muted">${c ? "Marca los ciclos que quieres crear." : "Elige o escribe la carrera y marca los ciclos."}</span>`;
    q("#cc-go").disabled = nuevos.length === 0;
  };
  m.el.addEventListener("input", actualizar);
  m.el.addEventListener("change", actualizar);
  m.el.addEventListener("click", (e) => {
    const t = /** @type {any} */ (e.target), c = t.closest("[data-c]"), s = t.closest("[data-s]"), r = t.closest("[data-rapido]");
    if (c) { ciclosSel.has(c.dataset.c) ? ciclosSel.delete(c.dataset.c) : ciclosSel.add(c.dataset.c); actualizar(); }
    if (s) { seccionesSel.has(s.dataset.s) ? seccionesSel.delete(s.dataset.s) : seccionesSel.add(s.dataset.s); actualizar(); }
    if (r) {
      ciclosSel.clear();
      CICLOS.forEach((ci, i) => { if (r.dataset.rapido === "todos" || (r.dataset.rapido === "impares" && i % 2 === 0) || (r.dataset.rapido === "pares" && i % 2 === 1)) ciclosSel.add(ci); });
      actualizar();
    }
  });
  q("#cc-go").addEventListener("click", async (/** @type {any} */ e) => {
    const c = carrera(), lista = nombres();
    const existen = new Set(DB.grados.filter((g) => norm(g.nivel) === norm(c)).map((g) => norm(g.nombre)));
    const nuevos = lista.filter((n) => !existen.has(norm(n)));
    e.target.disabled = true; e.target.textContent = "Creando…";
    try {
      const carreraExistente = DB.niveles.find((n) => norm(n) === norm(c));
      const nombreCarrera = carreraExistente || c;
      if (!carreraExistente) await api.save("niveles", { colegio_id: DB.cid, nombre: nombreCarrera });
      for (const nombre of nuevos) await api.save("grados", { colegio_id: DB.cid, nivel: nombreCarrera, nombre });
      await loadAll(); m.close(); repaint();
      toast(`${nuevos.length} ciclo(s) creados en ${nombreCarrera}`, "success");
    } catch (/** @type {any} */ ex) {
      await loadAll();  // refleja lo que sí alcanzó a crearse
      const er = q("#cc-err"); er.textContent = "No se pudo crear todo: " + ex.message; er.hidden = false;
      e.target.textContent = "Crear"; actualizar(); repaint();
    }
  });
  actualizar();
}

/* ============================ Alumnos ============================ */
let al = { q: "", nivel: "", grado: "", est: "", page: 1 };

export const alumnosPage = {
  id: "alumnos", title: "Alumnos", icon: "cap", group: "Gestión",
  render(/** @type {any} */ el) {
    try { if (sessionStorage.getItem("ra-alumnos-filtro") === "pend") { al.est = "pend"; al.page = 1; sessionStorage.removeItem("ra-alumnos-filtro"); } } catch { /* sin storage */ }
    el.innerHTML = `${pageHead("Alumnos", "Padrón de alumnos con su código único de acceso.",
      `<button class="btn btn-outline" data-action="al-codigo">${icon("qr", 16)} Código de registro</button><button class="btn btn-outline" data-action="al-import">${icon("upload", 16)} Importar CSV</button><button class="btn btn-primary" data-action="al-new">${icon("plus", 16)} Agregar</button>`)}
      <div class="toolbar"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="al-q" placeholder="Buscar por nombre, código, apoderado…" value="${esc(al.q)}" aria-label="Buscar"></div>
        <select class="filter" id="al-nivel" aria-label="Carrera">${options(opcionesNivel(true), al.nivel)}</select>
        <select class="filter" id="al-grado" aria-label="Ciclo">${options(opcionesGrado(al.nivel, true), al.grado)}</select>
        <select class="filter" id="al-est" aria-label="Estado de registro"><option value="">Todos</option><option value="pend">Pendientes de aprobación (${DB.alumnos.filter((a) => a.aprobado === false).length})</option></select></div>
      <div class="card flush" id="tbl"></div><div class="pager" id="pager"></div>`;
    /** @type {HTMLElement} */ (el.querySelector("#al-q")).addEventListener("input", debounce((e) => { al.q = e.target.value; al.page = 1; el._repaint(); }, 150));
    /** @type {HTMLElement} */ (el.querySelector("#al-nivel")).addEventListener("change", (/** @type {any} */ e) => { al.nivel = e.target.value; al.grado = ""; al.page = 1; /** @type {HTMLElement} */ (el.querySelector("#al-grado")).innerHTML = options(opcionesGrado(al.nivel, true), ""); el._repaint(); });
    /** @type {HTMLElement} */ (el.querySelector("#al-grado")).addEventListener("change", (/** @type {any} */ e) => { al.grado = e.target.value; al.page = 1; el._repaint(); });
    /** @type {HTMLInputElement} */ (el.querySelector("#al-est")).value = al.est;
    /** @type {HTMLElement} */ (el.querySelector("#al-est")).addEventListener("change", (/** @type {any} */ e) => { al.est = e.target.value; al.page = 1; el._repaint(); });
    el._repaint = () => {
      const q = norm(al.q);
      const l = DB.alumnos.filter((a) => (!q || norm([a.nombre, a.codigo, a.apoderado, a.nivel, a.grado].join(" ")).includes(q)) && (!al.nivel || a.nivel === al.nivel) && (!al.grado || a.grado === al.grado) && (al.est !== "pend" || a.aprobado === false));
      const tot = Math.max(1, Math.ceil(l.length / CONFIG.ALUMNOS_POR_PAGINA));
      al.page = Math.min(al.page, tot);
      const pag = l.slice((al.page - 1) * CONFIG.ALUMNOS_POR_PAGINA, al.page * CONFIG.ALUMNOS_POR_PAGINA);
      /** @type {HTMLElement} */ (el.querySelector("#tbl")).innerHTML = pag.length ? `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Código</th><th>Carrera · Ciclo</th><th>Apoderado</th><th>Estado</th><th></th></tr></thead><tbody>
        ${pag.map((a) => `<tr><td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td>
          <td class="mono">${esc(a.codigo)}</td><td>${esc(etiquetaCiclo(a.nivel, a.grado))}</td><td>${a.apoderado ? esc(a.apoderado) : '<span class="muted">—</span>'}</td>
          <td>${a.aprobado === false ? badge("Pendiente", "amber") : badge(a.estado === "ACTIVO" ? "Activo" : "Inactivo", a.estado === "ACTIVO" ? "green" : "neutral")}</td>
          <td class="t-right nowrap">
            ${a.aprobado === false ? `<button class="btn btn-teal btn-sm" data-action="al-revisar" data-id="${a.id}">${icon("userCheck", 14)} Revisar</button>` : ""}
            <button class="icon-only" title="Código de apoderado" aria-label="Código de apoderado de ${esc(a.nombre)}" data-action="al-apod" data-id="${a.id}">${icon("users", 16)}</button>
            <button class="icon-only" title="Editar" aria-label="Editar ${esc(a.nombre)}" data-action="al-edit" data-id="${a.id}">${icon("edit", 16)}</button>
            <button class="icon-only" title="Carnet" aria-label="Carnet de ${esc(a.nombre)}" data-action="al-carnet" data-id="${a.id}">${icon("idCard", 16)}</button>
            <button class="icon-only" title="Historial" aria-label="Historial de ${esc(a.nombre)}" data-action="al-hist" data-id="${a.id}">${icon("history", 16)}</button>
            <button class="icon-only danger" title="Eliminar" aria-label="Eliminar ${esc(a.nombre)}" data-action="al-del" data-id="${a.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("No se encontraron alumnos", "Prueba con otro criterio o agrega un nuevo alumno.", "search");
      /** @type {HTMLElement} */ (el.querySelector("#pager")).innerHTML = tot > 1 ? `
        <div class="pager-group" role="navigation" aria-label="Paginación de alumnos">
          <button class="btn btn-outline btn-sm" data-action="al-page" data-d="-1" ${al.page <= 1 ? "disabled" : ""} aria-label="Página anterior">Anterior</button>
          <span class="pager-info muted">Página ${al.page} de ${tot} · ${l.length} alumnos</span>
          <button class="btn btn-outline btn-sm" data-action="al-page" data-d="1" ${al.page >= tot ? "disabled" : ""} aria-label="Página siguiente">Siguiente</button>
        </div>` : `<span class="muted">${l.length} alumno(s)</span>`;
    };
    el._repaint();
  },
};

function alumnoForm(/** @type {any} */ a = undefined) {
  if (!DB.niveles.length) { toast("Primero crea al menos una carrera y un ciclo.", "error"); location.hash = "#/niveles"; return; }
  const nivel = a?.nivel || DB.niveles[0];
  const sig = DB.alumnos.reduce((m, x) => Math.max(m, parseInt(x.codigo.replace(/\D/g, ""), 10) || 0), 1000) + 1;
  formModal({
    title: a ? "Editar alumno" : "Agregar alumno",
    fields: [
      { name: "nombre", label: "Nombre completo", required: true, value: a?.nombre },
      { name: "codigo", label: "Código único de acceso", required: true, value: a?.codigo ?? `a${sig}` },
      { name: "nivel", label: "Carrera", type: "select", half: true, options: opcionesNivel(), value: nivel, onChange: (v, c) => c.setOptions("grado", opcionesGrado(v)) },
      { name: "grado", label: "Ciclo / salón", type: "select", half: true, options: opcionesGrado(nivel), value: a?.grado },
      { name: "apoderado", label: "Apoderado", value: a?.apoderado },
      { name: "estado", label: "Estado", type: "pills", options: [{ value: "ACTIVO", label: "Activo" }, { value: "INACTIVO", label: "Inactivo" }], value: a?.estado || "ACTIVO" },
    ],
    onSubmit: async (v) => {
      // Si se corrige el nombre de un alumno auto‑registrado, se limpian nombres/apellidos para que la censura use el nombre nuevo.
      if (a && "nombres" in a && v.nombre !== a.nombre) { v.nombres = /** @type {any} */ (null); v.apellidos = /** @type {any} */ (null); }
      try { await guardar("alumnos", v, a?.id); } catch (/** @type {any} */ e) { throw new Error(msgError(e, "Ese código ya está en uso.")); }
      repaint(); toast("Alumno guardado correctamente", "success");
    },
  });
}

/** Revisión de un estudiante auto‑registrado: ver su foto y datos, aprobar o rechazar. */
export async function revisarEstudiante(/** @type {any} */ a, alTerminar = () => {}) {
  const m = openModal({
    title: "Revisar registro de estudiante",
    body: `<div class="revisar"><div class="alert-photo big" id="rv-foto"><span>${esc(initials(a.nombre))}</span></div>
      <dl class="datos"><dt>Nombre</dt><dd>${esc(a.nombre)}</dd><dt>Carrera · Ciclo</dt><dd>${esc(etiquetaCiclo(a.nivel, a.grado))}</dd>
      ${a.dni ? `<dt>DNI</dt><dd>${esc(a.dni)}</dd>` : ""}${a.apoderado ? `<dt>Apoderado</dt><dd>${esc(a.apoderado)}</dd>` : ""}
      <dt>Código QR</dt><dd class="mono">${esc(a.codigo)}</dd>${a.registrado_en ? `<dt>Registrado</dt><dd>${esc(new Date(a.registrado_en).toLocaleString("es-PE"))}</dd>` : ""}</dl></div>
      <p class="muted">Al aprobar, su QR podrá registrar asistencia. Al rechazar, se elimina el registro.</p>`,
    footer: `<button class="btn btn-ghost-danger" id="rv-no">Rechazar</button><button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-teal" id="rv-ok">${icon("check", 16)} Aprobar</button>`,
  });
  /** @type {HTMLElement} */ (m.el.querySelector("[data-close2]")).addEventListener("click", m.close);
  api.fotoUrl(a).then((url) => {
    if (!url || !m.el.isConnected) return;
    const img = new Image(); img.alt = "Foto del estudiante";
    img.onload = () => { const b = /** @type {HTMLElement} */ (m.el.querySelector("#rv-foto")); if (b) { b.innerHTML = ""; b.appendChild(img); } };
    img.src = url;
  }).catch(() => {});
  /** @type {HTMLElement} */ (m.el.querySelector("#rv-ok")).addEventListener("click", async () => {
    try { await guardar("alumnos", { aprobado: true }, a.id); m.close(); repaint(); alTerminar(); toast("Estudiante aprobado: su QR ya registra asistencia", "success"); }
    catch (/** @type {any} */ e) { toast("No se pudo aprobar: " + e.message, "error"); }
  });
  /** @type {HTMLElement} */ (m.el.querySelector("#rv-no")).addEventListener("click", async () => {
    m.close();
    await eliminar("alumnos", a.id, `¿Rechazar y eliminar el registro de <b>${esc(a.nombre)}</b>?`, "Registro rechazado");
    alTerminar();
  });
}

function importarCSV() {
  /** @type {any} */
  let filas = [];
  const m = openModal({
    title: "Importar alumnos desde CSV", wide: true,
    body: `<p class="muted">Columnas (primera fila): <b>nombre, codigo, carrera, ciclo, apoderado, estado</b> (también valen <i>nivel</i> y <i>grado</i>). Solo <b>nombre</b> y <b>codigo</b> son obligatorias; si falta <b>estado</b> se asigna ACTIVO. Si el código ya existe, el alumno se actualiza.</p>
      <button class="btn btn-outline btn-sm" id="imp-tpl">${icon("download", 14)} Descargar plantilla</button>
      <div class="field" style="margin-top:14px"><label for="imp-file">Archivo CSV</label><input type="file" id="imp-file" accept=".csv,text/csv"></div><div id="imp-prev"></div>`,
    footer: `<button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-primary" id="imp-go" hidden>Importar alumnos</button>`,
  });
  const $ = (/** @type {any} */ s) => m.el.querySelector(s);
  $("[data-close2]").addEventListener("click", m.close);
  $("#imp-tpl").addEventListener("click", () => downloadFile("plantilla-alumnos.csv", "﻿nombre,codigo,carrera,ciclo,apoderado,estado\r\nJuan Perez Rios,a2001,MECANICA ELECTRICA,MECANICA ELECTRICA I,Maria Rios,ACTIVO\r\nAna Torres Vega,a2002,APSTI,APSTI III,,ACTIVO\r\n"));
  $("#imp-file").addEventListener("change", (/** @type {any} */ e) => {
    const f = e.target.files[0]; if (!f) return;
    Papa.parse(f, { header: true, skipEmptyLines: true, complete: (/** @type {any} */ res) => {
      const cols = (res.meta.fields || []).map((/** @type {any} */ c) => c.trim().toLowerCase());
      if (!cols.includes("nombre") || !cols.includes("codigo")) { $("#imp-prev").innerHTML = `<p class="err-msg">El archivo debe tener al menos las columnas "nombre" y "codigo".</p>`; $("#imp-go").hidden = true; return; }
      const r = normalizarFilasImport(res.data, DB.niveles, DB.grados);
      filas = r.validas;
      const avisos = filas.filter((/** @type {any} */ x) => x.aviso.length);
      $("#imp-prev").innerHTML = `<p class="ok-msg">${filas.length} alumno(s) listos para importar.</p>
        ${r.errores.length ? `<p class="err-msg">${r.errores.length} fila(s) omitidas: ${r.errores.slice(0, 5).map((x) => `línea ${x.linea} (${esc(x.motivo)})`).join("; ")}${r.errores.length > 5 ? "…" : ""}</p>` : ""}
        ${avisos.length ? `<p class="warn-msg">${avisos.length} fila(s) con carrera/ciclo inexistente; se importarán igualmente.</p>` : ""}
        <div class="table-wrap" style="max-height:220px"><table><thead><tr><th>Nombre</th><th>Código</th><th>Carrera</th><th>Ciclo</th></tr></thead><tbody>
        ${filas.slice(0, 50).map((/** @type {any} */ x) => `<tr><td>${esc(x.nombre)}</td><td class="mono">${esc(x.codigo)}</td><td>${esc(x.nivel)}</td><td>${esc(x.grado)}</td></tr>`).join("")}</tbody></table></div>
        ${filas.length > 50 ? `<p class="muted">Mostrando 50 de ${filas.length}.</p>` : ""}`;
      $("#imp-go").hidden = !filas.length;
    }, error: () => { $("#imp-prev").innerHTML = `<p class="err-msg">No se pudo leer el archivo.</p>`; } });
  });
  $("#imp-go").addEventListener("click", async () => {
    const b = $("#imp-go"); b.disabled = true; b.textContent = "Importando…";
    try {
      const n = await api.upsertAlumnos(/** @type {any[]} */ (filas).map(({ aviso, ...x }) => ({ colegio_id: DB.cid, ...x })));
      await loadAll(); m.close(); repaint(); toast(`${n} alumnos importados correctamente`, "success");
    } catch (/** @type {any} */ e) { b.disabled = false; b.textContent = "Importar alumnos"; $("#imp-prev").insertAdjacentHTML("afterbegin", `<p class="err-msg">Error: ${esc(e.message)}</p>`); }
  });
}

/* ============================ Docentes (solo lectura) ============================ */
// El personal se crea en «Personal y accesos»; aquí solo se consulta quién es docente o coordinador.
let dq = "";
export const docentesPage = {
  id: "docentes", title: "Docentes", icon: "briefcase", group: "Gestión",
  async render(/** @type {any} */ el) {
    el.innerHTML = `${pageHead("Docentes", "Docentes y coordinadores con acceso al sistema. Solo lectura: las cuentas se crean en «Personal y accesos».")}
      <div class="toolbar"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="do-q" placeholder="Buscar por nombre, rol o carrera…" value="${esc(dq)}" aria-label="Buscar"></div><span class="muted" id="do-total"></span></div><div class="card flush" id="tbl">${skeleton(4)}</div>`;
    /** @type {any} */
    let lista = [];
    try { lista = (await api.personalDirectorio()) || []; } catch { lista = []; }
    // Docentes cargados antes a mano (sin cuenta): se siguen mostrando, solo para consulta.
    const sinCuenta = DB.docentes.filter((d) => d.estado === "ACTIVO").map((d) => ({ id: d.id, nombre: d.nombre, rol: d.rol || "Docente", carrera: d.profesion || "", sinCuenta: true }));
    const todos = [...lista, ...sinCuenta];
    el._repaint = () => {
      const q = norm(dq);
      const l = todos.filter((d) => !q || norm([d.nombre, d.rol, d.carrera].join(" ")).includes(q));
      /** @type {HTMLElement} */ (el.querySelector("#do-total")).textContent = `${todos.filter((d) => /docente/i.test(d.rol)).length} docentes · ${todos.filter((d) => /coordinador/i.test(d.rol)).length} coordinadores`;
      /** @type {HTMLElement} */ (el.querySelector("#tbl")).innerHTML = l.length ? `<div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Rol</th><th>Carrera</th><th>Cuenta</th></tr></thead><tbody>
        ${l.map((d) => `<tr><td><div class="person"><span class="avatar" data-foto="${esc(d.foto_path || "")}">${esc(initials(d.nombre || "?"))}</span><span>${esc(d.nombre || "Sin nombre")}</span></div></td><td>${badge(d.rol, /coordinador/i.test(d.rol) ? "amber" : "green")}</td>
          <td>${d.carrera ? esc(d.carrera) : '<span class="muted">—</span>'}</td><td>${d.sinCuenta ? badge("Sin cuenta", "neutral") : badge("Con acceso", "green")}</td></tr>`).join("")}</tbody></table></div>`
        : emptyState("Aún no hay docentes", "Crea el primero en «Personal y accesos → Crear usuario» y aparecerá aquí.", "briefcase");
      el.querySelectorAll(".avatar[data-foto]").forEach(async (/** @type {any} */ av) => {
        if (!av.dataset.foto) return;
        const url = await api.fotoPersonalUrl(av.dataset.foto).catch(() => null);
        if (url) { av.style.backgroundImage = `url("${url}")`; av.style.backgroundSize = "cover"; av.textContent = ""; }
      });
    };
    /** @type {HTMLElement} */ (el.querySelector("#do-q")).addEventListener("input", debounce((e) => { dq = e.target.value; el._repaint(); }, 150));
    el._repaint();
  },
};

/* =========================== Comunicados =========================== */
export const comunicadosPage = {
  id: "comunicados", title: "Comunicados", icon: "megaphone", group: "Gestión",
  render(/** @type {any} */ el) {
    el.innerHTML = `${pageHead("Comunicados", "Avisos institucionales para alumnos y apoderados.", `<button class="btn btn-primary" data-action="co-new">${icon("plus", 16)} Nuevo comunicado</button>`)}<div id="lst" class="stack"></div>`;
    el._repaint = () => {
      /** @type {HTMLElement} */ (el.querySelector("#lst")).innerHTML = DB.comunicados.length ? DB.comunicados.map((c) => `<article class="card"><header class="card-head"><div><h3>${esc(c.titulo)}</h3><small class="muted">${esc(fmtDate(c.fecha))}</small></div>
        <button class="icon-only danger" title="Eliminar" aria-label="Eliminar comunicado" data-action="co-del" data-id="${c.id}">${icon("trash", 16)}</button></header><p class="prewrap">${esc(c.mensaje)}</p></article>`).join("")
        : `<div class="card">${emptyState("Sin comunicados", "Publica el primer comunicado para tus alumnos y apoderados.", "megaphone")}</div>`;
    };
    el._repaint();
  },
};

/* ============================= Acciones ============================= */
registerActions({
  "nivel-new": () => formModal({ title: "Agregar carrera", fields: [{ name: "nombre", label: "Nombre de la carrera", required: true, placeholder: "Ej: MECANICA ELECTRICA" }],
    onSubmit: async ({ nombre }) => {
      if (DB.niveles.some((n) => norm(n) === norm(nombre))) throw new Error("Esa carrera ya existe.");
      await guardar("niveles", { nombre: nombre.trim().replace(/\s+/g, " ").toUpperCase() }); repaint(); toast("Carrera agregada", "success");
    } }),
  "nivel-del": (/** @type {any} */ el) => {
    const n = /** @type {any} */ (DB.nivelesRaw.find((x) => x.id === el.dataset.id));
    const gr = DB.grados.filter((g) => g.nivel === n.nombre).length, alu = DB.alumnos.filter((a) => a.nivel === n.nombre).length;
    if (gr || alu) { toast(`No se puede eliminar "${n.nombre}": tiene ${gr} ciclo(s) y ${alu} alumno(s) asociados.`, "error"); return; }
    eliminar("niveles", n.id, `¿Eliminar la carrera <b>${esc(n.nombre)}</b>?`, "Carrera eliminada");
  },
  "grado-del": (/** @type {any} */ el) => {
    const g = /** @type {any} */ (DB.grados.find((x) => x.id === el.dataset.id));
    const alu = DB.alumnos.filter((a) => a.nivel === g.nivel && a.grado === g.nombre).length;
    if (alu) { toast(`No se puede eliminar "${g.nombre}": tiene ${alu} alumno(s).`, "error"); return; }
    eliminar("grados", g.id, `¿Eliminar el ciclo <b>${esc(g.nombre)}</b>?`, "Ciclo eliminado");
  },
  "al-new": () => alumnoForm(),
  "al-apod": (/** @type {any} */ el) => {
    const a = /** @type {any} */ (alumnoPorId(el.dataset.id));
    const cod = a.codigo_apoderado || "";
    const bonito = cod.replace(/(.{4})(?=.)/g, "$1-");
    const enlace = new URL("apoderado/?c=" + cod, new URL(".", location.href)).href;
    const texto = `Código para consultar la asistencia de ${a.nombre}: ${bonito}\n${enlace}`;
    const wa = a.apoderado_telefono ? enlaceWhatsApp(a.apoderado_telefono, texto) : `https://wa.me/?text=${encodeURIComponent(texto)}`;
    const m = openModal({ title: "Código de apoderado", body: cod ? `<p>${esc(a.nombre)}</p><div class="codigo-box codigo-grande"><code>${esc(bonito)}</code></div>
      <p class="muted">El apoderado entra a la página de apoderados y escribe este código para ver la asistencia (solo lectura).</p>` : `<p class="muted">Este alumno aún no tiene código de apoderado (aplica la migración 007).</p>`,
      footer: cod ? `<button class="btn btn-outline" data-close2>Cerrar</button><a class="btn btn-teal" target="_blank" rel="noopener" href="${esc(wa)}">Enviar por WhatsApp</a>` : `<button class="btn btn-outline" data-close2>Cerrar</button>` });
    m.el.querySelector("[data-close2]")?.addEventListener("click", m.close);
  },
  "al-edit": (/** @type {any} */ el) => alumnoForm(alumnoPorId(el.dataset.id)),
  "al-del": (/** @type {any} */ el) => { const a = /** @type {any} */ (alumnoPorId(el.dataset.id)); eliminar("alumnos", a.id, `¿Eliminar a <b>${esc(a.nombre)}</b>? También se borrará su historial de asistencia. Esta acción no se puede deshacer.`, "Alumno eliminado"); },
  "al-carnet": (/** @type {any} */ el) => (location.hash = `#/carnet?id=${el.dataset.id}`),
  "al-hist": (/** @type {any} */ el) => (location.hash = `#/asist-alumno?id=${el.dataset.id}`),
  "al-page": (/** @type {any} */ el) => { al.page += Number(el.dataset.d); repaint(); window.scrollTo({ top: 0, behavior: "smooth" }); },
  "al-import": importarCSV,
  "ciclos-new": (/** @type {any} */ el) => crearCiclos(el?.dataset?.carrera || ""),
  "grado-new": () => crearCiclos(),
  "al-codigo": () => { location.hash = "#/codigo"; },
  "al-revisar": (/** @type {any} */ el) => revisarEstudiante(alumnoPorId(el.dataset.id)),
  "co-new": () => formModal({ title: "Nuevo comunicado", submitLabel: "Publicar", fields: [
    { name: "titulo", label: "Título", required: true }, { name: "mensaje", label: "Mensaje", type: "textarea", required: true }],
  onSubmit: async (v) => { await guardar("comunicados", { ...v, fecha: todayStr() }); repaint(); toast("Comunicado publicado", "success"); } }),
  "co-del": (/** @type {any} */ el) => eliminar("comunicados", el.dataset.id, "¿Eliminar este comunicado?", "Comunicado eliminado"),
});
