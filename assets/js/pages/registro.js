// Registro de asistencia: por QR/NFC/código, por alumno y masivo por grado.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, alumnoPorCodigo, asegurarHoy, opcionesGrado, opcionesNivel, refreshHoy } from "../state.js";
import { badge, emptyState, icon, pageHead, registerActions, toast } from "../ui.js";
import { esTardanza } from "../stats.js";
import { debounce, esc, initials, norm, nowHHMM, todayStr } from "../utils.js";

/** Registra la asistencia de hoy. Devuelve 'ok' | 'dup' | 'inactivo'. Muestra errores reales (no los traga). */
export async function registrarHoy(alumno) {
  if (alumno.estado !== "ACTIVO") return "inactivo";
  await asegurarHoy();
  if (DB.hoy.some((x) => x.alumno_id === alumno.id)) return "dup";
  const hora = nowHHMM();
  try {
    await api.registrarAsistencia({ colegio_id: DB.cid, alumno_id: alumno.id, fecha: todayStr(), hora, registrado_por: await api.userId() });
  } catch (e) {
    if (e.code === "duplicate") { await refreshHoy(); return "dup"; }
    throw e;
  }
  await refreshHoy();
  return { hora };
}

const options = (list, sel) => list.map((o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");

/* ============================ QR / NFC ============================ */
let stream = null, raf = null, facing = "environment", nfcCtl = null, scanLog = [];

export const registroQrPage = {
  id: "registro-qr", title: "Registro por QR", icon: "qr", group: "Registro",
  render(root) {
    root.innerHTML = `
      ${pageHead("Registro por QR", "Escanea el carnet del alumno o ingresa su código para marcar el ingreso de hoy.")}
      <div class="grid-2 scan-layout">
        <section class="card">
          <div class="qr-frame" id="qr-frame"><video id="qr-video" playsinline muted></video><div class="qr-guide" aria-hidden="true"></div><div class="qr-idle" id="qr-idle">${icon("camera", 34)}<span>Cámara apagada</span></div></div>
          <div class="btn-row center">
            <button class="btn btn-primary" data-action="scan-start">${icon("camera", 16)} Iniciar cámara</button>
            <button class="btn btn-outline" data-action="scan-stop">Detener</button>
            <button class="btn btn-outline" id="flip-camera-btn" data-action="scan-flip" hidden>${icon("flip", 16)} Voltear</button>
          </div>
          <div id="scan-result" class="scan-result" role="status" aria-live="polite" hidden></div>
          <hr>
          <form id="manual-form" class="inline-form"><label class="sr-only" for="manual-qr-code">Código del alumno</label>
            <input class="input" id="manual-qr-code" placeholder="¿Sin cámara? Código único, ej: a1001" autocomplete="off">
            <button class="btn btn-teal" type="submit">Registrar</button></form>
        </section>
        <div class="stack">
          <section class="card"><header class="card-head"><h3>Registros de esta sesión</h3><span class="muted" id="scan-count"></span></header><div id="scan-log"></div></section>
          <section class="card"><header class="card-head"><h3>Lector NFC</h3></header>
            <p class="muted" id="nfc-support-msg"></p>
            <div class="btn-row" id="nfc-controls" hidden>
              <button class="btn btn-navy" data-action="nfc-start">${icon("nfc", 16)} Activar lector</button>
              <button class="btn btn-outline" data-action="nfc-stop">Detener</button></div></section>
        </div>
      </div>`;
    root.querySelector("#manual-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const inp = root.querySelector("#manual-qr-code");
      const code = inp.value.trim();
      if (!code) return;
      if (await procesarCodigo(code, true)) inp.value = "";
    });
    initNfc(root);
    pintarLog();
  },
  onLeave() { detenerCamara(); detenerNfc(true); },
};

async function procesarCodigo(codigo, avisarSiNoExiste) {
  const a = alumnoPorCodigo(codigo);
  if (!a) { if (avisarSiNoExiste) toast(`Código no encontrado: ${codigo}`, "error"); return null; }
  let res;
  try { res = await registrarHoy(a); } catch (e) { toast("Error al registrar: " + e.message, "error"); return null; }
  const box = document.getElementById("scan-result");
  const hora = res?.hora;
  const entrada = { alumno: a, estado: res === "dup" ? "dup" : res === "inactivo" ? "inactivo" : "ok", hora: hora || DB.hoy.find((x) => x.alumno_id === a.id)?.hora || "" };
  scanLog.unshift(entrada); scanLog = scanLog.slice(0, 12);
  if (box) {
    const tipo = { ok: ["ok", "Asistencia registrada"], dup: ["warn", "Ya estaba registrado hoy"], inactivo: ["err", "Alumno inactivo — no se registra"] }[entrada.estado];
    box.hidden = false; box.className = `scan-result scan-${tipo[0]}`;
    box.innerHTML = `<span class="avatar">${esc(initials(a.nombre))}</span><div><strong>${esc(a.nombre)}</strong><small>${esc(a.nivel)} · ${esc(a.grado)}${entrada.hora ? " · " + esc(entrada.hora) : ""}</small><em>${tipo[1]}${entrada.estado === "ok" && esTardanza(entrada.hora, CONFIG.HORA_LIMITE) ? " (tardanza)" : ""}</em></div>`;
  }
  pintarLog();
  return a;
}

function pintarLog() {
  const el = document.getElementById("scan-log");
  if (!el) return;
  document.getElementById("scan-count").textContent = scanLog.length ? `${scanLog.filter((s) => s.estado === "ok").length} nuevos` : "";
  el.innerHTML = scanLog.length ? `<ul class="log-list">${scanLog.map((s) => `<li><div class="person"><span class="avatar">${esc(initials(s.alumno.nombre))}</span><div><strong>${esc(s.alumno.nombre)}</strong><small>${esc(s.alumno.grado)}</small></div></div>
    <div class="log-right"><span class="mono">${esc(s.hora)}</span>${s.estado === "ok" ? badge("Registrado", "green") : s.estado === "dup" ? badge("Duplicado", "amber") : badge("Inactivo", "neutral")}</div></li>`).join("")}</ul>`
    : emptyState("Sin registros todavía", "Cada ingreso escaneado aparecerá aquí.", "qr");
}

async function iniciarCamara() {
  if (!navigator.mediaDevices?.getUserMedia) { toast("Este navegador no permite usar la cámara (requiere HTTPS o localhost).", "error"); return; }
  detenerCamara();
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing } });
    const v = document.getElementById("qr-video");
    v.srcObject = stream; await v.play();
    document.getElementById("qr-idle").hidden = true;
    bucleEscaneo();
    const cams = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === "videoinput");
    document.getElementById("flip-camera-btn").hidden = cams.length < 2;
  } catch { toast("No se pudo acceder a la cámara. Usa el registro manual.", "error"); }
}
function detenerCamara() {
  if (raf) cancelAnimationFrame(raf);
  raf = null;
  stream?.getTracks().forEach((t) => t.stop());
  stream = null;
  const v = document.getElementById("qr-video"); if (v) v.srcObject = null;
  const idle = document.getElementById("qr-idle"); if (idle) idle.hidden = false;
}
function bucleEscaneo() {
  const v = document.getElementById("qr-video");
  const canvas = document.createElement("canvas");
  const ctx = canvas.getContext("2d", { willReadFrequently: true });
  let ultimo = 0, ultimoCodigo = "";
  const loop = () => {
    if (!v.srcObject) return;
    if (v.readyState === v.HAVE_ENOUGH_DATA) {
      canvas.width = v.videoWidth; canvas.height = v.videoHeight;
      ctx.drawImage(v, 0, 0);
      const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
      const code = window.jsQR?.(img.data, img.width, img.height);
      const ahora = Date.now();
      if (code?.data && (code.data !== ultimoCodigo || ahora - ultimo > 3000) && ahora - ultimo > 1200) {
        ultimo = ahora; ultimoCodigo = code.data;
        procesarCodigo(code.data.trim(), false);
      }
    }
    raf = requestAnimationFrame(loop);
  };
  loop();
}

function initNfc(root) {
  const msg = root.querySelector("#nfc-support-msg");
  const ok = "NDEFReader" in window;
  msg.textContent = ok ? "Tu navegador soporta NFC (Android + Chrome, con NFC activado). Acerca el tag del alumno a la parte trasera del teléfono."
    : "Este dispositivo o navegador no soporta lectura NFC desde la web (solo Android con Chrome).";
  root.querySelector("#nfc-controls").hidden = !ok;
}
async function iniciarNfc() {
  if (!("NDEFReader" in window)) { toast("Este dispositivo no soporta NFC", "error"); return; }
  try {
    detenerNfc(true);
    nfcCtl = new AbortController();
    const ndef = new NDEFReader();
    await ndef.scan({ signal: nfcCtl.signal });
    toast("Lector NFC activado, acerca un tag", "success");
    let last = 0;
    ndef.onreading = (ev) => {
      if (Date.now() - last < 1500) return;
      last = Date.now();
      let codigo = "";
      for (const rec of ev.message.records) {
        try { codigo = new TextDecoder(rec.encoding || "utf-8").decode(rec.data).trim(); } catch { continue; }
        if (codigo) break;
      }
      if (codigo) procesarCodigo(codigo, true); else toast("No se pudo leer el contenido del tag NFC", "error");
    };
    ndef.onreadingerror = () => toast("No se pudo leer el tag NFC, intenta de nuevo", "error");
  } catch (e) {
    toast(e.name === "NotAllowedError" ? "Permiso de NFC denegado." : e.name === "NotSupportedError" ? "Este dispositivo no tiene NFC o está desactivado." : "No se pudo iniciar NFC: " + e.message, "error");
  }
}
function detenerNfc(silencioso) {
  if (!nfcCtl) return;
  nfcCtl.abort(); nfcCtl = null;
  if (!silencioso) toast("Lector NFC detenido");
}

/* ========================= Por alumno ========================= */
let ra = { q: "", solo: "todos" };
export const registroAlumnoPage = {
  id: "registro-alumno", title: "Registro por Alumno", icon: "userCheck", group: "Registro",
  render(root) {
    root.innerHTML = `
      ${pageHead("Registro por Alumno", "Busca un alumno y marca su asistencia de hoy.")}
      <div class="toolbar"><div class="search"><span class="search-ic">${icon("search", 16)}</span><input class="input" id="ra-q" placeholder="Buscar por nombre o código…" value="${esc(ra.q)}" aria-label="Buscar alumno"></div>
        <select class="filter" id="ra-solo" aria-label="Filtrar"><option value="todos">Todos</option><option value="pendientes">Pendientes de hoy</option><option value="presentes">Ya registrados</option></select></div>
      <div class="card flush" id="ra-list"></div>`;
    root.querySelector("#ra-solo").value = ra.solo;
    root.querySelector("#ra-q").addEventListener("input", debounce((e) => { ra.q = e.target.value; pintar(); }, 150));
    root.querySelector("#ra-solo").addEventListener("change", (e) => { ra.solo = e.target.value; pintar(); });
    pintar();
    function pintar() {
      const q = norm(ra.q);
      const ya = new Set(DB.hoy.map((x) => x.alumno_id));
      const list = DB.alumnos.filter((a) => a.estado === "ACTIVO" && (!q || norm(`${a.nombre} ${a.codigo}`).includes(q))
        && (ra.solo === "todos" || (ra.solo === "presentes") === ya.has(a.id)));
      const el = root.querySelector("#ra-list");
      if (!list.length) { el.innerHTML = emptyState("Sin resultados", "Prueba con otro nombre o cambia el filtro.", "search"); return; }
      el.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Código</th><th>Grado</th><th>Hoy</th><th></th></tr></thead><tbody>
        ${list.slice(0, 100).map((a) => { const h = DB.hoy.find((x) => x.alumno_id === a.id); return `<tr>
          <td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td>
          <td class="mono">${esc(a.codigo)}</td><td>${esc(a.nivel)} · ${esc(a.grado)}</td>
          <td>${h ? badge(`Asistió ${h.hora.slice(0, 5)}`, "green") : badge("Pendiente", "neutral")}</td>
          <td class="t-right"><button class="btn btn-teal btn-sm" data-action="reg-alumno" data-id="${a.id}" ${h ? "disabled" : ""}>${icon("check", 14)} Registrar</button></td></tr>`; }).join("")}
        </tbody></table></div>${list.length > 100 ? `<p class="muted pad">Mostrando 100 de ${list.length}. Refina la búsqueda.</p>` : ""}`;
    }
    root._repaint = pintar;
  },
};

/* ============================ Masivo ============================ */
let rm = { nivel: "", grado: "", fecha: "" };
export const registroMasivoPage = {
  id: "registro-masivo", title: "Registro Masivo por Grado", icon: "listCheck", group: "Registro",
  async render(root) {
    rm.fecha = rm.fecha || todayStr();
    root.innerHTML = `
      ${pageHead("Registro Masivo por Grado", "Marca la asistencia de un grupo completo en un solo paso.")}
      <div class="toolbar">
        <select class="filter" id="rm-nivel" aria-label="Nivel">${options(opcionesNivel(true), rm.nivel)}</select>
        <select class="filter" id="rm-grado" aria-label="Grado">${options(opcionesGrado(rm.nivel, true), rm.grado)}</select>
        <input class="filter" type="date" id="rm-fecha" max="${todayStr()}" value="${rm.fecha}" aria-label="Fecha"></div>
      <div class="card"><div class="card-head"><span class="muted" id="rm-count"></span>
        <div class="btn-row"><button class="btn btn-outline btn-sm" data-action="rm-all" data-v="1">Marcar todos</button><button class="btn btn-outline btn-sm" data-action="rm-all" data-v="0">Desmarcar todos</button></div></div>
        <div id="rm-list"></div>
        <div class="card-foot"><button class="btn btn-primary" data-action="rm-save">${icon("check", 16)} Guardar asistencia del grupo</button></div></div>`;
    const nivel = root.querySelector("#rm-nivel"), grado = root.querySelector("#rm-grado"), fecha = root.querySelector("#rm-fecha");
    nivel.addEventListener("change", () => { rm.nivel = nivel.value; rm.grado = ""; grado.innerHTML = options(opcionesGrado(rm.nivel, true), ""); pintar(); });
    grado.addEventListener("change", () => { rm.grado = grado.value; pintar(); });
    fecha.addEventListener("change", () => { rm.fecha = fecha.value || todayStr(); pintar(); });
    await pintar();
    async function pintar() {
      const list = DB.alumnos.filter((a) => a.estado === "ACTIVO" && (!rm.nivel || a.nivel === rm.nivel) && (!rm.grado || a.grado === rm.grado));
      root.querySelector("#rm-count").textContent = `${list.length} alumno(s) activos en este grupo`;
      const el = root.querySelector("#rm-list");
      if (!list.length) { el.innerHTML = emptyState("Sin alumnos", "Elige un nivel y grado con alumnos activos.", "users"); return; }
      let dia = [];
      try { dia = rm.fecha === todayStr() ? DB.hoy : await api.asistenciasPorFecha(DB.cid, rm.fecha); } catch (e) { toast("No se pudo leer la asistencia: " + e.message, "error"); }
      if (!el.isConnected) return;
      const ya = new Map(dia.map((x) => [x.alumno_id, x.hora]));
      el.innerHTML = list.map((a) => `<label class="check-row"><input type="checkbox" data-alumno-id="${a.id}" ${ya.has(a.id) ? "checked disabled" : ""}>
        <span class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)} <small class="mono muted">${esc(a.codigo)}</small></span></span>
        ${ya.has(a.id) ? badge(`Ya registrado ${ya.get(a.id).slice(0, 5)}`, "green") : ""}</label>`).join("");
    }
  },
};

registerActions({
  "scan-start": iniciarCamara,
  "scan-stop": detenerCamara,
  "scan-flip": () => { facing = facing === "environment" ? "user" : "environment"; iniciarCamara(); },
  "nfc-start": iniciarNfc,
  "nfc-stop": () => detenerNfc(false),
  "reg-alumno": async (el) => {
    const a = DB.alumnos.find((x) => x.id === el.dataset.id);
    el.disabled = true;
    try {
      const r = await registrarHoy(a);
      toast(r === "dup" ? "Ya estaba registrado hoy" : `Asistencia registrada: ${a.nombre}`, r === "dup" ? "info" : "success");
    } catch (e) { toast("Error al registrar: " + e.message, "error"); }
    document.getElementById("page-root")._repaint?.();
  },
  "rm-all": (el) => document.querySelectorAll("#rm-list input[type=checkbox]:not(:disabled)").forEach((c) => (c.checked = el.dataset.v === "1")),
  "rm-save": async (el) => {
    const marcados = [...document.querySelectorAll("#rm-list input[type=checkbox]:checked:not(:disabled)")];
    if (!marcados.length) { toast("Selecciona al menos un alumno"); return; }
    el.disabled = true;
    try {
      const fecha = rm.fecha || todayStr();
      // En fechas pasadas no se conoce la hora real: se usa la hora límite (cuenta como puntual).
      const hora = fecha === todayStr() ? nowHHMM() : CONFIG.HORA_LIMITE;
      const uid = await api.userId();
      const n = await api.registrarMasivo(marcados.map((c) => ({ colegio_id: DB.cid, alumno_id: c.dataset.alumnoId, fecha, hora, registrado_por: uid })));
      if (fecha === todayStr()) await refreshHoy();
      toast(`${n} asistencia(s) registradas`, "success");
      registroMasivoPage.render(document.getElementById("page-root"));
    } catch (e) { toast("Error al guardar: " + e.message, "error"); el.disabled = false; }
  },
});
