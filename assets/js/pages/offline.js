// @ts-check
// Respaldo offline (administrador): exporta a un archivo .rabackup lo registrado sin internet y lo importa luego,
// en la web, la app Android o el programa de PC, quedando cada registro en SU fecha (no en la de hoy).
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { cola } from "../cola.js";
import { EXTENSION, construirPaquete, nombreArchivo, planImportacion, validarPaquete } from "../respaldo-offline.js";
import { DB, loadAll } from "../state.js";
import { red } from "../sync.js";
import { emptyState, icon, pageHead, registerActions, toast } from "../ui.js";
import { addDays, downloadFile, esc, fmtDate, todayStr } from "../utils.js";

const root = () => /** @type {HTMLElement} */ (document.getElementById("page-root"));
const local = () => api.mode === "local" || api.mode === "demo";   // la base del propio equipo (modo local / demo)
/** @type {any} */
let pendienteImport = null;   // { paquete, plan, nombre }

/* ------------------------------ Exportar ------------------------------ */
/** Reúne lo registrado en el rango, venga de la base del equipo (modo local) o de la cola sin enviar (modo online). */
async function reunir(/** @type {any} */ desde, /** @type {any} */ hasta, /** @type {any} */ incluirServidor) {
  const r = /** @type {any} */ ({ asistencias: [], salidas: [], asistenciasCurso: [], justificaciones: [], origen: "" });
  const enRango = (/** @type {any} */ f) => f >= desde && f <= hasta;
  if (local()) {
    r.origen = "base local de este equipo";
    r.asistencias = await api.asistenciasRango(DB.cid, desde, hasta);
    r.asistenciasCurso = (/** @type {any} */ (api).db.asistencias_curso || []).filter((/** @type {any} */ x) => enRango(x.fecha));
    r.justificaciones = await api.justificacionesRango(DB.cid, desde, hasta).catch(() => []);
    return r;
  }
  const pend = cola.todos();
  r.asistencias = pend.filter((x) => x.tipo === "asistencia" && enRango(x.row.fecha)).map((x) => x.row);
  r.salidas = pend.filter((x) => x.tipo === "salida" && enRango(x.row.fecha)).map((x) => x.row);
  r.asistenciasCurso = pend.filter((x) => x.tipo === "curso" && enRango(x.row.fecha)).map((x) => x.row);
  r.origen = "registros guardados en este equipo sin enviar";
  if (incluirServidor && red.online()) {
    try {
      const [a, j] = await Promise.all([api.asistenciasRango(DB.cid, desde, hasta), api.justificacionesRango(DB.cid, desde, hasta).catch(() => [])]);
      r.asistencias = [...a, ...r.asistencias]; r.justificaciones = j; r.origen = "servidor + registros sin enviar";
    } catch { /* sin conexión: solo lo del equipo */ }
  }
  return r;
}

async function armarPaquete() {
  const desde = /** @type {HTMLInputElement} */ (root().querySelector("#of-desde")).value, hasta = /** @type {HTMLInputElement} */ (root().querySelector("#of-hasta")).value;
  if (!desde || !hasta || desde > hasta) throw new Error("Elige un rango de fechas válido (desde ≤ hasta).");
  if (hasta > todayStr()) throw new Error("La fecha final no puede ser futura.");
  const incluir = /** @type {HTMLInputElement | null} */ (root().querySelector("#of-servidor"))?.checked;
  const datos = await reunir(desde, hasta, incluir);
  const texto = await construirPaquete({
    instituto: { id: DB.cid, nombre: DB.perfil?.colegio || "" },
    origen: { modo: api.mode, app: globalThis.AndroidBridge ? "android" : globalThis.escritorio ? "pc" : "web", version: CONFIG.APP_VERSION, detalle: datos.origen },
    rango: { desde, hasta }, alumnos: DB.alumnos, cursos: DB.cursos, ...datos,
  });
  return { texto, desde, hasta, conteo: JSON.parse(texto).conteo };
}

/* ------------------------------ Importar ------------------------------ */
async function analizar(/** @type {any} */ file) {
  const out = /** @type {HTMLElement} */ (root().querySelector("#oi-res"));
  out.innerHTML = '<p class="muted">Leyendo el archivo…</p>';
  pendienteImport = null;
  const v = await validarPaquete(await file.text(), { hoy: todayStr() });
  if (!v.ok) { out.innerHTML = `<p class="err-msg" role="alert">${esc(v.errores.join(" "))}</p>`; return; }
  const p = /** @type {any} */ (v.paquete);
  const fechas = p.datos.asistencias.map((/** @type {any} */ x) => x.fecha).concat(p.datos.asistencias_curso.map((/** @type {any} */ x) => x.fecha), p.datos.justificaciones.map((/** @type {any} */ x) => x.fecha)).sort();
  if (!fechas.length) { out.innerHTML = '<p class="muted">El archivo no trae registros.</p>'; return; }
  // lo ya existente en esas fechas (para no duplicar)
  const desde = fechas[0], hasta = fechas.at(-1);
  const [asis, just] = await Promise.all([api.asistenciasRango(DB.cid, desde, hasta), api.justificacionesRango(DB.cid, desde, hasta).catch(() => [])]);
  const cursosEx = new Set();
  if (p.datos.asistencias_curso.length) {
    for (const f of [...new Set(p.datos.asistencias_curso.map((/** @type {any} */ x) => x.fecha))]) for (const c of DB.cursos) (await api.asistenciasCursoPorFecha(/** @type {string} */ (DB.cid), /** @type {string} */ (c.id), /** @type {string} */ (f)).catch(() => [])).forEach((/** @type {any} */ x) => cursosEx.add(`${x.alumno_id}|${x.curso_id}|${x.fecha}`));
  }
  const plan = planImportacion(p, {
    alumnos: DB.alumnos, cursos: DB.cursos,
    existentes: { asistencias: new Map(asis.map((x) => [`${x.alumno_id}|${x.fecha}`, x])), cursos: cursosEx, just: new Set(just.map((x) => `${x.alumno_id}|${x.fecha}`)) },
  });
  pendienteImport = { paquete: p, plan, nombre: file.name };
  const r = plan.resumen, otroInstituto = p.instituto?.nombre && DB.perfil?.colegio && p.instituto.nombre !== /** @type {any} */ (DB.perfil).colegio;
  out.innerHTML = `
    <div class="alert-box">${icon("info", 15)} Respaldo de <b>${esc(p.instituto?.nombre || "—")}</b> · creado el ${esc(fmtDate(String(p.creado_en).slice(0, 10), { day: "2-digit", month: "long", year: "numeric" }))} · origen: ${esc(p.origen?.detalle || p.origen?.modo || "—")} (${esc(p.origen?.app || "")} ${esc(p.origen?.version || "")})</div>
    ${otroInstituto ? `<p class="err-msg" role="alert">Este respaldo es de «${esc(p.instituto.nombre)}» y tu instituto es «${esc(/** @type {any} */ (DB.perfil).colegio)}». Importa solo si es el mismo instituto.</p>` : ""}
    <div class="table-wrap"><table><thead><tr><th>Fecha</th><th>Asistencias nuevas</th><th>Ya estaban</th><th>Salidas que se completan</th><th>Por curso</th><th>Justificaciones</th><th>Sin alumno</th></tr></thead><tbody>
      ${plan.porFecha.map((x) => `<tr><td><strong>${esc(fmtDate(x.fecha, { weekday: "short", day: "2-digit", month: "short", year: "numeric" }))}</strong></td><td>${x.nuevas}</td><td>${x.repetidas}</td><td>${x.salidas}</td><td>${x.curso}</td><td>${x.justificaciones}</td><td>${x.sinAlumno || "—"}</td></tr>`).join("")}</tbody></table></div>
    ${plan.desconocidos.length ? `<p class="muted" style="margin:10px 0 0">Códigos que no existen en este instituto (se omiten): ${esc(plan.desconocidos.slice(0, 12).join(", "))}${plan.desconocidos.length > 12 ? "…" : ""}</p>` : ""}
    ${plan.cursosSinMatch.length ? `<p class="muted" style="margin:6px 0 0">Cursos que no existen aquí (se omiten): ${esc(plan.cursosSinMatch.join(", "))}</p>` : ""}
    ${v.avisos.length ? `<p class="muted" style="margin:6px 0 0">${esc(v.avisos.join(" "))}</p>` : ""}
    <p style="margin:14px 0 0"><b>${r.asistencias}</b> asistencias, <b>${r.salidas}</b> salidas, <b>${r.cursos}</b> por curso y <b>${r.justificaciones}</b> justificaciones se agregarán en sus fechas originales. ${r.repetidas} ya existían y no se tocan.</p>
    <div class="btn-row" style="margin-top:12px"><button class="btn btn-primary" data-action="of-importar" ${r.asistencias + r.salidas + r.cursos + r.justificaciones ? "" : "disabled"}>${icon("upload", 16)} Importar en sus fechas</button></div>
    <p class="err-msg" id="oi-err" hidden></p>`;
}

async function aplicar(/** @type {any} */ btn) {
  if (!pendienteImport) return;
  const { plan } = pendienteImport, err = /** @type {HTMLElement} */ (root().querySelector("#oi-err"));
  btn.disabled = true; btn.textContent = "Importando…"; err.hidden = true;
  try {
    const base = { colegio_id: DB.cid, registrado_por: DB.userId };
    const lotes = (/** @type {any} */ a, n = 200) => Array.from({ length: Math.ceil(a.length / n) }, (_, i) => a.slice(i * n, i * n + n));
    let n = 0;
    for (const l of lotes(plan.asistencias)) n += await api.registrarMasivo(l.map((/** @type {any} */ x) => ({ ...base, ...x })));
    for (const l of lotes(plan.salidas)) await api.registrarSalidas(l);
    for (const l of lotes(plan.cursos)) await api.registrarMasivoCurso(l.map((/** @type {any} */ x) => ({ ...base, ...x })));
    let j = 0;
    for (const x of plan.justificaciones) { try { await api.guardarJustificacion({ ...base, ...x }); j++; } catch (/** @type {any} */ e) { if (e.code !== "duplicate") throw e; } }
    await loadAll();
    pendienteImport = null;
    /** @type {HTMLElement} */ (root().querySelector("#oi-res")).innerHTML = `<div class="alert-box ok">${icon("check", 15)} Listo: <b>${n}</b> asistencias, ${plan.salidas.length} salidas, ${plan.cursos.length} por curso y ${j} justificaciones importadas en sus fechas originales. Puedes repetir la importación sin duplicar.</div>`;
    toast("Respaldo importado en sus fechas", "success");
  } catch (/** @type {any} */ e) {
    err.textContent = "No se pudo completar: " + e.message + " Puedes reintentar: lo ya importado no se duplica."; err.hidden = false;
    btn.disabled = false; btn.innerHTML = `${icon("upload", 16)} Importar en sus fechas`;
  }
}

/* ------------------------------ Página ------------------------------ */
export const offlinePage = {
  id: "respaldo-offline", title: "Respaldo offline", icon: "upload", group: "Sistema", soloAdmin: true,
  render(/** @type {any} */ el) {
    const hoy = todayStr();
    el.innerHTML = `${pageHead("Respaldo offline", "Si no hay internet, exporta lo registrado a un archivo y, cuando vuelva la conexión, impórtalo: cada registro queda en la fecha en que se grabó.")}
      <div class="grid-2">
        <section class="card"><h3 style="margin-top:0">1 · Exportar (sin internet)</h3>
          <p class="muted">Guarda las asistencias, salidas, asistencias por curso y justificaciones de esas fechas en un archivo <b>${EXTENSION}</b> (en <i>Descargas</i> en el teléfono). ${local() ? "Se toma de la <b>base local de este equipo</b>." : "Se toma de lo <b>guardado en este equipo y aún sin enviar</b>."}</p>
          <div class="form-grid"><div class="field half"><label for="of-desde">Desde</label><input id="of-desde" type="date" value="${esc(addDays(hoy, -1))}" max="${esc(hoy)}"></div>
            <div class="field half"><label for="of-hasta">Hasta</label><input id="of-hasta" type="date" value="${esc(hoy)}" max="${esc(hoy)}"></div></div>
          ${local() ? "" : `<label class="check-row"><input type="checkbox" id="of-servidor"> Incluir también lo que ya está en el servidor (si hay conexión)</label>`}
          <p class="muted" id="of-prev" style="margin:8px 0"></p><p class="err-msg" id="of-err" hidden></p>
          <div class="btn-row"><button class="btn btn-outline" data-action="of-ver">Ver qué incluye</button><button class="btn btn-primary" data-action="of-exportar">${icon("download", 16)} Descargar respaldo</button></div>
          ${local() ? "" : '<p class="muted" style="margin:12px 0 0">Los registros pendientes <b>se siguen enviando solos</b> al volver internet; este archivo es una copia de seguridad extra (o para llevarlos a otro equipo).</p>'}
        </section>
        <section class="card"><h3 style="margin-top:0">2 · Importar (con internet)</h3>
          <p class="muted">Elige un archivo <b>${EXTENSION}</b>. Verás un resumen por fecha antes de confirmar. <b>No se duplica nada</b>: lo que ya existe se respeta.</p>
          <input type="file" id="oi-file" accept="*/*" aria-label="Archivo de respaldo ${EXTENSION}">
          <div id="oi-res" style="margin-top:14px"></div>
        </section>
      </div>`;
    /** @type {HTMLElement} */ (el.querySelector("#oi-file")).addEventListener("change", (/** @type {any} */ e) => { const f = e.target.files?.[0]; if (f) analizar(f).catch((x) => { /** @type {HTMLElement} */ (el.querySelector("#oi-res")).innerHTML = `<p class="err-msg">${esc(x.message)}</p>`; }); });
  },
};

registerActions({
  "of-ver": async () => {
    const err = /** @type {HTMLElement} */ (root().querySelector("#of-err")), prev = /** @type {HTMLElement} */ (root().querySelector("#of-prev")); err.hidden = true;
    try { const { conteo, desde, hasta } = await armarPaquete(); prev.textContent = `Del ${desde} al ${hasta}: ${conteo.asistencias} asistencias en ${conteo.dias} día(s), ${conteo.asistencias_curso} por curso, ${conteo.justificaciones} justificaciones, ${conteo.alumnos} alumnos.`; }
    catch (/** @type {any} */ e) { err.textContent = e.message; err.hidden = false; }
  },
  "of-exportar": async () => {
    const err = /** @type {HTMLElement} */ (root().querySelector("#of-err")); err.hidden = true;
    try {
      const { texto, desde, hasta, conteo } = await armarPaquete();
      if (!conteo.asistencias && !conteo.asistencias_curso && !conteo.justificaciones) { err.textContent = "No hay registros en ese rango para exportar."; err.hidden = false; return; }
      await downloadFile(nombreArchivo(desde, hasta), texto, "application/octet-stream");
      toast(`Respaldo descargado (${conteo.asistencias} asistencias)`, "success");
    } catch (/** @type {any} */ e) { err.textContent = e.message; err.hidden = false; }
  },
  "of-importar": (el) => aplicar(el),
});
