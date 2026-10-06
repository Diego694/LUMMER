// @ts-check
// Portal del estudiante: crear cuenta → registrarse con el código del instituto → subir foto → carnet con QR único.
import { CONFIG, isDemoMode } from "../config.js";
import { api } from "./api.js";
import { bindActions, confirmDialog, icon, openModal, registerActions, toast } from "../ui.js";
import { ahora, cicloCorto, compararCiclos, downloadFile, esc, etiquetaCiclo, initials, sincronizarReloj } from "../utils.js";
import { enviarPendientes, iniciarLogErrores } from "../errlog.js";
import { activarAvisos, desactivarAvisos, detenerVigilancia, pedirPermisoNotificacion, permisoNotificacion, vigilarAprobacion } from "../notificaciones.js";
import { VENTANA_MS, generarQR, qrModoEfectivo, segundosRestantes } from "../qr-seguro.js";

const $ = (/** @type {any} */ s, r = document) => r.querySelector(s);
const root = () => $("#est-root");
/** @type {any} */
let user = null;      // sesión
/** @type {any} */
let registro = null;  // { alumno, colegio: nombre del instituto }
let tema = "light";
/** @type {any} */
let qrTimer = null;   // temporizador del QR dinámico
/** @type {any} */
let qrInstancia = null; // instancia de QRCode
let ultimaVentana = -1;
const PRIVACIDAD = new URL("../privacidad.html", location.href).href;

/* ------------------------------ Tema ------------------------------ */
function setTheme(/** @type {any} */ t, persistir = true) {
  tema = t;
  document.documentElement.setAttribute("data-theme", t);
  if (persistir) try { localStorage.setItem("cv-theme", t); } catch { /* sin storage */ }
  document.querySelectorAll("[data-action=theme]").forEach((b) => { b.innerHTML = icon(t === "dark" ? "sun" : "moon"); b.setAttribute("aria-label", t === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"); });
}

/* ------------------------------ Fotos ------------------------------ */
/** Recorta al centro en cuadrado y reduce a 480×480 JPEG (~40 KB): ahorra datos y espacio. */
function aCuadrado(/** @type {any} */ fuente, /** @type {any} */ w, /** @type {any} */ h) {
  const lado = Math.min(w, h), c = document.createElement("canvas");
  c.width = c.height = 480;
  /** @type {CanvasRenderingContext2D} */ (c.getContext("2d")).drawImage(fuente, (w - lado) / 2, (h - lado) / 2, lado, lado, 0, 0, 480, 480);
  return new Promise((res) => c.toBlob((b) => res(/** @type {Blob} */ (b)), "image/jpeg", 0.82));
}
async function archivoACuadrado(/** @type {any} */ file) {
  if (!/^image\//.test(file.type)) throw new Error("El archivo debe ser una imagen.");
  if (/** @type {any} */ (window).createImageBitmap) {
    const bm = await createImageBitmap(file, { imageOrientation: "from-image" });
    const b = await aCuadrado(bm, bm.width, bm.height); bm.close?.(); return b;
  }
  const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = URL.createObjectURL(file); });
  return aCuadrado(img, img.naturalWidth, img.naturalHeight);
}

/** Modal para tomar una selfie o elegir de la galería. Devuelve el Blob JPEG (o null si cancela). */
function pedirFoto() {
  return new Promise((resolve) => {
    let stream = /** @type {any} */ (null), blob = /** @type {any} */ (null), urlPrevia = /** @type {any} */ (null), hecho = false;
    const m = openModal({
      title: "Tu foto",
      body: `<p class="muted" style="margin-top:0">Foto de frente, con buena luz y sin gorra ni lentes oscuros. Solo el personal del instituto la verá.</p>
        <div class="cam-box espejo" id="cam-box"><video id="cam-video" playsinline muted autoplay></video><div class="cam-guia"></div></div>
        <div class="cam-botones" id="cam-btns">
          <button class="btn btn-primary" id="cam-shot">${icon("camera", 16)} Tomar foto</button>
          <button class="btn btn-outline" id="cam-file">Elegir de la galería</button>
        </div>
        <input type="file" id="cam-input" accept="image/*" hidden>
        <p class="err-msg" id="cam-err" role="alert" hidden></p>`,
      footer: `<button class="btn btn-outline" id="cam-cancel">Cancelar</button><button class="btn btn-teal" id="cam-use" hidden>Usar esta foto</button>`,
    });
    const q = (/** @type {any} */ s) => m.el.querySelector(s);
    const err = (/** @type {any} */ t) => { q("#cam-err").textContent = t; q("#cam-err").hidden = !t; };
    const apagar = () => { stream?.getTracks().forEach((/** @type {any} */ t) => t.stop()); stream = null; };
    const fin = (/** @type {any} */ v) => { if (hecho) return; hecho = true; apagar(); if (urlPrevia) URL.revokeObjectURL(urlPrevia); m.close(); resolve(v); };
    const mostrarPrevia = (/** @type {any} */ b) => {
      blob = b; apagar();
      urlPrevia = URL.createObjectURL(b);
      const box = q("#cam-box"); box.classList.remove("espejo"); box.innerHTML = `<img src="${urlPrevia}" alt="Vista previa de tu foto">`;
      q("#cam-btns").innerHTML = `<button class="btn btn-outline" id="cam-retry">Repetir</button>`;
      // Repetir: se cierra este modal y se reabre uno nuevo que responderá a la misma promesa (sin resolver con null).
      q("#cam-retry").addEventListener("click", () => { hecho = true; apagar(); if (urlPrevia) URL.revokeObjectURL(urlPrevia); m.close(); pedirFoto().then(resolve); });
      q("#cam-use").hidden = false;
    };
    (async () => {
      if (!navigator.mediaDevices?.getUserMedia) { q("#cam-box").hidden = true; q("#cam-shot").hidden = true; err("Tu navegador no permite usar la cámara aquí: elige una foto de la galería."); return; }
      try {
        stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "user", width: { ideal: 960 }, height: { ideal: 960 } } });
        q("#cam-video").srcObject = stream;
      } catch { q("#cam-box").hidden = true; q("#cam-shot").hidden = true; err("No se pudo abrir la cámara (permiso denegado). Puedes elegir una foto de la galería."); }
    })();
    q("#cam-shot").addEventListener("click", async () => {
      const v = q("#cam-video");
      if (!v.videoWidth) { err("La cámara aún no está lista, espera un segundo."); return; }
      mostrarPrevia(await aCuadrado(v, v.videoWidth, v.videoHeight));
    });
    q("#cam-file").addEventListener("click", () => q("#cam-input").click());
    q("#cam-input").addEventListener("change", async (/** @type {any} */ e) => {
      const f = e.target.files[0]; if (!f) return;
      try { err(""); mostrarPrevia(await archivoACuadrado(f)); } catch (/** @type {any} */ ex) { err(ex.message); }
    });
    q("#cam-use").addEventListener("click", () => fin(blob));
    q("#cam-cancel").addEventListener("click", () => fin(null));
    q("[data-close]").addEventListener("click", () => fin(null));
    m.el.addEventListener("mousedown", (e) => { if (e.target === m.el) fin(null); });
    document.addEventListener("keydown", function esc_(e) { if (e.key === "Escape") { fin(null); document.removeEventListener("keydown", esc_); } });
  });
}

/* ------------------------------ Vistas ------------------------------ */
function vistaAuth(modo = "login") {
  const reg = modo === "signup";
  root().innerHTML = `
    <section class="est-hero"><h1 class="est-hero-t">${reg ? "Tu carnet institucional en 3 pasos" : "Tu carnet, siempre contigo"}</h1>
      <p>${reg ? "Crea tu cuenta, regístrate con el código de tu instituto y obtén tu QR personal." : "Muestra tu código QR al ingresar y revisa el estado de tu registro."}</p>
      <div class="pasos" aria-hidden="true"><span>1 · Cuenta</span><span>2 · Registro</span><span>3 · Carnet QR</span></div></section>
    <section class="est-card">
      <h2>${reg ? "Crear mi cuenta" : "Bienvenido"}</h2>
      <p class="est-sub">${reg ? "Paso 1 de 3 · Tu correo y una contraseña." : "Ingresa para ver tu carnet con código QR."}</p>
      <div class="tabs-est" role="tablist" aria-label="Acceso"><button type="button" class="${reg ? "" : "active"}" data-modo="login" role="tab" aria-selected="${!reg}">Ingresar</button><button type="button" class="${reg ? "active" : ""}" data-modo="signup" role="tab" aria-selected="${reg}">Crear cuenta</button></div>
      <form id="f-auth" class="est-form" novalidate>
        <div class="field"><label for="a-email">Correo</label><input id="a-email" type="email" autocomplete="username" placeholder="tucorreo@ejemplo.com" required></div>
        <div class="field"><label for="a-pass">Contraseña</label><input id="a-pass" type="password" autocomplete="${reg ? "new-password" : "current-password"}" placeholder="${reg ? "Mínimo 8 caracteres" : "••••••••"}" required></div>
        ${reg ? `<div class="field"><label for="a-pass2">Repite la contraseña</label><input id="a-pass2" type="password" autocomplete="new-password" required></div>` : ""}
        ${reg && CONFIG.TURNSTILE_SITEKEY ? '<div id="captcha" class="captcha"></div>' : ""}
        <button class="btn btn-primary btn-block" id="a-go" type="submit">${reg ? "Crear cuenta" : "Ingresar"}</button>
        <p class="err-msg" id="a-err" role="alert" hidden></p>
        ${reg ? "" : '<p class="est-link"><button type="button" class="link-btn" data-olvide>¿Olvidaste tu contraseña?</button></p>'}
        <p class="muted est-legal">Al continuar aceptas la <a href="${PRIVACIDAD}" target="_blank" rel="noopener">política de privacidad</a>.</p>
        ${isDemoMode() ? `<p class="demo-hint"><strong>Modo demo</strong>: crea una cuenta con cualquier correo. El código del instituto de prueba es <code>DEMO2026</code>.</p>` : ""}
      </form>
    </section>`;
  root().querySelectorAll("[data-modo]").forEach((/** @type {any} */ b) => b.addEventListener("click", () => vistaAuth(b.dataset.modo)));
  root().querySelector("[data-olvide]")?.addEventListener("click", () => vistaRecuperar());
  let captchaToken = "";
  if (reg && CONFIG.TURNSTILE_SITEKEY) cargarTurnstile((/** @type {any} */ tk) => { captchaToken = tk; });
  $("#f-auth").addEventListener("submit", async (/** @type {any} */ e) => {
    e.preventDefault();
    const err = $("#a-err"), btn = $("#a-go"), email = $("#a-email").value.trim(), pass = $("#a-pass").value;
    err.hidden = true;
    if (!/^\S+@\S+\.\S+$/.test(email)) { err.textContent = "Escribe un correo válido."; err.hidden = false; return; }
    if (reg && pass.length < 8) { err.textContent = "La contraseña debe tener al menos 8 caracteres."; err.hidden = false; return; }
    if (reg && pass !== $("#a-pass2").value) { err.textContent = "Las contraseñas no coinciden."; err.hidden = false; return; }
    if (reg && CONFIG.TURNSTILE_SITEKEY && !captchaToken) { err.textContent = "Completa la verificación de seguridad."; err.hidden = false; return; }
    btn.disabled = true;
    try {
      user = reg ? await api.signUp(email, pass, captchaToken) : await api.signIn(email, pass);
      await entrar();
    } catch (/** @type {any} */ ex) {
      err.textContent = ex.code === "auth" && !reg ? "Correo o contraseña incorrectos." : ex.message || "No se pudo continuar.";
      err.hidden = false; btn.disabled = false;
    }
  });
}

function vistaRegistro() {
  /** @type {any} */
  let info = null, codigo = "";
  const pintar = () => {
    root().innerHTML = `
      <section class="est-card">
        <div class="steps"><span class="on"></span><span class="${info ? "on" : ""}"></span><span></span></div>
        <h1>${info ? "Tus datos" : "Código del instituto"}</h1>
        <p class="est-sub">${info ? "Paso 2 de 3 · Completa tu información." : "Paso 2 de 3 · Pídele a tu instituto el código de registro."}</p>
        ${info ? `<div class="school-ok">${icon("check", 18)} ${esc(info.nombre)}</div>
        <form id="f-reg" class="est-form" novalidate>
          <div class="row2"><div class="field"><label for="r-nom">Nombres <span class="req">*</span></label><input id="r-nom" autocomplete="given-name" required></div>
          <div class="field"><label for="r-ape">Apellidos <span class="req">*</span></label><input id="r-ape" autocomplete="family-name" required></div></div>
          <div class="row2"><div class="field"><label for="r-niv">Carrera <span class="req">*</span></label><select id="r-niv">${info.niveles.map((/** @type {any} */ n) => `<option>${esc(n)}</option>`).join("")}</select></div>
          <div class="field"><label for="r-gra">Ciclo / salón <span class="req">*</span></label><select id="r-gra"></select></div></div>
          <div class="field"><label for="r-dni">DNI (opcional)</label><input id="r-dni" inputmode="numeric" maxlength="12" autocomplete="off"></div>
          <div class="field"><label for="r-apo">Apoderado (opcional)</label><input id="r-apo" autocomplete="off"></div>
          <div class="row2"><div class="field"><label for="r-atel">Celular del apoderado</label><input id="r-atel" type="tel" inputmode="tel" placeholder="999 888 777" autocomplete="off"></div>
          <div class="field"><label for="r-amail">Correo del apoderado</label><input id="r-amail" type="email" autocomplete="off"></div></div>
          <p class="muted" style="margin:-6px 0 12px;font-size:12px">Sirven para avisarle si faltas o llegas tarde. Son opcionales.</p>
          <label class="consent"><input type="checkbox" id="r-ok"><span>Autorizo el tratamiento de mis datos personales y de mi foto para el control de asistencia institucional. Si soy menor de edad, mi padre, madre o apoderado lo autoriza. <a href="${PRIVACIDAD}" target="_blank" rel="noopener">Ver política de privacidad</a>.</span></label>
          <button class="btn btn-primary btn-block" id="r-go" type="submit">Crear mi carnet</button>
          <p class="err-msg" id="r-err" role="alert" hidden></p>
        </form>`
        : `<form id="f-cod" class="est-form" novalidate>
          <div class="field"><label for="c-cod">Código de registro</label><input id="c-cod" autocomplete="off" autocapitalize="characters" placeholder="Ej: K7M3QX9P" value="${esc(codigo)}" required></div>
          <button class="btn btn-primary btn-block" id="c-go" type="submit">Verificar código</button>
          <p class="err-msg" id="c-err" role="alert" hidden></p>
        </form>`}
      </section>`;
    if (!info) {
      $("#f-cod").addEventListener("submit", async (/** @type {any} */ e) => {
        e.preventDefault();
        codigo = $("#c-cod").value.trim().toUpperCase();
        const err = $("#c-err"), btn = $("#c-go"); err.hidden = true;
        if (!codigo) { err.textContent = "Escribe el código."; err.hidden = false; return; }
        btn.disabled = true;
        try {
          info = await api.infoColegio(codigo);
          if (!info) { err.textContent = "Código inválido. Revísalo con tu instituto."; err.hidden = false; btn.disabled = false; return; }
          pintar();
        } catch (/** @type {any} */ ex) { err.textContent = ex.message; err.hidden = false; btn.disabled = false; }
      });
      return;
    }
    const sel = $("#r-niv"), gra = $("#r-gra");
    const llenarGrados = () => { gra.innerHTML = info.grados.filter((/** @type {any} */ g) => g.nivel === sel.value).sort((/** @type {any} */ a, /** @type {any} */ b) => compararCiclos(a.nombre, b.nombre)).map((/** @type {any} */ g) => `<option value="${esc(g.nombre)}">${esc(cicloCorto(g.nombre, sel.value))}</option>`).join(""); };
    llenarGrados(); sel.addEventListener("change", llenarGrados);
    $("#f-reg").addEventListener("submit", async (/** @type {any} */ e) => {
      e.preventDefault();
      const err = $("#r-err"), btn = $("#r-go"); err.hidden = true;
      const f = { codigoColegio: codigo, nombres: $("#r-nom").value.trim(), apellidos: $("#r-ape").value.trim(), nivel: sel.value, grado: gra.value, dni: $("#r-dni").value.trim(), apoderado: $("#r-apo").value.trim(), apoderadoTel: $("#r-atel").value.trim(), apoderadoEmail: $("#r-amail").value.trim() };
      if (f.nombres.length < 2 || f.apellidos.length < 2) { err.textContent = "Escribe tus nombres y apellidos."; err.hidden = false; return; }
      if (!f.grado) { err.textContent = "Elige tu ciclo."; err.hidden = false; return; }
      if (f.dni && !/^\d{6,12}$/.test(f.dni)) { err.textContent = "El DNI solo debe tener números."; err.hidden = false; return; }
      if (f.apoderadoTel && !/^\+?[\d\s-]{6,16}$/.test(f.apoderadoTel)) { err.textContent = "El celular del apoderado no es válido."; err.hidden = false; return; }
      if (f.apoderadoEmail && !/^\S+@\S+\.\S+$/.test(f.apoderadoEmail)) { err.textContent = "El correo del apoderado no es válido."; err.hidden = false; return; }
      if (!$("#r-ok").checked) { err.textContent = "Debes aceptar la autorización para continuar."; err.hidden = false; return; }
      btn.disabled = true;
      try { await api.registrar(user, f); registro = await api.miRegistro(user); await vistaCarnet(true); }
      catch (/** @type {any} */ ex) { err.textContent = ex.message; err.hidden = false; btn.disabled = false; }
    });
  };
  pintar();
}

async function vistaCarnet(recienCreado = false) {
  const { alumno: a, colegio } = registro;
  const aprobado = a.aprobado !== false;
  const modo = qrModoEfectivo(registro?.qr_modo);
  const dinamico = modo !== "off" && !!a.qr_secreto;
  detenerQR();
  $("#est-instituto").textContent = colegio;
  const textoPie = modo === "obligatorio"
    ? "Muestra este QR al docente al ingresar a clases. Cambia solo cada 30 s; una captura no sirve."
    : modo === "opcional"
    ? "Muestra este QR al docente al ingresar a clases. Cambia solo cada 30 s; una captura no sirve. También se aceptan carnets impresos con QR fijo."
    : "Muestra este QR al docente al ingresar a clases. No lo compartas con otras personas.";
  root().innerHTML = `
    <section class="carnet-est" aria-label="Lummer Estudiante">
      <div class="ce-head">${esc(colegio)} · Carnet institucional</div>
      <div class="ce-id">
        <div class="ce-foto" id="ce-foto"><span>${esc(initials(a.nombre))}</span></div>
        <div><div class="ce-name">${esc(a.nombre)}</div><div class="ce-meta">${esc(etiquetaCiclo(a.nivel, a.grado))}</div>
          <span class="estado-chip ${aprobado ? "estado-ok" : "estado-pend"}">${icon(aprobado ? "check" : "clock", 13)} ${aprobado ? "Registro aprobado" : "Pendiente de aprobación"}</span></div>
      </div>
      <div class="ce-qr" id="ce-qr" aria-label="${dinamico ? "Código QR dinámico de asistencia" : "Código QR de asistencia"}"></div>
      ${dinamico ? '<div class="qr-timer" aria-hidden="true"><i id="qr-bar"></i></div><div class="qr-info" id="qr-info">QR seguro: se actualiza solo</div>' : ""}
      ${modo === "obligatorio" ? "" : `<div class="ce-code">${esc(a.codigo)}</div>`}
    </section>
    ${aprobado ? "" : `<div class="aviso">${icon("info", 15)} ${recienCreado ? "¡Listo! Tu carnet fue creado. " : ""}Tu QR empezará a registrar asistencia cuando el instituto apruebe tu registro. Mientras tanto, agrega tu foto. <b>Te avisaremos cuando lo aprueben.</b>${!globalThis.AndroidBridge && permisoNotificacion() === "default" ? ` <button type="button" class="link-btn" data-action="avisar-aprobacion">Activar aviso en este navegador</button>` : ""}</div>`}
    ${a.foto_path ? "" : `<div class="aviso">${icon("camera", 15)} Agrega tu foto: el docente la verá cuando pases tu QR.</div>`}
    <div class="est-actions">
      <button class="btn btn-primary" data-action="foto">${icon("camera", 16)} ${a.foto_path ? "Cambiar foto" : "Agregar foto"}</button>
      <button class="btn btn-outline" data-action="descargar">${icon("download", 16)} Descargar carnet</button>
    </div>
    ${a.codigo_apoderado ? `<section class="est-card ap-codigo"><strong>Código para tu apoderado</strong><div class="codigo-box"><code id="ap-code">${esc(a.codigo_apoderado.replace(/(.{4})(?=.)/g, "$1-"))}</code></div>
      <p class="muted" style="margin:6px 0 10px">Con este código tu apoderado puede ver tu asistencia, sin cuenta.</p>
      <div class="est-actions"><button class="btn btn-outline" data-action="ap-copiar">Copiar código</button><a class="btn btn-teal" target="_blank" rel="noopener" href="https://wa.me/?text=${encodeURIComponent("Código para ver mi asistencia: " + a.codigo_apoderado.replace(/(.{4})(?=.)/g, "$1-") + "\n" + new URL("../apoderado/?c=" + a.codigo_apoderado, location.href).href)}">Enviar por WhatsApp</a></div></section>` : ""}
    <p class="muted" style="text-align:center;margin-top:18px">${textoPie}</p>
    <p class="est-legal muted"><a href="${PRIVACIDAD}" target="_blank" rel="noopener">Política de privacidad</a> · <button type="button" class="link-btn link-peligro" data-action="eliminar-cuenta">Eliminar mi cuenta y mis datos</button></p>`;
  if (aprobado) detenerVigilancia();
  else vigilarAprobacion(api, a.notif_token, async () => {
    try { registro = await api.miRegistro(user); } catch { registro.alumno.aprobado = true; }
    toast("¡Tu registro fue aprobado! Ya puedes usar tu carnet.", "success");
    await vistaCarnet();
  });
  const qrBox = $("#ce-qr");
  if (dinamico) {
    qrBox.style.filter = "blur(4px)";
    qrBox.style.transition = "filter 0.2s ease";
    qrInstancia = new QRCode(qrBox, { width: 200, height: 200, correctLevel: QRCode.CorrectLevel.M });
    iniciarQRDinamico(qrInstancia, a);
  } else {
    qrBox.style.filter = "";
    qrInstancia = new QRCode(qrBox, { text: a.codigo, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.M });
  }
  cargarFoto(a);
}

/* ---------------------- QR dinámico ---------------------- */
function detenerQR() { clearInterval(qrTimer); qrTimer = null; }
function alOcultarApp() {
  detenerQR();
  const modo = qrModoEfectivo(registro?.qr_modo);
  const dinamico = modo !== "off" && !!registro?.alumno?.qr_secreto;
  if (!dinamico) return;
  const box = $("#ce-qr");
  if (box) {
    const cvs = /** @type {HTMLCanvasElement | null} */ (box.querySelector("canvas"));
    if (cvs) {
      const ctx = cvs.getContext("2d");
      ctx?.clearRect(0, 0, cvs.width, cvs.height);
    }
    const img = /** @type {HTMLElement} */ (box.querySelector("img"));
    if (img) img.removeAttribute("src");
    box.style.filter = "blur(4px)";
  }
}
function alReanudarApp() {
  if (!registro?.alumno || !$("#ce-qr")) return;
  const modo = qrModoEfectivo(registro.qr_modo);
  if (modo !== "off" && registro.alumno.qr_secreto) {
    ultimaVentana = -1;
    detenerQR();
    if (!qrInstancia) {
      const box = $("#ce-qr");
      box.innerHTML = "";
      box.style.filter = "blur(4px)";
      qrInstancia = new QRCode(box, { width: 200, height: 200, correctLevel: QRCode.CorrectLevel.M });
    }
    iniciarQRDinamico(qrInstancia, registro.alumno);
  } else {
    detenerQR();
    const box = $("#ce-qr");
    box.style.filter = "";
    if (qrInstancia) {
      qrInstancia.makeCode(registro.alumno.codigo);
    } else {
      box.innerHTML = "";
      qrInstancia = new QRCode(box, { text: registro.alumno.codigo, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.M });
    }
  }
}
function iniciarQRDinamico(/** @type {any} */ qr, /** @type {any} */ a) {
  detenerQR();
  const tick = () => {
    const ms = ahora().getTime(), w = Math.floor(ms / VENTANA_MS);
    if (w !== ultimaVentana) {
      ultimaVentana = w;
      generarQR(a.codigo, a.qr_secreto, ms).then((txt) => {
        if (qrTimer || !document.hidden) {
          qr.makeCode(txt);
          const box = $("#ce-qr");
          if (box) box.style.filter = "";
        }
      });
    }
    const bar = $("#qr-bar"), info = $("#qr-info");
    if (!bar) { detenerQR(); return; }
    bar.style.width = `${100 - ((ms % VENTANA_MS) / VENTANA_MS) * 100}%`;
    info.textContent = `QR seguro · se actualiza en ${segundosRestantes(ms)} s`;
  };
  qrTimer = setInterval(tick, 500); tick();
}

/* ---------------------- Recuperar contraseña ---------------------- */
function vistaRecuperar() {
  detenerQR();
  root().innerHTML = `<section class="est-card"><h1>Recuperar contraseña</h1><p class="est-sub">Escribe tu correo y te enviaremos un enlace para crear una nueva.</p>
    <form id="f-rec" class="est-form" novalidate><div class="field"><label for="rc-email">Correo</label><input id="rc-email" type="email" autocomplete="username" required></div>
      <button class="btn btn-primary btn-block" id="rc-go" type="submit">Enviar enlace</button><p class="err-msg" id="rc-err" role="alert" hidden></p><p class="ok-msg" id="rc-ok" hidden></p>
      <p class="est-link"><button type="button" class="link-btn" data-volver>Volver</button></p></form></section>`;
  /** @type {HTMLElement} */ (root().querySelector("[data-volver]")).addEventListener("click", () => vistaAuth("login"));
  $("#f-rec").addEventListener("submit", async (/** @type {any} */ e) => {
    e.preventDefault();
    const err = $("#rc-err"), ok = $("#rc-ok"), btn = $("#rc-go"), email = $("#rc-email").value.trim();
    err.hidden = true; ok.hidden = true;
    if (!/^\S+@\S+\.\S+$/.test(email)) { err.textContent = "Escribe un correo válido."; err.hidden = false; return; }
    btn.disabled = true;
    try {
      await api.solicitarRecuperacion(email, new URL("./", location.href).href);
      ok.textContent = "Si el correo está registrado, recibirás un enlace en unos minutos. Revisa también la carpeta de spam."; ok.hidden = false;
    } catch (/** @type {any} */ ex) { err.textContent = ex.message; err.hidden = false; }
    btn.disabled = false;
  });
}
function vistaNuevaPassword() {
  detenerQR();
  root().innerHTML = `<section class="est-card"><h1>Nueva contraseña</h1><p class="est-sub">Elige una contraseña de al menos 8 caracteres.</p>
    <form id="f-np" class="est-form" novalidate><div class="field"><label for="np-1">Nueva contraseña</label><input id="np-1" type="password" autocomplete="new-password" required></div>
      <div class="field"><label for="np-2">Repite la contraseña</label><input id="np-2" type="password" autocomplete="new-password" required></div>
      <button class="btn btn-primary btn-block" id="np-go" type="submit">Guardar</button><p class="err-msg" id="np-err" role="alert" hidden></p></form></section>`;
  $("#f-np").addEventListener("submit", async (/** @type {any} */ e) => {
    e.preventDefault();
    const err = $("#np-err"), a = $("#np-1").value; err.hidden = true;
    if (a.length < 8) { err.textContent = "La contraseña debe tener al menos 8 caracteres."; err.hidden = false; return; }
    if (a !== $("#np-2").value) { err.textContent = "Las contraseñas no coinciden."; err.hidden = false; return; }
    try {
      await api.cambiarPassword(a);
      history.replaceState(null, "", location.pathname);
      toast("Contraseña actualizada", "success");
      user = await api.init(); if (user) await entrar(); else vistaAuth("login");
    } catch (/** @type {any} */ ex) { err.textContent = ex.message; err.hidden = false; }
  });
}

/* ---------------------- Verificación anti‑bots (opcional) ---------------------- */
function cargarTurnstile(/** @type {any} */ alToken) {
  const montar = () => /** @type {any} */ (window).turnstile?.render("#captcha", { sitekey: CONFIG.TURNSTILE_SITEKEY, callback: alToken, "expired-callback": () => alToken(""), theme: tema });
  if (/** @type {any} */ (window).turnstile) { montar(); return; }
  const s = document.createElement("script");
  s.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"; s.async = true; s.onload = montar;
  document.head.appendChild(s);
}

async function cargarFoto(/** @type {any} */ a) {
  try {
    const url = await api.fotoUrl(a);
    const box = $("#ce-foto");
    if (!url || !box) return;
    const img = new Image(); img.alt = "Mi foto";
    img.onload = () => { box.innerHTML = ""; box.appendChild(img); };
    img.src = url;
  } catch { /* se quedan las iniciales */ }
}

/** Dibuja el carnet (con foto y QR) en un canvas para descargarlo como imagen. */
async function carnetCanvas(/** @type {any} */ a, /** @type {any} */ colegio, modo = "off") {
  const c = document.createElement("canvas"); c.width = 560; c.height = 340;
  const x = /** @type {CanvasRenderingContext2D} */ (c.getContext("2d"));
  const g = x.createLinearGradient(0, 0, 560, 340); g.addColorStop(0, "#16223D"); g.addColorStop(1, "#22335A");
  x.fillStyle = "#fff"; x.fillRect(0, 0, 560, 340);
  x.fillStyle = g; x.beginPath(); x.roundRect(0, 0, 560, 340, 24); x.fill();
  x.fillStyle = "#E8A33D"; x.font = "bold 14px Arial"; x.fillText(`${colegio} · CARNET INSTITUCIONAL`.toUpperCase().slice(0, 60), 28, 40);
  x.fillStyle = "#fff"; x.font = "bold 24px Arial"; x.fillText(a.nombre.slice(0, 30), 168, 104);
  x.fillStyle = "#CFD7EA"; x.font = "15px Arial"; x.fillText(etiquetaCiclo(a.nivel, a.grado), 168, 136);
  // foto circular o iniciales
  x.save(); x.beginPath(); x.arc(94, 112, 54, 0, Math.PI * 2); x.clip();
  x.fillStyle = "rgba(255,255,255,.12)"; x.fillRect(40, 58, 108, 108);
  let dibujada = false;
  try {
    const url = await api.fotoUrl(a);
    if (url) { const im = await new Promise((res, rej) => { const i = new Image(); i.crossOrigin = "anonymous"; i.onload = () => res(i); i.onerror = rej; i.src = url; }); x.drawImage(im, 40, 58, 108, 108); dibujada = true; }
  } catch { /* sin foto */ }
  x.restore();
  if (!dibujada) { x.fillStyle = "#E8A33D"; x.font = "bold 34px Arial"; x.textAlign = "center"; x.fillText(initials(a.nombre), 94, 124); x.textAlign = "left"; }
  if (modo !== "off") {
    x.fillStyle = "rgba(255,255,255,.10)"; x.beginPath(); x.roundRect(190, 180, 180, 140, 12); x.fill();
    x.fillStyle = "#CFD7EA"; x.font = "bold 13px Arial"; x.textAlign = "center";
    x.fillText("El QR cambia cada 30 s", 280, 235);
    x.fillText("y solo se muestra", 280, 255);
    x.fillText("en la app", 280, 275);
    x.textAlign = "left";
  } else {
    const tmp = document.createElement("div"); tmp.style.cssText = "position:absolute;left:-9999px"; document.body.appendChild(tmp);
    new QRCode(tmp, { text: a.codigo, width: 150, height: 150, correctLevel: QRCode.CorrectLevel.M });
    await new Promise((r) => setTimeout(r, 40));
    x.fillStyle = "#fff"; x.beginPath(); x.roundRect(200, 176, 160, 150, 12); x.fill();
    const src = /** @type {HTMLCanvasElement | HTMLImageElement | null} */ (tmp.querySelector("canvas") || tmp.querySelector("img"));
    try { if (src) x.drawImage(src, 205, 181, 150, 140); } catch { /* QR no disponible */ }
    tmp.remove();
  }
  return c;
}

registerActions({
  theme: () => setTheme(tema === "dark" ? "light" : "dark"),
  "avisar-aprobacion": async () => { if (await pedirPermisoNotificacion()) { toast("Listo: te avisaremos aquí cuando te aprueben (con esta página abierta).", "success"); await vistaCarnet(); } else toast("No se concedió el permiso de notificaciones.", "error"); },
  "ap-copiar": async () => { try { await navigator.clipboard.writeText(registro.alumno.codigo_apoderado); toast("Código copiado", "success"); } catch { toast("No se pudo copiar", "error"); } },
  logout: async () => { desactivarAvisos(); detenerVigilancia(); await api.signOut(); user = null; registro = null; $("#est-logout").hidden = true; $("#est-instituto").textContent = "Portal del estudiante"; vistaAuth("login"); },
  foto: async (/** @type {any} */ btn) => {
    const blob = await pedirFoto();
    if (!blob) return;
    btn.disabled = true;
    try {
      await api.subirFoto(user, blob);
      registro = await api.miRegistro(user);
      toast("Foto guardada", "success");
      await vistaCarnet();
    } catch (/** @type {any} */ e) { toast("No se pudo guardar la foto: " + e.message, "error"); btn.disabled = false; }
  },
  "eliminar-cuenta": async () => {
    if (!(await confirmDialog({ title: "Eliminar mi cuenta y mis datos", message: "Se borrarán de forma <b>definitiva</b> tu cuenta, tu foto, tu carnet y tu historial de asistencia. Esta acción no se puede deshacer.", confirmLabel: "Sí, eliminar todo" }))) return;
    if (!(await confirmDialog({ title: "¿Estás seguro?", message: "Última confirmación: no podrás recuperar tu carnet ni tu historial.", confirmLabel: "Eliminar definitivamente" }))) return;
    try {
      await api.eliminarMiCuenta(user);
      user = null; registro = null; $("#est-logout").hidden = true; $("#est-instituto").textContent = "Portal del estudiante";
      vistaAuth("login"); toast("Tu cuenta y tus datos fueron eliminados", "success");
    } catch (/** @type {any} */ e) { toast("No se pudo eliminar: " + e.message, "error"); }
  },
  descargar: async () => {
    const modo = qrModoEfectivo(registro?.qr_modo);
    const c = await carnetCanvas(registro.alumno, registro.colegio, modo);
    await downloadFile(`mi-carnet-${registro.alumno.codigo}.png`, await new Promise((r) => c.toBlob((b) => r(/** @type {Blob} */ (b)), "image/png")));
  },
});

/* ------------------------------ Arranque ------------------------------ */
async function sincronizarRelojEstudiante() {
  try { sincronizarReloj(await api.horaServidor()); } catch { /* sin red: se usa la última corrección guardada */ }
}

async function entrar() {
  $("#est-logout").hidden = false;
  sincronizarRelojEstudiante(); enviarPendientes();
  registro = await api.miRegistro(user);
  if (registro) { await vistaCarnet(); activarAvisos(api); } else vistaRegistro();
}

async function boot() {
  let t = "light";
  try { t = localStorage.getItem("cv-theme") || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"); } catch { /* ok */ }
  setTheme(t, false);
  $("#est-logout").innerHTML = icon("logout");
  $("#est-demo").hidden = !isDemoMode();
  bindActions();
  iniciarLogErrores("estudiante", api);
  setInterval(sincronizarRelojEstudiante, 10 * 60 * 1000);
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) alOcultarApp();
    else alReanudarApp();
  });
  window.addEventListener("focus", () => {
    if (!document.hidden) alReanudarApp();
  });
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("../sw.js", { scope: "../" }).catch((/** @type {any} */ e) => console.warn("Service worker no registrado:", e.message));
  }
  if (api.mode === "error") { vistaAuth(); const e = $("#a-err"); e.textContent = /** @type {any} */ (api.error).message; e.hidden = false; return; }
  api.alRecuperar(() => vistaNuevaPassword());   // el enlace del correo abre el portal con una sesión de recuperación
  try { user = await api.init(); } catch (/** @type {any} */ e) { console.error(e); }
  if (/type=recovery/.test(location.hash)) return;   // la vista de nueva contraseña la muestra alRecuperar
  if (user) { try { await entrar(); return; } catch (/** @type {any} */ e) { console.error(e); } }
  vistaAuth("login");
}
boot();
