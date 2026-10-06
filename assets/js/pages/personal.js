// @ts-check
// Personal y accesos (solo administrador): crea las cuentas de los docentes (correo + contraseña), asigna su rol,
// cambia contraseñas y quita accesos, todo desde el panel. Crear cuentas lo hace la Edge Function «gestionar-personal».
import { api } from "../api.js";
import { DB } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { esc, initials } from "../utils.js";

const ROLES = [
  { value: "Docente", label: "Docente — asistencia, código y comunicados" },
  { value: "Coordinador", label: "Coordinador — como docente, solo su carrera" },
  { value: "Administrador", label: "Administrador — acceso total" },
];
/** @type {Record<string, string>} */
const TONO = { administrador: "navy", docente: "green", coordinador: "amber" };
/** @type {any[]} */
let lista = [];

const carreras = () => [{ value: "", label: "—" }, ...DB.niveles.map((/** @type {any} */ n) => ({ value: n.nombre || n, label: n.nombre || n }))];
const root = () => /** @type {HTMLElement} */ (document.getElementById("page-root"));

async function cargar() {
  const cont = /** @type {HTMLElement} */ (root().querySelector("#pers-tbl"));
  try { lista = await api.personalListar(); }
  catch (/** @type {any} */ e) {
    cont.innerHTML = emptyState("No se pudo cargar el personal", /does not exist|function|schema cache/i.test(e.message) ? "Falta aplicar la migración 005 (supabase/migrations/005_personal_avisos.sql)." : e.message, "alert");
    return;
  }
  cont.innerHTML = lista.length ? `<div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Carrera</th><th></th></tr></thead><tbody>
    ${lista.map((p) => `<tr><td><span class="pers-nombre"><span class="avatar" data-foto="${esc(p.foto_path || "")}">${esc(initials(p.nombre || p.email))}</span><strong>${esc(p.nombre || "—")}</strong></span></td><td>${esc(p.email)}</td>
      <td>${badge(p.rol, TONO[String(p.rol).toLowerCase()] || "neutral")}</td><td>${esc(p.carrera || "—")}</td>
      <td class="t-right nowrap"><button class="btn btn-outline btn-sm" data-action="pers-pass" data-id="${p.id}" title="Cambiar su contraseña">Contraseña</button>
      <button class="icon-only" title="Cambiar rol" aria-label="Cambiar rol de ${esc(p.email)}" data-action="pers-edit" data-email="${esc(p.email)}">${icon("edit", 16)}</button>
      <button class="icon-only danger" title="Quitar acceso" aria-label="Quitar acceso a ${esc(p.email)}" data-action="pers-del" data-id="${p.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
    : emptyState("Sin personal", "Crea la primera cuenta con «Crear usuario».", "users");
  cont.querySelectorAll(".avatar[data-foto]").forEach(async (/** @type {any} */ el) => {
    if (!el.dataset.foto) return;
    const url = await api.fotoPersonalUrl(el.dataset.foto).catch(() => null);
    if (url) { el.style.backgroundImage = `url("${url}")`; el.style.backgroundSize = "cover"; el.textContent = ""; }
  });
}

function formCrear() {
  formModal({
    title: "Crear usuario del personal",
    submitLabel: "Crear usuario",
    fields: [
      { name: "nombre", label: "Nombre completo", required: true, placeholder: "Ej: Rosa Mendoza Ruiz" },
      { name: "email", label: "Correo (será su usuario)", required: true, placeholder: "docente@instituto.pe" },
      { name: "password", label: "Contraseña (mínimo 8 caracteres)", type: "password", required: true },
      { name: "rol", label: "Rol", type: "select", options: ROLES, value: "Docente", half: true },
      { name: "carrera", label: "Carrera (solo coordinador)", type: "select", options: carreras(), half: true },
    ],
    onSubmit: async (v) => {
      if ((v.password || "").length < 8) throw new Error("La contraseña debe tener al menos 8 caracteres.");
      await api.personalCrear(/** @type {any} */ (v));
      toast("Usuario creado. Entrégale su correo y contraseña.", "success");
      await cargar();
    },
  });
}

/** @param {any} p */
function formRol(p) {
  formModal({
    title: "Dar acceso a una cuenta existente",
    submitLabel: "Guardar",
    fields: [
      { name: "email", label: "Correo de la cuenta", required: true, value: p?.email },
      { name: "nombre", label: "Nombre (opcional)", value: p?.nombre },
      { name: "rol", label: "Rol", type: "select", options: ROLES, value: p?.rol || "Docente" },
      { name: "carrera", label: "Carrera (solo coordinador)", type: "select", options: carreras(), value: p?.carrera || "" },
    ],
    onSubmit: async (v) => { await api.personalAsignar(v.email, v.rol, v.carrera, v.nombre); toast("Acceso guardado", "success"); await cargar(); },
  });
}

export const personalPage = {
  id: "personal", title: "Personal y accesos", icon: "users", group: "Gestión", soloAdmin: true,
  /** @param {HTMLElement} el */
  async render(el) {
    el.innerHTML = `${pageHead("Personal y accesos", "Crea las cuentas de tus docentes y define qué puede hacer cada uno.",
      `<button class="btn btn-outline" data-action="pers-existente">Dar acceso a cuenta existente</button><button class="btn btn-primary" data-action="pers-crear">${icon("plus", 16)} Crear usuario</button>`)}
      <div class="card"><p class="muted" style="margin:0"><b>Administrador:</b> todo (carreras, ciclos, alumnos, cursos, respaldo). <b>Docente:</b> ver asistencias, registrar asistencia o tardanza, justificar, avisar a apoderados, publicar comunicados y compartir el código de registro. <b>Coordinador:</b> lo de un docente pero solo en su carrera.
        Cada persona entra con su correo y contraseña desde la web, el programa de PC o la app, y puede subir su foto en <b>Mi perfil</b>.</p></div>
      <div class="card flush" id="pers-tbl">${skeleton(4)}</div>`;
    await cargar();
  },
};

registerActions({
  "pers-crear": () => formCrear(),
  "pers-existente": () => formRol(null),
  "pers-edit": (/** @type {HTMLElement} */ el) => formRol(lista.find((x) => x.email === el.dataset.email)),
  "pers-pass": (/** @type {HTMLElement} */ el) => {
    const p = lista.find((x) => x.id === el.dataset.id);
    formModal({
      title: `Nueva contraseña para ${p?.email || ""}`, submitLabel: "Cambiar contraseña",
      fields: [{ name: "password", label: "Nueva contraseña (mínimo 8 caracteres)", type: "password", required: true }],
      onSubmit: async (v) => {
        if ((v.password || "").length < 8) throw new Error("La contraseña debe tener al menos 8 caracteres.");
        await api.personalPassword(p.id, v.password);
        toast("Contraseña cambiada. Avísale a la persona.", "success");
      },
    });
  },
  "pers-del": async (/** @type {HTMLElement} */ el) => {
    const p = lista.find((x) => x.id === el.dataset.id);
    if (!(await confirmDialog({ title: "Quitar acceso", message: `¿Quitar el acceso de <b>${esc(p?.email || "")}</b>? Deja de poder entrar al sistema. Su cuenta se elimina por completo.`, confirmLabel: "Quitar acceso" }))) return;
    try { await api.personalEliminar(p.id); toast("Acceso quitado", "success"); await cargar(); }
    catch (/** @type {any} */ e) { toast(e.message, "error"); }
  },
});
