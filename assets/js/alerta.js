// Alerta de asistencia para el docente: foto del estudiante + nombre con apellidos parcialmente censurados (privacidad).
import { api } from "./api.js";
import { CONFIG } from "./config.js";
import { esTardanza } from "./stats.js";
import { censurarNombre, esc, etiquetaCiclo, initials } from "./utils.js";
import { icon } from "./ui.js";

const TIPOS = {
  ok: { clase: "ok", titulo: "Asistencia registrada", ic: "check" },
  offline: { clase: "warn", titulo: "Guardado sin conexión · se enviará solo", ic: "clock" },
  dup: { clase: "warn", titulo: "Ya estaba registrado hoy", ic: "info" },
  pendiente: { clase: "warn", titulo: "Registro pendiente de aprobación", ic: "alert" },
  no_pertenece: { clase: "err", titulo: "No pertenece a este curso", ic: "alert" },
  qr_invalido: { clase: "err", titulo: "QR no válido", ic: "alert" },
  inactivo: { clase: "err", titulo: "Alumno inactivo — no se registra", ic: "alert" },
};
let timer = null;
let version = 0;

/** tipo: ok | dup | pendiente | inactivo. Se cierra sola a los 5 s o al tocarla. */
export async function mostrarAlertaAsistencia(alumno, { tipo = "ok", hora = "", detalle = "" } = {}) {
  const t = TIPOS[tipo] || TIPOS.ok;
  const mia = ++version;
  let el = document.getElementById("asistencia-alert");
  if (!el) {
    el = document.createElement("div");
    el.id = "asistencia-alert";
    el.className = "asistencia-alert";
    el.setAttribute("role", "alert");
    el.addEventListener("click", () => el.classList.remove("show"));
    document.body.appendChild(el);
  }
  const registrada = tipo === "ok" || tipo === "offline";
  const tarde = registrada && esTardanza(hora, CONFIG.HORA_LIMITE);
  el.className = `asistencia-alert show alert-${t.clase}`;
  el.innerHTML = `
    <div class="alert-photo" id="alert-photo"><span>${esc(initials(alumno.nombre))}</span></div>
    <div class="alert-body">
      <span class="alert-title">${icon(t.ic, 15)} ${esc(t.titulo)}</span>
      <strong>${esc(censurarNombre(alumno))}</strong>
      <small>${esc(etiquetaCiclo(alumno.nivel, alumno.grado))}${hora ? " · " + esc(hora.slice(0, 5)) : ""}${registrada ? (tarde ? " · Tardanza" : " · Puntual") : ""}${detalle ? " · " + esc(detalle) : ""}</small>
    </div>`;
  clearTimeout(timer);
  timer = setTimeout(() => el.classList.remove("show"), 5000);
  try {
    const url = await api.fotoUrl(alumno);
    const box = document.getElementById("alert-photo");
    if (url && mia === version && box) {
      const img = new Image();
      img.alt = "Foto del estudiante";
      img.onload = () => { if (mia === version) { box.innerHTML = ""; box.appendChild(img); } };
      img.src = url;
    }
  } catch { /* sin foto: se queda con las iniciales */ }
}
