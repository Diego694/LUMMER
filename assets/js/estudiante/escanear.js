// @ts-check
// Escanear el QR de asistencia del docente (portal y app del estudiante).
// El estudiante abre la cámara, apunta al QR que el docente muestra en su panel y el servidor marca su asistencia en ese curso.
import { parsearTokenQrCurso } from "../api-qrcurso.js";
import { icon, registerActions, toast } from "../ui.js";
import { esc } from "../utils.js";

/** @typedef {import('../tipos.d.ts').BackendEstudiante} BackendEstudiante */

/**
 * @param {{ api: BackendEstudiante, user: { id: string }, root: () => HTMLElement, volver: () => void }} ctx
 */
export function iniciarEscaneoAsistencia(ctx) {
  /** @type {MediaStream | null} */
  let stream = null;
  /** @type {number | null} */
  let raf = null;
  let ocupado = false;

  function apagar() {
    if (raf) cancelAnimationFrame(raf);
    raf = null;
    stream?.getTracks().forEach((t) => t.stop());
    stream = null;
  }

  /** @param {string} clase @param {string} titulo @param {string} detalle */
  function resultado(clase, titulo, detalle) {
    const caja = ctx.root().querySelector("#esc-res");
    if (!caja) return;
    caja.className = `esc-res esc-${clase}`;
    caja.innerHTML = `<strong>${esc(titulo)}</strong>${detalle ? `<span>${esc(detalle)}</span>` : ""}`;
    /** @type {HTMLElement} */ (caja).hidden = false;
  }

  /** @param {string} texto */
  async function procesar(texto) {
    if (ocupado) return;
    if (!parsearTokenQrCurso(texto)) { resultado("warn", "Ese QR no es de asistencia", "Escanea el código que el docente muestra en su pantalla."); return; }
    ocupado = true;
    try {
      const r = await ctx.api.marcarQrCurso(ctx.user, texto);
      if (r.estado === "ok") {
        apagar();
        pantalla(`<div class="esc-ok-grande" role="status">${icon("check", 40)}<h2>¡Asistencia registrada!</h2><p><strong>${esc(r.curso || "")}</strong></p><p class="muted">Hora: ${esc(String(r.hora || "").slice(0, 8))}</p></div>`);
        return;
      }
      if (r.estado === "ya_marcado") { apagar(); pantalla(`<div class="esc-ok-grande" role="status">${icon("check", 40)}<h2>Ya estabas presente</h2><p><strong>${esc(r.curso || "")}</strong></p><p class="muted">Marcaste a las ${esc(String(r.hora || "").slice(0, 8))}</p></div>`); return; }
      resultado(r.estado === "vencido" ? "warn" : "err", r.mensaje || "No se pudo registrar", "");
    } catch (/** @type {any} */ e) {
      resultado("err", "No se pudo registrar tu asistencia", e.message || "Revisa tu conexión e inténtalo de nuevo.");
    } finally { setTimeout(() => { ocupado = false; }, 1500); }
  }

  /** @param {string} cuerpo */
  function pantalla(cuerpo) {
    ctx.root().innerHTML = `<section class="est-card esc-card"><button type="button" class="btn btn-outline btn-sm" data-action="esc-volver">${icon("history", 15)} Mi carnet</button>${cuerpo}
      <div class="est-actions"><button type="button" class="btn btn-primary" data-action="esc-otra">${icon("camera", 16)} Escanear otro</button><button type="button" class="btn btn-outline" data-action="esc-volver">Volver al carnet</button></div></section>`;
  }

  async function abrir() {
    apagar();
    ctx.root().innerHTML = `<section class="est-card esc-card"><button type="button" class="btn btn-outline btn-sm" data-action="esc-volver">${icon("history", 15)} Mi carnet</button>
      <h1 style="margin-top:12px">Marcar asistencia</h1>
      <p class="muted">Apunta la cámara al QR que muestra tu docente en su pantalla. Cambia cada 30 segundos: escanéalo en el momento.</p>
      <div class="esc-marco"><video id="esc-video" playsinline muted></video><div class="esc-guia" aria-hidden="true"></div></div>
      <div id="esc-res" class="esc-res" role="status" aria-live="polite" hidden></div></section>`;
    const video = /** @type {HTMLVideoElement} */ (ctx.root().querySelector("#esc-video"));
    if (!navigator.mediaDevices?.getUserMedia) { resultado("err", "Tu navegador no permite usar la cámara aquí", "Abre el portal con HTTPS o desde la app."); return; }
    if (!(/** @type {any} */ (window).jsQR)) { resultado("err", "No se cargó el lector de QR", "Revisa tu conexión y vuelve a abrir esta pantalla."); return; }
    try { stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" } }); }
    catch { resultado("err", "No se pudo abrir la cámara", "Permite el acceso a la cámara en tu navegador o en los permisos de la app."); return; }
    video.srcObject = stream;
    await video.play().catch(() => { /* el usuario puede tocar la pantalla */ });
    const canvas = document.createElement("canvas");
    const ctx2d = /** @type {CanvasRenderingContext2D} */ (canvas.getContext("2d", { willReadFrequently: true }));
    let ultimo = "", t0 = 0;
    const bucle = () => {
      if (!stream) return;
      if (video.readyState === video.HAVE_ENOUGH_DATA && !ocupado) {
        canvas.width = video.videoWidth; canvas.height = video.videoHeight;
        ctx2d.drawImage(video, 0, 0);
        const img = ctx2d.getImageData(0, 0, canvas.width, canvas.height);
        const code = /** @type {any} */ (window).jsQR(img.data, img.width, img.height);
        const ms = Date.now();
        if (code?.data && (code.data !== ultimo || ms - t0 > 4000) && ms - t0 > 1200) { ultimo = code.data; t0 = ms; procesar(code.data); }
      }
      raf = requestAnimationFrame(bucle);
    };
    bucle();
  }

  registerActions({
    "est-escanear": () => { abrir(); },
    "esc-otra": () => { abrir(); },
    "esc-volver": () => { apagar(); ctx.volver(); },
  });
  // La cámara se apaga al salir de la app y se reabre al volver (solo si sigue en esta pantalla)
  let reabrir = false;
  document.addEventListener("visibilitychange", () => {
    if (document.hidden && stream) { reabrir = true; apagar(); }
    else if (!document.hidden && reabrir) { reabrir = false; if (ctx.root().querySelector("#esc-video")) abrir().catch(() => toast("No se pudo reabrir la cámara.", "error")); }
  });
}
