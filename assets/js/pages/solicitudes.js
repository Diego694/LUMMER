// Solicitudes de ingreso: estudiantes que se registraron con el código del instituto y esperan aprobación.
// Mientras no se aprueban, su QR no registra asistencia. (Solo el administrador aprueba o rechaza.)
import { api } from "../api.js";
import { DB, loadAll } from "../state.js";
import { badge, confirmDialog, emptyState, icon, pageHead, registerActions, toast } from "../ui.js";
import { esc, etiquetaCiclo, initials } from "../utils.js";
import { revisarEstudiante } from "./mantenimiento.js";

export const pendientes = () => DB.alumnos.filter((a) => a.aprobado === false);

const cuando = (a) => (a.registrado_en || a.creado_en)
  ? new Date(a.registrado_en || a.creado_en).toLocaleString("es-PE", { timeZone: "America/Lima", day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }) : "—";

function pintar(root) {
  const l = pendientes().sort((x, y) => String(y.registrado_en || y.creado_en || "").localeCompare(String(x.registrado_en || x.creado_en || "")));
  const box = root.querySelector("#sol-lista");
  root.querySelector("#sol-total").textContent = l.length ? `${l.length} pendiente${l.length === 1 ? "" : "s"}` : "";
  box.innerHTML = l.length ? `<div class="table-wrap"><table><thead><tr><th>Estudiante</th><th>Carrera · Ciclo</th><th>DNI</th><th>Apoderado</th><th>Solicitó</th><th></th></tr></thead><tbody>
    ${l.map((a) => `<tr><td><div class="person"><span class="avatar" data-foto-id="${a.id}">${esc(initials(a.nombre))}</span><div><strong>${esc(a.nombre)}</strong><small class="muted mono">${esc(a.codigo)}</small></div></div></td>
      <td>${esc(etiquetaCiclo(a.nivel, a.grado))}</td><td class="mono">${a.dni ? esc(a.dni) : "—"}</td><td>${a.apoderado ? esc(a.apoderado) : "—"}</td><td class="nowrap">${esc(cuando(a))}</td>
      <td class="t-right nowrap"><button class="btn btn-outline btn-sm" data-action="sol-ver" data-id="${a.id}">Ver</button>
        <button class="btn btn-ghost-danger btn-sm" data-action="sol-no" data-id="${a.id}">Rechazar</button>
        <button class="btn btn-teal btn-sm" data-action="sol-ok" data-id="${a.id}">${icon("check", 14)} Aprobar</button></td></tr>`).join("")}</tbody></table></div>`
    : emptyState("No hay solicitudes pendientes", "Cuando un estudiante se registre con el código del instituto, aparecerá aquí para que lo apruebes.", "userCheck");
  box.querySelectorAll(".avatar[data-foto-id]").forEach(async (av) => {
    const a = DB.alumnos.find((x) => x.id === av.dataset.fotoId);
    const url = a?.foto_path || a?.foto_data ? await api.fotoUrl(a).catch(() => null) : null;
    if (url) { av.style.backgroundImage = `url("${url}")`; av.style.backgroundSize = "cover"; av.textContent = ""; }
  });
}
const root = () => document.getElementById("page-root");
const alumno = (el) => DB.alumnos.find((x) => x.id === el.dataset.id);

export const solicitudesPage = {
  id: "solicitudes", title: "Solicitudes de ingreso", icon: "userCheck", group: "Registro", soloAdmin: true,
  async render(el) {
    el.innerHTML = `${pageHead("Solicitudes de ingreso", "Estudiantes que se registraron con el código del instituto y esperan tu aprobación. Hasta entonces su QR no registra asistencia.",
      `<span class="muted" id="sol-total"></span><button class="btn btn-outline" data-action="sol-actualizar">${icon("flip", 16)} Actualizar</button><a class="btn btn-outline" href="#/codigo">${icon("qr", 16)} Código de registro</a>`)}
      <div class="card flush" id="sol-lista"></div>`;
    pintar(el);
    loadAll().then(() => { if (el.isConnected) pintar(el); }).catch(() => {});   // trae lo más reciente al abrir
  },
};

registerActions({
  "ir-solicitudes": () => { location.hash = "#/solicitudes"; },
  "sol-actualizar": async () => { try { await loadAll(); pintar(root()); toast("Lista actualizada", "success"); } catch (e) { toast(e.message, "error"); } },
  "sol-ver": (el) => revisarEstudiante(alumno(el), () => pintar(root())),
  "sol-ok": async (el) => {
    const a = alumno(el);
    try { await api.save("alumnos", { colegio_id: DB.cid, aprobado: true }, a.id); await loadAll(); pintar(root()); toast(`${a.nombre} aprobado: su QR ya registra asistencia`, "success"); }
    catch (e) { toast("No se pudo aprobar: " + e.message, "error"); }
  },
  "sol-no": async (el) => {
    const a = alumno(el);
    if (!(await confirmDialog({ title: "Rechazar solicitud", message: `¿Rechazar y eliminar el registro de <b>${esc(a.nombre)}</b>?`, confirmLabel: "Rechazar" }))) return;
    try { await api.remove("alumnos", a.id); await loadAll(); pintar(root()); toast("Solicitud rechazada", "success"); }
    catch (e) { toast("No se pudo rechazar: " + e.message, "error"); }
  },
});
void badge;
