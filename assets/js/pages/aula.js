// @ts-check
// Aula: material y actividades de cada curso. El administrador crea los cursos (Gestión → Cursos) y asigna docentes;
// cada docente asignado publica en SUS cursos. La seguridad real está en la base (migración 013); aquí solo se ocultan
// los botones que fallarían.
import { api } from "../api.js";
import { validarArchivoAula } from "../api-aula.js";
import { DB } from "../state.js";
import { esAdmin } from "../permisos.js";
import { badge, confirmDialog, emptyState, icon, openModal, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { esc, etiquetaCiclo, fmtDate } from "../utils.js";

/** @typedef {import('../tipos.d.ts').CursoMaterial} CursoMaterial */
/** @typedef {import('../tipos.d.ts').CursoActividad} CursoActividad */

/** @type {{ cursoId: string, tab: "material" | "actividades" }} */
const st = { cursoId: "", tab: "material" };
/** @type {{ materiales: CursoMaterial[], actividades: CursoActividad[], docentes: string[], gestiona: boolean }} */
let datos = { materiales: [], actividades: [], docentes: [], gestiona: false };

/** @type {Map<string, string>} */
const nombrePersonal = new Map();
const cursoActual = () => DB.cursos.find((c) => c.id === st.cursoId);
const ICONO_TIPO = { documento: "file", enlace: "link", aviso: "info" };
const kb = (/** @type {number | null | undefined} */ b) => (b == null ? "" : b >= 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);
/** @param {string | null | undefined} iso */
const fechaLimite = (iso) => (iso ? new Date(iso).toLocaleString("es-PE", { timeZone: "America/Lima", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "");

export const aulaPage = {
  id: "aula", title: "Aula", icon: "book", group: "Principal",
  /** @param {HTMLElement & { _repaint?: () => void }} el */
  async render(el) {
    const cursos = DB.cursos.filter((c) => c.activo !== false);
    if (!cursos.some((c) => c.id === st.cursoId)) st.cursoId = cursos[0]?.id || "";
    el.innerHTML = `${pageHead("Aula", "Material y actividades de cada curso. Lo que publicas aquí lo ven solo los estudiantes de ese curso.")}
      ${cursos.length ? `<div class="toolbar"><select class="filter" id="aula-curso" aria-label="Curso">${cursos.map((c) => `<option value="${c.id}" ${c.id === st.cursoId ? "selected" : ""}>${esc(c.nombre)} · ${esc(c.grado ? etiquetaCiclo(c.nivel, c.grado) : c.nivel)}</option>`).join("")}</select></div>
        <div id="aula-cuerpo" aria-live="polite">${skeleton(4)}</div>`
        : `<div class="card">${emptyState("Aún no hay cursos", esAdmin() ? "Crea el primer curso en Gestión → Cursos y asígnale un docente." : "El administrador debe crear los cursos y asignarte a ellos.", "book")}</div>`}`;
    if (!cursos.length) return;
    /** @type {HTMLSelectElement} */ (el.querySelector("#aula-curso")).addEventListener("change", (e) => { st.cursoId = /** @type {HTMLSelectElement} */ (e.target).value; cargar(); });
    el._repaint = () => { cargar(); };
    await cargar();
  },
};

async function cargar() {
  const cuerpo = document.getElementById("aula-cuerpo");
  const curso = cursoActual();
  if (!cuerpo || !curso?.id) return;
  const id = curso.id;
  cuerpo.innerHTML = skeleton(4);
  try {
    const [materiales, actividades, docentes] = await Promise.all([api.aulaMateriales(id), api.aulaActividades(id), api.aulaDocentes(id)]);
    if (!cuerpo.isConnected || st.cursoId !== id) return;
    if (esAdmin() && !nombrePersonal.size) (await api.personalListar().catch(() => [])).forEach((/** @type {any} */ p) => nombrePersonal.set(p.id, String(p.nombre || p.email || "Docente")));
    const yo = DB.userId || "";
    datos = { materiales, actividades, docentes: docentes.map((d) => d.user_id), gestiona: esAdmin() || docentes.some((d) => d.user_id === yo) };
    pintar();
  } catch (/** @type {any} */ e) {
    const sinMigracion = /does not exist|schema cache|curso_materiales/i.test(e.message || "");
    cuerpo.innerHTML = emptyState("No se pudo cargar el aula", sinMigracion ? "Falta aplicar la migración 013 en Supabase (supabase/migrations/013_cursos_aula.sql)." : e.message, "alert");
  }
}

function pintar() {
  const cuerpo = document.getElementById("aula-cuerpo");
  const curso = cursoActual();
  if (!cuerpo || !curso) return;
  const { materiales, actividades, gestiona } = datos;
  const nombresDoc = datos.docentes.map((u) => nombrePersonal.get(u) || "Docente");
  const boton = gestiona
    ? `<button class="btn btn-primary" data-action="${st.tab === "material" ? "aula-mat-new" : "aula-act-new"}">${icon("plus", 16)} ${st.tab === "material" ? "Agregar material" : "Nueva actividad"}</button>` : "";
  cuerpo.innerHTML = `
    <div class="card"><div class="card-head pad">
      <div><strong>${esc(curso.nombre)}</strong><div class="muted">${esc(curso.grado ? etiquetaCiclo(curso.nivel, curso.grado) : curso.nivel + " · todos los ciclos")}${nombresDoc.length ? " · " + esc(nombresDoc.join(", ")) : curso.docente ? " · " + esc(curso.docente) : ""}</div></div>
      ${esAdmin() ? `<button class="btn btn-outline btn-sm" data-action="aula-docentes">${icon("users", 16)} Docentes</button>` : ""}
    </div></div>
    <div class="toolbar" role="tablist" aria-label="Secciones del curso">
      <button class="pill ${st.tab === "material" ? "active" : ""}" role="tab" aria-selected="${st.tab === "material"}" data-action="aula-tab" data-tab="material">Material (${materiales.length})</button>
      <button class="pill ${st.tab === "actividades" ? "active" : ""}" role="tab" aria-selected="${st.tab === "actividades"}" data-action="aula-tab" data-tab="actividades">Actividades (${actividades.length})</button>
      <span style="flex:1"></span>${boton}</div>
    <div class="card flush">${st.tab === "material" ? htmlMaterial() : htmlActividades()}</div>`;
}

function htmlMaterial() {
  const { materiales, gestiona } = datos;
  if (!materiales.length) return emptyState("Sin material todavía", gestiona ? "Sube documentos, enlaces o avisos para tus estudiantes." : "Cuando el docente publique material, aparecerá aquí.", "file");
  /** @type {Map<string, CursoMaterial[]>} */
  const porTema = new Map();
  for (const m of materiales) porTema.set(m.tema, [...(porTema.get(m.tema) || []), m]);
  return [...porTema].map(([tema, l]) => `<section class="aula-tema"><h4 class="pad" style="margin:14px 0 4px">${esc(tema)}</h4><ul class="log-list">
    ${l.map((m) => `<li><div class="person"><span class="avatar" aria-hidden="true">${icon(/** @type {any} */ (ICONO_TIPO)[m.tipo] || "file", 16)}</span>
      <div><strong>${esc(m.titulo)}</strong>${m.publicado ? "" : ` ${badge("Borrador", "amber")}`}
        ${m.descripcion ? `<small>${esc(m.descripcion)}</small>` : ""}
        ${m.archivo_nombre ? `<small>${esc(m.archivo_nombre)} · ${kb(m.archivo_bytes)}</small>` : ""}</div></div>
      <div class="log-right nowrap">${m.url ? `<a class="btn btn-outline btn-sm" href="${esc(m.url)}" target="_blank" rel="noopener noreferrer">Abrir enlace</a>` : ""}
        ${m.archivo_path ? `<button class="btn btn-outline btn-sm" data-action="aula-abrir" data-path="${esc(m.archivo_path)}">${icon("download", 16)} Abrir</button>` : ""}
        ${gestiona ? `<button class="icon-only" aria-label="Editar ${esc(m.titulo)}" data-action="aula-mat-edit" data-id="${m.id}">${icon("edit", 16)}</button>
        <button class="icon-only danger" aria-label="Eliminar ${esc(m.titulo)}" data-action="aula-mat-del" data-id="${m.id}">${icon("trash", 16)}</button>` : ""}</div></li>`).join("")}
  </ul></section>`).join("");
}

function htmlActividades() {
  const { actividades, gestiona } = datos;
  if (!actividades.length) return emptyState("Sin actividades todavía", gestiona ? "Crea una actividad con instrucciones y fecha límite." : "Cuando el docente publique actividades, aparecerán aquí.", "listCheck");
  const ahora = Date.now();
  return `<ul class="log-list">${actividades.map((a) => {
    const vencida = a.fecha_limite ? new Date(a.fecha_limite).getTime() < ahora : false;
    return `<li><div class="person"><span class="avatar" aria-hidden="true">${icon("listCheck", 16)}</span>
      <div><strong>${esc(a.titulo)}</strong>${a.publicado ? "" : ` ${badge("Borrador", "amber")}`}
        ${a.instrucciones ? `<small>${esc(a.instrucciones)}</small>` : ""}
        <small>${a.fecha_limite ? `Entrega hasta ${esc(fechaLimite(a.fecha_limite))}` : "Sin fecha límite"} · ${a.puntaje_max} pts ${vencida ? badge("Vencida", "red") : ""}</small>
        ${a.archivo_nombre ? `<small>${esc(a.archivo_nombre)} · ${kb(a.archivo_bytes)}</small>` : ""}</div></div>
      <div class="log-right nowrap">${a.archivo_path ? `<button class="btn btn-outline btn-sm" data-action="aula-abrir" data-path="${esc(a.archivo_path)}">${icon("download", 16)} Abrir</button>` : ""}
        ${gestiona ? `<button class="icon-only" aria-label="Editar ${esc(a.titulo)}" data-action="aula-act-edit" data-id="${a.id}">${icon("edit", 16)}</button>
        <button class="icon-only danger" aria-label="Eliminar ${esc(a.titulo)}" data-action="aula-act-del" data-id="${a.id}">${icon("trash", 16)}</button>` : ""}</div></li>`;
  }).join("")}</ul>`;
}

/* ------------------------------ Formularios ------------------------------ */
/**
 * Formulario con archivo opcional (formModal no soporta <input type=file>).
 * @param {{ titulo: string, cuerpo: string, alGuardar: (f: HTMLFormElement, archivo: File | undefined) => Promise<void> }} o
 */
function formularioAula({ titulo, cuerpo, alGuardar }) {
  const m = openModal({
    title: titulo, wide: true,
    body: `<form id="aula-form" novalidate>${cuerpo}<p class="err-msg" id="aula-err" role="alert" hidden></p></form>`,
    footer: `<button class="btn btn-outline" type="button" data-cancel>Cancelar</button><button class="btn btn-primary" type="submit" form="aula-form">Guardar</button>`,
  });
  const f = /** @type {HTMLFormElement} */ (m.el.querySelector("#aula-form"));
  const err = /** @type {HTMLElement} */ (m.el.querySelector("#aula-err"));
  m.el.querySelector("[data-cancel]")?.addEventListener("click", m.close);
  const enviar = /** @type {HTMLButtonElement} */ (m.el.querySelector("button[type=submit]"));
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    err.hidden = true;
    const archivo = /** @type {HTMLInputElement | null} */ (f.querySelector("input[type=file]"))?.files?.[0];
    if (archivo) { const p = validarArchivoAula(archivo); if (p) { err.textContent = p; err.hidden = false; return; } }
    enviar.disabled = true; enviar.textContent = archivo ? "Subiendo…" : "Guardando…";
    try { await alGuardar(f, archivo); m.close(); toast("Guardado", "success"); await cargar(); }
    catch (/** @type {any} */ ex) { err.textContent = ex.message; err.hidden = false; enviar.disabled = false; enviar.textContent = "Guardar"; }
  });
  return m;
}
const val = (/** @type {HTMLFormElement} */ f, /** @type {string} */ n) => String(/** @type {HTMLInputElement} */ (f.elements.namedItem(n))?.value ?? "").trim();
const marcado = (/** @type {HTMLFormElement} */ f, /** @type {string} */ n) => !!/** @type {HTMLInputElement} */ (f.elements.namedItem(n))?.checked;
const campoArchivo = (/** @type {any} */ actual) => `<div class="field"><label for="aula-archivo">Archivo (opcional · máx. 10 MB)</label>
  <input id="aula-archivo" name="archivo" type="file" accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.zip,.jpg,.jpeg,.png,.webp">${actual?.archivo_nombre ? `<small class="muted">Actual: ${esc(actual.archivo_nombre)} (se reemplaza si eliges otro)</small>` : ""}</div>`;
const campo = (/** @type {string} */ n, /** @type {string} */ label, /** @type {string} */ extra = "", /** @type {string} */ v = "") =>
  `<div class="field"><label for="aula-${n}">${label}</label><input id="aula-${n}" name="${n}" value="${esc(v)}" ${extra}></div>`;

/** @param {CursoMaterial} [m] */
function formMaterial(m) {
  const curso = cursoActual();
  if (!curso?.id) return;
  const temas = [...new Set(datos.materiales.map((x) => x.tema))];
  formularioAula({
    titulo: m ? "Editar material" : "Agregar material",
    cuerpo: `<div class="field"><label for="aula-tipo">Tipo</label><select id="aula-tipo" name="tipo">
        ${[["documento", "Documento"], ["enlace", "Enlace"], ["aviso", "Aviso"]].map(([v, l]) => `<option value="${v}" ${(m?.tipo || "documento") === v ? "selected" : ""}>${l}</option>`).join("")}</select></div>
      ${campo("titulo", "Título", "required maxlength=160", m?.titulo)}
      <div class="field"><label for="aula-tema">Tema o semana</label><input id="aula-tema" name="tema" list="aula-temas" maxlength="80" value="${esc(m?.tema || "General")}"><datalist id="aula-temas">${temas.map((t) => `<option value="${esc(t)}">`).join("")}</datalist></div>
      <div class="field"><label for="aula-descripcion">Descripción (opcional)</label><textarea id="aula-descripcion" name="descripcion" rows="3" maxlength="4000">${esc(m?.descripcion || "")}</textarea></div>
      ${campo("url", "Enlace (solo si el tipo es Enlace)", "type=url placeholder=https://", m?.url || "")}
      ${campoArchivo(m)}
      <label class="check-row"><input type="checkbox" name="publicado" ${m?.publicado === false ? "" : "checked"}> Visible para los estudiantes</label>`,
    alGuardar: async (f, archivo) => {
      const tipo = /** @type {"documento" | "enlace" | "aviso"} */ (val(f, "tipo"));
      const titulo = val(f, "titulo");
      if (!titulo) throw new Error("Escribe un título.");
      const url = val(f, "url");
      if (tipo === "enlace" && !/^https?:\/\//i.test(url)) throw new Error("El enlace debe empezar con http:// o https://");
      await api.aulaGuardar("curso_materiales", {
        id: m?.id, colegio_id: curso.colegio_id || DB.cid, curso_id: curso.id, tipo, titulo, tema: val(f, "tema") || "General",
        descripcion: val(f, "descripcion"), url: tipo === "enlace" ? url : null, publicado: marcado(f, "publicado"),
      }, archivo);
    },
  });
}

/** @param {CursoActividad} [a] */
function formActividad(a) {
  const curso = cursoActual();
  if (!curso?.id) return;
  // datetime-local usa hora local del navegador; se guarda en ISO UTC
  const local = a?.fecha_limite ? new Date(new Date(a.fecha_limite).getTime() - new Date(a.fecha_limite).getTimezoneOffset() * 60000).toISOString().slice(0, 16) : "";
  formularioAula({
    titulo: a ? "Editar actividad" : "Nueva actividad",
    cuerpo: `${campo("titulo", "Título", "required maxlength=160", a?.titulo)}
      <div class="field"><label for="aula-instrucciones">Instrucciones</label><textarea id="aula-instrucciones" name="instrucciones" rows="4" maxlength="8000">${esc(a?.instrucciones || "")}</textarea></div>
      <div class="row2">${campo("fecha_limite", "Fecha límite (opcional)", "type=datetime-local", local)}${campo("puntaje_max", "Puntaje máximo", "type=number min=1 max=100 step=0.5", String(a?.puntaje_max ?? 20))}</div>
      ${campoArchivo(a)}
      <label class="check-row"><input type="checkbox" name="publicado" ${a?.publicado === false ? "" : "checked"}> Visible para los estudiantes</label>`,
    alGuardar: async (f, archivo) => {
      const titulo = val(f, "titulo");
      if (!titulo) throw new Error("Escribe un título.");
      const puntaje = Number(val(f, "puntaje_max") || 20);
      if (!(puntaje > 0 && puntaje <= 100)) throw new Error("El puntaje debe estar entre 1 y 100.");
      const limite = val(f, "fecha_limite");
      await api.aulaGuardar("curso_actividades", {
        id: a?.id, colegio_id: curso.colegio_id || DB.cid, curso_id: curso.id, titulo, instrucciones: val(f, "instrucciones"),
        fecha_limite: limite ? new Date(limite).toISOString() : null, puntaje_max: puntaje, publicado: marcado(f, "publicado"),
      }, archivo);
    },
  });
}

/* ------------------------------ Docentes del curso (administrador) ------------------------------ */
async function gestionarDocentes() {
  const curso = cursoActual();
  if (!curso?.id) return;
  const cursoId = curso.id;
  const personal = /** @type {any[]} */ (await api.personalListar().catch(() => [])).filter((p) => /docente|coordinador/i.test(String(p.rol || "")));
  const m = openModal({ title: `Docentes de ${curso.nombre}`, body: `<div id="aula-doc-lista"></div>`, footer: `<button class="btn btn-outline" data-cancel>Cerrar</button>` });
  m.el.querySelector("[data-cancel]")?.addEventListener("click", m.close);
  const lista = /** @type {HTMLElement} */ (m.el.querySelector("#aula-doc-lista"));
  const pintarLista = () => {
    lista.innerHTML = personal.length ? `<ul class="log-list">${personal.map((p) => {
      const on = datos.docentes.includes(p.id);
      return `<li><div class="person"><span class="avatar" aria-hidden="true">${icon("users", 16)}</span><div><strong>${esc(p.nombre || p.email)}</strong><small>${esc(p.rol)}</small></div></div>
        <div class="log-right"><button class="btn btn-sm ${on ? "btn-outline" : "btn-primary"}" data-doc="${p.id}" data-on="${on ? 1 : 0}">${on ? "Quitar" : "Asignar"}</button></div></li>`;
    }).join("")}</ul>` : emptyState("Sin docentes", "Crea al personal docente en Sistema → Personal y accesos.", "users");
  };
  pintarLista();
  lista.addEventListener("click", async (e) => {
    const b = /** @type {HTMLElement | null} */ (/** @type {HTMLElement} */ (e.target).closest("[data-doc]"));
    if (!b) return;
    const uid = /** @type {string} */ (b.dataset.doc);
    /** @type {HTMLButtonElement} */ (b).disabled = true;
    try {
      if (b.dataset.on === "1") { await api.aulaQuitarDocente(cursoId, uid); datos.docentes = datos.docentes.filter((x) => x !== uid); }
      else { await api.aulaAsignarDocente(cursoId, uid); datos.docentes.push(uid); }
      pintarLista(); pintar();
    } catch (/** @type {any} */ ex) { toast(ex.message, "error"); /** @type {HTMLButtonElement} */ (b).disabled = false; }
  });
}

registerActions({
  "aula-tab": (/** @type {HTMLElement} */ el) => { st.tab = el.dataset.tab === "actividades" ? "actividades" : "material"; pintar(); },
  "aula-mat-new": () => formMaterial(),
  "aula-mat-edit": (/** @type {HTMLElement} */ el) => formMaterial(datos.materiales.find((m) => m.id === el.dataset.id)),
  "aula-act-new": () => formActividad(),
  "aula-act-edit": (/** @type {HTMLElement} */ el) => formActividad(datos.actividades.find((a) => a.id === el.dataset.id)),
  "aula-docentes": () => { gestionarDocentes(); },
  "aula-abrir": async (/** @type {HTMLElement} */ el) => {
    try { window.open(await api.aulaUrlArchivo(/** @type {string} */ (el.dataset.path)), "_blank", "noopener"); }
    catch (/** @type {any} */ e) { toast(e.message, "error"); }
  },
  "aula-mat-del": async (/** @type {HTMLElement} */ el) => eliminar("curso_materiales", datos.materiales.find((m) => m.id === el.dataset.id), "este material"),
  "aula-act-del": async (/** @type {HTMLElement} */ el) => eliminar("curso_actividades", datos.actividades.find((a) => a.id === el.dataset.id), "esta actividad"),
});

/**
 * @param {"curso_materiales" | "curso_actividades"} tabla
 * @param {CursoMaterial | CursoActividad | undefined} fila
 * @param {string} que
 */
async function eliminar(tabla, fila, que) {
  if (!fila?.id) return;
  if (!(await confirmDialog({ title: "Eliminar", message: `¿Eliminar ${que} «${esc(fila.titulo)}»? Los estudiantes dejarán de verlo y no se puede deshacer.` }))) return;
  try { await api.aulaEliminar(tabla, { id: fila.id, archivo_path: fila.archivo_path }); toast("Eliminado", "success"); await cargar(); }
  catch (/** @type {any} */ e) { toast("No se pudo eliminar: " + e.message, "error"); }
}

export { fmtDate };
