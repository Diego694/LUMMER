// Portal del estudiante: crear cuenta → registrarse con el código del instituto → subir foto → carnet con QR único.
import { CONFIG, isDemoMode } from "../config.js";
import { api } from "./api.js";
import { bindActions, icon, openModal, registerActions, toast } from "../ui.js";
import { cicloCorto, compararCiclos, downloadFile, esc, etiquetaCiclo, initials } from "../utils.js";

const $ = (s, r = document) => r.querySelector(s);
const root = () => $("#est-root");
let user = null;      // sesión
let registro = null;  // { alumno, colegio: nombre del instituto }
let tema = "light";

/* ------------------------------ Tema ------------------------------ */
function setTheme(t, persistir = true) {
  tema = t;
  document.documentElement.setAttribute("data-theme", t);
  if (persistir) try { localStorage.setItem("cv-theme", t); } catch { /* sin storage */ }
  document.querySelectorAll("[data-action=theme]").forEach((b) => { b.innerHTML = icon(t === "dark" ? "sun" : "moon"); b.setAttribute("aria-label", t === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"); });
}

/* ------------------------------ Fotos ------------------------------ */
/** Recorta al centro en cuadrado y reduce a 480×480 JPEG (~40 KB): ahorra datos y espacio. */
function aCuadrado(fuente, w, h) {
  const lado = Math.min(w, h), c = document.createElement("canvas");
  c.width = c.height = 480;
  c.getContext("2d").drawImage(fuente, (w - lado) / 2, (h - lado) / 2, lado, lado, 0, 0, 480, 480);
  return new Promise((res) => c.toBlob(res, "image/jpeg", 0.82));
}
async function archivoACuadrado(file) {
  if (!/^image\//.test(file.type)) throw new Error("El archivo debe ser una imagen.");
  if (window.createImageBitmap) {
    const bm = await createImageBitmap(file, { imageOrientation: "from-image" });
    const b = await aCuadrado(bm, bm.width, bm.height); bm.close?.(); return b;
  }
  const img = await new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = URL.createObjectURL(file); });
  return aCuadrado(img, img.naturalWidth, img.naturalHeight);
}

/** Modal para tomar una selfie o elegir de la galería. Devuelve el Blob JPEG (o null si cancela). */
function pedirFoto() {
  return new Promise((resolve) => {
    let stream = null, blob = null, urlPrevia = null, hecho = false;
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
    const q = (s) => m.el.querySelector(s);
    const err = (t) => { q("#cam-err").textContent = t; q("#cam-err").hidden = !t; };
    const apagar = () => { stream?.getTracks().forEach((t) => t.stop()); stream = null; };
    const fin = (v) => { if (hecho) return; hecho = true; apagar(); if (urlPrevia) URL.revokeObjectURL(urlPrevia); m.close(); resolve(v); };
    const mostrarPrevia = (b) => {
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
    q("#cam-input").addEventListener("change", async (e) => {
      const f = e.target.files[0]; if (!f) return;
      try { err(""); mostrarPrevia(await archivoACuadrado(f)); } catch (ex) { err(ex.message); }
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
    <section class="est-card">
      <h1>${reg ? "Crear mi cuenta" : "Bienvenido"}</h1>
      <p class="est-sub">${reg ? "Paso 1 de 3 · Tu correo y una contraseña." : "Ingresa para ver tu carnet con código QR."}</p>
      <div class="tabs-est" role="tablist"><button type="button" class="${reg ? "" : "active"}" data-modo="login" role="tab">Ingresar</button><button type="button" class="${reg ? "active" : ""}" data-modo="signup" role="tab">Crear cuenta</button></div>
      <form id="f-auth" class="est-form" novalidate>
        <div class="field"><label for="a-email">Correo</label><input id="a-email" type="email" autocomplete="username" placeholder="tucorreo@ejemplo.com" required></div>
        <div class="field"><label for="a-pass">Contraseña</label><input id="a-pass" type="password" autocomplete="${reg ? "new-password" : "current-password"}" placeholder="${reg ? "Mínimo 8 caracteres" : "••••••••"}" required></div>
        ${reg ? `<div class="field"><label for="a-pass2">Repite la contraseña</label><input id="a-pass2" type="password" autocomplete="new-password" required></div>` : ""}
        <button class="btn btn-primary btn-block" id="a-go" type="submit">${reg ? "Crear cuenta" : "Ingresar"}</button>
        <p class="err-msg" id="a-err" role="alert" hidden></p>
        ${isDemoMode() ? `<p class="demo-hint"><strong>Modo demo</strong>: crea una cuenta con cualquier correo. El código del instituto de prueba es <code>DEMO2026</code>.</p>` : ""}
      </form>
    </section>`;
  root().querySelectorAll("[data-modo]").forEach((b) => b.addEventListener("click", () => vistaAuth(b.dataset.modo)));
  $("#f-auth").addEventListener("submit", async (e) => {
    e.preventDefault();
    const err = $("#a-err"), btn = $("#a-go"), email = $("#a-email").value.trim(), pass = $("#a-pass").value;
    err.hidden = true;
    if (!/^\S+@\S+\.\S+$/.test(email)) { err.textContent = "Escribe un correo válido."; err.hidden = false; return; }
    if (reg && pass.length < 8) { err.textContent = "La contraseña debe tener al menos 8 caracteres."; err.hidden = false; return; }
    if (reg && pass !== $("#a-pass2").value) { err.textContent = "Las contraseñas no coinciden."; err.hidden = false; return; }
    btn.disabled = true;
    try {
      user = reg ? await api.signUp(email, pass) : await api.signIn(email, pass);
      await entrar();
    } catch (ex) {
      err.textContent = ex.code === "auth" && !reg ? "Correo o contraseña incorrectos." : ex.message || "No se pudo continuar.";
      err.hidden = false; btn.disabled = false;
    }
  });
}

function vistaRegistro() {
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
          <div class="row2"><div class="field"><label for="r-niv">Carrera <span class="req">*</span></label><select id="r-niv">${info.niveles.map((n) => `<option>${esc(n)}</option>`).join("")}</select></div>
          <div class="field"><label for="r-gra">Ciclo / salón <span class="req">*</span></label><select id="r-gra"></select></div></div>
          <div class="field"><label for="r-dni">DNI (opcional)</label><input id="r-dni" inputmode="numeric" maxlength="12" autocomplete="off"></div>
          <div class="field"><label for="r-apo">Apoderado (opcional)</label><input id="r-apo" autocomplete="off"></div>
          <label class="consent"><input type="checkbox" id="r-ok"><span>Autorizo el tratamiento de mis datos personales y de mi foto para el control de asistencia institucional. Si soy menor de edad, mi padre, madre o apoderado lo autoriza.</span></label>
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
      $("#f-cod").addEventListener("submit", async (e) => {
        e.preventDefault();
        codigo = $("#c-cod").value.trim().toUpperCase();
        const err = $("#c-err"), btn = $("#c-go"); err.hidden = true;
        if (!codigo) { err.textContent = "Escribe el código."; err.hidden = false; return; }
        btn.disabled = true;
        try {
          info = await api.infoColegio(codigo);
          if (!info) { err.textContent = "Código inválido. Revísalo con tu instituto."; err.hidden = false; btn.disabled = false; return; }
          pintar();
        } catch (ex) { err.textContent = ex.message; err.hidden = false; btn.disabled = false; }
      });
      return;
    }
    const sel = $("#r-niv"), gra = $("#r-gra");
    const llenarGrados = () => { gra.innerHTML = info.grados.filter((g) => g.nivel === sel.value).sort((a, b) => compararCiclos(a.nombre, b.nombre)).map((g) => `<option value="${esc(g.nombre)}">${esc(cicloCorto(g.nombre, sel.value))}</option>`).join(""); };
    llenarGrados(); sel.addEventListener("change", llenarGrados);
    $("#f-reg").addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = $("#r-err"), btn = $("#r-go"); err.hidden = true;
      const f = { codigoColegio: codigo, nombres: $("#r-nom").value.trim(), apellidos: $("#r-ape").value.trim(), nivel: sel.value, grado: gra.value, dni: $("#r-dni").value.trim(), apoderado: $("#r-apo").value.trim() };
      if (f.nombres.length < 2 || f.apellidos.length < 2) { err.textContent = "Escribe tus nombres y apellidos."; err.hidden = false; return; }
      if (!f.grado) { err.textContent = "Elige tu ciclo."; err.hidden = false; return; }
      if (f.dni && !/^\d{6,12}$/.test(f.dni)) { err.textContent = "El DNI solo debe tener números."; err.hidden = false; return; }
      if (!$("#r-ok").checked) { err.textContent = "Debes aceptar la autorización para continuar."; err.hidden = false; return; }
      btn.disabled = true;
      try { await api.registrar(user, f); registro = await api.miRegistro(user); await vistaCarnet(true); }
      catch (ex) { err.textContent = ex.message; err.hidden = false; btn.disabled = false; }
    });
  };
  pintar();
}

async function vistaCarnet(recienCreado = false) {
  const { alumno: a, colegio } = registro;
  const aprobado = a.aprobado !== false;
  $("#est-instituto").textContent = colegio;
  root().innerHTML = `
    <section class="carnet-est" aria-label="Mi carnet institucional">
      <div class="ce-head">${esc(colegio)} · Carnet institucional</div>
      <div class="ce-id">
        <div class="ce-foto" id="ce-foto"><span>${esc(initials(a.nombre))}</span></div>
        <div><div class="ce-name">${esc(a.nombre)}</div><div class="ce-meta">${esc(etiquetaCiclo(a.nivel, a.grado))}</div>
          <span class="estado-chip ${aprobado ? "estado-ok" : "estado-pend"}">${icon(aprobado ? "check" : "clock", 13)} ${aprobado ? "Registro aprobado" : "Pendiente de aprobación"}</span></div>
      </div>
      <div class="ce-qr" id="ce-qr" aria-label="Código QR de asistencia"></div>
      <div class="ce-code">${esc(a.codigo)}</div>
    </section>
    ${aprobado ? "" : `<div class="aviso">${icon("info", 15)} ${recienCreado ? "¡Listo! Tu carnet fue creado. " : ""}Tu QR empezará a registrar asistencia cuando el instituto apruebe tu registro. Mientras tanto, agrega tu foto.</div>`}
    ${a.foto_path ? "" : `<div class="aviso">${icon("camera", 15)} Agrega tu foto: el docente la verá cuando pases tu QR.</div>`}
    <div class="est-actions">
      <button class="btn btn-primary" data-action="foto">${icon("camera", 16)} ${a.foto_path ? "Cambiar foto" : "Agregar foto"}</button>
      <button class="btn btn-outline" data-action="descargar">${icon("download", 16)} Descargar carnet</button>
    </div>
    <p class="muted" style="text-align:center;margin-top:18px">Muestra este QR al docente al ingresar a clases. No lo compartas con otras personas.</p>`;
  new QRCode($("#ce-qr"), { text: a.codigo, width: 200, height: 200, correctLevel: QRCode.CorrectLevel.M });
  cargarFoto(a);
}

async function cargarFoto(a) {
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
async function carnetCanvas(a, colegio) {
  const c = document.createElement("canvas"); c.width = 560; c.height = 340;
  const x = c.getContext("2d");
  const g = x.createLinearGradient(0, 0, 560, 340); g.addColorStop(0, "#16223D"); g.addColorStop(1, "#22335A");
  x.fillStyle = "#fff"; x.fillRect(0, 0, 560, 340);
  x.fillStyle = g; x.beginPath(); x.roundRect(0, 0, 560, 340, 24); x.fill();
  x.fillStyle = "#E8A33D"; x.font = "bold 14px Arial"; x.fillText(`${colegio} · CARNET INSTITUCIONAL`.toUpperCase().slice(0, 60), 28, 40);
  x.fillStyle = "#fff"; x.font = "bold 24px Arial"; x.fillText(a.nombre.slice(0, 30), 168, 100);
  x.fillStyle = "#CFD7EA"; x.font = "15px Arial"; x.fillText(etiquetaCiclo(a.nivel, a.grado), 168, 128);
  x.fillStyle = "#B8C2DC"; x.font = "14px monospace"; x.fillText(a.codigo, 168, 154);
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
  const tmp = document.createElement("div"); tmp.style.cssText = "position:absolute;left:-9999px"; document.body.appendChild(tmp);
  new QRCode(tmp, { text: a.codigo, width: 150, height: 150, correctLevel: QRCode.CorrectLevel.M });
  await new Promise((r) => setTimeout(r, 40));
  x.fillStyle = "#fff"; x.beginPath(); x.roundRect(200, 176, 160, 150, 12); x.fill();
  const src = tmp.querySelector("canvas") || tmp.querySelector("img");
  try { if (src) x.drawImage(src, 205, 181, 150, 140); } catch { /* QR no disponible */ }
  tmp.remove();
  return c;
}

registerActions({
  theme: () => setTheme(tema === "dark" ? "light" : "dark"),
  logout: async () => { await api.signOut(); user = null; registro = null; $("#est-logout").hidden = true; $("#est-instituto").textContent = "Portal del estudiante"; vistaAuth("login"); },
  foto: async (btn) => {
    const blob = await pedirFoto();
    if (!blob) return;
    btn.disabled = true;
    try {
      await api.subirFoto(user, blob);
      registro = await api.miRegistro(user);
      toast("Foto guardada", "success");
      await vistaCarnet();
    } catch (e) { toast("No se pudo guardar la foto: " + e.message, "error"); btn.disabled = false; }
  },
  descargar: async () => {
    const c = await carnetCanvas(registro.alumno, registro.colegio);
    await downloadFile(`mi-carnet-${registro.alumno.codigo}.png`, await new Promise((r) => c.toBlob(r, "image/png")));
  },
});

/* ------------------------------ Arranque ------------------------------ */
async function entrar() {
  $("#est-logout").hidden = false;
  registro = await api.miRegistro(user);
  if (registro) await vistaCarnet(); else vistaRegistro();
}

async function boot() {
  let t = "light";
  try { t = localStorage.getItem("cv-theme") || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"); } catch { /* ok */ }
  setTheme(t, false);
  $("#est-logout").innerHTML = icon("logout");
  $("#est-demo").hidden = !isDemoMode();
  bindActions();
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("../sw.js", { scope: "../" }).catch((e) => console.warn("Service worker no registrado:", e.message));
  }
  if (api.mode === "error") { vistaAuth(); const e = $("#a-err"); e.textContent = api.error.message; e.hidden = false; return; }
  try { user = await api.init(); } catch (e) { console.error(e); }
  if (user) { try { await entrar(); return; } catch (e) { console.error(e); } }
  vistaAuth("login");
}
boot();
