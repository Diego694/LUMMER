// @ts-check
// Calendario y horarios (administrador): feriados y días sin clases, y la hora de ingreso/salida del instituto y de cada carrera.
import { api } from "../api.js";
import { feriadosPeru, horarioDe } from "../calendario.js";
import { DB, loadAll } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, pageHead, registerActions, toast } from "../ui.js";
import { esc, fmtDate, todayStr } from "../utils.js";

/** @type {Record<string, string>} */
const TONO = { Feriado: "red", "Sin clases": "amber", Evento: "blue" };
const root = () => /** @type {HTMLElement} */ (document.getElementById("page-root"));

/**
 * @param {string} tabla
 * @param {Record<string, any>} data
 * @param {string} [id]
 */
async function guardar(tabla, data, id) { await api.save(tabla, { colegio_id: DB.cid, ...data }, id); }
async function refrescar() { await loadAll(); pintar(root()); }

/** @param {HTMLElement} el */
function pintar(el) {
  /** @param {string | null} nivel */
  const horario = (nivel) => DB.horarios.find((h) => (h.nivel || null) === nivel);
  /**
   * @param {string | null} nivel
   * @param {string} etiqueta
   */
  const fila = (nivel, etiqueta) => {
    const h = horario(nivel), e = horarioDe(DB.horarios, nivel);
    const detalle = h ? `<div>Puntual hasta <b class="mono">${esc(e.limite)}</b> · Tardanza ${e.hasta ? `hasta <b class="mono">${esc(e.hasta)}</b>` : "sin tope"}</div>
        <small class="muted">${e.desde ? `Ingreso desde ${esc(e.desde)} · ` : ""}Salida desde ${esc(String(Math.round(e.permanencia / 6) / 10))} h después del ingreso${e.salida ? ` · fin de clases ${esc(e.salida)}` : ""}</small>` : "—";
    return `<tr><td><strong>${esc(etiqueta)}</strong>${!nivel && !h ? ' <small class="muted">(sin definir: se usa 08:00)</small>' : ""}</td>
      <td class="mono">${h ? esc(h.hora_ingreso) : "—"}</td><td>${detalle}</td>
      <td class="t-right nowrap"><button class="btn btn-outline btn-sm" data-action="hor-edit" data-nivel="${esc(nivel || "")}">${h ? "Cambiar" : "Definir"}</button>
      ${h && nivel ? `<button class="icon-only danger" title="Quitar (usará el general)" aria-label="Quitar horario de ${esc(nivel)}" data-action="hor-del" data-id="${h.id}">${icon("trash", 16)}</button>` : ""}</td></tr>`;
  };
  const cal = [...DB.calendario].sort((a, b) => a.fecha.localeCompare(b.fecha));
  const proximos = cal.filter((c) => c.fecha >= todayStr());
  /** @type {HTMLElement} */ (el.querySelector("#hor-card")).innerHTML = `<div class="table-wrap"><table><thead><tr><th>Horario</th><th>Inicio de clases</th><th>Cómo se marca</th><th></th></tr></thead><tbody>
    ${fila(null, "General (todo el instituto)")}${DB.niveles.map((n) => fila(n, n)).join("")}</tbody></table></div>`;
  /** @type {HTMLElement} */ (el.querySelector("#cal-card")).innerHTML = cal.length ? `<div class="table-wrap"><table><thead><tr><th>Fecha</th><th>Tipo</th><th>Nombre</th><th></th></tr></thead><tbody>
    ${cal.map((c) => `<tr class="${c.fecha < todayStr() ? "muted-row" : ""}"><td>${esc(fmtDate(c.fecha, { weekday: "short", day: "2-digit", month: "short", year: "numeric" }))}</td><td>${badge(c.tipo, TONO[c.tipo] || "neutral")}</td><td>${esc(c.nombre)}</td>
      <td class="t-right"><button class="icon-only danger" aria-label="Quitar ${esc(c.nombre)}" data-action="cal-del" data-id="${c.id}">${icon("trash", 16)}</button></td></tr>`).join("")}</tbody></table></div>`
    : emptyState("Sin fechas cargadas", "Carga los feriados nacionales con un clic o agrega los días sin clases de tu instituto.", "calendar");
  /** @type {HTMLElement} */ (el.querySelector("#cal-hint")).textContent = proximos.length ? `Próximo: ${fmtDate(proximos[0].fecha, { day: "2-digit", month: "long" })} — ${proximos[0].nombre}` : "";
}

export const calendarioPage = {
  id: "calendario", title: "Calendario y horarios", icon: "calendar", group: "Gestión", soloAdmin: true,
  /** @param {HTMLElement} el */
  render(el) {
    el.innerHTML = `${pageHead("Calendario y horarios", "Los feriados no cuentan como falta; los horarios definen quién llega tarde (por carrera).")}
      <section class="card"><header class="card-head"><h3>Horarios de ingreso y salida</h3><button class="btn btn-outline btn-sm" data-action="hor-preset">Usar horario tarde/noche (14:00–20:00)</button></header><div id="hor-card"></div>
        <p class="muted" style="margin:10px 0 0">Es <b>puntual</b> quien ingresa hasta <b>inicio de clases + tolerancia</b>; después es <b>tardanza</b> hasta el <b>cierre del ingreso</b>. Antes de la apertura o después del cierre, el quiosco no registra. La <b>salida</b> solo se habilita pasados los minutos mínimos desde el ingreso (120 = 2 horas), también para quien sale temprano. Una carrera sin horario propio usa el general.</p></section>
      <section class="card" style="margin-top:16px"><header class="card-head"><div><h3>Feriados y días sin clases</h3><small class="muted" id="cal-hint"></small></div>
        <div class="btn-row"><button class="btn btn-outline" data-action="cal-peru">Cargar feriados de Perú</button><button class="btn btn-primary" data-action="cal-new">${icon("plus", 16)} Agregar fecha</button></div></header><div id="cal-card"></div></section>`;
    pintar(el);
  },
};

/** @type {Record<string, string>} */
const PRESET_TARDE = { hora_ingreso: "14:00", tolerancia_min: "10", hora_salida: "20:00", ingreso_desde: "13:00", ingreso_hasta: "19:00", permanencia_min: "120" };

/**
 * @param {string | null} nivel
 * @param {Record<string, string> | null} [preset]
 */
function abrirFormularioHorario(nivel, preset = null) {
  const h = DB.horarios.find((x) => (x.nivel || null) === nivel);
  const v0 = preset || { hora_ingreso: h?.hora_ingreso || "08:00", tolerancia_min: String(h?.tolerancia_min ?? 0), hora_salida: h?.hora_salida || "", ingreso_desde: h?.ingreso_desde || "", ingreso_hasta: h?.ingreso_hasta || "", permanencia_min: String(h?.permanencia_min ?? 120) };
  formModal({
    title: nivel ? `Horario de ${nivel}` : "Horario general del instituto",
    fields: [
      { name: "hora_ingreso", label: "Inicio de clases — hora de ingreso (HH:MM, 24 h)", required: true, value: v0.hora_ingreso, half: true, placeholder: "14:00" },
      { name: "tolerancia_min", label: "Tolerancia (min): hasta ahí es PUNTUAL", value: v0.tolerancia_min, half: true, placeholder: "10" },
      { name: "ingreso_desde", label: "Se puede marcar ingreso desde (opcional)", value: v0.ingreso_desde, half: true, placeholder: "13:00" },
      { name: "ingreso_hasta", label: "Cierre del ingreso (opcional)", value: v0.ingreso_hasta, half: true, placeholder: "19:00" },
      { name: "hora_salida", label: "Fin de clases — hora de salida (opcional)", value: v0.hora_salida, half: true, placeholder: "20:00" },
      { name: "permanencia_min", label: "Minutos mínimos antes de poder marcar SALIDA", value: v0.permanencia_min, half: true, placeholder: "120" },
    ],
    submitLabel: "Guardar horario",
    onSubmit: async (v) => {
      const hhmm = /^([01]\d|2[0-3]):[0-5]\d$/;
      /** @param {string | undefined} x */
      const opc = (x) => (x || "").trim() || null;
      const ing = v.hora_ingreso.trim(), des = opc(v.ingreso_desde), has = opc(v.ingreso_hasta), sal = opc(v.hora_salida);
      if (!hhmm.test(ing)) throw new Error("La hora de ingreso debe ser HH:MM (por ejemplo 14:00).");
      for (const [t, x] of [["apertura del ingreso", des], ["cierre del ingreso", has], ["hora de salida", sal]]) if (x && !hhmm.test(x)) throw new Error(`La ${t} debe ser HH:MM (por ejemplo 19:00).`);
      const tol = Number(v.tolerancia_min || 0), perm = Number(v.permanencia_min === "" ? 120 : v.permanencia_min);
      if (!Number.isInteger(tol) || tol < 0 || tol > 120) throw new Error("La tolerancia va de 0 a 120 minutos.");
      if (!Number.isInteger(perm) || perm < 0 || perm > 600) throw new Error("La permanencia mínima va de 0 a 600 minutos (120 = 2 horas).");
      if (des && des > ing) throw new Error("El ingreso debe poder marcarse desde antes (o a la misma hora) del inicio de clases.");
      if (has && has < ing) throw new Error("El cierre del ingreso debe ser después del inicio de clases.");
      if (sal && sal <= ing) throw new Error("La hora de salida debe ser después del inicio de clases.");
      await guardar("horarios", { nivel, hora_ingreso: ing, tolerancia_min: tol, hora_salida: sal, ingreso_desde: des, ingreso_hasta: has, permanencia_min: perm }, h?.id);
      await refrescar(); toast("Horario guardado", "success");
    },
  });
}

registerActions({
  "hor-preset": () => abrirFormularioHorario(null, PRESET_TARDE),
  "hor-edit": (/** @type {HTMLElement} */ el) => abrirFormularioHorario(el.dataset.nivel || null),
  "hor-del": async (/** @type {HTMLElement} */ el) => {
    if (!(await confirmDialog({ title: "Quitar horario", message: "Esta carrera volverá a usar el horario general.", confirmLabel: "Quitar" }))) return;
    await api.remove("horarios", /** @type {string} */ (el.dataset.id)); await refrescar();
  },
  "cal-new": () => formModal({
    title: "Agregar fecha", fields: [
      { name: "fecha", label: "Fecha", type: "date", required: true },
      { name: "tipo", label: "Tipo", type: "select", options: [{ value: "Feriado", label: "Feriado" }, { value: "Sin clases", label: "Sin clases (suspensión)" }, { value: "Evento", label: "Evento (sí hay clases)" }], value: "Feriado" },
      { name: "nombre", label: "Nombre", required: true, placeholder: "Ej: Aniversario del instituto" },
    ],
    onSubmit: async (v) => {
      try { await guardar("calendario", v); } catch (/** @type {any} */ e) { throw new Error(e.code === "duplicate" ? "Esa fecha ya está en el calendario." : e.message); }
      await refrescar(); toast("Fecha agregada", "success");
    },
  }),
  "cal-del": async (/** @type {HTMLElement} */ el) => { await api.remove("calendario", /** @type {string} */ (el.dataset.id)); await refrescar(); },
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
