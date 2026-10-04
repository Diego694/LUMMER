// Mantenimiento (CRUD): niveles, grados, alumnos (con importación CSV), docentes y comunicados.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, alumnoPorId, loadAll, opcionesGrado, opcionesNivel } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, openModal, pageHead, registerActions, toast } from "../ui.js";
import { normalizarFilasImport } from "../stats.js";
import { debounce, downloadFile, esc, fmtDate, initials, norm, todayStr } from "../utils.js";

const root = () => document.getElementById("page-root");
const repaint = () => root()._repaint?.();
const options = (list, sel) => list.map((o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");
const msgError = (e, dup) => (e.code === "duplicate" ? dup : e.message);

async function guardar(tabla, data, id) {
  await api.save(tabla, { colegio_id: DB.cid, ...data }, id);
  await loadAll();
}
async function eliminar(tabla, id, mensaje, exito) {
  if (!(await confirmDialog({ title: "Confirmar eliminación", message: mensaje }))) return;
  try { await api.remove(tabla, id); await loadAll(); repaint(); toast(exito, "success"); }
  catch (e) { toast("No se pudo eliminar: " + e.message, "error"); }
}

/* ============================ Niveles ============================ */
export const nivelesPage = {
  id: "niveles", title: "Niveles", icon: "layers", group: "Gestión",
  render(el) {
    el.innerHTML = `${pageHead("Niveles", "Niveles educativos de la institución.", `<button class="btn btn-primary" data-action="nivel-new">${icon("plus", 16)} Agregar nivel</button>`)}<div class="card flush" id="tbl"></div>`;
    el._repaint = () => {
      el.querySelector("#tbl").innerHTML = DB.niveles.length ? `<div class="table-wrap"><table><thead><tr><th>Nivel</th><th>Grados</th><th>Alumnos</th><th></th></tr></thead><tbody>
        ${DB.nivelesRaw.map((n) => `<tr><td><strong>${esc(n.nombre)}</strong></td><td>${DB.grados.filter((g) => g.nivel === n.nombre).length}</td><td>${DB.alumnos.filter((a) => a.nivel === n.nombre).length}</td>
          <td class="t-right"><button class="btn btn-ghost-danger btn-sm" data-action="nivel-del" data-id="${n.id}">${icon("trash", 14)} Eliminar</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("Sin niveles", "Crea el primer nivel (por ejemplo Primaria).", "layers");
    };
    el._repaint();
  },
};

/* ============================= Grados ============================= */
export const gradosPage = {
  id: "grados", title: "Grados", icon: "book", group: "Gestión",
  render(el) {
    el.innerHTML = `${pageHead("Grados", "Grados y secciones por nivel.", `<button class="btn btn-primary" data-action="grado-new">${icon("plus", 16)} Agregar grado</button>`)}<div class="card flush" id="tbl"></div>`;
    el._repaint = () => {
      el.querySelector("#tbl").innerHTML = DB.grados.length ? `<div class="table-wrap"><table><thead><tr><th>Grado</th><th>Nivel</th><th>Alumnos</th><th></th></tr></thead><tbody>
        ${DB.grados.map((g) => `<tr><td><strong>${esc(g.nombre)}</strong></td><td>${esc(g.nivel)}</td><td>${DB.alumnos.filter((a) => a.nivel === g.nivel && a.grado === g.nombre).length}</td>
          <td class="t-right"><button class="btn btn-ghost-danger btn-sm" data-action="grado-del" data-id="${g.id}">${icon("trash", 14)} Eliminar</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("Sin grados", "Agrega los grados de cada nivel.", "book");
    };
    el._repaint();
  },
};

/* ============================ Alumnos ============================ */
let al = { q: "", nivel: "", grado: "", est: "", page: 1 };

export const alumnosPage = {
  id: "alumnos", title: "Alumnos", icon: "cap", group: "Gestión",
  render(el) {
    el.innerHTML = `${pageHead("Alumnos", "Padrón de alumnos con su código único de acceso.",
      `<button class="btn btn-outline" data-action="al-codigo">${icon("qr", 16)} Código de registro</button><button class="btn btn-outline" data-action="al-import">${icon("upload", 16)} Importar CSV</button><button class="btn btn-primary" data-action="al-new">${icon("plus", 16)} Agregar</button>`)}
      <div class="toolbar"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="al-q" placeholder="Buscar por nombre, código, apoderado…" value="${esc(al.q)}" aria-label="Buscar"></div>
        <select class="filter" id="al-nivel" aria-label="Nivel">${options(opcionesNivel(true), al.nivel)}</select>
        <select class="filter" id="al-grado" aria-label="Grado">${options(opcionesGrado(al.nivel, true), al.grado)}</select>
        <select class="filter" id="al-est" aria-label="Estado de registro"><option value="">Todos</option><option value="pend">Pendientes de aprobación (${DB.alumnos.filter((a) => a.aprobado === false).length})</option></select></div>
      <div class="card flush" id="tbl"></div><div class="pager" id="pager"></div>`;
    el.querySelector("#al-q").addEventListener("input", debounce((e) => { al.q = e.target.value; al.page = 1; el._repaint(); }, 150));
    el.querySelector("#al-nivel").addEventListener("change", (e) => { al.nivel = e.target.value; al.grado = ""; al.page = 1; el.querySelector("#al-grado").innerHTML = options(opcionesGrado(al.nivel, true), ""); el._repaint(); });
    el.querySelector("#al-grado").addEventListener("change", (e) => { al.grado = e.target.value; al.page = 1; el._repaint(); });
    el.querySelector("#al-est").value = al.est;
    el.querySelector("#al-est").addEventListener("change", (e) => { al.est = e.target.value; al.page = 1; el._repaint(); });
    el._repaint = () => {
      const q = norm(al.q);
      const l = DB.alumnos.filter((a) => (!q || norm([a.nombre, a.codigo, a.apoderado, a.nivel, a.grado].join(" ")).includes(q)) && (!al.nivel || a.nivel === al.nivel) && (!al.grado || a.grado === al.grado) && (al.est !== "pend" || a.aprobado === false));
      const tot = Math.max(1, Math.ceil(l.length / CONFIG.ALUMNOS_POR_PAGINA));
      al.page = Math.min(al.page, tot);
      const pag = l.slice((al.page - 1) * CONFIG.ALUMNOS_POR_PAGINA, al.page * CONFIG.ALUMNOS_POR_PAGINA);
      el.querySelector("#tbl").innerHTML = pag.length ? `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Código</th><th>Nivel · Grado</th><th>Apoderado</th><th>Estado</th><th></th></tr></thead><tbody>
        ${pag.map((a) => `<tr><td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td>
          <td class="mono">${esc(a.codigo)}</td><td>${esc(a.nivel)} · ${esc(a.grado)}</td><td>${a.apoderado ? esc(a.apoderado) : '<span class="muted">—</span>'}</td>
          <td>${a.aprobado === false ? badge("Pendiente", "amber") : badge(a.estado === "ACTIVO" ? "Activo" : "Inactivo", a.estado === "ACTIVO" ? "green" : "neutral")}</td>
          <td class="t-right nowrap">
            ${a.aprobado === false ? `<button class="btn btn-teal btn-sm" data-action="al-revisar" data-id="${a.id}">${icon("userCheck", 14)} Revisar</button>` : ""}
            <button class="icon-only" title="Editar" aria-label="Editar ${esc(a.nombre)}" data-action="al-edit" data-id="${a.id}">${icon("edit", 16)}</button>
            <button class="icon-only" title="Carnet" aria-label="Carnet de ${esc(a.nombre)}" data-action="al-carnet" data-id="${a.id}">${icon("idCard", 16)}</button>
            <button class="icon-only" title="Historial" aria-label="Historial de ${esc(a.nombre)}" data-action="al-hist" data-id="${a.id}">${icon("history", 16)}</button>
            <button class="icon-only danger" title="Eliminar" aria-label="Eliminar ${esc(a.nombre)}" data-action="al-del" data-id="${a.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("No se encontraron alumnos", "Prueba con otro criterio o agrega un nuevo alumno.", "search");
      el.querySelector("#pager").innerHTML = tot > 1 ? `<button class="btn btn-outline btn-sm" data-action="al-page" data-d="-1" ${al.page <= 1 ? "disabled" : ""}>Anterior</button>
        <span class="muted">Página ${al.page} de ${tot} · ${l.length} alumnos</span><button class="btn btn-outline btn-sm" data-action="al-page" data-d="1" ${al.page >= tot ? "disabled" : ""}>Siguiente</button>` : `<span class="muted">${l.length} alumno(s)</span>`;
    };
    el._repaint();
  },
};

function alumnoForm(a) {
  if (!DB.niveles.length) { toast("Primero crea al menos un nivel y un grado.", "error"); location.hash = "#/niveles"; return; }
  const nivel = a?.nivel || DB.niveles[0];
  const sig = DB.alumnos.reduce((m, x) => Math.max(m, parseInt(x.codigo.replace(/\D/g, ""), 10) || 0), 1000) + 1;
  formModal({
    title: a ? "Editar alumno" : "Agregar alumno",
    fields: [
      { name: "nombre", label: "Nombre completo", required: true, value: a?.nombre },
      { name: "codigo", label: "Código único de acceso", required: true, value: a?.codigo ?? `a${sig}` },
      { name: "nivel", label: "Nivel", type: "select", half: true, options: opcionesNivel(), value: nivel, onChange: (v, c) => c.setOptions("grado", opcionesGrado(v)) },
      { name: "grado", label: "Grado", type: "select", half: true, options: opcionesGrado(nivel), value: a?.grado },
      { name: "apoderado", label: "Apoderado", value: a?.apoderado },
      { name: "estado", label: "Estado", type: "pills", options: [{ value: "ACTIVO", label: "Activo" }, { value: "INACTIVO", label: "Inactivo" }], value: a?.estado || "ACTIVO" },
    ],
    onSubmit: async (v) => {
      // Si se corrige el nombre de un alumno auto‑registrado, se limpian nombres/apellidos para que la censura use el nombre nuevo.
      if (a && "nombres" in a && v.nombre !== a.nombre) { v.nombres = null; v.apellidos = null; }
      try { await guardar("alumnos", v, a?.id); } catch (e) { throw new Error(msgError(e, "Ese código ya está en uso.")); }
      repaint(); toast("Alumno guardado correctamente", "success");
    },
  });
}

/** Código que los estudiantes necesitan para registrarse en su portal. Se puede generar y regenerar. */
async function codigoRegistro() {
  const actual = await api.getCodigoRegistro(DB.cid);
  const url = new URL("estudiante/", location.href.split("#")[0]).href;
  const m = openModal({
    title: "Código de registro de estudiantes",
    body: `<p class="muted">Entrégalo a los estudiantes. Lo escriben una sola vez en su portal para registrarse; luego tú apruebas cada registro. Si lo regeneras, el código anterior deja de funcionar.</p>
      <div class="codigo-box"><code id="cr-code">${actual ? esc(actual) : "— sin código —"}</code></div>
      <p class="muted">Portal para estudiantes: <a href="${esc(url)}" target="_blank" rel="noopener">${esc(url)}</a></p>
      <p class="err-msg" id="cr-err" role="alert" hidden></p>`,
    footer: `<button class="btn btn-outline" data-close2>Cerrar</button><button class="btn btn-primary" id="cr-gen">${actual ? "Regenerar código" : "Generar código"}</button>`,
  });
  m.el.querySelector("[data-close2]").addEventListener("click", m.close);
  m.el.querySelector("#cr-gen").addEventListener("click", async (e) => {
    const alfabeto = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";  // sin caracteres ambiguos (0/O, 1/I)
    const bytes = crypto.getRandomValues(new Uint8Array(8));
    const nuevo = [...bytes].map((b) => alfabeto[b % alfabeto.length]).join("");
    e.target.disabled = true;
    try {
      await api.setCodigoRegistro(DB.cid, nuevo);
      m.el.querySelector("#cr-code").textContent = nuevo;
      e.target.textContent = "Regenerar código";
      toast("Código generado", "success");
    } catch (ex) {
      const er = m.el.querySelector("#cr-err");
      er.textContent = /codigo_registro|column/i.test(ex.message) ? "Falta aplicar la migración del portal de estudiantes (supabase/migrations/002_estudiantes.sql)." : ex.message;
      er.hidden = false;
    }
    e.target.disabled = false;
  });
}

/** Revisión de un estudiante auto‑registrado: ver su foto y datos, aprobar o rechazar. */
async function revisarEstudiante(a) {
  const m = openModal({
    title: "Revisar registro de estudiante",
    body: `<div class="revisar"><div class="alert-photo big" id="rv-foto"><span>${esc(initials(a.nombre))}</span></div>
      <dl class="datos"><dt>Nombre</dt><dd>${esc(a.nombre)}</dd><dt>Nivel · Grado</dt><dd>${esc(a.nivel)} · ${esc(a.grado)}</dd>
      ${a.dni ? `<dt>DNI</dt><dd>${esc(a.dni)}</dd>` : ""}${a.apoderado ? `<dt>Apoderado</dt><dd>${esc(a.apoderado)}</dd>` : ""}
      <dt>Código QR</dt><dd class="mono">${esc(a.codigo)}</dd>${a.registrado_en ? `<dt>Registrado</dt><dd>${esc(new Date(a.registrado_en).toLocaleString("es-PE"))}</dd>` : ""}</dl></div>
      <p class="muted">Al aprobar, su QR podrá registrar asistencia. Al rechazar, se elimina el registro.</p>`,
    footer: `<button class="btn btn-ghost-danger" id="rv-no">Rechazar</button><button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-teal" id="rv-ok">${icon("check", 16)} Aprobar</button>`,
  });
  m.el.querySelector("[data-close2]").addEventListener("click", m.close);
  api.fotoUrl(a).then((url) => {
    if (!url || !m.el.isConnected) return;
    const img = new Image(); img.alt = "Foto del estudiante";
    img.onload = () => { const b = m.el.querySelector("#rv-foto"); if (b) { b.innerHTML = ""; b.appendChild(img); } };
    img.src = url;
  }).catch(() => {});
  m.el.querySelector("#rv-ok").addEventListener("click", async () => {
    try { await guardar("alumnos", { aprobado: true }, a.id); m.close(); repaint(); toast("Estudiante aprobado: su QR ya registra asistencia", "success"); }
    catch (e) { toast("No se pudo aprobar: " + e.message, "error"); }
  });
  m.el.querySelector("#rv-no").addEventListener("click", async () => {
    m.close();
    await eliminar("alumnos", a.id, `¿Rechazar y eliminar el registro de <b>${esc(a.nombre)}</b>?`, "Registro rechazado");
  });
}

function importarCSV() {
  let filas = [];
  const m = openModal({
    title: "Importar alumnos desde CSV", wide: true,
    body: `<p class="muted">Columnas (primera fila): <b>nombre, codigo, nivel, grado, apoderado, estado</b>. Solo <b>nombre</b> y <b>codigo</b> son obligatorias; si falta <b>estado</b> se asigna ACTIVO. Si el código ya existe, el alumno se actualiza.</p>
      <button class="btn btn-outline btn-sm" id="imp-tpl">${icon("download", 14)} Descargar plantilla</button>
      <div class="field" style="margin-top:14px"><label for="imp-file">Archivo CSV</label><input type="file" id="imp-file" accept=".csv,text/csv"></div><div id="imp-prev"></div>`,
    footer: `<button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-primary" id="imp-go" hidden>Importar alumnos</button>`,
  });
  const $ = (s) => m.el.querySelector(s);
  $("[data-close2]").addEventListener("click", m.close);
  $("#imp-tpl").addEventListener("click", () => downloadFile("plantilla-alumnos.csv", "﻿nombre,codigo,nivel,grado,apoderado,estado\r\nJuan Perez Rios,a2001,Primaria,1er grado,Maria Rios,ACTIVO\r\nAna Torres Vega,a2002,Secundaria,1er año,,ACTIVO\r\n"));
  $("#imp-file").addEventListener("change", (e) => {
    const f = e.target.files[0]; if (!f) return;
    Papa.parse(f, { header: true, skipEmptyLines: true, complete: (res) => {
      const cols = (res.meta.fields || []).map((c) => c.trim().toLowerCase());
      if (!cols.includes("nombre") || !cols.includes("codigo")) { $("#imp-prev").innerHTML = `<p class="err-msg">El archivo debe tener al menos las columnas "nombre" y "codigo".</p>`; $("#imp-go").hidden = true; return; }
      const r = normalizarFilasImport(res.data, DB.niveles, DB.grados);
      filas = r.validas;
      const avisos = filas.filter((x) => x.aviso.length);
      $("#imp-prev").innerHTML = `<p class="ok-msg">${filas.length} alumno(s) listos para importar.</p>
        ${r.errores.length ? `<p class="err-msg">${r.errores.length} fila(s) omitidas: ${r.errores.slice(0, 5).map((x) => `línea ${x.linea} (${esc(x.motivo)})`).join("; ")}${r.errores.length > 5 ? "…" : ""}</p>` : ""}
        ${avisos.length ? `<p class="warn-msg">${avisos.length} fila(s) con nivel/grado inexistente; se importarán igualmente.</p>` : ""}
        <div class="table-wrap" style="max-height:220px"><table><thead><tr><th>Nombre</th><th>Código</th><th>Nivel</th><th>Grado</th></tr></thead><tbody>
        ${filas.slice(0, 50).map((x) => `<tr><td>${esc(x.nombre)}</td><td class="mono">${esc(x.codigo)}</td><td>${esc(x.nivel)}</td><td>${esc(x.grado)}</td></tr>`).join("")}</tbody></table></div>
        ${filas.length > 50 ? `<p class="muted">Mostrando 50 de ${filas.length}.</p>` : ""}`;
      $("#imp-go").hidden = !filas.length;
    }, error: () => { $("#imp-prev").innerHTML = `<p class="err-msg">No se pudo leer el archivo.</p>`; } });
  });
  $("#imp-go").addEventListener("click", async () => {
    const b = $("#imp-go"); b.disabled = true; b.textContent = "Importando…";
    try {
      const n = await api.upsertAlumnos(filas.map(({ aviso, ...x }) => ({ colegio_id: DB.cid, ...x })));
      await loadAll(); m.close(); repaint(); toast(`${n} alumnos importados correctamente`, "success");
    } catch (e) { b.disabled = false; b.textContent = "Importar alumnos"; $("#imp-prev").insertAdjacentHTML("afterbegin", `<p class="err-msg">Error: ${esc(e.message)}</p>`); }
  });
}

/* ============================ Docentes ============================ */
let dq = "";
export const docentesPage = {
  id: "docentes", title: "Docentes", icon: "briefcase", group: "Gestión",
  render(el) {
    el.innerHTML = `${pageHead("Docentes", "Personal docente y administrativo.", `<button class="btn btn-primary" data-action="do-new">${icon("plus", 16)} Agregar</button>`)}
      <div class="toolbar"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="do-q" placeholder="Buscar por nombre, rol o profesión…" value="${esc(dq)}" aria-label="Buscar"></div></div><div class="card flush" id="tbl"></div>`;
    el.querySelector("#do-q").addEventListener("input", debounce((e) => { dq = e.target.value; el._repaint(); }, 150));
    el._repaint = () => {
      const q = norm(dq);
      const l = DB.docentes.filter((d) => !q || norm([d.nombre, d.rol, d.profesion].join(" ")).includes(q));
      el.querySelector("#tbl").innerHTML = l.length ? `<div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Rol</th><th>Profesión</th><th>Estado</th><th></th></tr></thead><tbody>
        ${l.map((d) => `<tr><td><div class="person"><span class="avatar">${esc(initials(d.nombre))}</span><span>${esc(d.nombre)}</span></div></td><td>${esc(d.rol)}</td><td>${d.profesion ? esc(d.profesion) : '<span class="muted">—</span>'}</td>
          <td>${badge(d.estado === "ACTIVO" ? "Activo" : "Inactivo", d.estado === "ACTIVO" ? "green" : "neutral")}</td>
          <td class="t-right nowrap"><button class="icon-only" title="Editar" aria-label="Editar ${esc(d.nombre)}" data-action="do-edit" data-id="${d.id}">${icon("edit", 16)}</button>
          <button class="icon-only danger" title="Eliminar" aria-label="Eliminar ${esc(d.nombre)}" data-action="do-del" data-id="${d.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
        : emptyState("No se encontraron docentes", "Agrega un nuevo docente o ajusta tu búsqueda.", "search");
    };
    el._repaint();
  },
};

function docenteForm(d) {
  formModal({
    title: d ? "Editar docente" : "Agregar docente",
    fields: [
      { name: "nombre", label: "Nombre completo", required: true, value: d?.nombre, placeholder: "Ej: Rosa Mendoza Ruiz" },
      { name: "profesion", label: "Profesión", value: d?.profesion, placeholder: "Ej: Profesora de Comunicación" },
      { name: "rol", label: "Rol dentro del sistema", type: "pills", options: ["Docente", "Coordinador", "Auxiliar", "Administrativo"].map((r) => ({ value: r, label: r })), value: d?.rol || "Docente" },
      { name: "estado", label: "Estado", type: "pills", options: [{ value: "ACTIVO", label: "Activo" }, { value: "INACTIVO", label: "Inactivo" }], value: d?.estado || "ACTIVO" },
    ],
    onSubmit: async (v) => { await guardar("docentes", v, d?.id); repaint(); toast("Docente guardado correctamente", "success"); },
  });
}

/* =========================== Comunicados =========================== */
export const comunicadosPage = {
  id: "comunicados", title: "Comunicados", icon: "megaphone", group: "Gestión",
  render(el) {
    el.innerHTML = `${pageHead("Comunicados", "Avisos institucionales para alumnos y apoderados.", `<button class="btn btn-primary" data-action="co-new">${icon("plus", 16)} Nuevo comunicado</button>`)}<div id="lst" class="stack"></div>`;
    el._repaint = () => {
      el.querySelector("#lst").innerHTML = DB.comunicados.length ? DB.comunicados.map((c) => `<article class="card"><header class="card-head"><div><h3>${esc(c.titulo)}</h3><small class="muted">${esc(fmtDate(c.fecha))}</small></div>
        <button class="icon-only danger" title="Eliminar" aria-label="Eliminar comunicado" data-action="co-del" data-id="${c.id}">${icon("trash", 16)}</button></header><p class="prewrap">${esc(c.mensaje)}</p></article>`).join("")
        : `<div class="card">${emptyState("Sin comunicados", "Publica el primer comunicado para tus alumnos y apoderados.", "megaphone")}</div>`;
    };
    el._repaint();
  },
};

/* ============================= Acciones ============================= */
registerActions({
  "nivel-new": () => formModal({ title: "Agregar nivel", fields: [{ name: "nombre", label: "Nombre del nivel", required: true, placeholder: "Ej: Primaria" }],
    onSubmit: async ({ nombre }) => {
      if (DB.niveles.some((n) => norm(n) === norm(nombre))) throw new Error("Ese nivel ya existe.");
      await guardar("niveles", { nombre }); repaint(); toast("Nivel agregado", "success");
    } }),
  "nivel-del": (el) => {
    const n = DB.nivelesRaw.find((x) => x.id === el.dataset.id);
    const gr = DB.grados.filter((g) => g.nivel === n.nombre).length, alu = DB.alumnos.filter((a) => a.nivel === n.nombre).length;
    if (gr || alu) { toast(`No se puede eliminar "${n.nombre}": tiene ${gr} grado(s) y ${alu} alumno(s) asociados.`, "error"); return; }
    eliminar("niveles", n.id, `¿Eliminar el nivel <b>${esc(n.nombre)}</b>?`, "Nivel eliminado");
  },
  "grado-new": () => {
    if (!DB.niveles.length) { toast("Primero crea un nivel.", "error"); return; }
    formModal({ title: "Agregar grado", fields: [
      { name: "nivel", label: "Nivel", type: "select", options: opcionesNivel(), value: DB.niveles[0] },
      { name: "nombre", label: "Nombre del grado", required: true, placeholder: 'Ej: 1er "A"' }],
    onSubmit: async ({ nivel, nombre }) => {
      if (DB.grados.some((g) => g.nivel === nivel && norm(g.nombre) === norm(nombre))) throw new Error("Ese grado ya existe en el nivel.");
      await guardar("grados", { nivel, nombre }); repaint(); toast("Grado agregado", "success");
    } });
  },
  "grado-del": (el) => {
    const g = DB.grados.find((x) => x.id === el.dataset.id);
    const alu = DB.alumnos.filter((a) => a.nivel === g.nivel && a.grado === g.nombre).length;
    if (alu) { toast(`No se puede eliminar "${g.nombre}": tiene ${alu} alumno(s).`, "error"); return; }
    eliminar("grados", g.id, `¿Eliminar el grado <b>${esc(g.nombre)}</b>?`, "Grado eliminado");
  },
  "al-new": () => alumnoForm(),
  "al-edit": (el) => alumnoForm(alumnoPorId(el.dataset.id)),
  "al-del": (el) => { const a = alumnoPorId(el.dataset.id); eliminar("alumnos", a.id, `¿Eliminar a <b>${esc(a.nombre)}</b>? También se borrará su historial de asistencia. Esta acción no se puede deshacer.`, "Alumno eliminado"); },
  "al-carnet": (el) => (location.hash = `#/carnet?id=${el.dataset.id}`),
  "al-hist": (el) => (location.hash = `#/asist-alumno?id=${el.dataset.id}`),
  "al-page": (el) => { al.page += Number(el.dataset.d); repaint(); window.scrollTo({ top: 0, behavior: "smooth" }); },
  "al-import": importarCSV,
  "al-codigo": codigoRegistro,
  "al-revisar": (el) => revisarEstudiante(alumnoPorId(el.dataset.id)),
  "do-new": () => docenteForm(),
  "do-edit": (el) => docenteForm(DB.docentes.find((x) => x.id === el.dataset.id)),
  "do-del": (el) => { const d = DB.docentes.find((x) => x.id === el.dataset.id); eliminar("docentes", d.id, `¿Eliminar a <b>${esc(d.nombre)}</b>?`, "Docente eliminado"); },
  "co-new": () => formModal({ title: "Nuevo comunicado", submitLabel: "Publicar", fields: [
    { name: "titulo", label: "Título", required: true }, { name: "mensaje", label: "Mensaje", type: "textarea", required: true }],
  onSubmit: async (v) => { await guardar("comunicados", { ...v, fecha: todayStr() }); repaint(); toast("Comunicado publicado", "success"); } }),
  "co-del": (el) => eliminar("comunicados", el.dataset.id, "¿Eliminar este comunicado?", "Comunicado eliminado"),
});
