// Punto de entrada: autenticación, navegación (router por hash), tema y arranque.
import { api } from "./api.js";
import { CONFIG, DEMO_USER, isDemoMode } from "./config.js";
import { borrarPerfil, guardarPerfil, leerPerfil } from "./cola.js";
import { DB, alCargarDatos, loadAll, refreshHoy } from "./state.js";
import { alNecesitarSesion, alSincronizar, estadoSync, iniciarSync, onEstado, red, sincronizar, sincronizarRelojServidor } from "./sync.js";
import { bindActions, confirmDialog, emptyState, icon, registerActions, setAutorizador, toast } from "./ui.js";
import { ETIQUETA_ROL, aplicarPermisos, esAdmin, instalarEstiloPermisos, puede, rolActual } from "./permisos.js";
import { enviarPendientes, iniciarLogErrores } from "./errlog.js";
import { iniciarSelectorModo } from "./modo.js";
import { activarAvisos, desactivarAvisos } from "./notificaciones.js";
import { personalPage } from "./pages/personal.js";
import { perfilPage, pintarAvatar } from "./pages/perfil.js";
import { calendarioPage } from "./pages/calendario.js";
import { periodosPage } from "./pages/periodos.js";
import { alertasPage } from "./pages/alertas.js";
import { historialPage } from "./pages/historial.js";
import { migrarPage } from "./pages/migrar.js";
import { quioscoPage, reanudarQuiosco } from "./pages/quiosco.js";
import { pendientes, solicitudesPage } from "./pages/solicitudes.js";
import { offlinePage } from "./pages/offline.js";
import { esErrorRed, esc, fmtDate, initials, todayStr } from "./utils.js";
import { dashboardPage } from "./pages/dashboard.js";
import { registroAlumnoPage, registroMasivoPage, registroQrPage } from "./pages/registro.js";
import { asistAlumnoPage, asistGradoPage } from "./pages/consultas.js";
import { carnetPage } from "./pages/carnet.js";
import { codigoPage } from "./pages/codigo.js";
import { avisosPage } from "./pages/avisos.js";
import { justificacionesPage, reportePage } from "./pages/reportes.js";
import { asistCursoPage, cursosPage } from "./pages/cursos.js";
import { diagnosticoPage, erroresPage, respaldoPage } from "./pages/sistema.js";
import { alumnosPage, comunicadosPage, docentesPage, gradosPage, nivelesPage } from "./pages/mantenimiento.js";

const PAGES = [
  dashboardPage, perfilPage,
  registroQrPage, quioscoPage, solicitudesPage, registroAlumnoPage, registroMasivoPage,
  asistGradoPage, asistAlumnoPage, asistCursoPage, reportePage, alertasPage, avisosPage,
  carnetPage, codigoPage, alumnosPage, docentesPage, personalPage, nivelesPage, gradosPage, cursosPage, calendarioPage, periodosPage, justificacionesPage, comunicadosPage,
  diagnosticoPage, respaldoPage, offlinePage, historialPage, migrarPage, erroresPage,
];
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

/* ------------------- Aviso de solicitudes de ingreso (administrador) ------------------- */
let solPrevias = null;
function pintarSolicitudes() {
  const chip = $("#sol-chip");
  const n = esAdmin() ? pendientes().length : 0;
  if (chip) {
    chip.hidden = n === 0;
    chip.innerHTML = `<span aria-hidden="true">🔔</span> <b>${n}</b><span class="sol-txt"> solicitud${n === 1 ? "" : "es"} de ingreso</span>`;
    chip.setAttribute("aria-label", `${n} solicitud${n === 1 ? "" : "es"} de ingreso esperando aprobación`);
    chip.title = "Estudiantes esperando aprobación";
  }
  document.querySelectorAll('#nav a[data-page="solicitudes"]').forEach((a) => {
    a.querySelector(".nav-badge")?.remove();
    if (n) a.insertAdjacentHTML("beforeend", `<span class="nav-badge">${n}</span>`);
  });
  if (solPrevias !== null && n > solPrevias) toast(`Nueva solicitud de ingreso (${n} pendiente${n === 1 ? "" : "s"})`, "info");
  solPrevias = n;
}
alCargarDatos(pintarSolicitudes);
// El administrador ve las solicitudes nuevas sin recargar: se revisa cada 60 s mientras la pestaña está visible.
setInterval(() => { if (logged && esAdmin() && !document.hidden && api.mode !== "local") loadAll().catch(() => {}); }, 60000);

/* ---------------------------- Navegación ---------------------------- */
const visibles = () => PAGES.filter((p) => !p.soloAdmin || esAdmin());
const CHEVRON = '<svg class="icon chev" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6"/></svg>';
const gruposCerrados = () => { try { return JSON.parse(localStorage.getItem("ra-nav-cerrados")) || []; } catch { return []; } };
const guardarCerrados = (l) => { try { localStorage.setItem("ra-nav-cerrados", JSON.stringify(l)); } catch { /* sin almacenamiento */ } };
const slug = (t) => String(t).toLowerCase().normalize("NFD").replace(/[^a-z0-9]+/g, "-");

/** Menú por grupos plegables (el estado se recuerda). El grupo de la página abierta siempre se muestra. */
function buildNav() {
  const grupos = [...new Set(visibles().map((p) => p.group))], cerrados = gruposCerrados();
  $("#nav").innerHTML = grupos.map((g) => {
    const abierto = !cerrados.includes(g), id = `ng-${slug(g)}`;
    return `<div class="nav-group${abierto ? "" : " cerrado"}" data-grupo="${esc(g)}"><button type="button" class="nav-group-title" aria-expanded="${abierto}" aria-controls="${id}" data-action="nav-grupo">${esc(g)}${CHEVRON}</button>
      <div class="nav-items" id="${id}">${visibles().filter((p) => p.group === g).map((p) => `<a class="nav-item" href="#/${p.id}" data-page="${p.id}">${icon(p.icon)}<span>${esc(p.title)}</span></a>`).join("")}</div></div>`;
  }).join("");
}
function abrirGrupoDe(item) {
  const g = item?.closest(".nav-group");
  if (!g || !g.classList.contains("cerrado")) return;
  g.classList.remove("cerrado"); g.querySelector(".nav-group-title")?.setAttribute("aria-expanded", "true");
}

function parseHash() {
  const [path, qs] = location.hash.replace(/^#\/?/, "").split("?");
  return { id: path || "dashboard", params: Object.fromEntries(new URLSearchParams(qs || "")) };
}

async function route() {
  if (!logged) return;
  const { id, params } = parseHash();
  let page = PAGES.find((p) => p.id === id) || dashboardPage;
  if (page.soloAdmin && !esAdmin()) { toast("Esa sección es solo para el administrador.", "error"); page = dashboardPage; history.replaceState(null, "", "#/dashboard"); }
  if (actual && actual !== page) await actual.onLeave?.();
  actual = page;
  document.querySelectorAll(".nav-item").forEach((n) => { const on = n.dataset.page === page.id; n.classList.toggle("active", on); if (on) { n.setAttribute("aria-current", "page"); abrirGrupoDe(n); } else n.removeAttribute("aria-current"); });
  $("#topbar-group").textContent = page.group || "Panel";
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
/**
 * Entra a la aplicación. `guardado` (perfil de la última sesión) permite abrir SIN red y seguir registrando asistencia:
 * se trabaja con la copia local de los datos y los registros quedan en cola hasta recuperar la conexión.
 */
async function entrar(user, guardado = null) {
  let perfil;
  if (guardado) { perfil = guardado.perfil; DB.sesionOffline = true; }
  else { perfil = await api.getProfile(user); guardarPerfil(user, perfil); DB.sesionOffline = false; }
  DB.cid = perfil.colegio_id; DB.rol = perfil.rol; DB.perfil = perfil; DB.userId = user.id; DB.userEmail = user.email || "";
  await Promise.race([sincronizarRelojServidor(), new Promise((r) => setTimeout(r, 3000))]); // hora confiable antes de registrar
  await loadAll();
  logged = true;
  $("#login-screen").hidden = true; $("#app").hidden = false;
  $("#user-name").textContent = perfil.nombre || perfil.rol;
  aplicarPermisos();
  buildNav();
  pintarSolicitudes();
  $("#user-role").textContent = `${ETIQUETA_ROL[rolActual()]}${perfil.carrera ? " de " + perfil.carrera : ""}${perfil.colegio ? " · " + perfil.colegio : ""}`;
  enviarPendientes();
  activarAvisos(api);
  reanudarQuiosco();
  pintarAvatar();
  $("#topbar-date").textContent = fmtDate(todayStr(), { weekday: "short", day: "2-digit", month: "short" });
  $("#mode-badge").hidden = api.mode !== "demo" && api.mode !== "local";
  $("#mode-badge").textContent = api.mode === "local" ? "Modo local" : "Modo demo";
  $("#demo-reset").hidden = api.mode !== "demo";
  pintarChip(estadoSync());
  sincronizar();
  if (!location.hash) location.hash = "#/dashboard"; else route();
}

/* ---------------------- Indicador de red y envíos ---------------------- */
let chipTimer = null, tuvoPendientes = false;
function pintarChip(s) {
  const c = $("#net-chip"); if (!c) return;
  const dot = '<span class="dot"></span>';
  let clase = "", html = "";
  if (!s.online) { clase = "off"; html = `${dot} Sin conexión${s.pendientes ? ` · ${s.pendientes} sin enviar` : ""}`; }
  else if (s.enCurso) { clase = "sync"; html = `${dot} Sincronizando…`; }
  else if (s.error === "sesion" && s.pendientes) { clase = "pend"; html = `${dot} Inicia sesión para enviar ${s.pendientes}`; }
  else if (s.pendientes) { clase = "pend"; html = `${dot} ${s.pendientes} sin enviar · tocar`; }
  else if (s.rechazados) { clase = "off"; html = `${dot} ${s.rechazados} no se pudieron enviar`; }
  else if (tuvoPendientes) { clase = "ok"; html = `${dot} Todo enviado`; clearTimeout(chipTimer); chipTimer = setTimeout(() => { c.hidden = true; }, 3500); }
  tuvoPendientes = s.pendientes > 0;
  c.className = `chip net-chip ${clase}`; c.innerHTML = html; c.hidden = !html;
}

async function salir() {
  await actual?.onLeave?.(); actual = null; logged = false;
  desactivarAvisos();
  borrarPerfil();  // otra persona en este teléfono no debe entrar con el perfil guardado de la anterior (la cola de envíos se conserva)
  await api.signOut();
  $("#app").hidden = true; $("#login-screen").hidden = false;
  $("#login-pass").value = "";
  history.replaceState(null, "", location.pathname);
}

function initLogin() {
  const form = $("#login-form"), err = $("#login-err"), btn = $("#login-btn");
  $("#pw-toggle")?.addEventListener("click", (e) => {
    const inp = $("#login-pass"), ver = inp.type === "password";
    inp.type = ver ? "text" : "password";
    e.currentTarget.setAttribute("aria-pressed", String(ver)); e.currentTarget.setAttribute("aria-label", ver ? "Ocultar contraseña" : "Mostrar contraseña");
  });
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
  "nav-grupo": (el) => {
    const g = el.closest(".nav-group"), cerrado = g.classList.toggle("cerrado");
    el.setAttribute("aria-expanded", String(!cerrado));
    const l = gruposCerrados().filter((x) => x !== g.dataset.grupo); if (cerrado) l.push(g.dataset.grupo); guardarCerrados(l);
  },
  theme: () => setTheme(temaActual() === "dark" ? "light" : "dark"),
  logout: salir,
  menu: () => toggleSidebar(),
  "sync-now": () => { if (estadoSync().error === "sesion") return salir(); sincronizar(); },
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
  instalarEstiloPermisos();
  setAutorizador(puede);
  iniciarLogErrores("docente", api);
  bindActions();
  $("#backdrop").addEventListener("click", () => toggleSidebar(false));
  addEventListener("hashchange", route);
  initLogin();
  iniciarSelectorModo();
  $("#app-version").textContent = `v${CONFIG.APP_VERSION} · ${globalThis.AndroidBridge ? "app Android" : globalThis.escritorio ? "programa PC" : "web"}`;
  // PWA: instalable y con arranque sin conexión. El SW pide siempre la versión nueva primero (ver sw.js).
  if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
    navigator.serviceWorker.register("sw.js").catch((e) => console.warn("Service worker no registrado:", e.message));
  }
  if (api.mode === "error") { const e = $("#login-err"); e.textContent = api.error.message; e.hidden = false; return; }

  // Envíos pendientes: refrescar datos al terminar y recuperar la sesión si se abrió sin red.
  alSincronizar(async () => { await refreshHoy(); if (actual?.id === "dashboard" || actual?.id === "asist-grado") route(); });
  alNecesitarSesion(async () => {
    if (!DB.sesionOffline) return true;
    try { const u = await api.init(); if (u) { DB.sesionOffline = false; DB.userId = u.id; DB.sinConexion = false; return true; } } catch { /* sigue sin sesión */ }
    return false;
  });
  onEstado(pintarChip);
  iniciarSync();

  let user = null;
  try { user = await api.init(); } catch (e) { if (!esErrorRed(e)) console.error(e); }
  const guardado = leerPerfil();
  try {
    if (user) await entrar(user, null);
    else if (guardado && (!red.online() || api.mode === "supabase")) {
      // Sin sesión válida pero con un perfil guardado en este teléfono: si no hay red, se trabaja sin conexión.
      if (!red.online()) await entrar(guardado.user, guardado);
    }
  } catch (e) {
    if ((esErrorRed(e) || !red.online()) && guardado) { try { await entrar(guardado.user, guardado); return; } catch (e2) { console.error(e2); } }
    console.error(e); const el = $("#login-err"); el.textContent = e.message; el.hidden = false;
  }
}
boot();
