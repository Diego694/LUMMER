// @ts-check
// Conexiones de datos (superadmin): registra bases externas con llaves públicas y copia a ellas todos los datos de LUMMER.
import { api } from "../api.js";
import { DB } from "../state.js";
import { badge, confirmDialog, emptyState, formModal, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { downloadFile, esc, fmtDate } from "../utils.js";
import { TABLAS, TIPOS, clasificarLlave, contarEnDestino, crearPaquete, importarPaquete, normalizarDestino, probarConexion } from "../conectores.js";

/**
 * @typedef {{ id: string, nombre: string, tipo: import("../conectores.js").TipoConexion, url: string, llave_publica: string,
 *   estado: string, destino: boolean, detalle: string, ultima_prueba?: string | null, ultima_copia?: string | null }} Conexion
 */

const KEY_LOCAL = "ra-demo-conexiones";
/** @type {HTMLElement} */ let raiz;
/** @type {Conexion[]} */ let lista = [];

/* ---- almacén: la tabla conexiones_datos en Supabase; en demo, este navegador ---- */
const sb = () => /** @type {any} */ (api).sb;
async function leer() {
  if (sb()) {
    const { data, error } = await sb().from("conexiones_datos").select("*").order("creado_en");
    if (error) throw new Error(error.message);
    return /** @type {Conexion[]} */ (data);
  }
  try { return JSON.parse(localStorage.getItem(KEY_LOCAL) || "[]"); } catch { return []; }
}
/** @param {Conexion[]} l */
const guardarLocal = (l) => { try { localStorage.setItem(KEY_LOCAL, JSON.stringify(l)); } catch { /* sin almacenamiento */ } };
/** @param {string} id @param {Record<string, any>} cambios */
async function actualizar(id, cambios) {
  if (sb()) { const { error } = await sb().from("conexiones_datos").update(cambios).eq("id", id); if (error) throw new Error(error.message); return; }
  guardarLocal((await leer()).map((/** @type {Conexion} */ c) => (c.id === id ? { ...c, ...cambios } : c)));
}

/** Lee todas las filas de una tabla de LUMMER de la institución activa (paginado). */
/** @param {string} tabla @returns {Promise<Record<string, any>[]>} */
async function leerTabla(tabla) {
  if (!sb()) return /** @type {any} */ (api).db?.[tabla] || [];
  /** @type {Record<string, any>[]} */ const todas = [];
  for (let desde = 0; ; desde += 1000) {
    const { data, error } = await sb().from(tabla).select("*").range(desde, desde + 999);
    if (error) throw new Error(error.message);
    todas.push(...data);
    if (data.length < 1000) break;
  }
  return todas;
}

const ESTADO = /** @type {Record<string, [string, string]>} */ ({ pendiente: ["Sin probar", "neutral"], verificada: ["Verificada", "green"], error: ["Con error", "red"], copiada: ["Datos copiados", "green"] });

function pintar() {
  raiz.querySelector("#cx-lista")?.replaceChildren();
  const caja = /** @type {HTMLElement} */ (raiz.querySelector("#cx-lista"));
  caja.innerHTML = `
    <article class="card cx-card cx-principal">
      <div class="cx-head"><div><strong>LUMMER · base principal</strong><small>Supabase del proyecto (en uso)</small></div>${badge("En uso", "green")}</div>
      <p class="muted">Es la base que usa hoy la aplicación, con sus cuentas de acceso. Mientras no esté lista la migración completa de cuentas (ver docs/CONEXIONES.md) sigue siendo la principal.</p>
    </article>
    ${lista.length ? lista.map((c) => {
      const [txt, tono] = ESTADO[c.estado] || ESTADO.pendiente;
      return `<article class="card cx-card">
        <div class="cx-head"><div><strong>${esc(c.nombre)}</strong><small>${esc(TIPOS[c.tipo]?.etiqueta || c.tipo)}</small></div>
          <div class="cx-badges">${c.destino ? badge("Destino de la copia", "amber") : ""}${badge(txt, tono)}</div></div>
        <p class="muted cx-url">${esc(c.url)}${c.detalle ? ` · ${esc(c.detalle)}` : ""}</p>
        <p class="muted">${c.ultima_prueba ? `Probada ${esc(fmtDate(c.ultima_prueba.slice(0, 10)))}` : "Aún no se probó"}${c.ultima_copia ? ` · Última copia ${esc(fmtDate(c.ultima_copia.slice(0, 10)))}` : ""}</p>
        <div class="cx-acciones">
          <button class="btn btn-outline btn-sm" type="button" data-action="cx-probar" data-id="${esc(c.id)}">${icon("check", 15)} Probar</button>
          <button class="btn btn-outline btn-sm" type="button" data-action="cx-destino" data-id="${esc(c.id)}">${c.destino ? "Quitar como destino" : "Marcar como destino"}</button>
          <button class="btn btn-primary btn-sm" type="button" data-action="cx-copiar" data-id="${esc(c.id)}" ${c.destino ? "" : "disabled"}>${icon("upload", 15)} Copiar todos los datos</button>
          <button class="btn btn-ghost btn-sm cx-quitar" type="button" data-action="cx-quitar" data-id="${esc(c.id)}">${icon("trash", 15)} Desasociar</button>
        </div></article>`;
    }).join("") : `<div class="card">${emptyState("Sin conexiones", "Agrega una base de datos externa para copiar allí los datos de LUMMER.", "layers")}</div>`}`;
}

async function cargar() {
  try { lista = await leer(); pintar(); }
  catch (/** @type {any} */ e) {
    /** @type {HTMLElement} */ (raiz.querySelector("#cx-lista")).innerHTML = `<div class="card">${emptyState("No se pudo cargar las conexiones", /missing|does not exist|relation/i.test(e.message) ? "Falta aplicar la migración 017 en Supabase." : e.message, "alert")}</div>`;
  }
}

/** @param {string} id */
const buscar = (id) => lista.find((c) => c.id === id);

function agregar() {
  formModal({
    title: "Agregar conexión", submitLabel: "Guardar conexión",
    fields: [
      { name: "tipo", label: "Tipo de base de datos", type: "select", required: true, options: Object.entries(TIPOS).map(([v, t]) => ({ value: v, label: t.etiqueta })) },
      { name: "nombre", label: "Nombre", type: "text", required: true, placeholder: "Instituto — respaldo" },
      { name: "url", label: "URL del proyecto / ID de Firebase / URL de la API", type: "text", required: true, placeholder: "https://xxxx.supabase.co" },
      { name: "llave", label: "Llave PÚBLICA (nunca service_role ni claves privadas)", type: "password", required: true },
    ],
    async onSubmit(/** @type {any} */ v) {
      const tipo = /** @type {import("../conectores.js").TipoConexion} */ (v.tipo);
      const k = clasificarLlave(v.llave); if (!k.ok) throw new Error(k.motivo);
      const u = normalizarDestino(tipo, v.url); if (!u.ok) throw new Error(u.motivo);
      if (String(v.nombre).trim().length < 2) throw new Error("Escribe un nombre.");
      const fila = { nombre: String(v.nombre).trim(), tipo, url: u.valor, llave_publica: String(v.llave).trim() };
      if (sb()) { const { error } = await sb().from("conexiones_datos").insert(fila); if (error) throw new Error(error.message); }
      else guardarLocal([...(await leer()), { ...fila, id: crypto.randomUUID(), estado: "pendiente", destino: false, detalle: "" }]);
      toast("Conexión guardada. Pruébala antes de copiar datos.", "success");
      await cargar();
    },
  });
}

/** @param {HTMLElement} el */
async function probar(el) {
  const c = buscar(el.dataset.id || ""); if (!c) return;
  el.setAttribute("disabled", "");
  const r = await probarConexion({ tipo: c.tipo, url: c.url, llave: c.llave_publica });
  await actualizar(c.id, { estado: r.ok ? "verificada" : "error", detalle: r.detalle.slice(0, 200), ultima_prueba: new Date().toISOString() });
  toast(r.detalle, r.ok ? "success" : "error");
  await cargar();
}

/** @param {HTMLElement} el */
async function marcarDestino(el) {
  const c = buscar(el.dataset.id || ""); if (!c) return;
  try {
    if (!c.destino) for (const o of lista.filter((x) => x.destino)) await actualizar(o.id, { destino: false });
    await actualizar(c.id, { destino: !c.destino });
  } catch (/** @type {any} */ e) { toast(e.message, "error"); }
  await cargar();
}

/** @param {HTMLElement} el */
async function quitar(el) {
  const c = buscar(el.dataset.id || ""); if (!c) return;
  if (!(await confirmDialog({ title: "Desasociar conexión", message: `¿Quitar «${esc(c.nombre)}» de LUMMER? Se borra la llave guardada; los datos que ya copiaste allí no se tocan.`, confirmLabel: "Desasociar" }))) return;
  try {
    if (sb()) { const { error } = await sb().from("conexiones_datos").delete().eq("id", c.id); if (error) throw new Error(error.message); }
    else guardarLocal((await leer()).filter((/** @type {Conexion} */ x) => x.id !== c.id));
    toast("Conexión desasociada.", "success");
  } catch (/** @type {any} */ e) { toast(e.message, "error"); }
  await cargar();
}

/** Copia de seguridad en un archivo, sin necesidad de ninguna conexión. */
async function descargar() {
  try {
    const p = await crearPaquete(leerTabla);
    downloadFile(`lummer-paquete-${new Date().toISOString().slice(0, 10)}.json`, JSON.stringify(p), "application/json");
    toast(`Paquete descargado (${Object.values(p.conteos).reduce((a, b) => a + b, 0)} filas).`, "success");
  } catch (/** @type {any} */ e) { toast(e.message, "error"); }
}

/** @param {HTMLElement} el */
async function copiar(el) {
  const c = buscar(el.dataset.id || ""); if (!c || !c.destino) return;
  const ok = await confirmDialog({
    title: "Copiar todos los datos",
    message: `Se copiarán los datos de <b>${esc(DB.perfil?.colegio || "la institución activa")}</b> a «${esc(c.nombre)}». Es una copia (upsert): no borra nada del destino ni de LUMMER y puede repetirse. Los archivos (fotos y materiales) no se copian.`,
    confirmLabel: "Copiar", danger: false,
  });
  if (!ok) return;
  const panel = /** @type {HTMLElement} */ (raiz.querySelector("#cx-progreso"));
  panel.hidden = false;
  const linea = (/** @type {string} */ t) => { panel.innerHTML = `<p role="status">${esc(t)}</p>`; };
  const destino = { tipo: c.tipo, url: c.url, llave: c.llave_publica };
  try {
    const paquete = await crearPaquete(leerTabla, linea);
    const informe = await importarPaquete(destino, paquete, { alProgresar: (m) => linea(m) });
    const fallo = informe.find((i) => i.error);
    /** @type {string[]} */ const filas = [];
    for (const i of informe) {
      const dest = await contarEnDestino(destino, i.tabla);
      const esperadas = paquete.conteos[i.tabla];
      const estado = i.error ? "error" : dest === null ? "enviada (sin verificar)" : dest >= esperadas ? "verificada" : `faltan filas (${dest}/${esperadas})`;
      filas.push(`<tr><td>${esc(i.tabla)}</td><td class="mono">${i.enviadas}</td><td>${esc(estado)}${i.error ? `<br><small>${esc(i.error)}</small>` : ""}</td></tr>`);
    }
    panel.innerHTML = `<h3>Informe de la copia</h3>${paquete.avisos.length ? `<p class="muted">${esc(paquete.avisos.join(" · "))}</p>` : ""}
      <div class="table-wrap"><table><thead><tr><th>Tabla</th><th>Enviadas</th><th>Resultado</th></tr></thead><tbody>${filas.join("")}</tbody></table></div>
      <p>${fallo ? `${icon("alert", 15)} La copia se detuvo en «${esc(fallo.tabla)}». Lo ya copiado queda en el destino; corrige y repite.` : `${icon("check", 15)} Copia terminada. Tablas: ${informe.length} de ${TABLAS.length}.`}</p>`;
    await actualizar(c.id, fallo ? { estado: "error", detalle: (fallo.error || "").slice(0, 200) } : { estado: "copiada", detalle: "", ultima_copia: new Date().toISOString() });
    await cargar();
  } catch (/** @type {any} */ e) { panel.innerHTML = `<p>${icon("alert", 15)} ${esc(e.message)}</p>`; }
}

export const conexionesPage = {
  id: "conexiones", title: "Conexiones de datos", icon: "layers", group: "Sistema", soloSuper: true,
  /** @param {HTMLElement} root */
  async render(root) {
    raiz = root;
    root.innerHTML = `${pageHead("Conexiones de datos", "Asocia otras bases de datos con llaves públicas y copia allí todos los datos de LUMMER.",
      `<button class="btn btn-outline" type="button" data-action="cx-descargar">${icon("download", 16)} Descargar paquete</button><button class="btn btn-primary" type="button" data-action="cx-nueva">${icon("plus", 16)} Agregar conexión</button>`)}
      <div class="cx-aviso" role="note">${icon("info", 18)}<span>Solo se guardan llaves <b>públicas</b>; las secretas (service_role, claves privadas) se rechazan. Esta versión copia los datos; mover también las cuentas de acceso a otra base es una fase aparte (docs/CONEXIONES.md).</span></div>
      <div id="cx-lista" class="cx-lista">${skeleton(3)}</div><section class="card" id="cx-progreso" hidden></section>`;
    registerActions({ "cx-nueva": agregar, "cx-descargar": descargar, "cx-probar": probar, "cx-destino": marcarDestino, "cx-copiar": copiar, "cx-quitar": quitar });
    await cargar();
  },
};
