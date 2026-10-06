// @ts-check
// Código de registro del instituto: lo que los estudiantes escriben en "Lummer Estudiante" para registrarse.
// Página propia para generarlo, copiarlo y compartirlo en un toque.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { DB } from "../state.js";
import { esAdmin } from "../permisos.js";
import { confirmDialog, icon, pageHead, registerActions, toast } from "../ui.js";
import { esc } from "../utils.js";

const ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin caracteres ambiguos (0/O, 1/I)
const generar = () => [...crypto.getRandomValues(new Uint8Array(8))].map((b) => ALFABETO[b % ALFABETO.length]).join("");
const portalUrl = () => new URL("estudiante/", location.href.split("#")[0].split("?")[0]).href;

/** Copia al portapapeles (con alternativa para WebView/HTTP, donde navigator.clipboard puede no estar disponible). */
/** @param {string} texto */
async function copiar(texto) {
  try { await navigator.clipboard.writeText(texto); return true; } catch { /* alternativa */ }
  const t = document.createElement("textarea");
  t.value = texto; t.style.cssText = "position:fixed;opacity:0"; document.body.appendChild(t); t.select();
  let ok = false;
  try { ok = document.execCommand("copy"); } catch { /* sin permiso */ }
  t.remove();
  return ok;
}

/** @param {string} codigo */
function textoCompartir(codigo) {
  const apk = CONFIG.APK_ESTUDIANTE_URL;
  return [
    `Regístrate en *Lummer Estudiante* de ${DB.perfil?.colegio || "nuestro instituto"}:`,
    apk ? `1) Descarga la app: ${apk}` : `1) Abre: ${portalUrl()}`,
    `2) Crea tu cuenta y escribe el código del instituto: *${codigo}*`,
    `3) Completa tus datos y sube tu foto. Tu carnet con QR se activa cuando el instituto lo apruebe.`,
    apk ? `(También puedes registrarte desde el navegador: ${portalUrl()})` : "",
  ].filter(Boolean).join("\n");
}

/** @type {string | null} */
let actual = null;

export const codigoPage = {
  id: "codigo", title: "Código de registro", icon: "qr", group: "Gestión",
  /** @param {HTMLElement} root */
  async render(root) {
    actual = await api.getCodigoRegistro(/** @type {string} */ (DB.cid));
    const pend = DB.alumnos.filter((a) => a.aprobado === false).length;
    root.innerHTML = `
      ${pageHead("Código de registro", "El código que tus estudiantes escriben en <b>Lummer Estudiante</b> para registrarse. Tú apruebas cada registro.")}
      <div class="grid-2 split">
        <section class="card" id="cd-card"></section>
        <section class="card center-col" id="cd-qr-card">
          <h3 style="margin-bottom:6px">Portal del estudiante</h3>
          <p class="muted" style="text-align:center;margin:0 0 14px">Que escaneen este QR con la cámara de su teléfono para abrir el portal.</p>
          <div class="qr-portal" id="cd-qr"></div>
          <a class="link" href="${esc(portalUrl())}" target="_blank" rel="noopener" style="margin-top:12px;word-break:break-all;text-align:center">${esc(portalUrl())}</a>
          ${pend ? `<a class="btn btn-teal btn-sm" href="#/alumnos" data-action="al-pend" style="margin-top:14px">${icon("userCheck", 14)} ${pend} registro(s) por aprobar</a>` : ""}
        </section>
      </div>`;
    pintar(root);
    new QRCode(root.querySelector("#cd-qr"), { text: portalUrl(), width: 168, height: 168, correctLevel: QRCode.CorrectLevel.M });
  },
};

/** @param {HTMLElement} root */
function pintar(root) {
  const el = /** @type {HTMLElement} */ (root.querySelector("#cd-card"));
  el.innerHTML = actual
    ? `<span class="muted">Código vigente</span>
       <div class="codigo-box codigo-grande"><code id="cd-code">${esc(actual)}</code></div>
       <div class="btn-row">
         <button class="btn btn-primary" data-action="cd-copiar">${icon("check", 16)} Copiar código</button>
         <a class="btn btn-teal" id="cd-wa" target="_blank" rel="noopener" href="https://wa.me/?text=${encodeURIComponent(textoCompartir(actual))}">Compartir por WhatsApp</a>
         <button class="btn btn-outline" data-action="cd-copiar-msg">Copiar mensaje</button>
       </div>
       <hr>
       <div class="btn-row"><button class="btn btn-outline btn-sm" data-action="cd-regenerar">${icon("flip", 14)} Regenerar</button></div>
       <details class="cd-propio solo-admin"><summary>Usar un código propio</summary>
         <form id="cd-form" class="inline-form" style="margin-top:10px"><label class="sr-only" for="cd-input">Código propio</label>
           <input class="input" id="cd-input" placeholder="Ej: INSTITUTO2026 (6 a 20 letras o números)" maxlength="20" autocomplete="off" autocapitalize="characters">
           <button class="btn btn-outline" type="submit">Guardar</button></form></details>
       <p class="muted" style="margin-top:14px">Si lo regeneras o lo cambias, el código anterior deja de funcionar (los estudiantes ya registrados no se ven afectados).</p>`
    : `<div class="empty-state" style="padding:20px 10px"><div class="empty-ic">${icon("qr", 26)}</div><h3>Aún no tienes código</h3><p>${esAdmin() ? "Genera uno para que los estudiantes puedan registrarse." : "Pídele al administrador que genere el código de registro."}</p></div>
       <button class="btn btn-primary btn-block" data-action="cd-generar">${icon("plus", 16)} Generar código ahora</button>
       <p class="err-msg" id="cd-err" role="alert" hidden></p>`;
  const f = el.querySelector("#cd-form");
  if (f) f.addEventListener("submit", async (e) => { e.preventDefault(); await guardarCodigo(root, /** @type {HTMLInputElement} */ (f.querySelector("input")).value.trim().toUpperCase()); });
}

/**
 * @param {HTMLElement} root
 * @param {string} codigo
 */
async function guardarCodigo(root, codigo) {
  if (!/^[A-Z0-9]{6,20}$/.test(codigo)) { toast("El código debe tener de 6 a 20 letras o números, sin espacios.", "error"); return; }
  try {
    await api.setCodigoRegistro(/** @type {string} */ (DB.cid), codigo);
    actual = codigo;
    pintar(root);
    toast("Código guardado", "success");
  } catch (/** @type {any} */ e) {
    const falta = /codigo_registro|column|does not exist/i.test(e.message);
    toast(falta ? "Falta aplicar la migración del portal de estudiantes (supabase/migrations/002_estudiantes.sql)." : e.message, "error");
  }
}

registerActions({
  "cd-generar": () => guardarCodigo(/** @type {HTMLElement} */ (document.getElementById("page-root")), generar()),
  "cd-regenerar": async () => {
    if (!(await confirmDialog({ title: "Regenerar código", message: "El código actual dejará de funcionar. ¿Generar uno nuevo?", confirmLabel: "Regenerar", danger: false }))) return;
    await guardarCodigo(/** @type {HTMLElement} */ (document.getElementById("page-root")), generar());
  },
  "cd-copiar": async () => { const ok = await copiar(/** @type {string} */ (actual)); toast(ok ? "Código copiado" : "No se pudo copiar: selecciónalo y cópialo manualmente", ok ? "success" : "error"); },
  "cd-copiar-msg": async () => { const ok = await copiar(textoCompartir(/** @type {string} */ (actual))); toast(ok ? "Mensaje copiado: pégalo en WhatsApp, correo o donde quieras" : "No se pudo copiar", ok ? "success" : "error"); },
  // bindActions cancela el clic del enlace (preventDefault), así que la navegación se hace aquí.
  "al-pend": () => { try { sessionStorage.setItem("ra-alumnos-filtro", "pend"); } catch { /* sin storage */ } location.hash = "#/alumnos"; },
});
