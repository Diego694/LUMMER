// Punto de entrada: autenticación, navegación (router por hash), tema y arranque.
import { api } from "./api.js";
import { CONFIG, DEMO_USER, isDemoMode } from "./config.js";
import { DB, loadAll } from "./state.js";
import { bindActions, confirmDialog, emptyState, icon, registerActions, toast } from "./ui.js";
import { esc, fmtDate, initials, todayStr } from "./utils.js";
import { dashboardPage } from "./pages/dashboard.js";
import { registroAlumnoPage, registroMasivoPage, registroQrPage } from "./pages/registro.js";
import { asistAlumnoPage, asistGradoPage } from "./pages/consultas.js";
import { carnetPage } from "./pages/carnet.js";
import { alumnosPage, comunicadosPage, docentesPage, gradosPage, nivelesPage } from "./pages/mantenimiento.js";

const PAGES = [dashboardPage, registroQrPage, registroAlumnoPage, registroMasivoPage, asistGradoPage, asistAlumnoPage,
  carnetPage, alumnosPage, docentesPage, nivelesPage, gradosPage, comunicadosPage];
const $ = (s) => document.querySelector(s);
let actual = null;
let logged = false;

/* ------------------------------ Tema ------------------------------ */
function setTheme(t, persistir = true) {
  document.documentElement.setAttribute("data-theme", t);
  if (persistir) try { localStorage.setItem("cv-theme", t); } catch { /* sin storage */ }
  document.querySelectorAll("[data-action=theme]").forEach((b) => { b.innerHTML = icon(t === "dark" ? "sun" : "moon"); b.setAttribute("aria-label", t === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"); });
  actual?.onTheme?.();
}
const temaActual = () => document.documentElement.getAttribute("data-theme") || "light";

/* ---------------------------- Navegación ---------------------------- */
function buildNav() {
  const grupos = [...new Set(PAGES.map((p) => p.group))];
  $("#nav").innerHTML = grupos.map((g) => `<div class="nav-group"><div class="nav-group-title">${esc(g)}</div>${PAGES.filter((p) => p.group === g)
    .map((p) => `<a class="nav-item" href="#/${p.id}" data-page="${p.id}">${icon(p.icon)}<span>${esc(p.title)}</span></a>`).join("")}</div>`).join("");
}

function parseHash() {
  const [path, qs] = location.hash.replace(/^#\/?/, "").split("?");
  return { id: path || "dashboard", params: Object.fromEntries(new URLSearchParams(qs || "")) };
}

async function route() {
  if (!logged) return;
  const { id, params } = parseHash();
  const page = PAGES.find((p) => p.id === id) || dashboardPage;
  if (actual && actual !== page) await actual.onLeave?.();
  actual = page;
  document.querySelectorAll(".nav-item").forEach((n) => { const on = n.dataset.page === page.id; n.classList.toggle("active", on); n.toggleAttribute("aria-current", on); });
  $("#topbar-title").textContent = page.title;
  document.title = `${page.title} · ${CONFIG.APP_NAME}`;
  const root = $("#page-root");
  root._repaint = null; root._hist = null;
  toggleSidebar(false);
  try { await page.render(root, params); }
  catch (e) { console.error(e); root.innerHTML = `<div class="card">${emptyState("No se pudo mostrar la página", e.message, "alert")}</div>`; }
  window.scrollTo(0, 0);
  root.focus({ preventScroll: true });
}

function toggleSidebar(force) {
  const open = typeof force === "boolean" ? force : !$("#sidebar").classList.contains("open");
  $("#sidebar").classList.toggle("open", open);
  $("#backdrop").classList.toggle("open", open);
  $("#menu-toggle").setAttribute("aria-expanded", open);
}

/* ------------------------------ Sesión ------------------------------ */
async function entrar(user) {
  const perfil = await api.getProfile(user);
  DB.cid = perfil.colegio_id; DB.rol = perfil.rol; DB.perfil = perfil;
  await loadAll();
  logged = true;
  $("#login-screen").hidden = true; $("#app").hidden = false;
  $("#user-name").textContent = perfil.nombre || perfil.rol;
  $("#user-role").textContent = `${perfil.rol}${perfil.colegio ? " · " + perfil.colegio : ""}`;
  $("#user-avatar").textContent = initials(perfil.nombre || perfil.rol);
  $("#topbar-date").textContent = fmtDate(todayStr(), { weekday: "short", day: "2-digit", month: "short" });
  $("#mode-badge").hidden = api.mode !== "demo";
  $("#demo-reset").hidden = api.mode !== "demo";
  if (!location.hash) location.hash = "#/dashboard"; else route();
}

async function salir() {
  await actual?.onLeave?.(); actual = null; logged = false;
  await api.signOut();
  $("#app").hidden = true; $("#login-screen").hidden = false;
  $("#login-pass").value = "";
  history.replaceState(null, "", location.pathname);
}

function initLogin() {
  const form = $("#login-form"), err = $("#login-err"), btn = $("#login-btn");
  if (isDemoMode()) {
    $("#demo-hint").hidden = false;
    $("#demo-hint-creds").textContent = `${DEMO_USER.email} / ${DEMO_USER.password}`;
    $("#login-email").value = DEMO_USER.email; $("#login-pass").value = DEMO_USER.password;
  }
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    err.hidden = true; btn.disabled = true; btn.textContent = "Ingresando…";
    try {
      const user = await api.signIn($("#login-email").value.trim(), $("#login-pass").value);
      await entrar(user);
    } catch (ex) {
      err.textContent = ex.code === "auth" ? "Correo o contraseña incorrectos." : ex.message || "No se pudo iniciar sesión.";
      err.hidden = false;
      if (ex.code === "profile") await api.signOut();
    } finally { btn.disabled = false; btn.textContent = "Ingresar"; }
  });
}

registerActions({
  theme: () => setTheme(temaActual() === "dark" ? "light" : "dark"),
  logout: salir,
  menu: () => toggleSidebar(),
  "demo-reset": async () => {
    if (!(await confirmDialog({ title: "Restablecer datos demo", message: "Se descartarán los cambios y se regenerarán los datos de ejemplo.", confirmLabel: "Restablecer" }))) return;
    api.reset(); await loadAll(); route(); toast("Datos de demostración restablecidos", "success");
  },
});

/* ------------------------------ Arranque ------------------------------ */
async function boot() {
  let t = "light";
  try { t = localStorage.getItem("cv-theme") || (matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light"); } catch { /* ok */ }
  setTheme(t, false);
  bindActions();
  buildNav();
  $("#backdrop").addEventListener("click", () => toggleSidebar(false));
  addEventListener("hashchange", route);
  initLogin();
  $("#app-version").textContent = `v${CONFIG.APP_VERSION} · ${globalThis.AndroidBridge ? "app Android" : "web"}`;
  // PWA: instalable y con arranque sin conexión. El SW pide siempre la versión nueva primero (ver sw.js).
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch((e) => console.warn("Service worker no registrado:", e.message));
  }
  if (api.mode === "error") { const e = $("#login-err"); e.textContent = api.error.message; e.hidden = false; return; }
  try {
    const user = await api.init();
    if (user) await entrar(user);
  } catch (e) { console.error(e); const el = $("#login-err"); el.textContent = e.message; el.hidden = false; }
}
boot();
