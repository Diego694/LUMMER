// @ts-check
// Componentes de interfaz reutilizables: iconos, toasts, modales, formularios, tarjetas.
import { esc } from "./utils.js";

/** @typedef {import('./tipos.d.ts').OpenModalOptions} OpenModalOptions */
/** @typedef {import('./tipos.d.ts').ModalInstance} ModalInstance */
/** @typedef {import('./tipos.d.ts').ConfirmDialogOptions} ConfirmDialogOptions */
/** @typedef {import('./tipos.d.ts').FormFieldOption} FormFieldOption */
/** @typedef {import('./tipos.d.ts').FormFieldControl} FormFieldControl */
/** @typedef {import('./tipos.d.ts').FormField} FormField */
/** @typedef {import('./tipos.d.ts').FormModalOptions} FormModalOptions */
/** @typedef {import('./tipos.d.ts').FormModalInstance} FormModalInstance */
/** @typedef {import('./tipos.d.ts').KpiOptions} KpiOptions */
/** @typedef {import('./tipos.d.ts').ActionHandler} ActionHandler */

/** @type {Record<string, string>} */
const P = {
  dashboard: '<rect x="3" y="3" width="7" height="9" rx="1"/><rect x="14" y="3" width="7" height="5" rx="1"/><rect x="14" y="12" width="7" height="9" rx="1"/><rect x="3" y="16" width="7" height="5" rx="1"/>',
  qr: '<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3h-3zM20 14v.01M14 20h3M20 17v4"/>',
  userCheck: '<circle cx="9" cy="8" r="4"/><path d="M2 21v-1a6 6 0 0 1 6-6h2"/><path d="m16 19 2 2 4-4"/>',
  userX: '<circle cx="9" cy="8" r="4"/><path d="M2 21v-1a6 6 0 0 1 6-6h2M17 15l5 5M22 15l-5 5"/>',
  users: '<circle cx="9" cy="8" r="4"/><path d="M2 21v-1a6 6 0 0 1 6-6h2a6 6 0 0 1 6 6v1"/><path d="M17 4a4 4 0 0 1 0 8M22 21v-1a5 5 0 0 0-3-4.6"/>',
  listCheck: '<path d="M11 6h10M11 12h10M11 18h10M3 6l1.5 1.5L7 5M3 12l1.5 1.5L7 11M3 18l1.5 1.5L7 17"/>',
  table: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 10h18M9 4v16"/>',
  idCard: '<rect x="2" y="5" width="20" height="14" rx="2"/><circle cx="8" cy="12" r="2"/><path d="M13 10h5M13 14h4M5 17c.5-1.5 5.5-1.5 6 0"/>',
  layers: '<path d="m12 3 9 5-9 5-9-5z"/><path d="m3 13 9 5 9-5"/>',
  book: '<path d="M4 4h12a3 3 0 0 1 3 3v13H7a3 3 0 0 1-3-3z"/><path d="M4 17a3 3 0 0 1 3-3h12"/>',
  cap: '<path d="m2 9 10-5 10 5-10 5z"/><path d="M6 11v5c0 1.5 3 3 6 3s6-1.5 6-3v-5"/>',
  briefcase: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2M3 13h18"/>',
  megaphone: '<path d="M3 11v3a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15 9a4 4 0 0 1 0 6M18 6a8 8 0 0 1 0 12"/>',
  sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
  moon: '<path d="M20.5 14.5A8.5 8.5 0 0 1 9.5 3.5a8.5 8.5 0 1 0 11 11z"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m21 21-4.3-4.3"/>',
  download: '<path d="M12 3v12M7 10l5 5 5-5M4 21h16"/>',
  upload: '<path d="M12 15V3M7 8l5-5 5 5M4 21h16"/>',
  edit: '<path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  trash: '<path d="M3 6h18M8 6V4h8v2M6 6l1 14h10l1-14M10 11v6M14 11v6"/>',
  camera: '<path d="M3 8a2 2 0 0 1 2-2h2l2-2h6l2 2h2a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><circle cx="12" cy="13" r="4"/>',
  check: '<path d="m5 12 5 5L20 7"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  alert: '<path d="M12 3 2 20h20z"/><path d="M12 10v4M12 17v.01"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 10h18M8 3v4M16 3v4"/>',
  flip: '<path d="M3 12a9 9 0 0 1 15-6.7L21 8M21 3v5h-5M21 12a9 9 0 0 1-15 6.7L3 16M3 21v-5h5"/>',
  nfc: '<path d="M6 8.3a8 8 0 0 1 0 7.4M10 6a12 12 0 0 1 0 12M14 4a16 16 0 0 1 0 16M18 3a20 20 0 0 1 0 18"/>',
  trend: '<path d="m3 17 6-6 4 4 8-8M15 7h6v6"/>',
  percent: '<path d="M19 5 5 19"/><circle cx="7" cy="7" r="2.5"/><circle cx="17" cy="17" r="2.5"/>',
  history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8v.01"/>',
};

/**
 * @param {string} name
 * @param {number} [size]
 * @returns {string}
 */
export function icon(name, size = 18) {
  return `<svg class="icon" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${P[name] || ""}</svg>`;
}

/* ------------------------------ Toast ------------------------------ */
/** @type {ReturnType<typeof setTimeout> | undefined} */
let toastTimer;
/**
 * @param {string} msg
 * @param {'info' | 'success' | 'error' | string} [type]
 * @returns {void}
 */
export function toast(msg, type = "info") {
  const el = document.getElementById("toast");
  if (!el) return;
  el.className = `toast show toast-${type}`;
  el.innerHTML = `${icon(type === "error" ? "alert" : type === "success" ? "check" : "info", 16)}<span>${esc(msg)}</span>`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("show"), 3200);
}

/* ------------------------------ Modales ------------------------------ */
const FOCUSABLE = 'button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[href],[tabindex]:not([tabindex="-1"])';

/**
 * @param {OpenModalOptions} options
 * @returns {ModalInstance}
 */
export function openModal({ title, body, footer = "", wide = false }) {
  const previo = /** @type {HTMLElement | null} */ (document.activeElement);
  const overlay = document.createElement("div");
  overlay.className = "modal-overlay open";
  overlay.innerHTML = `
    <div class="modal ${wide ? "modal-wide" : ""}" role="dialog" aria-modal="true" aria-label="${esc(title)}">
      <div class="modal-head"><h3>${esc(title)}</h3><button class="icon-only" data-close aria-label="Cerrar">${icon("x")}</button></div>
      <div class="modal-body">${body}</div>
      ${footer ? `<div class="modal-foot">${footer}</div>` : ""}
    </div>`;
  document.body.appendChild(overlay);
  const close = () => { overlay.remove(); document.removeEventListener("keydown", onKey); previo?.focus?.(); };
  /** @param {KeyboardEvent} e */
  const onKey = (e) => {
    if (e.key === "Escape") close();
    if (e.key === "Tab") {
      const f = /** @type {HTMLElement[]} */ ([...overlay.querySelectorAll(FOCUSABLE)]);
      if (!f.length) return;
      const first = f[0], last = f[f.length - 1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    }
  };
  document.addEventListener("keydown", onKey);
  overlay.addEventListener("mousedown", (e) => { if (e.target === overlay) close(); });
  overlay.querySelector("[data-close]")?.addEventListener("click", close);
  const initialFocus = /** @type {HTMLElement | null} */ (overlay.querySelector("input:not([type=hidden]),select,textarea") || overlay.querySelector(".modal"));
  initialFocus?.focus();
  return { el: overlay, close };
}

/**
 * @param {ConfirmDialogOptions} options
 * @returns {Promise<boolean>}
 */
export function confirmDialog({ title = "Confirmar", message, confirmLabel = "Eliminar", danger = true }) {
  return new Promise((resolve) => {
    const icName = danger ? "alert" : "info";
    const icTone = danger ? "confirm-ic-danger" : "confirm-ic-info";
    const m = openModal({
      title,
      body: `<div class="confirm-layout"><div class="confirm-ic ${icTone}">${icon(icName, 22)}</div><div class="confirm-content"><p class="confirm-msg">${message}</p></div></div>`,
      footer: `<button class="btn btn-outline" data-no>Cancelar</button><button class="btn ${danger ? "btn-danger-solid" : "btn-primary"}" data-yes>${esc(confirmLabel)}</button>`,
    });
    let done = false;
    /** @param {boolean} v */
    const fin = (v) => { if (!done) { done = true; m.close(); resolve(v); } };
    m.el.querySelector("[data-yes]")?.addEventListener("click", () => fin(true));
    m.el.querySelector("[data-no]")?.addEventListener("click", () => fin(false));
    m.el.querySelector("[data-close]")?.addEventListener("click", () => fin(false));
    m.el.addEventListener("mousedown", (e) => { if (e.target === m.el) fin(false); });
    /** @type {HTMLElement | null} */ (m.el.querySelector("[data-yes]"))?.focus();
  });
}

/**
 * Modal con formulario declarativo.
 * fields: [{name,label,type:'text'|'select'|'textarea'|'pills',options:[{value,label}],value,required,placeholder,half,onChange}]
 * onSubmit(values) puede lanzar Error → se muestra dentro del formulario y el modal sigue abierto.
 * @param {FormModalOptions} options
 * @returns {FormModalInstance}
 */
export function formModal({ title, fields, submitLabel = "Guardar", onSubmit }) {
  /**
   * @param {FormField} f
   * @returns {string}
   */
  const renderField = (f) => {
    const id = `f-${f.name}`;
    const v = f.value ?? "";
    let control;
    if (f.type === "select") {
      // Si el valor actual ya no existe en el catálogo (p. ej. grado importado por CSV), se conserva como opción
      // para que guardar no lo cambie en silencio.
      const opts = v && !(f.options || []).some((o) => o.value === v) ? [{ value: v, label: `${v} (no existe en el catálogo)` }, ...(f.options || [])] : f.options || [];
      control = `<select id="${id}" name="${f.name}" ${f.required ? "required" : ""}>${opts.map((o) => `<option value="${esc(o.value)}" ${o.value === v ? "selected" : ""}>${esc(o.label)}</option>`).join("")}</select>`;
    } else if (f.type === "textarea") {
      control = `<textarea id="${id}" name="${f.name}" rows="4" placeholder="${esc(f.placeholder || "")}" ${f.required ? "required" : ""}>${esc(v)}</textarea>`;
    } else if (f.type === "pills") {
      control = `<div class="pill-select" role="radiogroup" data-pills="${f.name}">${(f.options || []).map((o) => `<button type="button" role="radio" aria-checked="${o.value === v}" class="pill ${o.value === v ? "active" : ""}" data-val="${esc(o.value)}">${esc(o.label)}</button>`).join("")}</div><input type="hidden" name="${f.name}" value="${esc(v)}">`;
    } else {
      control = `<input id="${id}" name="${f.name}" type="${f.type === "date" ? "date" : f.type === "password" ? "password" : "text"}" value="${esc(v)}" ${f.max ? `max="${esc(f.max)}"` : ""} placeholder="${esc(f.placeholder || "")}" ${f.required ? "required" : ""} autocomplete="${f.type === "password" ? "new-password" : "off"}">`;
    }
    return `<div class="field ${f.half ? "half" : ""}"><label for="${id}">${esc(f.label)}${f.required ? ' <span class="req">*</span>' : ""}</label>${control}</div>`;
  };

  const m = openModal({
    title,
    body: `<form id="modal-form" novalidate><div class="form-grid">${fields.map(renderField).join("")}</div><p class="err-msg" id="form-err" role="alert" hidden></p></form>`,
    footer: `<button class="btn btn-outline" data-close2>Cancelar</button><button class="btn btn-primary" id="form-submit">${esc(submitLabel)}</button>`,
  });
  const form = /** @type {HTMLFormElement | null} */ (m.el.querySelector("#modal-form"));
  const errEl = /** @type {HTMLElement | null} */ (m.el.querySelector("#form-err"));
  /** @param {string} t */
  const setErr = (t) => { if (errEl) { errEl.textContent = t || ""; errEl.hidden = !t; } };

  m.el.querySelectorAll("[data-pills]").forEach((group) => {
    group.addEventListener("click", (e) => {
      const target = /** @type {HTMLElement | null} */ (e.target instanceof Element ? e.target : null);
      const b = /** @type {HTMLElement | null} */ (target?.closest(".pill") ?? null);
      if (!b) return;
      group.querySelectorAll(".pill").forEach((p) => { p.classList.toggle("active", p === b); p.setAttribute("aria-checked", String(p === b)); });
      const pillName = /** @type {HTMLElement} */ (group).dataset.pills;
      if (form && pillName) {
        const input = /** @type {HTMLInputElement | undefined} */ (/** @type {any} */ (form.elements)[pillName]);
        if (input && b.dataset.val !== undefined) input.value = b.dataset.val;
      }
    });
  });

  /** @type {FormFieldControl} */
  const control = {
    setOptions(name, options, selected) {
      if (!form) return;
      const sel = /** @type {HTMLSelectElement | undefined} */ (/** @type {any} */ (form.elements)[name]);
      if (!sel) return;
      sel.innerHTML = options.map((o) => `<option value="${esc(o.value)}">${esc(o.label)}</option>`).join("");
      if (selected !== undefined) sel.value = selected;
    },
  };
  fields.forEach((f) => {
    if (f.onChange && form) {
      const fieldEl = /** @type {HTMLElement | undefined} */ (/** @type {any} */ (form.elements)[f.name]);
      fieldEl?.addEventListener("change", (e) => {
        const val = /** @type {HTMLInputElement | HTMLSelectElement} */ (e.target).value;
        f.onChange?.(val, control);
      });
    }
  });

  const values = () => Object.fromEntries(fields.map((f) => {
    const el = form ? /** @type {any} */ (form.elements)[f.name] : null;
    return [f.name, (el?.value || "").trim()];
  }));
  const submit = async () => {
    setErr("");
    const vals = values();
    const faltante = fields.find((f) => f.required && !vals[f.name]);
    if (faltante) {
      setErr(`Completa el campo "${faltante.label}".`);
      if (form) /** @type {any} */ (form.elements)[faltante.name]?.focus?.();
      return;
    }
    const btn = /** @type {HTMLButtonElement | null} */ (m.el.querySelector("#form-submit"));
    if (btn) btn.disabled = true;
    try { await onSubmit(vals); m.close(); }
    catch (e) {
      setErr(/** @type {Error} */ (e).message || "No se pudo guardar.");
      if (btn) btn.disabled = false;
    }
  };
  m.el.querySelector("#form-submit")?.addEventListener("click", submit);
  m.el.querySelector("[data-close2]")?.addEventListener("click", m.close);
  form?.addEventListener("submit", (e) => { e.preventDefault(); submit(); });
  return { ...m, control };
}

/* --------------------------- Piezas de UI --------------------------- */
/**
 * @param {string} title
 * @param {string} sub
 * @param {string} [ic]
 * @returns {string}
 */
export function emptyState(title, sub, ic = "info") {
  return `<div class="empty-state"><div class="empty-ic">${icon(ic, 26)}</div><h3>${esc(title)}</h3><p>${esc(sub)}</p></div>`;
}
/**
 * @param {KpiOptions} options
 * @returns {string}
 */
export function kpi({ label, value, hint = "", ic, tone = "navy", delta = null }) {
  let deltaHtml = "";
  if (delta !== null && delta !== undefined) {
    const isObj = typeof delta === "object" && delta !== null;
    const num = isObj ? delta.val : Number(delta);
    if (!isNaN(num)) {
      const isUp = num >= 0;
      const arrow = isUp ? "▲" : "▼";
      const txt = isObj ? (delta.text || `${Math.abs(num)} pts`) : `${Math.abs(num)} pts`;
      deltaHtml = `<span class="kpi-delta ${isUp ? "kpi-delta-up" : "kpi-delta-down"}"><span aria-hidden="true">${arrow}</span> ${esc(String(txt))}</span>`;
    }
  }
  return `<div class="kpi kpi-${tone}"><div class="kpi-ic">${icon(ic, 20)}</div><div class="kpi-body"><span class="kpi-label">${esc(label)}</span><span class="kpi-value">${value}</span>${deltaHtml}${hint ? `<span class="kpi-hint">${hint}</span>` : ""}</div></div>`;
}
/**
 * @param {string} text
 * @param {string} [tone]
 * @returns {string}
 */
export function badge(text, tone = "neutral") { return `<span class="badge badge-${tone}">${esc(text)}</span>`; }
/**
 * @param {number} [lines]
 * @returns {string}
 */
export function skeleton(lines = 3) { return `<div class="skeleton-wrap">${Array.from({ length: lines }, () => '<div class="skeleton"></div>').join("")}</div>`; }
/**
 * @param {string} title
 * @param {string} [subtitle]
 * @param {string} [actions]
 * @returns {string}
 */
export function pageHead(title, subtitle = "", actions = "") {
  return `<div class="page-head"><div><h1>${esc(title)}</h1>${subtitle ? `<p class="page-sub">${subtitle}</p>` : ""}</div><div class="actions">${actions}</div></div>`;
}

/** Delegación de eventos: <button data-action="nombre" data-id="..."> → handlers.nombre(el, event). */
/** @type {Record<string, ActionHandler>} */
const handlers = {};
/** @type {((accion: string) => boolean) | null} */
let autorizador = null;   // (accion) => boolean; lo fija la app según el rol (defensa en profundidad: la base de datos es el control real)
/**
 * @param {Record<string, ActionHandler>} map
 * @returns {void}
 */
export function registerActions(map) { Object.assign(handlers, map); }
/**
 * @param {((accion: string) => boolean) | null} fn
 * @returns {void}
 */
export function setAutorizador(fn) { autorizador = fn; }
/**
 * @param {Document | HTMLElement} [root]
 * @returns {void}
 */
export function bindActions(root = document) {
  root.addEventListener("click", (e) => {
    const target = /** @type {HTMLElement | null} */ (e.target instanceof Element ? e.target : null);
    const el = /** @type {HTMLElement | null} */ (target?.closest("[data-action]") ?? null);
    if (!el || /** @type {HTMLButtonElement} */ (el).disabled) return;
    const action = el.dataset.action;
    if (!action) return;
    if (autorizador && !autorizador(action)) { e.preventDefault(); toast("Solo el administrador puede hacer esto.", "error"); return; }
    const fn = handlers[action];
    if (fn) { e.preventDefault(); fn(el, e); }
  });
}
