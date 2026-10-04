// Personal y accesos (solo administrador): asigna el rol de cada cuenta del instituto sin tocar SQL.
// La cuenta (correo + contraseña) se crea antes en Supabase → Authentication → Users; aquí se le da el rol.
import { api } from "../api.js";
import { DB } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { esc } from "../utils.js";

const ROLES = [
  { value: "Administrador", label: "Administrador — acceso total" },
  { value: "Docente", label: "Docente — asistencia, código y comunicados" },
  { value: "Coordinador", label: "Coordinador — como docente, solo su carrera" },
];
const TONO = { administrador: "navy", docente: "green", coordinador: "amber" };
let lista = [];

async function cargar(root) {
  const cont = root.querySelector("#pers-tbl");
  try { lista = await api.personalListar(); }
  catch (e) {
    cont.innerHTML = emptyState("No se pudo cargar el personal", /does not exist|function|schema cache/i.test(e.message) ? "Falta aplicar la migración 005 (supabase/migrations/005_personal_avisos.sql)." : e.message, "alert");
    return;
  }
  cont.innerHTML = lista.length ? `<div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Correo</th><th>Rol</th><th>Carrera</th><th></th></tr></thead><tbody>
    ${lista.map((p) => `<tr><td><strong>${esc(p.nombre || "—")}</strong></td><td>${esc(p.email)}</td>
      <td>${badge(p.rol, TONO[String(p.rol).toLowerCase()] || "neutral")}</td><td>${esc(p.carrera || "—")}</td>
      <td class="t-right nowrap"><button class="icon-only" title="Cambiar rol" aria-label="Cambiar rol de ${esc(p.email)}" data-action="pers-edit" data-email="${esc(p.email)}">${icon("edit", 16)}</button>
      <button class="icon-only danger" title="Quitar acceso" aria-label="Quitar acceso a ${esc(p.email)}" data-action="pers-del" data-id="${p.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
    : emptyState("Sin personal", "Asigna un rol a la primera cuenta.", "users");
}

function formulario(p) {
  formModal({
    title: p ? "Cambiar rol" : "Dar acceso al personal",
    submitLabel: "Guardar",
    fields: [
      { name: "email", label: "Correo de la cuenta", required: true, value: p?.email, placeholder: "docente@instituto.pe" },
      { name: "nombre", label: "Nombre (opcional)", value: p?.nombre },
      { name: "rol", label: "Rol", type: "select", options: ROLES, value: p?.rol || "Docente" },
      { name: "carrera", label: "Carrera (solo coordinador)", type: "select", options: [{ value: "", label: "—" }, ...DB.niveles.map((n) => ({ value: n.nombre || n, label: n.nombre || n }))], value: p?.carrera || "" },
    ],
    onSubmit: async (v) => {
      await api.personalAsignar(v.email, v.rol, v.carrera, v.nombre);
      toast("Acceso guardado", "success");
      await cargar(document.getElementById("page-root"));
    },
  });
}

export const personalPage = {
  id: "personal", title: "Personal y accesos", icon: "users", group: "Gestión", soloAdmin: true,
  async render(root) {
    root.innerHTML = `${pageHead("Personal y accesos", "Quién puede entrar al sistema y qué puede hacer.",
      `<button class="btn btn-primary" data-action="pers-new">${icon("plus", 16)} Dar acceso</button>`)}
      <div class="card"><h3 style="margin-top:0">Cómo dar acceso a un docente</h3>
        <ol class="muted" style="margin:6px 0 0;padding-left:20px;line-height:1.7">
          <li>En Supabase → <b>Authentication → Users → Add user</b>, crea la cuenta (correo y contraseña) y entrégasela al docente.</li>
          <li>Aquí pulsa <b>Dar acceso</b>, escribe ese correo y elige el rol.</li>
        </ol>
        <p class="muted" style="margin:10px 0 0"><b>Administrador:</b> todo (carreras, ciclos, alumnos, cursos, respaldo). <b>Docente:</b> ver asistencias, registrar asistencia o tardanza, justificar, avisar a apoderados, publicar comunicados y compartir el código de registro. <b>Coordinador:</b> lo de un docente pero solo en su carrera.</p></div>
      <div class="card flush" id="pers-tbl">${skeleton(4)}</div>`;
    await cargar(root);
  },
};

registerActions({
  "pers-new": () => formulario(null),
  "pers-edit": (el) => formulario(lista.find((x) => x.email === el.dataset.email)),
  "pers-del": async (el) => {
    const p = lista.find((x) => x.id === el.dataset.id);
    if (!(await confirmDialog({ title: "Quitar acceso", message: `¿Quitar el acceso de <b>${esc(p?.email || "")}</b>? La cuenta no se borra; solo deja de entrar al sistema.`, confirmLabel: "Quitar acceso" }))) return;
    try { await api.personalQuitar(p.id); toast("Acceso quitado", "success"); await cargar(document.getElementById("page-root")); }
    catch (e) { toast(e.message, "error"); }
  },
});
