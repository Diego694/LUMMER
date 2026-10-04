// Portal de apoderados: sin cuenta, con el código de 12 caracteres que entrega el instituto. Solo lectura.
import { CONFIG, isDemoMode } from "../config.js";
import { bindActions, icon, registerActions } from "../ui.js";
import { esc, fmtDate } from "../utils.js";

const $ = (s) => document.querySelector(s);
const root = () => $("#ap-root");
const CLAVE = "ra-apoderado";   // solo se recuerda en este dispositivo para no teclear el código cada vez

/* ------------------------------ Datos ------------------------------ */
async function consultar(codigo) {
  if (isDemoMode()) return consultaDemo(codigo);
  if (!window.supabase) throw new Error("No se pudo cargar la librería de Supabase (¿sin conexión?)");
  const sb = window.supabase.createClient(CONFIG.SUPABASE_URL, CONFIG.SUPABASE_ANON_KEY, { auth: { persistSession: false } });
  const { data, error } = await sb.rpc("consulta_apoderado", { p_codigo: codigo });
  if (error) throw new Error(/Demasiados/.test(error.message) ? error.message : "No se pudo consultar. Revisa tu conexión e inténtalo de nuevo.");
  return data;   // null si el código no existe
}

/** Demo: lee la base demo del navegador (misma forma que devuelve la función SQL). */
function consultaDemo(codigo) {
  let db; try { db = JSON.parse(localStorage.getItem("ra-demo-db-v1")); } catch { db = null; }
  const cod = String(codigo).toUpperCase().replace(/[^0-9A-Z]/g, "");
  const a = db?.alumnos?.find((x) => x.codigo_apoderado === cod);
  if (!a) return null;
  const mios = (db.asistencias || []).filter((x) => x.alumno_id === a.id).sort((p, q) => q.fecha.localeCompare(p.fecha));
  const dias = mios.slice(0, 30).map((x) => ({ fecha: x.fecha, estado: x.hora.slice(0, 5) > "08:00" ? "T" : "P", hora: x.hora.slice(0, 5), salida: x.hora_salida || null }));
  const p = dias.filter((d) => d.estado === "P").length, t = dias.filter((d) => d.estado === "T").length;
  return { alumno: { nombre: a.nombre, carrera: a.nivel, ciclo: a.grado }, instituto: db.colegio.nombre, periodo: null, resumen: { dias: dias.length, presentes: p + t, tardanzas: t, justificadas: 0, faltas: 0, pct: dias.length ? Math.round(100 * (p + t) / dias.length) : null }, dias, comunicados: (db.comunicados || []).slice(0, 3) };
}

/* ------------------------------ Vistas ------------------------------ */
function vistaCodigo(msg = "") {
  root().innerHTML = `<section class="est-card"><h1>Consulta la asistencia</h1>
    <p class="muted">Escribe el <b>código de apoderado</b> que te entregó el instituto (12 letras y números).</p>
    <form id="ap-form" class="stack" autocomplete="off"><label class="sr-only" for="ap-codigo">Código de apoderado</label>
      <input class="input" id="ap-codigo" placeholder="XXXX-XXXX-XXXX" maxlength="16" autocapitalize="characters" inputmode="text" required style="font-size:20px;text-align:center;letter-spacing:.12em">
      <p class="err-msg" id="ap-err" role="alert" ${msg ? "" : "hidden"}>${esc(msg)}</p>
      <button class="btn btn-primary btn-block" type="submit">Ver asistencia</button></form>
    <p class="muted est-legal">Solo muestra la asistencia de un estudiante. Si no tienes el código, pídelo en el instituto.</p></section>`;
  const f = $("#ap-form");
  f.addEventListener("submit", async (e) => {
    e.preventDefault();
    const codigo = $("#ap-codigo").value.trim();
    const btn = f.querySelector("button"); btn.disabled = true; btn.textContent = "Consultando…";
    try {
      const r = await consultar(codigo);
      if (!r) return vistaCodigo("Código no válido. Revísalo e inténtalo de nuevo.");
      try { localStorage.setItem(CLAVE, codigo); } catch { /* sin almacenamiento */ }
      vistaResultado(r, codigo);
    } catch (ex) { vistaCodigo(ex.message); }
  });
}

const CHIP = { P: ["Presente", "ap-p"], T: ["Tardanza", "ap-t"], J: ["Justificada", "ap-j"], F: ["Falta", "ap-f"], pendiente: ["Hoy", "ap-h"] };

function vistaResultado(r, codigo) {
  $("#ap-instituto").textContent = r.instituto || "Asistencia de tu hijo o hija";
  const s = r.resumen || {};
  root().innerHTML = `<section class="est-card"><h1>${esc(r.alumno.nombre)}</h1>
      <p class="muted" style="margin:0">${esc(r.alumno.carrera || "")} · ${esc(r.alumno.ciclo || "")}${r.periodo ? " · Periodo " + esc(r.periodo) : ""}</p></section>
    <section class="ap-kpis"><div class="ap-kpi"><b>${s.pct ?? "—"}${s.pct != null ? "%" : ""}</b><span>Asistencia</span></div><div class="ap-kpi"><b>${s.faltas ?? 0}</b><span>Faltas</span></div>
      <div class="ap-kpi"><b>${s.tardanzas ?? 0}</b><span>Tardanzas</span></div><div class="ap-kpi"><b>${s.justificadas ?? 0}</b><span>Justificadas</span></div></section>
    <section class="est-card"><h3 style="margin-top:0">Últimos días de clase</h3>
      ${(r.dias || []).length ? `<ul class="ap-dias">${r.dias.map((d) => { const [t, c] = CHIP[d.estado] || [d.estado, ""]; return `<li><span>${esc(fmtDate(d.fecha, { weekday: "short", day: "2-digit", month: "short" }))}</span><span class="mono">${d.hora ? esc(d.hora) : ""}${d.salida ? " → " + esc(d.salida) : ""}</span><span class="ap-chip ${c}">${t}</span></li>`; }).join("")}</ul>` : '<p class="muted">Aún no hay días de clase registrados.</p>'}</section>
    ${(r.comunicados || []).length ? `<section class="est-card"><h3 style="margin-top:0">Comunicados del instituto</h3>${r.comunicados.map((c) => `<article class="ap-com"><strong>${esc(c.titulo)}</strong><small class="muted"> · ${esc(fmtDate(c.fecha))}</small><p>${esc(c.mensaje)}</p></article>`).join("")}</section>` : ""}
    <div class="est-actions"><button class="btn btn-outline" data-action="ap-actualizar" data-codigo="${esc(codigo)}">${icon("flip", 16)} Actualizar</button><button class="btn btn-outline" data-action="ap-otro">Consultar otro código</button></div>
    <p class="muted est-legal">Información de solo lectura. Si algo no coincide, comunícate con el instituto.</p>`;
}

registerActions({
  "ap-actualizar": async (el) => { try { const r = await consultar(el.dataset.codigo); if (r) vistaResultado(r, el.dataset.codigo); } catch (e) { alert(e.message); } },
  "ap-otro": () => { try { localStorage.removeItem(CLAVE); } catch { /* ok */ } vistaCodigo(); },
  theme: () => { const t = document.documentElement.getAttribute("data-theme") === "dark" ? "light" : "dark"; document.documentElement.setAttribute("data-theme", t); try { localStorage.setItem("cv-theme", t); } catch { /* ok */ } },
});

async function boot() {
  bindActions();
  let recordado = null; try { recordado = localStorage.getItem(CLAVE); } catch { /* ok */ }
  const url = new URL(location.href).searchParams.get("c");   // enlace compartido: ?c=CODIGO
  if (url) history.replaceState(null, "", location.pathname);   // el código no queda en la barra de direcciones
  const cod = url || recordado;
  if (cod) { try { const r = await consultar(cod); if (r) return vistaResultado(r, cod); } catch { /* cae a pedir el código */ } }
  vistaCodigo();
}
boot();
