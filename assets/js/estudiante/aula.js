// @ts-check
// Aula del estudiante: sus cursos (carrera y ciclo), con el material y las actividades que publican los docentes.
import { validarArchivoAula } from "../api-aula.js";
import { badge, emptyState, icon, openModal, registerActions, skeleton, toast } from "../ui.js";
import { esc } from "../utils.js";

/** @typedef {import('../tipos.d.ts').BackendEstudiante} BackendEstudiante */
/** @typedef {import('../tipos.d.ts').Curso} Curso */
/** @typedef {import('../tipos.d.ts').CursoActividad} CursoActividad */
/** @typedef {import('../tipos.d.ts').CursoEntrega} CursoEntrega */

const fechaLimite = (/** @type {string} */ iso) => new Date(iso).toLocaleString("es-PE", { timeZone: "America/Lima", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" });
const tamano = (/** @type {number | null | undefined} */ b) => (b ? (b >= 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`) : "");

/**
 * @param {{ api: BackendEstudiante, user: { id: string }, root: () => HTMLElement, volver: () => void }} ctx
 */
export function iniciarAulaEstudiante(ctx) {
  /** @type {Curso[]} */
  let cursos = [];
  /** @type {CursoActividad[]} */
  let actividadesCurso = [];
  /** @type {Map<string, CursoEntrega | null>} */
  const misEntregas = new Map();
  let cursoAbierto = "";

  const cabecera = (/** @type {string} */ titulo, /** @type {string} */ accion, /** @type {string} */ etiqueta) =>
    `<div class="est-card"><button type="button" class="btn btn-outline btn-sm" data-action="${accion}">${icon("history", 15)} ${etiqueta}</button><h1 style="margin-top:12px">${esc(titulo)}</h1>`;

  async function lista() {
    ctx.root().innerHTML = `${cabecera("Mis cursos", "est-aula-volver", "Mi carnet")}<div id="aula-est">${skeleton(3)}</div></div>`;
    const caja = /** @type {HTMLElement} */ (ctx.root().querySelector("#aula-est"));
    try {
      cursos = await ctx.api.aulaCursos(ctx.user);
      caja.innerHTML = cursos.length
        ? `<div class="aula-est-lista">${cursos.map((c) => `<button type="button" class="aula-est-curso" data-action="est-aula-curso" data-id="${esc(c.id || "")}"><strong>${esc(c.nombre)}</strong><small>${esc(c.docente || "Docente por asignar")}</small></button>`).join("")}</div>`
        : emptyState("Aún no hay cursos", "Tu instituto todavía no publicó cursos para tu carrera y ciclo.", "book");
    } catch (/** @type {any} */ e) {
      caja.innerHTML = emptyState("No se pudieron cargar tus cursos", e.message || "Revisa tu conexión e inténtalo de nuevo.", "alert");
    }
  }

  /** @param {string} id */
  async function curso(id) {
    const c = cursos.find((x) => x.id === id);
    if (!c) return lista();
    ctx.root().innerHTML = `${cabecera(c.nombre, "est-aula-lista", "Mis cursos")}<p class="est-sub">${esc(c.docente || "")}</p><div id="aula-est">${skeleton(4)}</div></div>`;
    const caja = /** @type {HTMLElement} */ (ctx.root().querySelector("#aula-est"));
    try {
      const [materiales, actividades] = await Promise.all([ctx.api.aulaMateriales(id), ctx.api.aulaActividades(id)]);
      actividadesCurso = actividades; cursoAbierto = id; misEntregas.clear();
      await Promise.all(actividades.map(async (a) => { misEntregas.set(String(a.id), await ctx.api.aulaMiEntrega(String(a.id)).catch(() => null)); }));
      const ahora = Date.now();
      const acts = actividades.map((a) => {
        const vencida = a.fecha_limite ? new Date(a.fecha_limite).getTime() < ahora : false;
        return `<li class="aula-est-item"><div><strong>${esc(a.titulo)}</strong>
          <small>${a.fecha_limite ? `Hasta ${esc(fechaLimite(a.fecha_limite))}` : "Sin fecha límite"} · ${a.puntaje_max} pts ${vencida ? badge("Vencida", "red") : ""}</small>
          ${a.instrucciones ? `<p>${esc(a.instrucciones)}</p>` : ""}${estadoEntrega(a, vencida)}</div>${a.archivo_path ? `<button class="btn btn-outline btn-sm" data-action="est-aula-abrir" data-path="${esc(a.archivo_path)}">${icon("download", 14)} ${esc(a.archivo_nombre || "Abrir")} ${esc(tamano(a.archivo_bytes))}</button>` : ""}</li>`;
      }).join("");
      const temas = [...new Set(materiales.map((m) => m.tema))];
      const mats = temas.map((t) => `<h3 class="aula-est-tema">${esc(t)}</h3><ul class="aula-est-items">${materiales.filter((m) => m.tema === t).map((m) => {
        const boton = m.archivo_path
          ? `<button class="btn btn-outline btn-sm" data-action="est-aula-abrir" data-path="${esc(m.archivo_path)}">${icon("download", 14)} ${esc(m.archivo_nombre || "Abrir")} ${esc(tamano(m.archivo_bytes))}</button>`
          : m.url ? `<a class="btn btn-outline btn-sm" target="_blank" rel="noopener noreferrer" href="${esc(m.url)}">Abrir enlace</a>` : "";
        return `<li class="aula-est-item"><div><strong>${esc(m.titulo)}</strong>${m.tipo === "aviso" ? ` ${badge("Aviso", "amber")}` : ""}${m.descripcion ? `<p>${esc(m.descripcion)}</p>` : ""}</div>${boton}</li>`;
      }).join("")}</ul>`).join("");
      caja.innerHTML = `<h2 class="aula-est-h">Actividades</h2>${acts ? `<ul class="aula-est-items">${acts}</ul>` : emptyState("Sin actividades", "Tu docente aún no publicó actividades.", "calendar")}
        <h2 class="aula-est-h">Material</h2>${mats || emptyState("Sin material", "Tu docente aún no subió material.", "book")}`;
    } catch (/** @type {any} */ e) {
      caja.innerHTML = emptyState("No se pudo cargar el curso", e.message || "Inténtalo de nuevo.", "alert");
    }
  }

  /**
   * @param {CursoActividad} a
   * @param {boolean} vencida
   */
  function estadoEntrega(a, vencida) {
    const e = misEntregas.get(String(a.id));
    const calificada = e?.nota != null;
    const boton = calificada ? "" : `<button class="btn btn-primary btn-sm" type="button" data-action="est-aula-entregar" data-id="${esc(String(a.id))}">${icon("upload", 14)} ${e ? "Cambiar mi entrega" : "Entregar"}</button>`;
    if (!e) return `<div class="aula-est-entrega">${vencida ? `${badge("No entregada", "red")} ` : ""}${boton}</div>`;
    return `<div class="aula-est-entrega">${calificada ? badge(`Nota: ${e.nota} / ${a.puntaje_max}`, "green") : badge("Entregada", "blue")}${e.tardia ? ` ${badge("Tardía", "amber")}` : ""}
      ${e.archivo_nombre ? `<small>${esc(e.archivo_nombre)}</small>` : ""}${e.comentario ? `<p><b>Comentario del docente:</b> ${esc(e.comentario)}</p>` : ""} ${boton}</div>`;
  }

  /** @param {string} actividadId */
  function formularioEntrega(actividadId) {
    const a = actividadesCurso.find((x) => x.id === actividadId);
    if (!a) return;
    const previa = misEntregas.get(actividadId);
    const m = openModal({
      title: `Entregar · ${a.titulo}`,
      body: `<form id="est-entrega-form" novalidate>
        <div class="field"><label for="est-entrega-texto">Tu respuesta</label><textarea id="est-entrega-texto" name="texto" rows="5" maxlength="8000">${esc(previa?.texto || "")}</textarea></div>
        <div class="field"><label for="est-entrega-archivo">Archivo (opcional · máx. 10 MB)</label><input id="est-entrega-archivo" name="archivo" type="file" accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.txt,.zip,.jpg,.jpeg,.png,.webp"></div>
        <p class="err-msg" id="est-entrega-err" role="alert" hidden></p></form>`,
      footer: `<button class="btn btn-outline" type="button" data-cancel>Cancelar</button><button class="btn btn-primary" type="submit" form="est-entrega-form">Enviar</button>`,
    });
    m.el.querySelector("[data-cancel]")?.addEventListener("click", m.close);
    const f = /** @type {HTMLFormElement} */ (m.el.querySelector("#est-entrega-form"));
    const err = /** @type {HTMLElement} */ (m.el.querySelector("#est-entrega-err"));
    const enviar = /** @type {HTMLButtonElement} */ (m.el.querySelector("button[type=submit]"));
    f.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      err.hidden = true;
      const texto = String(/** @type {HTMLTextAreaElement} */ (f.elements.namedItem("texto")).value).trim();
      const archivo = /** @type {HTMLInputElement} */ (f.elements.namedItem("archivo")).files?.[0];
      const problema = archivo ? validarArchivoAula(archivo) : null;
      if (problema) { err.textContent = problema; err.hidden = false; return; }
      enviar.disabled = true; enviar.textContent = archivo ? "Subiendo…" : "Enviando…";
      try {
        await ctx.api.aulaEntregar(ctx.user, a, texto, archivo);
        m.close(); toast("Entrega enviada", "success"); await curso(cursoAbierto);
      } catch (/** @type {any} */ ex) { err.textContent = ex.message; err.hidden = false; enviar.disabled = false; enviar.textContent = "Enviar"; }
    });
  }

  registerActions({
    "est-aula-entregar": (/** @type {HTMLElement} */ b) => formularioEntrega(b.dataset.id || ""),
    "est-aula": lista,
    "est-aula-lista": lista,
    "est-aula-volver": () => ctx.volver(),
    "est-aula-curso": (/** @type {HTMLElement} */ b) => curso(b.dataset.id || ""),
    "est-aula-abrir": async (/** @type {HTMLElement} */ b) => {
      try { window.open(await ctx.api.aulaUrlArchivo(b.dataset.path || ""), "_blank", "noopener"); }
      catch (/** @type {any} */ e) { toast(e.message || "No se pudo abrir el archivo", "error"); }
    },
  });
}
