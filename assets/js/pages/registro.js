// Registro de asistencia: por QR/NFC/código, por alumno y masivo por grado.
import { api } from "../api.js";
import { cola } from "../cola.js";
import { CONFIG } from "../config.js";
import { DB, alumnoPorCodigo, asegurarHoy, opcionesGrado, opcionesNivel, refreshHoy } from "../state.js";
import { badge, emptyState, icon, pageHead, registerActions, toast } from "../ui.js";
import { horarioDe } from "../calendario.js";
import { decidirAccion, esTardanza } from "../stats.js";
import { emitir, guardarAsistencias, red, registrarEnvio } from "../sync.js";
import { verificarQR } from "../qr-seguro.js";
import { mostrarAlertaAsistencia } from "../alerta.js";
import { cargarCursoHoy, cursosActivos, etiquetaCurso, registrarEnCurso } from "./cursos.js";
import { ahora, censurarNombre, debounce, esErrorRed, esc, etiquetaCiclo, initials, norm, nowHHMM, todayStr } from "../utils.js";

/**
 * Registra la asistencia de hoy con la hora confiable (servidor + zona del instituto).
 * Devuelve 'dup' | 'inactivo' | 'pendiente' | { hora, offline? }. Sin red, el registro queda en la cola del teléfono y se envía solo.
 * Los errores que no son de red (permisos, datos) se propagan: no se tragan.
 */
export async function registrarHoy(alumno, origen = "manual") {
  if (alumno.estado !== "ACTIVO") return "inactivo";
  if (alumno.aprobado === false) return "pendiente"; // auto‑registrado: el instituto aún no aprobó su QR
  await asegurarHoy();
  if (DB.hoy.some((x) => x.alumno_id === alumno.id)) return "dup";
  const hora = nowHHMM(), fecha = todayStr();
  const row = { colegio_id: DB.cid, alumno_id: alumno.id, fecha, hora, registrado_por: DB.userId || (await api.userId()), origen };
  const aCola = () => {
    cola.agregar({ tipo: "asistencia", row, clave: `a|${alumno.id}|${fecha}` });
    DB.hoy.push({ ...row, _pendiente: true });
    emitir();
    return { hora, offline: true };
  };
  if (!red.online()) return aCola();
  try { await api.registrarAsistencia(row); }
  catch (e) {
    if (e.code === "duplicate") { await refreshHoy(); return "dup"; }
    if (esErrorRed(e)) return aCola();
    throw e;
  }
  await refreshHoy();
  return { hora };
}

registrarEnvio("salida", (rows) => api.registrarSalidas(rows));

/**
 * Registra la SALIDA de hoy (solo si ya hay ingreso y solo una vez). Sin red, queda en la cola y se envía sola.
 * Devuelve 'inactivo' | 'pendiente' | 'sin_entrada' | 'dup_salida' | { hora, salida:true, offline? }.
 */
export async function registrarSalida(alumno, origen = "manual") {
  if (alumno.estado !== "ACTIVO") return "inactivo";
  if (alumno.aprobado === false) return "pendiente";
  await asegurarHoy();
  const reg = DB.hoy.find((x) => x.alumno_id === alumno.id);
  if (!reg) return "sin_entrada";
  if (reg.hora_salida) return "dup_salida";
  const hora = nowHHMM(), fecha = todayStr();
  const row = { alumno_id: alumno.id, fecha, hora, origen };
  const aCola = () => {
    cola.agregar({ tipo: "salida", row, clave: `s|${alumno.id}|${fecha}` });
    reg.hora_salida = hora; reg._salidaPendiente = true;
    emitir();
    return { hora, salida: true, offline: true };
  };
  if (!red.online()) return aCola();
  try {
    const r = await api.registrarSalidas([row]);
    if (r?.dup) { await refreshHoy(); return "dup_salida"; }
    if (r?.sin_entrada) return "sin_entrada";
  } catch (e) {
    if (esErrorRed(e)) return aCola();
    throw e;
  }
  await refreshHoy();
  return { hora, salida: true };
}

/** Qué corresponde hacer con un alumno que se presenta en la puerta: ingreso, salida o nada (ver stats.decidirAccion). */
export async function accionParaAlumno(alumno) {
  await asegurarHoy();
  return decidirAccion(DB.hoy.find((x) => x.alumno_id === alumno.id), nowHHMM(), horarioDe(DB.horarios, alumno.nivel, { limite: CONFIG.HORA_LIMITE, permanencia: CONFIG.MIN_PERMANENCIA_MIN }).permanencia);
}

const options = (list, sel) => list.map((o) => `<option value="${esc(o.value)}" ${o.value === sel ? "selected" : ""}>${esc(o.label)}</option>`).join("");

/* ============================ QR / NFC ============================ */
let stream = null, raf = null, facing = "environment", nfcCtl = null, scanLog = [];
let modoCurso = null;   // null = asistencia diaria; si no, el curso donde se pasa lista
let modoSalida = false; // false = ingreso; true = salida

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
          <div class="field" style="margin:14px 0 0"><label for="modo-tipo">Estoy registrando</label><select id="modo-tipo"><option value="ingreso" ${modoSalida ? "" : "selected"}>Ingreso</option><option value="salida" ${modoSalida ? "selected" : ""}>Salida</option></select></div>
          ${cursosActivos().length ? `<div class="field" style="margin:14px 0 0"><label for="modo-registro">Registrar en</label><select id="modo-registro"><option value="">Asistencia diaria</option>${cursosActivos().map((c) => `<option value="${c.id}" ${modoCurso?.id === c.id ? "selected" : ""}>${esc(etiquetaCurso(c))}</option>`).join("")}</select></div>` : ""}
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
    root.querySelector("#modo-tipo").addEventListener("change", (e) => { modoSalida = e.target.value === "salida"; toast(modoSalida ? "Registrando SALIDAS" : "Registrando INGRESOS", "info"); });
    root.querySelector("#modo-registro")?.addEventListener("change", async (e) => {
      modoCurso = DB.cursos.find((c) => c.id === e.target.value) || null;
      if (modoCurso) { try { await cargarCursoHoy(modoCurso); } catch { /* se cargará al primer registro */ } toast(`Pasando lista en: ${modoCurso.nombre}`, "info"); }
    });
    initNfc(root);
    pintarLog();
  },
  onLeave() { detenerCamara(); detenerNfc(true); },
};

const MOTIVO_QR = {
  vencido: "El QR está vencido. Pide al estudiante que abra su carnet para actualizarlo.",
  firma: "La firma del QR no coincide: puede ser una copia. Verifica la identidad del estudiante.",
  sinsecreto: "Este alumno aún no tiene QR seguro. Usa su código o NFC.",
  desconocido: "QR no reconocido.",
};

/**
 * Del texto leído (QR, NFC o código) al alumno, validando el QR dinámico según CONFIG.QR_MODO.
 * Devuelve { alumno } o { rechazo: { motivo, mensaje, alumno? } }.
 */
export async function resolverAlumno(texto, origen = "manual") {
  let codigo = texto;
  if (origen === "qr") {
    const v = await verificarQR(texto, alumnoPorCodigo, ahora().getTime());
    if (v.estatico) {
      if (CONFIG.QR_MODO === "obligatorio" && alumnoPorCodigo(texto)) return { rechazo: { motivo: "estatico", mensaje: "QR estático no permitido: el estudiante debe abrir su carnet en la app (se puede usar NFC o código manual)." } };
    } else if (!v.ok) return { rechazo: { motivo: v.motivo, mensaje: MOTIVO_QR[v.motivo], alumno: v.alumno || null } };
    else codigo = v.alumno.codigo;
  }
  const a = alumnoPorCodigo(codigo);
  if (!a) return { rechazo: { motivo: "desconocido", mensaje: `Código no encontrado: ${codigo}` } };
  return { alumno: a };
}

async function procesarCodigo(texto, avisarSiNoExiste, origen = "manual") {
  const r = await resolverAlumno(texto, origen);
  if (r.rechazo) {
    if (r.rechazo.alumno) mostrarAlertaAsistencia(r.rechazo.alumno, { tipo: "qr_invalido", detalle: r.rechazo.mensaje.split(".")[0] });
    else if (avisarSiNoExiste || r.rechazo.motivo === "estatico") toast(r.rechazo.mensaje, "error");
    return null;
  }
  const a = r.alumno;
  let res;
  try { res = modoSalida ? await registrarSalida(a, origen) : modoCurso ? await registrarEnCurso(a, modoCurso, origen) : await registrarHoy(a, origen); } catch (e) { toast("Error al registrar: " + e.message, "error"); return null; }
  const box = document.getElementById("scan-result");
  const hora = res?.hora;
  const estado = typeof res === "string" ? res : res?.offline ? "offline" : res?.salida ? "salida" : "ok";
  const entrada = { alumno: a, estado, hora: hora || DB.hoy.find((x) => x.alumno_id === a.id)?.hora || "" };
  scanLog.unshift(entrada); scanLog = scanLog.slice(0, 12);
  if (box) {
    const tipo = { salida: ["ok", "Salida registrada"], sin_entrada: ["err", "Sin ingreso hoy: no se puede registrar salida"], dup_salida: ["warn", "Ya tenía salida registrada"], ok: ["ok", modoCurso ? `Asistencia registrada en ${modoCurso.nombre}` : "Asistencia registrada"], no_pertenece: ["err", "No pertenece a este curso"], offline: ["warn", "Guardado sin conexión: se enviará solo"], dup: ["warn", "Ya estaba registrado hoy"], pendiente: ["warn", "Registro pendiente de aprobación"], inactivo: ["err", "Alumno inactivo — no se registra"] }[estado];
    box.hidden = false; box.className = `scan-result scan-${tipo[0]}`;
    box.innerHTML = `<span class="avatar">${esc(initials(a.nombre))}</span><div><strong>${esc(censurarNombre(a))}</strong><small>${esc(etiquetaCiclo(a.nivel, a.grado))}${entrada.hora ? " · " + esc(entrada.hora) : ""}</small><em>${tipo[1]}${(estado === "ok" || estado === "offline") && esTardanza(entrada.hora, CONFIG.HORA_LIMITE, a.nivel) ? " (tardanza)" : ""}</em></div>`;
  }
  mostrarAlertaAsistencia(a, { tipo: estado, hora: entrada.hora, detalle: modoCurso?.nombre || "" });
  pintarLog();
  return a;
}

function pintarLog() {
  const el = document.getElementById("scan-log");
  if (!el) return;
  document.getElementById("scan-count").textContent = scanLog.length ? `${scanLog.filter((s) => s.estado === "ok" || s.estado === "offline" || s.estado === "salida").length} nuevos` : "";
  el.innerHTML = scanLog.length ? `<ul class="log-list">${scanLog.map((s) => `<li><div class="person"><span class="avatar">${esc(initials(s.alumno.nombre))}</span><div><strong>${esc(censurarNombre(s.alumno))}</strong><small>${esc(s.alumno.grado)}</small></div></div>
    <div class="log-right"><span class="mono">${esc(s.hora)}</span>${s.estado === "salida" ? badge("Salida", "navy") : s.estado === "dup_salida" ? badge("Salida repetida", "amber") : s.estado === "sin_entrada" ? badge("Sin ingreso", "red") : s.estado === "ok" ? badge("Registrado", "green") : s.estado === "offline" ? badge("Sin enviar", "amber") : s.estado === "dup" ? badge("Duplicado", "amber") : s.estado === "pendiente" ? badge("Pendiente", "amber") : s.estado === "no_pertenece" ? badge("Otro curso", "red") : badge("Inactivo", "neutral")}</div></li>`).join("")}</ul>`
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
        procesarCodigo(code.data.trim(), false, "qr");
      }
    }
    raf = requestAnimationFrame(loop);
  };
  loop();
}

// Dentro del APK, Web NFC no existe en WebView: el lector lo hace la app nativa (AndroidBridge) y llama a window.onNativeNfc(código).
const nfcNativo = () => globalThis.AndroidBridge?.nfcState?.();

function initNfc(root) {
  const msg = root.querySelector("#nfc-support-msg");
  const estado = nfcNativo();
  if (estado) {
    window.onNativeNfc = (codigo) => procesarCodigo(String(codigo).trim(), true, "nfc");
    msg.textContent = estado === "on" ? "Lector NFC de la app disponible. Acerca el tag del alumno a la parte trasera del teléfono."
      : estado === "off" ? "El NFC está desactivado en el teléfono. Actívalo en Ajustes y vuelve a esta pantalla." : "Este teléfono no tiene NFC.";
    root.querySelector("#nfc-controls").hidden = estado !== "on";
    return;
  }
  const ok = "NDEFReader" in window;
  msg.textContent = ok ? "Tu navegador soporta NFC (Android + Chrome, con NFC activado). Acerca el tag del alumno a la parte trasera del teléfono."
    : "Este dispositivo o navegador no soporta lectura NFC desde la web (solo Android con Chrome).";
  root.querySelector("#nfc-controls").hidden = !ok;
}
async function iniciarNfc() {
  if (nfcNativo()) { globalThis.AndroidBridge.startNfc(); toast("Lector NFC activado, acerca un tag", "success"); return; }
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
      if (codigo) procesarCodigo(codigo, true, "nfc"); else toast("No se pudo leer el contenido del tag NFC", "error");
    };
    ndef.onreadingerror = () => toast("No se pudo leer el tag NFC, intenta de nuevo", "error");
  } catch (e) {
    toast(e.name === "NotAllowedError" ? "Permiso de NFC denegado." : e.name === "NotSupportedError" ? "Este dispositivo no tiene NFC o está desactivado." : "No se pudo iniciar NFC: " + e.message, "error");
  }
}
function detenerNfc(silencioso) {
  if (nfcNativo()) { globalThis.AndroidBridge.stopNfc(); if (!silencioso) toast("Lector NFC detenido"); return; }
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
      const list = DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && (!q || norm(`${a.nombre} ${a.codigo}`).includes(q))
        && (ra.solo === "todos" || (ra.solo === "presentes") === ya.has(a.id)));
      const el = root.querySelector("#ra-list");
      if (!list.length) { el.innerHTML = emptyState("Sin resultados", "Prueba con otro nombre o cambia el filtro.", "search"); return; }
      el.innerHTML = `<div class="table-wrap"><table><thead><tr><th>Alumno</th><th>Código</th><th>Ciclo</th><th>Hoy</th><th></th></tr></thead><tbody>
        ${list.slice(0, 100).map((a) => { const h = DB.hoy.find((x) => x.alumno_id === a.id); return `<tr>
          <td><div class="person"><span class="avatar">${esc(initials(a.nombre))}</span><span>${esc(a.nombre)}</span></div></td>
          <td class="mono">${esc(a.codigo)}</td><td>${esc(etiquetaCiclo(a.nivel, a.grado))}</td>
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
  id: "registro-masivo", title: "Registro Masivo por Ciclo", icon: "listCheck", group: "Registro",
  async render(root) {
    rm.fecha = rm.fecha || todayStr();
    root.innerHTML = `
      ${pageHead("Registro Masivo por Ciclo", "Marca la asistencia de un ciclo o salón completo en un solo paso.")}
      <div class="toolbar">
        <select class="filter" id="rm-nivel" aria-label="Carrera">${options(opcionesNivel(true), rm.nivel)}</select>
        <select class="filter" id="rm-grado" aria-label="Ciclo">${options(opcionesGrado(rm.nivel, true), rm.grado)}</select>
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
      const list = DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false && (!rm.nivel || a.nivel === rm.nivel) && (!rm.grado || a.grado === rm.grado));
      root.querySelector("#rm-count").textContent = `${list.length} alumno(s) activos en este grupo`;
      const el = root.querySelector("#rm-list");
      if (!list.length) { el.innerHTML = emptyState("Sin alumnos", "Elige una carrera y ciclo con alumnos activos.", "users"); return; }
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
      const r = await registrarHoy(a, "alumno");
      mostrarAlertaAsistencia(a, { tipo: typeof r === "string" ? r : r?.offline ? "offline" : "ok", hora: r?.hora || DB.hoy.find((x) => x.alumno_id === a.id)?.hora || "" });
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
      const uid = DB.userId || (await api.userId());
      const r = await guardarAsistencias(marcados.map((c) => ({ colegio_id: DB.cid, alumno_id: c.dataset.alumnoId, fecha, hora, registrado_por: uid, origen: "masivo" })));
      if (fecha === todayStr()) await refreshHoy();
      toast(r.offline ? `${r.n} asistencia(s) guardadas SIN CONEXIÓN: se enviarán solas al reconectar` : `${r.n} asistencia(s) registradas`, r.offline ? "info" : "success");
      registroMasivoPage.render(document.getElementById("page-root"));
    } catch (e) { toast("Error al guardar: " + e.message, "error"); el.disabled = false; }
  },
});
