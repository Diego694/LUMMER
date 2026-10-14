// @ts-check
// Modo quiosco: una tablet o teléfono fijo en la puerta donde cada estudiante muestra su carnet (QR) o acerca su tag NFC.
// Pantalla completa, sin menús, resultado grande con foto y sonido, ingreso/salida automáticos, funciona sin internet
// (los registros quedan en la cola y se envían solos) y solo se sale con un PIN.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB, asegurarHoy, refreshHoy } from "../state.js";
import { registerActions, toast } from "../ui.js";
import { estadoIngreso, horarioDe, salidaPermitida } from "../calendario.js";
import { esTardanza } from "../stats.js";
import { estadoSync, onEstado, red } from "../sync.js";
import { accionParaAlumno, registrarHoy, registrarSalida, resolverAlumno } from "./registro.js";
import { censurarNombre, esc, etiquetaCiclo, initials, nowHHMM, todayStr, ahora, horaZona } from "../utils.js";

const KEY = "ra-quiosco";
/** @type {any} */
let ajustes = null;           // { pinHash, modo: 'auto'|'entrada'|'salida', camara: 'user'|'environment', voz, sonido }
let activo = false, ocupado = false;
/** @type {any} */
let stream = null;
/** @type {any} */
let raf = null;
/** @type {any} */
let timerFin = null;
/** @type {any} */
let timerReloj = null;
/** @type {any} */
let timerRefresco = null;
/** @type {any} */
let wake = null;
/** @type {any} */
let quitarEstado = null;
let buffer = "", bufferT = 0;

/* ----------------------------- Utilidades ----------------------------- */
const leer = () => { try { return JSON.parse(localStorage.getItem(KEY) || "null"); } catch { return null; } };
const guardar = (/** @type {any} */ a) => { try { localStorage.setItem(KEY, JSON.stringify(a)); } catch { /* sin almacenamiento */ } };

/**
 * Normaliza la configuración persistida del quiosco garantizando tipos y valores válidos.
 * @param {any} [raw]
 * @returns {{ pinHash: string, modo: 'auto'|'entrada'|'salida', camara: 'user'|'environment', sonido: boolean, voz: boolean, vista: 'camara'|'qr', qrSeg: 15|30 }}
 */
export function normalizarConfigQuiosco(raw = {}) {
  const c = raw || {};
  const modo = c.modo === "entrada" || c.modo === "salida" ? c.modo : "auto";
  const camara = c.camara === "environment" ? "environment" : "user";
  const vista = c.vista === "qr" ? "qr" : "camara";
  const qrSeg = Number(c.qrSeg) === 15 ? 15 : 30;
  return {
    pinHash: String(c.pinHash || ""),
    modo,
    camara,
    sonido: c.sonido !== false,
    voz: !!c.voz,
    vista,
    qrSeg,
  };
}

/**
 * Calcula los segundos restantes en la ventana actual para un intervalo dado.
 * @param {number} epochSegundos
 * @param {number} [intervaloSeg]
 * @returns {number}
 */
export function segundosRestantesQr(epochSegundos, intervaloSeg = 30) {
  const i = Math.max(10, Math.min(60, Number(intervaloSeg) || 30));
  const s = Math.floor(Number(epochSegundos) || 0);
  const mod = ((s % i) + i) % i;
  return i - mod;
}

/** @type {{ sesionId: string, timer: any, qr: any, ultimoToken: number, restante: number, intervaloSeg: number } | null} */
let qrActivo = null;
let ocupadoQrTick = false;


export async function hashPin(/** @type {any} */ pin) {
  const texto = `ra-quiosco:${pin}`;
  try {
    const b = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(texto));
    return [...new Uint8Array(b)].map((x) => x.toString(16).padStart(2, "0")).join("");
  } catch { let h = 0; for (const c of texto) h = (h * 31 + c.charCodeAt(0)) | 0; return "w" + h; }   // sin crypto.subtle (contexto no seguro)
}

/** @type {any} */
let ctxAudio = null;
function pitido(/** @type {any} */ tipo) {
  if (!ajustes?.sonido) return;
  try {
    ctxAudio ??= new (window.AudioContext || window.webkitAudioContext)();
    const notas = /** @type {Record<string, number[][]>} */ ({ ok: [[880, 0.12]], tarde: [[660, 0.12], [660, 0.12]], error: [[220, 0.35]], info: [[520, 0.12]], salida: [[660, 0.1], [880, 0.14]] })[tipo] || [[600, 0.1]];
    let t = ctxAudio.currentTime;
    notas.forEach(([f = 0, d = 0]) => {
      const o = ctxAudio.createOscillator(), g = ctxAudio.createGain();
      o.frequency.value = f; o.type = "sine"; g.gain.value = 0.18;
      o.connect(g); g.connect(ctxAudio.destination); o.start(t); o.stop(t + d); t += d + 0.06;
    });
  } catch { /* sin audio */ }
}
function hablar(/** @type {any} */ texto) {
  if (!ajustes?.voz || !("speechSynthesis" in window)) return;
  try { speechSynthesis.cancel(); const u = new SpeechSynthesisUtterance(texto); u.lang = "es-PE"; u.rate = 1; speechSynthesis.speak(u); } catch { /* sin voz */ }
}

/* ------------------------------ Resultado ------------------------------ */
/** Convierte lo que devolvió registrarHoy/registrarSalida en lo que se muestra: { clase, titulo, detalle, sonido, voz }. */
export function presentarResultado(/** @type {any} */ estado, /** @type {any} */ alumno, /** @type {any} */ hora, /** @type {any} */ extra = {}) {
  const nombre = (alumno.nombre || "").split(" ")[0];
  if (typeof estado === "string") {
    return {
      dup: { clase: "info", titulo: "Ya registraste tu ingreso", detalle: "Hoy ya estás registrado.", sonido: "info", voz: `${nombre}, ya registraste tu ingreso` },
      ya_ingreso: { clase: "info", titulo: "Ya registraste tu ingreso", detalle: extra.desde ? `Podrás marcar tu salida desde las ${extra.desde}.` : "Hoy ya estás registrado.", sonido: "info", voz: extra.desde ? `${nombre}, podrás salir desde las ${extra.desde}` : `${nombre}, ya registraste tu ingreso` },
      salida_temprana: { clase: "warn", titulo: "Aún no puedes marcar tu salida", detalle: `Podrás hacerlo desde las ${extra.desde || "—"}. Si necesitas salir antes, habla con tu docente.`, sonido: "error", voz: "Aún no puedes marcar tu salida" },
      temprano: { clase: "warn", titulo: "Aún no es hora de ingreso", detalle: `El ingreso se abre a las ${extra.desde || "—"}.`, sonido: "error", voz: "Aún no es hora de ingreso" },
      cerrado: { clase: "err", titulo: "El ingreso ya cerró", detalle: `Cerró a las ${extra.hasta || "—"}. Habla con tu docente.`, sonido: "error", voz: "El ingreso ya cerró" },
      dup_salida: { clase: "info", titulo: "Ya registraste tu salida", detalle: "", sonido: "info", voz: `${nombre}, ya registraste tu salida` },
      sin_entrada: { clase: "err", titulo: "No registraste tu ingreso hoy", detalle: "Habla con tu docente.", sonido: "error", voz: "No registraste tu ingreso hoy" },
      pendiente: { clase: "warn", titulo: "Tu registro está pendiente", detalle: "El instituto aún debe aprobarlo.", sonido: "error", voz: "Tu registro está pendiente de aprobación" },
      inactivo: { clase: "err", titulo: "Alumno inactivo", detalle: "Consulta en secretaría.", sonido: "error", voz: "Alumno inactivo" },
    }[estado] || { clase: "err", titulo: "No se pudo registrar", detalle: "", sonido: "error", voz: "" };
  }
  const off = estado.offline ? "Guardado sin conexión: se enviará solo." : "";
  if (estado.salida) return { clase: "out", titulo: "¡Hasta luego!", detalle: off, sonido: "salida", voz: `Hasta luego, ${nombre}` };
  const tarde = esTardanza(hora, CONFIG.HORA_LIMITE, alumno.nivel);
  return tarde
    ? { clase: "warn", titulo: "Llegada tardía", detalle: off || "Tu ingreso quedó registrado con tardanza.", sonido: "tarde", voz: `Bienvenido, ${nombre}. Llegaste tarde` }
    : { clase: "ok", titulo: "¡Bienvenido/a!", detalle: off || "Ingreso puntual registrado.", sonido: "ok", voz: `Bienvenido, ${nombre}` };
}

function mostrar(/** @type {any} */ r, /** @type {any} */ alumno, /** @type {any} */ hora = undefined) {
  const el = /** @type {HTMLElement} */ (document.getElementById("q-resultado"));
  if (!el) return;
  clearTimeout(timerFin);
  el.className = `q-resultado q-${r.clase}`;
  el.innerHTML = `<div class="q-foto" id="q-foto">${alumno ? esc(initials(alumno.nombre)) : "!"}</div>
    <div class="q-textos"><h2>${esc(r.titulo)}</h2>${alumno ? `<strong>${esc(censurarNombre(alumno))}</strong><span>${esc(etiquetaCiclo(alumno.nivel, alumno.grado))}${hora ? " · " + esc(hora) : ""}</span>` : ""}${r.detalle ? `<p>${esc(r.detalle)}</p>` : ""}</div>`;
  if (alumno) api.fotoUrl(alumno).then((u) => { const f = /** @type {HTMLElement} */ (document.getElementById("q-foto")); if (u && f) f.innerHTML = `<img src="${esc(u)}" alt="">`; }).catch(() => {});
  pitido(r.sonido); hablar(r.voz);
  timerFin = setTimeout(reposo, r.clase === "err" ? 4500 : 3500);
  pintarContadores();
}
function reposo() {
  const el = /** @type {HTMLElement} */ (document.getElementById("q-resultado"));
  if (!el) return;
  el.className = "q-resultado q-espera";
  if (ajustes?.vista === "qr") {
    el.innerHTML = `<div class="q-guia-texto"><h2>Escanea el código</h2><p>Abre la app LUMMER en tu celular y toca <b>Escanear</b>.</p></div>`;
  } else {
    el.innerHTML = `<div class="q-guia-texto"><h2>Acerca tu carnet</h2><p>Muestra el código QR a la cámara o acerca tu tag NFC.</p></div>`;
  }
}

function pintarContadores() {
  const c = /** @type {HTMLElement} */ (document.getElementById("q-contadores"));
  if (!c) return;
  const activos = DB.alumnos.filter((a) => a.estado === "ACTIVO" && a.aprobado !== false).length;
  const ing = DB.hoy.length, sal = DB.hoy.filter((x) => x.hora_salida).length;
  const s = estadoSync();
  c.innerHTML = `<span class="q-stat"><b>${ing}</b> de ${activos} ingresaron</span><span class="q-stat"><b>${sal}</b> salidas</span>${!red.online() ? '<span class="q-stat q-off">Sin conexión</span>' : ""}${s.pendientes ? `<span class="q-stat q-off">${s.pendientes} por enviar</span>` : ""}`;
}

/* ------------------------------ Procesar lectura ------------------------------ */
/** Lectura de QR / NFC / lector USB / código: devuelve true si se atendió. Exportada para pruebas. */
export async function procesar(/** @type {any} */ texto, origen = "qr") {
  if (ocupado || !texto) return false;
  ocupado = true;
  try {
    const r = await resolverAlumno(String(texto).trim(), origen);
    if (r.rechazo) {
      mostrar({ clase: "err", titulo: r.rechazo.motivo === "vencido" ? "QR vencido" : r.rechazo.motivo === "desconocido" ? "Código no reconocido" : "QR no válido",
        detalle: r.rechazo.motivo === "vencido" ? "Abre tu carnet en la app para actualizar el código." : r.rechazo.mensaje, sonido: "error", voz: "" }, r.rechazo.alumno || null);
      return true;
    }
    const a = r.alumno;
    const modo = ajustes?.modo || "auto";
    const h = horarioDe(DB.horarios, a.nivel, { limite: CONFIG.HORA_LIMITE, permanencia: CONFIG.MIN_PERMANENCIA_MIN });
    const ahoraHM = nowHHMM();
    let accion = modo === "salida" ? "salida" : modo === "entrada" ? "entrada" : await accionParaAlumno(a);
    const reg = DB.hoy.find((x) => x.alumno_id === a.id);
    let res, extra = {};
    if (accion === "entrada") {
      // ventana de ingreso del horario: antes de la apertura o después del cierre no se registra
      const ev = reg ? "puntual" : estadoIngreso(h, ahoraHM);
      if (ev === "temprano") { res = "temprano"; extra = { desde: h.desde }; }
      else if (ev === "cerrado") { res = "cerrado"; extra = { hasta: h.hasta }; }
      else res = await registrarHoy(a, origen === "qr" ? "quiosco" : origen);
    } else if (accion === "salida") {
      // permanencia mínima: nadie sale antes de X minutos desde su ingreso (evita fugas)
      const sp = /** @type {any} */ (reg && !reg.hora_salida ? salidaPermitida(reg.hora, ahoraHM, h.permanencia) : { ok: true });
      if (!sp.ok) { res = "salida_temprana"; extra = { desde: sp.desde }; }
      else res = await registrarSalida(a, origen === "qr" ? "quiosco" : origen);
    } else {
      res = accion;   // 'ya_ingreso' | 'dup_salida'
      if (accion === "ya_ingreso" && reg) extra = { desde: salidaPermitida(reg.hora, ahoraHM, h.permanencia).desde };
    }
    const hora = typeof res === "object" ? res.hora : DB.hoy.find((x) => x.alumno_id === a.id)?.hora || "";
    mostrar(presentarResultado(res, a, hora, extra), a, hora);
  } catch (/** @type {any} */ e) {
    mostrar({ clase: "err", titulo: "No se pudo registrar", detalle: e.message, sonido: "error", voz: "" }, null);
  } finally { setTimeout(() => { ocupado = false; }, 700); }
  return true;
}

/* ------------------------------ Entradas: cámara, NFC, lector USB ------------------------------ */
async function iniciarCamara() {
  if (!navigator.mediaDevices?.getUserMedia) { toast("Este navegador no permite usar la cámara (requiere HTTPS).", "error"); return; }
  try {
    stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: ajustes?.camara || "environment" } });
    const v = /** @type {HTMLVideoElement} */ (document.getElementById("q-video"));
    v.srcObject = stream; await v.play();
    const canvas = document.createElement("canvas"), ctx = /** @type {CanvasRenderingContext2D} */ (canvas.getContext("2d", { willReadFrequently: true }));
    let ultimo = "", t0 = 0;
    const loop = () => {
      if (!activo || !v.srcObject) return;
      if (v.readyState === v.HAVE_ENOUGH_DATA) {
        canvas.width = v.videoWidth; canvas.height = v.videoHeight; ctx.drawImage(v, 0, 0);
        const img = ctx.getImageData(0, 0, canvas.width, canvas.height);
        const code = /** @type {any} */ (window).jsQR?.(img.data, img.width, img.height);
        const ahoraMs = Date.now();
        if (code?.data && (code.data !== ultimo || ahoraMs - t0 > 4000) && ahoraMs - t0 > 1200) { ultimo = code.data; t0 = ahoraMs; procesar(code.data, "qr"); }
      }
      raf = requestAnimationFrame(loop);
    };
    loop();
  } catch { /** @type {HTMLElement} */ (document.getElementById("q-video-msg")).textContent = "Sin cámara: usa NFC o el lector USB."; }
}
function detenerCamara() {
  if (raf) cancelAnimationFrame(raf); raf = null;
  stream?.getTracks().forEach((/** @type {any} */ t) => t.stop()); stream = null;
}
function iniciarNfc() {
  if (globalThis.AndroidBridge?.nfcState) { /** @type {any} */ (window).onNativeNfc = (/** @type {any} */ c) => procesar(String(c), "nfc"); try { globalThis.AndroidBridge.startNfc(); } catch { /* sin NFC */ } }
}
function detenerNfc() { try { globalThis.AndroidBridge?.stopNfc?.(); } catch { /* ok */ } if (/** @type {any} */ (window).onNativeNfc) /** @type {any} */ (window).onNativeNfc = null; }

/** Lectores USB/Bluetooth tipo teclado: escriben el código muy rápido y terminan con Enter. */
function teclado(/** @type {any} */ e) {
  if (!activo || e.target.closest?.("#q-pin-modal")) return;
  const t = Date.now();
  if (t - bufferT > 120) buffer = "";
  bufferT = t;
  if (e.key === "Enter") { const c = buffer; buffer = ""; if (c.length >= 3) procesar(c, "manual"); e.preventDefault(); return; }
  if (e.key.length === 1) buffer += e.key;
}

/* ------------------------------ Pantalla completa y bloqueo ------------------------------ */
async function mantenerPantalla(/** @type {any} */ on) {
  try { globalThis.AndroidBridge?.mantenerPantalla?.(on); } catch { /* sin puente */ }
  try {
    if (on && navigator.wakeLock) wake = await navigator.wakeLock.request("screen");
    else { await wake?.release?.(); wake = null; }
  } catch { /* sin wake lock */ }
}
const alVolver = () => { if (activo && document.visibilityState === "visible") { mantenerPantalla(true); refreshHoy().then(pintarContadores).catch(() => {}); } };
const bloquearHash = () => { if (activo && location.hash !== "#/quiosco") location.hash = "#/quiosco"; };
const sinMenu = (/** @type {any} */ e) => { if (activo) e.preventDefault(); };

/* ------------------------------ Pantalla completa ↔ modo normal ------------------------------ */
// El quiosco sigue activo en ambos casos (con su PIN): solo cambia si el navegador muestra o no su barra.
const enPantallaCompleta = () => !!document.fullscreenElement;
/** Cambiar entre pantalla completa y modo normal también exige el PIN: así nadie puede salir de la página sin permiso. */
function alternarPantallaCompleta() {
  const completa = enPantallaCompleta();
  pedirPin({
    titulo: completa ? "Pasar a modo normal" : "Pasar a pantalla completa",
    boton: "Confirmar",
    alExito: cambiarPantallaCompleta,
  });
}
async function cambiarPantallaCompleta() {
  try {
    if (enPantallaCompleta()) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  } catch { toast("Este navegador no permite pantalla completa.", "error"); }
  pintarBotonPantalla();
}
function pintarBotonPantalla() {
  const b = /** @type {HTMLElement} */ (document.getElementById("q-pantalla"));
  if (!b) return;
  const completa = enPantallaCompleta();
  b.textContent = completa ? "⤡ Modo normal" : "⛶ Pantalla completa";
  b.setAttribute("aria-label", completa ? "Salir de pantalla completa (modo normal)" : "Poner en pantalla completa");
  b.title = b.getAttribute("aria-label") || "";
}

/* ------------------------------ Código QR rotativo de ingreso ------------------------------ */
function asegurarEstilosQr() {
  if (typeof document === "undefined" || document.getElementById("q-qr-style")) return;
  const st = document.createElement("style");
  st.id = "q-qr-style";
  st.textContent = `
    .q-qr-panel{background:#16223D;border-radius:24px;border:3px solid #22335A;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:20px;gap:16px;text-align:center;box-sizing:border-box}
    .q-qr-card{background:#ffffff;padding:12px;border-radius:18px;display:flex;align-items:center;justify-content:center;box-shadow:0 8px 24px rgba(0,0,0,.28);line-height:0}
    .q-qr-meta{display:flex;flex-direction:column;align-items:center;gap:10px;width:100%;max-width:320px}
    .q-qr-timer{font-size:16px;font-weight:600;color:#C9D3EA;font-variant-numeric:tabular-nums}
    .q-qr-barra{width:100%;height:8px;border-radius:99px;background:#22335A;overflow:hidden}
    #q-qr-barra-fill{display:block;height:100%;background:#E8A33D;border-radius:99px;transition:width .9s linear}
    @media (prefers-reduced-motion:reduce){#q-qr-barra-fill{transition:none!important}}
    .q-qr-aviso{margin:0;font-size:14px;color:#9AA7C6;line-height:1.45;max-width:32ch}
  `;
  document.head.appendChild(st);
}

function pintarBotonVista() {
  const b = /** @type {HTMLElement} */ (document.getElementById("q-vista-btn"));
  if (!b) return;
  const esQr = ajustes?.vista === "qr";
  b.textContent = esQr ? "Cámara frontal" : "Mostrar QR";
  b.setAttribute("aria-label", esQr ? "Cambiar a cámara frontal" : "Mostrar código QR para los estudiantes");
  b.title = b.getAttribute("aria-label") || "";
}

function actualizarVisibilidadVistas() {
  const secCam = document.getElementById("q-camara-box");
  const secQr = document.getElementById("q-qr-panel");
  const esQr = ajustes?.vista === "qr";
  if (secCam) secCam.hidden = esQr;
  if (secQr) secQr.hidden = !esQr;
  pintarBotonVista();
  reposo();
}

function pintarQrCuentaAtras(/** @type {number} */ restante, /** @type {number} */ total) {
  const txt = document.getElementById("q-qr-seg-txt");
  const fill = document.getElementById("q-qr-barra-fill");
  if (txt) txt.textContent = `El código cambia en ${restante} s`;
  if (fill) {
    const tot = total || 30;
    const p = Math.max(0, Math.min(100, Math.round((restante / tot) * 100)));
    fill.style.width = `${p}%`;
  }
}

function limpiarQr() {
  if (qrActivo) {
    if (qrActivo.timer) clearInterval(qrActivo.timer);
    const sid = qrActivo.sesionId;
    qrActivo = null;
    if (sid) {
      /** @type {any} */ (api).qrIngresoCerrar?.(sid)?.catch?.(() => {});
    }
  }
}

async function activarVistaQr() {
  if (typeof window !== "undefined" && !(/** @type {any} */ (window)).QRCode) {
    toast("No se cargó el generador de QR. Revisa tu conexión.", "error");
    ajustes.vista = "camara";
    guardar(ajustes);
    actualizarVisibilidadVistas();
    iniciarCamara();
    return false;
  }
  detenerCamara();
  let sesion;
  try {
    sesion = await /** @type {any} */ (api).qrIngresoAbrir(ajustes?.qrSeg || 30, 600);
  } catch (/** @type {any} */ e) {
    const msg = /Disponible solo/i.test(e.message)
      ? "El código QR rotativo está disponible solo con conexión al servidor."
      : /Failed to fetch|network|conexi[oó]n/i.test(e.message)
      ? "Sin conexión al servidor para abrir la sesión de QR."
      : "No se pudo abrir el QR: " + (e.message || "error");
    toast(msg, "error");
    ajustes.vista = "camara";
    guardar(ajustes);
    actualizarVisibilidadVistas();
    iniciarCamara();
    return false;
  }

  ajustes.vista = "qr";
  guardar(ajustes);
  actualizarVisibilidadVistas();

  const caja = document.getElementById("q-qr-canvas");
  if (caja) {
    caja.innerHTML = "";
    const qr = new (/** @type {any} */ (window)).QRCode(caja, {
      width: 280,
      height: 280,
      correctLevel: (/** @type {any} */ (window)).QRCode.CorrectLevel.M,
    });
    const sesionId = String(sesion.sesion || sesion.id || "");
    const intervaloSeg = Number(sesion.intervalo) || ajustes?.qrSeg || 30;
    qrActivo = { sesionId, timer: null, qr, ultimoToken: 0, restante: 0, intervaloSeg };

    const tick = async () => {
      if (!qrActivo || ocupadoQrTick || !activo) return;
      ocupadoQrTick = true;
      try {
        const ahora = Date.now();
        if (qrActivo.restante <= 1 || !qrActivo.ultimoToken) {
          const t = await /** @type {any} */ (api).qrIngresoToken(qrActivo.sesionId);
          if (t.cerrada) {
            toast("La sesión de QR de ingreso terminó.", "info");
            activarVistaCamara();
            return;
          }
          qr.makeCode(String(t.token));
          qrActivo.ultimoToken = ahora;
          qrActivo.restante = Number(t.segundos) || qrActivo.intervaloSeg;
        } else {
          qrActivo.restante--;
        }
        pintarQrCuentaAtras(qrActivo.restante, qrActivo.intervaloSeg);
      } catch (/** @type {any} */ e) {
        const txt = document.getElementById("q-qr-seg-txt");
        if (txt) txt.textContent = "Reintentando conexión… (" + (e.message || "error") + ")";
        qrActivo.restante = 0;
      } finally {
        ocupadoQrTick = false;
      }
    };
    qrActivo.timer = setInterval(tick, 1000);
    tick();
  }
  return true;
}

function activarVistaCamara() {
  limpiarQr();
  ajustes.vista = "camara";
  guardar(ajustes);
  actualizarVisibilidadVistas();
  iniciarCamara();
}

async function alternarVista() {
  if (ajustes?.vista === "qr") {
    activarVistaCamara();
  } else {
    await activarVistaQr();
  }
}

/* ------------------------------ Construcción ------------------------------ */
function pintarReloj() {
  const r = /** @type {HTMLElement} */ (document.getElementById("q-reloj")), f = /** @type {HTMLElement} */ (document.getElementById("q-fecha"));
  if (r) r.textContent = horaZona(ahora());
  if (f) f.textContent = new Date(ahora()).toLocaleDateString("es-PE", { weekday: "long", day: "numeric", month: "long", timeZone: "America/Lima" });
}

export async function abrirQuiosco(/** @type {any} */ config) {
  ajustes = normalizarConfigQuiosco(config); guardar(ajustes);
  await asegurarHoy();
  if (/** @type {HTMLElement} */ (document.getElementById("quiosco"))) return;
  asegurarEstilosQr();
  const hoy = todayStr(), noLect = DB.noLectivos.get(hoy);
  const ov = document.createElement("div");
  ov.id = "quiosco"; ov.className = "quiosco"; ov.setAttribute("role", "application"); ov.setAttribute("aria-label", "Modo quiosco");
  ov.innerHTML = `<header class="q-top"><div class="q-marca"><div><strong>${esc(DB.perfil?.colegio || "LUMMER")}</strong><small id="q-modo">${ajustes.modo === "salida" ? "SALIDA" : ajustes.modo === "entrada" ? "INGRESO" : "INGRESO Y SALIDA"}</small></div></div>
      <div class="q-hora"><div id="q-reloj" class="q-reloj"></div><div id="q-fecha" class="q-fecha"></div></div>
      <div class="q-botones"><button class="q-btn" id="q-vista-btn" type="button"></button><button class="q-btn" id="q-pantalla" type="button"></button><button class="q-btn q-salir" id="q-salir" type="button" aria-label="Salir del modo quiosco" title="Salir del modo quiosco">🔒</button></div></header>
    ${noLect ? `<div class="q-aviso">Hoy es ${esc(noLect.tipo.toLowerCase())}: ${esc(noLect.nombre)}. Los registros se guardan igual.</div>` : ""}
    <main class="q-main">
      <section class="q-camara" id="q-camara-box"><video id="q-video" playsinline muted></video><div class="q-marco" aria-hidden="true"></div><p id="q-video-msg" class="q-video-msg"></p></section>
      <section class="q-qr-panel" id="q-qr-panel" hidden aria-label="Código QR rotativo de ingreso">
        <div class="q-qr-card"><div id="q-qr-canvas" class="qrc-qr" aria-label="Código QR de ingreso"></div></div>
        <div class="q-qr-meta">
          <div class="q-qr-timer" role="status" aria-live="polite"><span id="q-qr-seg-txt">Generando código…</span></div>
          <div class="q-qr-barra" aria-hidden="true"><span id="q-qr-barra-fill" style="width:100%"></span></div>
          <p class="q-qr-aviso">Los estudiantes escanean este código con la app LUMMER → Escanear</p>
        </div>
      </section>
      <section id="q-resultado" class="q-resultado q-espera"></section>
    </main>
    <footer class="q-pie" id="q-contadores"></footer>`;
  document.body.appendChild(ov);
  activo = true;
  document.body.classList.add("quiosco-activo");
  reposo(); pintarReloj(); pintarContadores();
  timerReloj = setInterval(pintarReloj, 1000);
  timerRefresco = setInterval(() => { refreshHoy().then(pintarContadores).catch(() => {}); }, 60000);
  quitarEstado = onEstado(pintarContadores);
  location.hash = "#/quiosco";
  addEventListener("hashchange", bloquearHash);
  addEventListener("keydown", teclado, true);
  addEventListener("contextmenu", sinMenu);
  document.addEventListener("visibilitychange", alVolver);
  /** @type {HTMLElement} */ (ov.querySelector("#q-salir")).addEventListener("click", pedirSalida);
  /** @type {HTMLElement} */ (ov.querySelector("#q-vista-btn")).addEventListener("click", alternarVista);
  const bp = /** @type {HTMLElement} */ (ov.querySelector("#q-pantalla"));
  if (!document.documentElement.requestFullscreen) bp.hidden = true;   // p. ej. iPhone/Safari: no existe pantalla completa para páginas
  bp.addEventListener("click", alternarPantallaCompleta);
  document.addEventListener("fullscreenchange", pintarBotonPantalla);
  pintarBotonPantalla();
  try { await document.documentElement.requestFullscreen?.(); } catch { /* sin pantalla completa */ }
  pintarBotonPantalla();
  mantenerPantalla(true);
  iniciarNfc();
  if (ajustes.vista === "qr") {
    activarVistaQr();
  } else {
    pintarBotonVista();
    iniciarCamara();
  }
}

function cerrarQuiosco() {
  activo = false;
  limpiarQr();
  detenerCamara(); detenerNfc(); mantenerPantalla(false);
  clearInterval(timerReloj); clearInterval(timerRefresco); clearTimeout(timerFin); quitarEstado?.();

  removeEventListener("hashchange", bloquearHash); removeEventListener("keydown", teclado, true); removeEventListener("contextmenu", sinMenu);
  document.removeEventListener("visibilitychange", alVolver);
  document.removeEventListener("fullscreenchange", pintarBotonPantalla);
  document.getElementById("quiosco")?.remove();
  document.body.classList.remove("quiosco-activo");
  try { if (document.fullscreenElement) document.exitFullscreen(); } catch { /* ok */ }
  try { localStorage.removeItem(KEY); } catch { /* ok */ }
  ajustes = null;
}

/* ------------------------------ Salida con PIN ------------------------------ */
function pedirSalida() {
  pedirPin({ titulo: "Salir del modo quiosco", boton: "Salir", alExito: () => { cerrarQuiosco(); location.hash = "#/dashboard"; } });
}

/** Diálogo de PIN del quiosco. Solo el PIN: no hay vía alternativa (un prompt del navegador saca de pantalla completa).
 * @param {{ titulo: string, boton: string, alExito: () => void }} o */
function pedirPin(o) {
  if (/** @type {HTMLElement} */ (document.getElementById("q-pin-modal"))) return;
  const m = document.createElement("div");
  m.id = "q-pin-modal"; m.className = "q-pin-modal";
  m.setAttribute("role", "dialog"); m.setAttribute("aria-modal", "true");
  m.innerHTML = `<div class="q-pin-card"><h3>${o.titulo}</h3>
    <input id="q-pin" type="password" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="PIN" aria-label="PIN de seguridad">
    <p class="q-pin-err" id="q-pin-err" hidden></p>
    <div class="q-pin-btns"><button class="btn btn-outline" id="q-pin-no">Cancelar</button><button class="btn btn-primary" id="q-pin-ok">${o.boton}</button></div></div>`;
  /** @type {HTMLElement} */ (document.getElementById("quiosco")).appendChild(m);
  const inp = /** @type {HTMLInputElement} */ (m.querySelector("#q-pin")), err = /** @type {HTMLElement} */ (m.querySelector("#q-pin-err"));
  inp.focus();
  const fallo = (/** @type {any} */ t) => { err.textContent = t; err.hidden = false; inp.value = ""; inp.focus(); };
  /** @type {HTMLElement} */ (m.querySelector("#q-pin-no")).addEventListener("click", () => m.remove());
  const probar = async () => {
    if ((await hashPin(inp.value)) === ajustes.pinHash) { m.remove(); o.alExito(); }
    else fallo("PIN incorrecto");
  };
  /** @type {HTMLElement} */ (m.querySelector("#q-pin-ok")).addEventListener("click", probar);
  inp.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); probar(); } else if (e.key === "Escape") { e.preventDefault(); m.remove(); } });
}

/** Al abrir la app: si el quiosco estaba activo (p. ej. la tablet se reinició), vuelve a abrirse solo. */
export function reanudarQuiosco() {
  const a = leer();
  if (a?.pinHash && !/** @type {HTMLElement} */ (document.getElementById("quiosco"))) abrirQuiosco(a);
}

/* ------------------------------ Página de inicio del quiosco ------------------------------ */
export const quioscoPage = {
  noDocente: true, id: "quiosco", title: "Modo quiosco", icon: "idCard", group: "Registro",
  render(/** @type {any} */ root) {
    const prev = normalizarConfigQuiosco(leer());
    root.innerHTML = `<div class="page-head"><div><h1>Modo quiosco</h1><p class="muted">Deja una tablet o teléfono fijo en la puerta: cada estudiante muestra su carnet y se registra solo, sin que el docente tenga que escanear.</p></div></div>
      <div class="grid-2"><section class="card">
        <h3 style="margin-top:0">Configurar</h3>
        <div class="field"><label for="qk-modo">Qué registra</label><select id="qk-modo"><option value="auto">Ingreso y salida (automático)</option><option value="entrada">Solo ingreso</option><option value="salida">Solo salida</option></select></div>
        <div class="field"><label for="qk-vista">Modo de escaneo</label><select id="qk-vista"><option value="camara">Cámara (el quiosco escanea carnets)</option><option value="qr">Mostrar QR (el estudiante escanea con su celular)</option></select></div>
        <div class="field" id="qk-qrseg-box"><label for="qk-qrseg">Rotación del código QR</label><select id="qk-qrseg"><option value="30">Cada 30 segundos (recomendado)</option><option value="15">Cada 15 segundos</option></select></div>
        <div class="field"><label for="qk-cam">Cámara</label><select id="qk-cam"><option value="user">Frontal (tablet de pie)</option><option value="environment">Trasera</option></select></div>
        <div class="field"><label for="qk-pin">PIN para salir (4 a 6 números) <span class="req">*</span></label><input id="qk-pin" type="password" inputmode="numeric" maxlength="6" autocomplete="off" placeholder="Ej: 4821"></div>
        <label class="check-row"><input type="checkbox" id="qk-son" checked> Sonido de confirmación</label>
        <label class="check-row"><input type="checkbox" id="qk-voz"> Saludar por voz («Bienvenido, Juan»)</label>
        <p class="err-msg" id="qk-err" role="alert" hidden></p>
        <div class="btn-row"><button class="btn btn-primary" data-action="quiosco-iniciar">Iniciar modo quiosco</button></div>
      </section>
      <section class="card"><h3 style="margin-top:0">Cómo funciona</h3>
        <ul class="muted" style="margin:6px 0 0;padding-left:20px;line-height:1.7">
          <li>Pantalla completa y sin menús; la pantalla no se apaga. <b>Salir, o cambiar entre pantalla completa y modo normal, solo se hace con el PIN.</b></li>
          <li>Acepta el <b>QR del carnet</b> (también el dinámico), <b>tags NFC</b> (en la app Android) y <b>lectores USB/Bluetooth</b>.</li>
          <li>Permite cambiar en vivo entre <b>cámara</b> y <b>código QR proyectado</b> que rota cada 15 o 30 segundos.</li>
          <li>En «automático»: el primer pase es el <b>ingreso</b>; la <b>salida</b> solo se habilita pasado el tiempo mínimo del horario (por defecto <b>2 horas</b> después del ingreso: evita fugas); antes de eso se avisa desde qué hora podrá salir. El ingreso respeta la <b>ventana</b> del horario (no antes de la apertura ni después del cierre).</li>
          <li>Muestra <b>foto y nombre</b> (apellidos protegidos), puntual o tardanza, con sonido.</li>
          <li><b>Sin internet</b> sigue funcionando y envía todo al volver la conexión.</li>
          <li>Si el aparato se reinicia, el quiosco <b>vuelve a abrirse solo</b>.</li></ul></section></div>`;
    /** @type {HTMLSelectElement} */ (root.querySelector("#qk-modo")).value = prev.modo;
    /** @type {HTMLSelectElement} */ (root.querySelector("#qk-cam")).value = prev.camara;
    /** @type {HTMLSelectElement} */ (root.querySelector("#qk-vista")).value = prev.vista;
    /** @type {HTMLSelectElement} */ (root.querySelector("#qk-qrseg")).value = String(prev.qrSeg);
  },
};

registerActions({
  "quiosco-iniciar": async () => {
    const $ = (/** @type {any} */ s) => document.querySelector(s), err = $("#qk-err");
    const pin = $("#qk-pin").value.trim();
    if (!/^\d{4,6}$/.test(pin)) { err.textContent = "El PIN debe tener de 4 a 6 números."; err.hidden = false; return; }
    err.hidden = true;
    await abrirQuiosco({
      pinHash: await hashPin(pin),
      modo: $("#qk-modo").value,
      camara: $("#qk-cam").value,
      vista: $("#qk-vista").value,
      qrSeg: Number($("#qk-qrseg").value) === 15 ? 15 : 30,
      sonido: $("#qk-son").checked,
      voz: $("#qk-voz").checked,
    });
  },
});
void nowHHMM;
