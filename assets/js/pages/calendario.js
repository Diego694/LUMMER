// Calendario y horarios (administrador): feriados y días sin clases, y la hora de ingreso/salida del instituto y de cada carrera.
import { api } from "../api.js";
import { feriadosPeru } from "../calendario.js";
import { DB, loadAll } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, pageHead, registerActions, toast } from "../ui.js";
import { esc, fmtDate, todayStr } from "../utils.js";

const TONO = { Feriado: "red", "Sin clases": "amber", Evento: "blue" };
const root = () => document.getElementById("page-root");

async function guardar(tabla, data, id) { await api.save(tabla, { colegio_id: DB.cid, ...data }, id); }
async function refrescar() { await loadAll(); pintar(root()); }

function pintar(el) {
  const horario = (nivel) => DB.horarios.find((h) => (h.nivel || null) === nivel);
  const fila = (nivel, etiqueta) => {
    const h = horario(nivel);
    return `<tr><td><strong>${esc(etiqueta)}</strong>${!nivel && !h ? ' <small class="muted">(sin definir: se usa 08:00)</small>' : ""}</td>
      <td class="mono">${h ? esc(h.hora_ingreso) : "—"}</td><td>${h ? esc(String(h.tolerancia_min)) + " min" : "—"}</td><td class="mono">${h?.hora_salida ? esc(h.hora_salida) : "—"}</td>
      <td class="t-right nowrap"><button class="btn btn-outline btn-sm" data-action="hor-edit" data-nivel="${esc(nivel || "")}">${h ? "Cambiar" : "Definir"}</button>
      ${h && nivel ? `<button class="icon-only danger" title="Quitar (usará el general)" aria-label="Quitar horario de ${esc(nivel)}" data-action="hor-del" data-id="${h.id}">${icon("trash", 16)}</button>` : ""}</td></tr>`;
  };
  const cal = [...DB.calendario].sort((a, b) => a.fecha.localeCompare(b.fecha));
  const proximos = cal.filter((c) => c.fecha >= todayStr());
  el.querySelector("#hor-card").innerHTML = `<div class="table-wrap"><table><thead><tr><th>Horario</th><th>Ingreso</th><th>Tolerancia</th><th>Salida</th><th></th></tr></thead><tbody>
    ${fila(null, "General (todo el instituto)")}${DB.niveles.map((n) => fila(n, n)).join("")}</tbody></table></div>`;
  el.querySelector("#cal-card").innerHTML = cal.length ? `<div class="table-wrap"><table><thead><tr><th>Fecha</th><th>Tipo</th><th>Nombre</th><th></th></tr></thead><tbody>
    ${cal.map((c) => `<tr class="${c.fecha < todayStr() ? "muted-row" : ""}"><td>${esc(fmtDate(c.fecha, { weekday: "short", day: "2-digit", month: "short", year: "numeric" }))}</td><td>${badge(c.tipo, TONO[c.tipo] || "neutral")}</td><td>${esc(c.nombre)}</td>
      <td class="t-right"><button class="icon-only danger" aria-label="Quitar ${esc(c.nombre)}" data-action="cal-del" data-id="${c.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
    : emptyState("Sin fechas cargadas", "Carga los feriados nacionales con un clic o agrega los días sin clases de tu instituto.", "calendar");
  el.querySelector("#cal-hint").textContent = proximos.length ? `Próximo: ${fmtDate(proximos[0].fecha, { day: "2-digit", month: "long" })} — ${proximos[0].nombre}` : "";
}

export const calendarioPage = {
  id: "calendario", title: "Calendario y horarios", icon: "calendar", group: "Gestión", soloAdmin: true,
  render(el) {
    el.innerHTML = `${pageHead("Calendario y horarios", "Los feriados no cuentan como falta; los horarios definen quién llega tarde (por carrera).")}
      <section class="card"><header class="card-head"><h3>Horarios de ingreso y salida</h3></header><div id="hor-card"></div>
        <p class="muted" style="margin:10px 0 0">Una persona llega <b>tarde</b> si ingresa después de <b>hora de ingreso + tolerancia</b>. Una carrera sin horario propio usa el general.</p></section>
      <section class="card" style="margin-top:16px"><header class="card-head"><div><h3>Feriados y días sin clases</h3><small class="muted" id="cal-hint"></small></div>
        <div class="btn-row"><button class="btn btn-outline" data-action="cal-peru">Cargar feriados de Perú</button><button class="btn btn-primary" data-action="cal-new">${icon("plus", 16)} Agregar fecha</button></div></header><div id="cal-card"></div></section>`;
    pintar(el);
  },
};

registerActions({
  "hor-edit": (el) => {
    const nivel = el.dataset.nivel || null;
    const h = DB.horarios.find((x) => (x.nivel || null) === nivel);
    formModal({
      title: nivel ? `Horario de ${nivel}` : "Horario general",
      fields: [
        { name: "hora_ingreso", label: "Hora de ingreso (HH:MM, 24 h)", required: true, value: h?.hora_ingreso || "08:00", half: true, placeholder: "08:00" },
        { name: "tolerancia_min", label: "Tolerancia (minutos)", value: String(h?.tolerancia_min ?? 0), half: true, placeholder: "0" },
        { name: "hora_salida", label: "Hora de salida (opcional)", value: h?.hora_salida || "", placeholder: "13:00" },
      ],
      onSubmit: async (v) => {
        const hhmm = /^([01]\d|2[0-3]):[0-5]\d$/;
        if (!hhmm.test(v.hora_ingreso)) throw new Error("La hora de ingreso debe ser HH:MM (por ejemplo 07:30).");
        if (v.hora_salida && !hhmm.test(v.hora_salida)) throw new Error("La hora de salida debe ser HH:MM (por ejemplo 13:00).");
        const tol = Number(v.tolerancia_min || 0);
        if (!Number.isInteger(tol) || tol < 0 || tol > 120) throw new Error("La tolerancia va de 0 a 120 minutos.");
        await guardar("horarios", { nivel, hora_ingreso: v.hora_ingreso, tolerancia_min: tol, hora_salida: v.hora_salida || null }, h?.id);
        await refrescar(); toast("Horario guardado", "success");
      },
    });
  },
  "hor-del": async (el) => {
    if (!(await confirmDialog({ title: "Quitar horario", message: "Esta carrera volverá a usar el horario general.", confirmLabel: "Quitar" }))) return;
    await api.remove("horarios", el.dataset.id); await refrescar();
  },
  "cal-new": () => formModal({
    title: "Agregar fecha", fields: [
      { name: "fecha", label: "Fecha", type: "date", required: true },
      { name: "tipo", label: "Tipo", type: "select", options: [{ value: "Feriado", label: "Feriado" }, { value: "Sin clases", label: "Sin clases (suspensión)" }, { value: "Evento", label: "Evento (sí hay clases)" }], value: "Feriado" },
      { name: "nombre", label: "Nombre", required: true, placeholder: "Ej: Aniversario del instituto" },
    ],
    onSubmit: async (v) => {
      try { await guardar("calendario", v); } catch (e) { throw new Error(e.code === "duplicate" ? "Esa fecha ya está en el calendario." : e.message); }
      await refrescar(); toast("Fecha agregada", "success");
    },
  }),
  "cal-del": async (el) => { await api.remove("calendario", el.dataset.id); await refrescar(); },
  "cal-peru": () => {
    const anio = Number(todayStr().slice(0, 4));
    formModal({
      title: "Cargar feriados nacionales de Perú", submitLabel: "Cargar",
      fields: [{ name: "anio", label: "Año", value: String(anio), required: true }],
      onSubmit: async (v) => {
        const y = Number(v.anio);
        if (!(y >= 2020 && y <= 2100)) throw new Error("Año no válido.");
        const ya = new Set(DB.calendario.map((c) => c.fecha));
        const nuevos = feriadosPeru(y).filter((f) => !ya.has(f.fecha));
        for (const f of nuevos) await guardar("calendario", f);
        await refrescar();
        toast(nuevos.length ? `${nuevos.length} feriados cargados. Verifica traslados o días no laborables decretados.` : "Esos feriados ya estaban cargados.", "success");
      },
    });
  },
});
