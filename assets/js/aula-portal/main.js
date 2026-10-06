// @ts-check
// Portal del aula virtual: exclusivo para personal (administrador, coordinadores y docentes).
import { api } from "../api.js";
import { DEMO_USER, isDemoMode } from "../config.js";
import { DB, loadAll } from "../state.js";
import { bindActions, icon, registerActions, setAutorizador, skeleton, toast } from "../ui.js";
import { aplicarPermisos, ETIQUETA_ROL, instalarEstiloPermisos, puede, puedeEntrarAlAula, rolActual } from "../permisos.js";
import { aulaPage } from "../pages/aula.js";
import { iniciarLogErrores } from "../errlog.js";

/** @param {string} sel @returns {HTMLElement | null} */
const $ = (sel) => document.querySelector(sel);

/** @type {'light' | 'dark'} */
let tema = "light";

/* ------------------------------ Tema ------------------------------ */
/**
 * Aplica el tema claro u oscuro en el portal.
 * @param {'light' | 'dark'} t
 * @param {boolean} [persistir]
 */
function setTheme(t, persistir = true) {
  tema = t;
  document.documentElement.setAttribute("data-theme", t);
  if (persistir) {
    try { localStorage.setItem("cv-theme", t); } catch { /* sin almacenamiento */ }
  }
  document.querySelectorAll("[data-action=theme]").forEach((b) => {
    b.innerHTML = icon(t === "dark" ? "sun" : "moon");
    b.setAttribute("aria-label", t === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro");
  });
}

/* ------------------------ Visibilidad de contraseña ------------------------ */
function initPasswordToggle() {
  const btn = $("#pw-toggle");
  const inp = /** @type {HTMLInputElement | null} */ ($("#login-pass"));
  if (!btn || !inp) return;
  btn.addEventListener("click", () => {
    const ver = inp.type === "password";
    inp.type = ver ? "text" : "password";
    btn.setAttribute("aria-pressed", String(ver));
    btn.setAttribute("aria-label", ver ? "Ocultar contraseña" : "Mostrar contraseña");
  });
}

/* ------------------------------ Sesión ------------------------------ */
/**
 * Inicia la sesión y monta el aula virtual.
 * @param {any} user
 */
async function entrar(user) {
  let perfil;
  try {
    perfil = await api.getProfile(user);
  } catch (/** @type {any} */ ex) {
    await api.signOut().catch(() => {});
    throw new Error("Esta cuenta no cuenta con perfil de personal. Si eres estudiante, ingresa desde el portal del estudiante.");
  }

  if (!puedeEntrarAlAula(perfil.rol)) {
    await api.signOut().catch(() => {});
    throw new Error("Acceso no autorizado: este portal es exclusivo para docentes y directivos. Si eres estudiante, ingresa desde el portal del estudiante.");
  }

  DB.cid = perfil.colegio_id;
  DB.rol = perfil.rol;
  DB.perfil = perfil;
  DB.userId = user.id;
  DB.userEmail = user.email || "";

  instalarEstiloPermisos();
  setAutorizador(puede);
  aplicarPermisos();

  const nameEl = $("#user-name");
  if (nameEl) nameEl.textContent = perfil.nombre || perfil.email || "Docente";

  const roleEl = $("#user-role");
  if (roleEl) {
    const rolEtiqueta = ETIQUETA_ROL[rolActual()] || perfil.rol;
    roleEl.textContent = `${rolEtiqueta}${perfil.carrera ? " · " + perfil.carrera : ""}${perfil.colegio ? " · " + perfil.colegio : ""}`;
  }

  const subEl = $("#aula-subtitulo");
  if (subEl && perfil.colegio) {
    subEl.textContent = perfil.colegio;
  }

  const badgeEl = $("#mode-badge");
  if (badgeEl) badgeEl.hidden = api.mode !== "demo";

  const loginView = $("#aula-login");
  const appView = $("#aula-app");
  if (loginView) loginView.hidden = true;
  if (appView) appView.hidden = false;

  const root = /** @type {HTMLElement & { _repaint?: () => void } | null} */ (document.getElementById("page-root"));
  if (root) {
    root.innerHTML = skeleton(4);
    try {
      await loadAll();
      await aulaPage.render(root);
    } catch (/** @type {any} */ e) {
      console.error(e);
      toast("No se pudieron cargar los cursos del aula: " + e.message, "error");
    }
  }
}

/**
 * Cierra la sesión activa y restaura la vista de login.
 */
async function salir() {
  await api.signOut().catch(() => {});
  DB.cid = null;
  DB.rol = null;
  DB.perfil = null;
  DB.userId = null;
  DB.cursos = [];

  const appView = $("#aula-app");
  const loginView = $("#aula-login");
  if (appView) appView.hidden = true;
  if (loginView) loginView.hidden = false;

  const passInp = /** @type {HTMLInputElement | null} */ ($("#login-pass"));
  if (passInp) passInp.value = "";

  const errEl = $("#login-err");
  if (errEl) errEl.hidden = true;
}

/* ------------------------ Formulario de Login ------------------------ */
function initLogin() {
  const form = /** @type {HTMLFormElement | null} */ ($("#aula-login-form"));
  const errEl = $("#login-err");
  const btn = /** @type {HTMLButtonElement | null} */ ($("#login-btn"));
  if (!form) return;

  if (isDemoMode()) {
    const hint = $("#demo-hint");
    const creds = $("#demo-hint-creds");
    if (hint) hint.hidden = false;
    if (creds) creds.textContent = `${DEMO_USER.email} / ${DEMO_USER.password}`;
    const emailInp = /** @type {HTMLInputElement | null} */ ($("#login-email"));
    const passInp = /** @type {HTMLInputElement | null} */ ($("#login-pass"));
    if (emailInp) emailInp.value = DEMO_USER.email;
    if (passInp) passInp.value = DEMO_USER.password;
  }

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (errEl) errEl.hidden = true;
    if (btn) {
      btn.disabled = true;
      btn.textContent = "Ingresando…";
    }

    const emailInp = /** @type {HTMLInputElement | null} */ ($("#login-email"));
    const passInp = /** @type {HTMLInputElement | null} */ ($("#login-pass"));
    const email = emailInp?.value.trim() || "";
    const pass = passInp?.value || "";

    try {
      const user = await api.signIn(email, pass);
      await entrar(user);
    } catch (/** @type {any} */ ex) {
      console.error(ex);
      if (errEl) {
        errEl.textContent = ex.code === "auth" ? "Correo o contraseña incorrectos." : ex.message || "No se pudo iniciar sesión.";
        errEl.hidden = false;
      }
    } finally {
      if (btn) {
        btn.disabled = false;
        btn.textContent = "Ingresar al aula";
      }
    }
  });
}

registerActions({
  theme: () => setTheme(tema === "dark" ? "light" : "dark"),
  logout: salir,
});

/* ------------------------------ Arranque ------------------------------ */
async function boot() {
  let t = "light";
  try {
    t = localStorage.getItem("cv-theme") || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light");
  } catch { /* sin almacenamiento */ }
  setTheme(/** @type {'light' | 'dark'} */ (t), false);

  bindActions();
  initPasswordToggle();
  initLogin();

  iniciarLogErrores("aula", api);

  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("../sw.js", { scope: "../" }).catch((/** @type {any} */ e) => console.warn("Service worker no registrado:", e.message));
  }

  if (api.mode === "error") {
    const errEl = $("#login-err");
    if (errEl) {
      errEl.textContent = /** @type {any} */ (api).error?.message || "Error al inicializar la conexión.";
      errEl.hidden = false;
    }
    return;
  }

  let user = null;
  try {
    user = await api.init();
  } catch (/** @type {any} */ e) {
    console.error(e);
  }

  if (user) {
    try {
      await entrar(user);
      return;
    } catch (/** @type {any} */ ex) {
      console.error(ex);
      const errEl = $("#login-err");
      if (errEl) {
        errEl.textContent = ex.message || "No se pudo recuperar la sesión.";
        errEl.hidden = false;
      }
    }
  }
}

boot();
