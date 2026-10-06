// @ts-check
// Mi perfil: cada persona del personal sube su foto, corrige su nombre y cambia su contraseña.
import { api } from "../api.js";
import { DB } from "../state.js";
import { ETIQUETA_ROL, rolActual } from "../permisos.js";
import { icon, pageHead, registerActions, toast } from "../ui.js";
import { archivoACuadrado, esc, initials } from "../utils.js";

/** Pinta la foto (o las iniciales) en el avatar de la barra lateral. */
export async function pintarAvatar() {
  const el = document.getElementById("user-avatar");
  if (!el) return;
  const nombre = DB.perfil?.nombre || DB.perfil?.rol || "";
  el.style.backgroundImage = ""; el.textContent = initials(nombre);
  if (!DB.perfil?.foto_path) return;
  try {
    const url = await api.fotoPersonalUrl(DB.perfil.foto_path);
    if (url) { el.style.backgroundImage = `url("${url}")`; el.style.backgroundSize = "cover"; el.style.backgroundPosition = "center"; el.textContent = ""; }
  } catch { /* sin foto: iniciales */ }
}

/** @param {HTMLElement} root */
async function pintarFotoGrande(root) {
  const box = root.querySelector("#perfil-foto");
  if (!box) return;
  box.textContent = initials(DB.perfil?.nombre || DB.perfil?.rol || "");
  if (!DB.perfil?.foto_path) return;
  const url = await api.fotoPersonalUrl(DB.perfil.foto_path).catch(() => null);
  if (url) box.innerHTML = `<img src="${esc(url)}" alt="Mi foto de perfil">`;
}

export const perfilPage = {
  id: "perfil", title: "Mi perfil", icon: "userCheck", group: "Principal",
  /** @param {HTMLElement} root */
  async render(root) {
    const p = /** @type {any} */ (DB.perfil || {});
    root.innerHTML = `${pageHead("Mi perfil", "Tu foto, tu nombre y tu contraseña.")}
      <div class="grid-2">
        <section class="card center-col">
          <div class="perfil-foto" id="perfil-foto" aria-label="Foto de perfil"></div>
          <div class="btn-row">
            <button class="btn btn-primary" data-action="perfil-foto">${icon("camera", 16)} Subir foto</button>
          </div>
          <input type="file" id="perfil-file" accept="image/*" hidden>
          <p class="muted" style="text-align:center;margin:10px 0 0">${esc(ETIQUETA_ROL[rolActual()])}${p.carrera ? " de " + esc(p.carrera) : ""}${p.colegio ? " · " + esc(p.colegio) : ""}</p>
        </section>
        <section class="card">
          <h3 style="margin-top:0">Datos</h3>
          <form id="perfil-nombre" class="inline-form"><label class="sr-only" for="perfil-n">Nombre</label>
            <input class="input" id="perfil-n" value="${esc(p.nombre || "")}" maxlength="80" placeholder="Tu nombre completo" autocomplete="name">
            <button class="btn btn-outline" type="submit">Guardar nombre</button></form>
          <h3 style="margin:22px 0 8px">Cambiar contraseña</h3>
          <form id="perfil-pass" class="stack" autocomplete="off">
            <input class="input" id="perfil-p1" type="password" placeholder="Nueva contraseña (mínimo 8 caracteres)" aria-label="Nueva contraseña" minlength="8" autocomplete="new-password">
            <input class="input" id="perfil-p2" type="password" placeholder="Repite la nueva contraseña" aria-label="Repite la nueva contraseña" minlength="8" autocomplete="new-password">
            <button class="btn btn-outline" type="submit">Cambiar contraseña</button>
          </form>
        </section>
      </div>`;
    pintarFotoGrande(root);

    /** @type {HTMLInputElement} */ (root.querySelector("#perfil-file")).addEventListener("change", async (e) => {
      const inp = /** @type {HTMLInputElement} */ (e.target);
      const f = inp.files?.[0]; inp.value = "";
      if (!f) return;
      try {
        const blob = await archivoACuadrado(f);
        /** @type {any} */ (DB.perfil).foto_path = await api.subirFotoPerfil(/** @type {string} */ (DB.userId), /** @type {Blob} */ (blob));
        await pintarFotoGrande(root); pintarAvatar();
        toast("Foto actualizada", "success");
      } catch (/** @type {any} */ ex) { toast("No se pudo subir la foto: " + ex.message, "error"); }
    });
    /** @type {HTMLElement} */ (root.querySelector("#perfil-nombre")).addEventListener("submit", async (e) => {
      e.preventDefault();
      const n = /** @type {HTMLInputElement} */ (root.querySelector("#perfil-n")).value.trim();
      if (!n) return;
      try {
        await api.actualizarMiPerfil(n);
        const perfil = /** @type {any} */ (DB.perfil);
        perfil.nombre = n;
        /** @type {HTMLElement} */ (document.getElementById("user-name")).textContent = n;
        if (!perfil.foto_path) pintarAvatar();
        toast("Nombre guardado", "success");
      } catch (/** @type {any} */ ex) { toast(ex.message, "error"); }
    });
    /** @type {HTMLFormElement} */ (root.querySelector("#perfil-pass")).addEventListener("submit", async (e) => {
      e.preventDefault();
      const a = /** @type {HTMLInputElement} */ (root.querySelector("#perfil-p1")).value, b = /** @type {HTMLInputElement} */ (root.querySelector("#perfil-p2")).value;
      if (a.length < 8) return toast("La contraseña debe tener al menos 8 caracteres.", "error");
      if (a !== b) return toast("Las contraseñas no coinciden.", "error");
      try { await api.cambiarPassword(a); /** @type {HTMLFormElement} */ (e.target).reset(); toast("Contraseña cambiada", "success"); }
      catch (/** @type {any} */ ex) { toast(ex.message, "error"); }
    });
  },
};

registerActions({
  "perfil-foto": () => document.getElementById("perfil-file")?.click(),
});
