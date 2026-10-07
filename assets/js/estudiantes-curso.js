// @ts-check
// Gestión de estudiantes del curso: lista de alumnos (automáticos y manuales) y diálogo para agregar alumnos manualmente.
import { api } from "./api.js";
import { DB } from "./state.js";
import { confirmDialog, emptyState, icon, openModal, toast } from "./ui.js";
import { perteneceACurso } from "./stats.js";
import { cicloCorto, esc, initials, norm } from "./utils.js";

/** @typedef {import('./tipos.d.ts').Curso} Curso */
/** @typedef {import('./tipos.d.ts').Alumno} Alumno */
/** @typedef {import('./tipos.d.ts').CursoAlumno} CursoAlumno */

/**
 * Determina el tipo de pertenencia de un alumno a un curso:
 * - "manual": si su id está en la lista/conjunto de matriculados manuales.
 * - "ciclo": si pertenece automáticamente por su carrera/ciclo y no es manual.
 * - "ninguno": si no pertenece al curso.
 * @param {any} alumno
 * @param {any} curso
 * @param {Set<string> | string[] | { alumno_id?: string }[]} [manuales]
 * @returns {"manual" | "ciclo" | "ninguno"}
 */
export function tipoPertenencia(alumno, curso, manuales) {
  if (!alumno || !curso) return "ninguno";
  const id = alumno.id;
  if (id && manuales) {
    if (manuales instanceof Set && manuales.has(id)) return "manual";
    if (Array.isArray(manuales)) {
      for (const m of manuales) {
        if (m === id || (typeof m === "object" && m && m.alumno_id === id)) return "manual";
      }
    }
  }
  if (curso.nivel && alumno.nivel !== curso.nivel) return "ninguno";
  if (curso.grado && alumno.grado !== curso.grado) return "ninguno";
  return "ciclo";
}

/**
 * Obtiene los candidatos que pueden ser agregados manualmente a un curso:
 * alumnos activos, aprobados y que NO pertenezcan ya al curso (ni por ciclo ni manualmente).
 * @template {{ id: string, nombre: string, nivel: string, grado?: string, estado?: string, aprobado?: boolean | null, codigo?: string }} A
 * @param {A[]} alumnos
 * @param {any} curso
 * @param {Set<string> | string[] | { alumno_id?: string }[]} [manuales]
 * @returns {A[]}
 */
export function candidatosParaCurso(alumnos, curso, manuales) {
  return alumnos
    .filter((a) => (a.estado ?? "ACTIVO") === "ACTIVO" && a.aprobado !== false && !perteneceACurso(a, curso, manuales))
    .sort((x, y) => x.nombre.localeCompare(y.nombre, "es"));
}

/**
 * Filtra una lista de alumnos por texto de búsqueda (nombre, código o carrera).
 * @template {{ nombre: string, codigo?: string, nivel?: string }} A
 * @param {A[]} alumnos
 * @param {string} busqueda
 * @returns {A[]}
 */
export function filtrarPorBusqueda(alumnos, busqueda) {
  const q = norm(busqueda);
  if (!q) return alumnos;
  return alumnos.filter((a) =>
    norm(a.nombre).includes(q) ||
    norm(String(a.codigo || "")).includes(q) ||
    (a.nivel ? norm(a.nivel).includes(q) : false)
  );
}

/**
 * Abre el diálogo de gestión de estudiantes de un curso.
 * Permite ver la lista de alumnos actuales (distinguiendo automáticos con «Por su ciclo»
 * y manuales con «Manual» + «Quitar»), y agregar nuevos estudiantes activos con buscador y selección múltiple.
 * @param {Curso} curso
 * @param {() => void} [onActualizar]
 */
export async function abrirEstudiantesCurso(curso, onActualizar) {
  if (!curso?.id) return;
  const cursoId = String(curso.id);

  // Sincroniza las filas de curso_alumnos de este curso si es posible
  try {
    const remotas = await api.cursoAlumnos(cursoId);
    if (Array.isArray(remotas)) {
      DB.curso_alumnos = [
        ...(DB.curso_alumnos || []).filter((ca) => ca.curso_id !== cursoId),
        ...remotas,
      ];
    }
  } catch {
    // Si falla o está sin conexión, se conserva la copia local de DB.curso_alumnos
  }

  let vista = "lista"; // "lista" | "agregar"
  let filtroLista = "";
  let busquedaAgregar = "";
  /** @type {Set<string>} */
  const seleccionados = new Set();
  let guardando = false;

  const m = openModal({
    title: `Estudiantes · ${curso.nombre}`,
    wide: true,
    body: `<div id="ec-contenedor" aria-live="polite"></div>`,
    footer: `<button type="button" class="btn btn-outline" id="ec-btn-cerrar">Cerrar</button>`,
  });

  const contenedor = /** @type {HTMLElement} */ (m.el.querySelector("#ec-contenedor"));
  m.el.querySelector("#ec-btn-cerrar")?.addEventListener("click", m.close);

  function pintar() {
    if (!contenedor.isConnected) return;
    if (vista === "lista") pintarLista();
    else pintarAgregar();
  }

  /* ---------------------- Vista 1: Lista del curso ---------------------- */
  function pintarLista() {
    const manualesIds = new Set(
      (DB.curso_alumnos || [])
        .filter((ca) => ca.curso_id === cursoId)
        .map((ca) => ca.alumno_id)
    );

    const todos = DB.alumnos
      .filter((a) => (a.estado ?? "ACTIVO") === "ACTIVO" && a.aprobado !== false && perteneceACurso(a, curso, manualesIds))
      .sort((x, y) => x.nombre.localeCompare(y.nombre, "es"));

    const filtrados = filtrarPorBusqueda(todos, filtroLista);

    const nAuto = todos.filter((a) => tipoPertenencia(a, curso, manualesIds) === "ciclo").length;
    const nManual = todos.filter((a) => tipoPertenencia(a, curso, manualesIds) === "manual").length;

    contenedor.innerHTML = `
      <div class="card-head" style="margin-bottom:12px;gap:8px;flex-wrap:wrap">
        <div>
          <span class="muted">${todos.length} estudiante${todos.length === 1 ? "" : "s"} (${nAuto} por su ciclo, ${nManual} manual${nManual === 1 ? "" : "es"})</span>
        </div>
        <button type="button" class="btn btn-primary btn-sm" id="ec-ir-agregar">
          ${icon("plus", 14)} Agregar alumnos
        </button>
      </div>
      <div style="margin-bottom:12px">
        <input class="input" id="ec-filtro-lista" type="search" placeholder="Filtrar estudiantes en este curso…" aria-label="Filtrar estudiantes del curso" value="${esc(filtroLista)}" autocomplete="off">
      </div>
      ${filtrados.length ? `
        <ul class="pick-list" style="max-height:380px;overflow-y:auto;border-top:1px solid var(--line);list-style:none;padding:0;margin:0">
          ${filtrados.map((a) => {
            const manual = manualesIds.has(a.id);
            return `
              <li class="check-row" style="cursor:default;display:flex;align-items:center;justify-content:space-between;gap:12px">
                <div class="person">
                  <span class="avatar" aria-hidden="true">${esc(initials(a.nombre))}</span>
                  <div>
                    <strong>${esc(a.nombre)}</strong>
                    <small>${esc(String(a.codigo || ""))} · ${esc(a.nivel)}${a.grado ? " · " + esc(cicloCorto(a.grado, a.nivel)) : ""}</small>
                  </div>
                </div>
                <div style="display:flex;align-items:center;gap:8px;flex-shrink:0">
                  ${manual
                    ? `<span class="badge badge-amber">Manual</span><button type="button" class="btn btn-sm btn-ghost-danger" data-quitar="${esc(a.id)}" aria-label="Quitar a ${esc(a.nombre)} del curso">Quitar</button>`
                    : `<span class="badge badge-neutral">Por su ciclo</span>`}
                </div>
              </li>
            `;
          }).join("")}
        </ul>
      ` : emptyState(todos.length ? "Sin coincidencias" : "Sin estudiantes", todos.length ? "Cambia el término de búsqueda." : "No hay estudiantes activos en este curso.", "users")}`;

    // Eventos de la lista
    const inpFiltro = /** @type {HTMLInputElement | null} */ (contenedor.querySelector("#ec-filtro-lista"));
    inpFiltro?.addEventListener("input", (e) => {
      filtroLista = /** @type {HTMLInputElement} */ (e.target).value;
      pintarLista();
      const nuevoInp = /** @type {HTMLInputElement | null} */ (contenedor.querySelector("#ec-filtro-lista"));
      if (nuevoInp) {
        nuevoInp.focus();
        nuevoInp.setSelectionRange(nuevoInp.value.length, nuevoInp.value.length);
      }
    });

    contenedor.querySelector("#ec-ir-agregar")?.addEventListener("click", () => {
      vista = "agregar";
      seleccionados.clear();
      busquedaAgregar = "";
      pintar();
      contenedor.querySelector("#ec-buscar-candidatos")?.dispatchEvent(new Event("focus"));
    });

    contenedor.querySelectorAll("[data-quitar]").forEach((btn) => {
      btn.addEventListener("click", async (e) => {
        const target = /** @type {HTMLElement} */ (e.currentTarget);
        const alumnoId = target.dataset.quitar;
        if (!alumnoId) return;
        const alumno = DB.alumnos.find((x) => x.id === alumnoId);
        if (!alumno) return;
        const confirm = await confirmDialog({
          title: "Quitar estudiante",
          message: `¿Quitar a <b>${esc(alumno.nombre)}</b> del curso <b>${esc(curso.nombre)}</b>?`,
          confirmLabel: "Quitar",
          danger: true,
        });
        if (!confirm) return;
        try {
          await api.cursoQuitarAlumno(cursoId, alumnoId);
          DB.curso_alumnos = (DB.curso_alumnos || []).filter(
            (ca) => !(ca.curso_id === cursoId && ca.alumno_id === alumnoId)
          );
          toast("Estudiante quitado del curso", "success");
          pintarLista();
          onActualizar?.();
        } catch (/** @type {any} */ ex) {
          toast("No se pudo quitar: " + (ex?.message || "error"), "error");
        }
      });
    });
  }

  /* ---------------------- Vista 2: Agregar alumnos ---------------------- */
  function pintarAgregar() {
    const manualesIds = new Set(
      (DB.curso_alumnos || [])
        .filter((ca) => ca.curso_id === cursoId)
        .map((ca) => ca.alumno_id)
    );

    // Fuente de alumnos: todo el alumnado de la institución activo y aprobado
    const fuente = DB._alumnosTodos && DB._alumnosTodos.length ? DB._alumnosTodos : DB.alumnos;
    const candidatos = candidatosParaCurso(fuente, curso, manualesIds);
    const visibles = filtrarPorBusqueda(candidatos, busquedaAgregar);

    const todosVisiblesSeleccionados = visibles.length > 0 && visibles.every((a) => seleccionados.has(a.id));

    contenedor.innerHTML = `
      <div class="card-head" style="margin-bottom:12px;gap:8px;flex-wrap:wrap">
        <button type="button" class="btn btn-outline btn-sm" id="ec-volver-lista">
          ${icon("arrow-left", 14)} Volver
        </button>
        <strong>Agregar estudiantes a ${esc(curso.nombre)}</strong>
      </div>
      <div style="margin-bottom:10px">
        <input class="input" id="ec-buscar-candidatos" type="search" placeholder="Buscar por nombre, código o carrera…" aria-label="Buscar alumnos para agregar" value="${esc(busquedaAgregar)}" autocomplete="off">
      </div>
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px;flex-wrap:wrap;gap:6px">
        <small class="muted">${visibles.length} disponible${visibles.length === 1 ? "" : "s"} · ${seleccionados.size} seleccionado${seleccionados.size === 1 ? "" : "s"}</small>
        ${visibles.length ? `
          <button type="button" class="btn btn-sm btn-outline" id="ec-sel-todos">
            ${todosVisiblesSeleccionados ? "Deseleccionar visibles" : "Seleccionar visibles"}
          </button>
        ` : ""}
      </div>
      ${visibles.length ? `
        <ul class="pick-list" style="max-height:320px;overflow-y:auto;border-top:1px solid var(--line);list-style:none;padding:0;margin:0">
          ${visibles.map((a) => `
            <li>
              <label class="check-row" style="display:flex;align-items:center;gap:12px">
                <input type="checkbox" data-candidato="${esc(a.id)}" ${seleccionados.has(a.id) ? "checked" : ""} aria-label="Seleccionar ${esc(a.nombre)}">
                <div class="person">
                  <span class="avatar" aria-hidden="true">${esc(initials(a.nombre))}</span>
                  <div>
                    <strong>${esc(a.nombre)}</strong>
                    <small>${esc(String(a.codigo || ""))} · ${esc(a.nivel)}${a.grado ? " · " + esc(cicloCorto(a.grado, a.nivel)) : ""}</small>
                  </div>
                </div>
              </label>
            </li>
          `).join("")}
        </ul>
      ` : emptyState("Sin candidatos", candidatos.length ? "No se encontraron estudiantes con esa búsqueda." : "Todos los estudiantes activos ya pertenecen a este curso.", "users")}` +
      `<div class="card-foot" style="margin-top:14px;display:flex;justify-content:flex-end;gap:10px">
        <button type="button" class="btn btn-outline" id="ec-cancelar-agregar">Cancelar</button>
        <button type="button" class="btn btn-primary" id="ec-confirmar-agregar" ${seleccionados.size === 0 || guardando ? "disabled" : ""}>
          ${guardando ? "Agregando…" : `Agregar seleccionados (${seleccionados.size})`}
        </button>
      </div>`;

    // Eventos de vista agregar
    contenedor.querySelector("#ec-volver-lista")?.addEventListener("click", () => {
      vista = "lista";
      pintar();
    });

    contenedor.querySelector("#ec-cancelar-agregar")?.addEventListener("click", () => {
      vista = "lista";
      pintar();
    });

    const inpBusq = /** @type {HTMLInputElement | null} */ (contenedor.querySelector("#ec-buscar-candidatos"));
    inpBusq?.addEventListener("input", (e) => {
      busquedaAgregar = /** @type {HTMLInputElement} */ (e.target).value;
      pintarAgregar();
      const nuevoInp = /** @type {HTMLInputElement | null} */ (contenedor.querySelector("#ec-buscar-candidatos"));
      if (nuevoInp) {
        nuevoInp.focus();
        nuevoInp.setSelectionRange(nuevoInp.value.length, nuevoInp.value.length);
      }
    });

    contenedor.querySelector("#ec-sel-todos")?.addEventListener("click", () => {
      if (todosVisiblesSeleccionados) {
        visibles.forEach((a) => seleccionados.delete(a.id));
      } else {
        visibles.forEach((a) => seleccionados.add(a.id));
      }
      pintarAgregar();
    });

    contenedor.querySelectorAll("input[data-candidato]").forEach((chk) => {
      chk.addEventListener("change", (e) => {
        const input = /** @type {HTMLInputElement} */ (e.target);
        const id = input.dataset.candidato;
        if (!id) return;
        if (input.checked) seleccionados.add(id);
        else seleccionados.delete(id);
        // Actualiza el botón de confirmación sin repintar toda la lista
        const btnConf = /** @type {HTMLButtonElement | null} */ (contenedor.querySelector("#ec-confirmar-agregar"));
        if (btnConf) {
          btnConf.disabled = seleccionados.size === 0 || guardando;
          btnConf.textContent = `Agregar seleccionados (${seleccionados.size})`;
        }
      });
    });

    contenedor.querySelector("#ec-confirmar-agregar")?.addEventListener("click", async () => {
      if (!seleccionados.size || guardando) return;
      guardando = true;
      const btnConf = /** @type {HTMLButtonElement | null} */ (contenedor.querySelector("#ec-confirmar-agregar"));
      if (btnConf) { btnConf.disabled = true; btnConf.textContent = "Agregando…"; }

      const total = seleccionados.size;
      let agregados = 0;
      let fallos = 0;

      for (const alumnoId of seleccionados) {
        try {
          await api.cursoAgregarAlumno(cursoId, alumnoId, curso.colegio_id || DB.cid || "");
          if (!DB.curso_alumnos.some((ca) => ca.curso_id === cursoId && ca.alumno_id === alumnoId)) {
            DB.curso_alumnos.push({
              curso_id: cursoId,
              alumno_id: alumnoId,
              colegio_id: curso.colegio_id || DB.cid || "",
              creado_en: new Date().toISOString(),
            });
          }
          agregados++;
        } catch {
          fallos++;
        }
      }

      guardando = false;
      toast(
        `${agregados} estudiante${agregados === 1 ? "" : "s"} agregado${agregados === 1 ? "" : "s"}${fallos ? ` · ${fallos} con error` : ""}`,
        fallos ? "warn" : "success"
      );
      vista = "lista";
      pintar();
      onActualizar?.();
    });
  }

  pintar();
}
