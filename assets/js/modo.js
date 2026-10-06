// @ts-check
// Selector de modo del programa de escritorio (.exe): ONLINE (Supabase, con usuario y contraseña) o LOCAL (base de datos
// propia en este equipo, sin internet). Cada modo guarda sus datos aparte y NUNCA se borran al cambiar.
import { esEscritorio, modoActual } from "./config.js";
import { confirmDialog, registerActions } from "./ui.js";

/** @type {Record<string, { titulo: string, msg: string, ok: string }>} */
const TEXTO = {
  local: {
    titulo: "Cambiar a modo LOCAL",
    msg: "Trabajarás sin internet con la base de datos de este equipo (vacía la primera vez). Tus datos del modo online no se tocan ni se borran. Los registros que hagas en modo local se quedan solo en este equipo.",
    ok: "Cambiar a modo local",
  },
  online: {
    titulo: "Cambiar a modo ONLINE",
    msg: "Volverás a la base de datos en línea (Supabase) y tendrás que iniciar sesión con tu correo y contraseña. Los datos que ingresaste en modo local se conservan en este equipo y estarán ahí cuando vuelvas.",
    ok: "Cambiar a modo online",
  },
};

/**
 * @param {string} destino
 * @returns {Promise<boolean>}
 */
export async function cambiarModo(destino) {
  if (!esEscritorio() || destino === modoActual()) return false;
  const t = TEXTO[destino];
  if (!t) return false;
  if (!(await confirmDialog({ title: t.titulo, message: t.msg, confirmLabel: t.ok }))) return false;
  try { localStorage.setItem("ra-modo", destino); } catch { return false; }
  location.reload();
  return true;
}

/** Segmentado "Online | Local" (login) y botón del menú lateral. No hace nada fuera del programa de escritorio. */
export function iniciarSelectorModo() {
  if (!esEscritorio()) return;
  const actual = modoActual();
  const card = document.querySelector("#login-form");
  if (card && !document.getElementById("modo-switch")) {
    const sw = document.createElement("div");
    sw.id = "modo-switch"; sw.className = "modo-switch"; sw.setAttribute("role", "group"); sw.setAttribute("aria-label", "Modo de trabajo");
    sw.innerHTML = ["online", "local"].map((m) =>
      `<button type="button" data-action="modo" data-modo="${m}" aria-pressed="${m === actual}" class="${m === actual ? "on" : ""}">${m === "online" ? "Modo online" : "Modo local"}</button>`).join("");
    const loginTitle = card.querySelector(".login-title");
    if (loginTitle) card.insertBefore(sw, loginTitle);
    const nota = document.createElement("p");
    nota.className = "modo-nota";
    nota.textContent = actual === "local"
      ? "Modo local: sin internet, con la base de datos de este equipo. No necesita cuenta."
      : "Modo online: con tu usuario y contraseña, en la base de datos en línea.";
    sw.after(nota);
  }
  if (actual === "local") {
    const t = document.querySelector(".login-title"); if (t) t.textContent = "Modo local";
    ["#login-email", "#login-pass"].forEach((s) => {
      const el = /** @type {HTMLInputElement | null} */ (document.querySelector(s));
      if (el) {
        el.required = false;
        const f = /** @type {HTMLElement | null} */ (el.closest(".field"));
        if (f) f.hidden = true;
      }
    });
    const b = document.querySelector("#login-btn"); if (b) b.textContent = "Entrar";
  }
  const foot = document.querySelector(".side-foot");
  if (foot && !document.getElementById("modo-side")) {
    const b = document.createElement("button");
    b.id = "modo-side"; b.type = "button"; b.className = "btn btn-ghost-light btn-sm btn-block";
    b.dataset.action = "modo"; b.dataset.modo = actual === "local" ? "online" : "local";
    b.textContent = actual === "local" ? "Modo local · Cambiar a online" : "Modo online · Cambiar a local";
    foot.insertBefore(b, foot.firstChild);
  }
  const badge = document.getElementById("mode-badge");
  if (badge && actual === "local") { badge.textContent = "Modo local"; badge.hidden = false; }
}

registerActions({
  modo: (/** @type {HTMLElement} */ el) => cambiarModo(el.dataset.modo || ""),
});
