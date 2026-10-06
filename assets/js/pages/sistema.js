// Sistema: Diagnóstico del dispositivo (para la prueba piloto y soporte), Respaldo de datos y Registro de errores.
import { api } from "../api.js";
import { CONFIG } from "../config.js";
import { cola } from "../cola.js";
import { DB } from "../state.js";
import { red, sincronizar } from "../sync.js";
import { badge, confirmDialog, emptyState, icon, pageHead, registerActions, skeleton, toast } from "../ui.js";
import { desfaseReloj, downloadFile, esc, fmtDate, ahora, todayStr, toCSV, ZONA_HORARIA } from "../utils.js";
import { rolActual } from "../permisos.js";

/* ============================ Diagnóstico ============================ */
const ICONO = { ok: "check", warn: "alert", err: "x", info: "info" };
let ultimoInforme = [];

async function ejecutarDiagnostico() {
  const it = [];
  const add = (nombre, estado, detalle) => it.push({ nombre, estado, detalle });

  add("Conexión a internet", red.online() ? "ok" : "err", red.online() ? "Hay conexión." : "Sin conexión: los registros se guardan en el teléfono y se envían al reconectar.");

  // Servidor y reloj
  let servidorMs = null, ms = null;
  try { const t0 = performance.now(); servidorMs = await api.horaServidor(); ms = Math.round(performance.now() - t0); } catch { /* sin red o migración pendiente */ }
  add("Servidor de datos", servidorMs ? (ms > 2500 ? "warn" : "ok") : "err", servidorMs ? `Responde en ${ms} ms.` : "No responde (¿sin red, proyecto pausado o falta la migración 004?).");
  if (servidorMs) {
    const dif = servidorMs - Date.now();
    add("Reloj del teléfono", Math.abs(dif) > 120000 ? "warn" : "ok", Math.abs(dif) > 120000
      ? `Desfasado ${Math.round(Math.abs(dif) / 60000)} min respecto al servidor (${dif > 0 ? "atrasado" : "adelantado"}). No afecta la asistencia: se usa la hora del servidor.`
      : `Correcto (diferencia ${Math.round(dif / 1000)} s).`);
  }
  add("Hora de asistencia", "info", `${ahora().toLocaleTimeString("es-PE", { timeZone: ZONA_HORARIA })} (${ZONA_HORARIA}) · fecha ${todayStr()} · límite de puntualidad ${CONFIG.HORA_LIMITE} · corrección aplicada ${Math.round(desfaseReloj() / 1000)} s`);

  // Datos y cola
  add("Datos del instituto", DB.alumnos.length ? "ok" : "warn", `${DB.alumnos.length} alumnos · ${DB.niveles.length} carreras · ${DB.grados.length} ciclos${DB.sinConexion ? " · (copia local, sin conexión)" : ""}`);
  const pend = cola.pendientes().length, rech = cola.rechazados().length;
  add("Registros sin enviar", rech ? "err" : pend ? "warn" : "ok", pend || rech ? `${pend} pendientes · ${rech} rechazados` : "Ninguno.");

  // Almacenamiento
  try {
    const k = "ra-test-almacenamiento"; localStorage.setItem(k, "x".repeat(100 * 1024)); localStorage.removeItem(k);
    let extra = "";
    if (navigator.storage?.estimate) { const e = await navigator.storage.estimate(); extra = ` · usado ${(e.usage / 1048576).toFixed(1)} MB de ${(e.quota / 1048576).toFixed(0)} MB`; }
    add("Almacenamiento del teléfono", "ok", "Se puede guardar información sin conexión" + extra);
  } catch { add("Almacenamiento del teléfono", "err", "No se puede guardar datos: el modo sin conexión no funcionará (¿navegación privada o memoria llena?)."); }

  // Cámara
  if (navigator.mediaDevices?.enumerateDevices) {
    try {
      const cams = (await navigator.mediaDevices.enumerateDevices()).filter((d) => d.kind === "videoinput").length;
      add("Cámara", cams ? "ok" : "warn", cams ? `${cams} cámara(s) detectada(s). Usa «Probar cámara» para confirmar el permiso.` : "No se detectó cámara: se puede registrar por código manual o NFC.");
    } catch { add("Cámara", "warn", "No se pudo consultar."); }
  } else add("Cámara", "err", "Este navegador no permite usar la cámara (requiere HTTPS o localhost).");

  // NFC
  const nfcNativo = globalThis.AndroidBridge?.nfcState?.();
  add("NFC", nfcNativo === "on" || (!nfcNativo && "NDEFReader" in window) ? "ok" : "warn",
    nfcNativo ? ({ on: "Disponible (lector nativo de la app).", off: "El NFC está desactivado en el teléfono.", none: "El teléfono no tiene NFC." }[nfcNativo])
      : ("NDEFReader" in window ? "Disponible en este navegador (Android + Chrome)." : "No disponible aquí. Usa la app Android o el código/QR."));

  // Plataforma
  const ua = navigator.userAgent, chrome = /Chrome\/(\d+)/.exec(ua)?.[1];
  add("Navegador / WebView", chrome && Number(chrome) < 90 ? "warn" : "ok", `${chrome ? "Chrome/WebView " + chrome : ua.slice(0, 60)}${chrome && Number(chrome) < 90 ? " — antiguo: actualiza «Android System WebView» en Play Store" : ""}`);
  const sw = "serviceWorker" in navigator ? (await navigator.serviceWorker.getRegistration().catch(() => null)) : null;
  add("Modo sin conexión (service worker)", sw ? "ok" : "warn", sw ? "Activo: la app abre aunque no haya internet." : "No activo (primera visita, o navegador sin soporte).");
  add("Aplicación", "info", `v${CONFIG.APP_VERSION} · ${globalThis.AndroidBridge ? "app Android" : "web"} · modo ${api.mode}`);
  add("Sesión", "info", `${DB.perfil?.nombre || "—"} · rol ${rolActual()}${DB.perfil?.carrera ? " · carrera " + DB.perfil.carrera : ""} · ${DB.perfil?.colegio || ""}${DB.sesionOffline ? " · sesión sin conexión" : ""}`);
  return it;
}

export const diagnosticoPage = {
  id: "diagnostico", title: "Diagnóstico", icon: "info", group: "Sistema",
  async render(root) {
    root.innerHTML = `${pageHead("Diagnóstico del dispositivo", "Comprueba que este teléfono está listo: conexión, hora, cámara, NFC y modo sin conexión. Úsalo en la prueba piloto y para pedir soporte.",
      `<button class="btn btn-outline" data-action="diag-camara">${icon("camera", 16)} Probar cámara</button><button class="btn btn-outline" data-action="diag-copiar">Copiar informe</button><button class="btn btn-primary" data-action="diag-repetir">Volver a comprobar</button>`)}
      <div class="card flush" id="diag-lista">${skeleton(6)}</div>`;
    await pintar(root);
  },
};
async function pintar(root) {
  const box = root.querySelector("#diag-lista");
  box.innerHTML = skeleton(6);
  ultimoInforme = await ejecutarDiagnostico();
  if (!box.isConnected) return;
  const resumen = ultimoInforme.filter((x) => x.estado === "err").length ? "Hay problemas por resolver" : ultimoInforme.filter((x) => x.estado === "warn").length ? "Listo, con avisos" : "Todo en orden";
  box.innerHTML = `<div class="diag-resumen diag-${ultimoInforme.some((x) => x.estado === "err") ? "err" : ultimoInforme.some((x) => x.estado === "warn") ? "warn" : "ok"}">${esc(resumen)}</div>
    <ul class="diag-lista">${ultimoInforme.map((x) => `<li class="diag-${x.estado}"><span class="diag-ic">${icon(ICONO[x.estado], 16)}</span><div><strong>${esc(x.nombre)}</strong><small>${esc(x.detalle)}</small></div></li>`).join("")}</ul>`;
}

/* ============================== Respaldo ============================== */
const ultimoRespaldo = () => { try { return localStorage.getItem("ra-ultimo-respaldo"); } catch { return null; } };

export const respaldoPage = {
  id: "respaldo", title: "Respaldo", icon: "download", group: "Sistema", soloAdmin: true,
  render(root) {
    const ult = ultimoRespaldo();
    root.innerHTML = `${pageHead("Respaldo de datos", "Descarga una copia completa de la información de tu instituto. Guárdala en un lugar seguro y hazlo con frecuencia.")}
      <div class="grid-2">
        <section class="card"><h3>Copia completa</h3>
          <p class="muted">Incluye alumnos, carreras, ciclos, docentes, comunicados, cursos, asistencias, justificaciones y avisos, en un solo archivo JSON.</p>
          <div class="btn-row"><button class="btn btn-primary" data-action="respaldo-json">${icon("download", 16)} Descargar respaldo (JSON)</button></div>
          <p class="muted" style="margin-top:12px">${ult ? `Último respaldo hecho desde este equipo: <b>${esc(fmtDate(ult.slice(0, 10)))}</b>.` : "Aún no has hecho un respaldo desde este equipo."}</p></section>
        <section class="card"><h3>Para Excel</h3>
          <p class="muted">Tablas en CSV (se abren en Excel con tildes correctas).</p>
          <div class="btn-row"><button class="btn btn-outline" data-action="respaldo-csv-alumnos">Alumnos</button><button class="btn btn-outline" data-action="respaldo-csv-asistencias">Asistencias</button></div></section>
      </div>
      <section class="card" style="margin-top:16px"><h3>Buenas prácticas</h3><ul class="plano">
        <li><b>Frecuencia:</b> al menos una vez al mes y siempre antes de cambios importantes (cierre de año, importaciones masivas).</li>
        <li><b>Seguridad:</b> el archivo contiene datos personales de menores. No lo envíes por WhatsApp ni lo subas a carpetas públicas; guárdalo cifrado o en un disco de acceso restringido.</li>
        <li><b>Supabase (plan gratuito):</b> no incluye copias de seguridad restaurables ni protege de pausas por inactividad. Para uso con alumnos reales, considera el plan Pro (copias diarias).</li>
        <li><b>Restauración:</b> el JSON conserva todos los identificadores; si algún día hiciera falta, se carga con un script de importación (ver docs/OPERACION.md).</li></ul></section>`;
  },
};

async function descargarRespaldo(el) {
  el.disabled = true;
  try {
    const datos = await api.exportarTodo(DB.cid);
    const total = Object.values(datos).reduce((s, l) => s + l.length, 0);
    const meta = { instituto: DB.perfil?.colegio, colegio_id: DB.cid, generado: new Date().toISOString(), version_app: CONFIG.APP_VERSION, tablas: Object.fromEntries(Object.entries(datos).map(([t, l]) => [t, l.length])) };
    await downloadFile(`respaldo_${todayStr()}.json`, JSON.stringify({ meta, datos }, null, 1), "application/json");
    try { localStorage.setItem("ra-ultimo-respaldo", new Date().toISOString()); } catch { /* ok */ }
    toast(`Respaldo descargado (${total} registros)`, "success");
    respaldoPage.render(document.getElementById("page-root"));
  } catch (e) { toast("No se pudo generar el respaldo: " + e.message, "error"); el.disabled = false; }
}

/* ============================== Errores ============================== */
export const erroresPage = {
  id: "errores", title: "Errores", icon: "alert", group: "Sistema", soloAdmin: true,
  async render(root) {
    root.innerHTML = `${pageHead("Registro de errores", "Fallos ocurridos en los teléfonos de docentes y estudiantes (sin datos personales). Úsalo para detectar problemas antes de que te los reporten.",
      `<button class="btn btn-ghost-danger" data-action="err-borrar">${icon("trash", 16)} Borrar todos</button>`)}<div class="card flush" id="err-lista">${skeleton(4)}</div>`;
    try {
      const l = await api.erroresRecientes(DB.cid);
      root.querySelector("#err-lista").innerHTML = l.length ? `<div class="table-wrap"><table><thead><tr><th>Fecha</th><th>App</th><th>Mensaje</th><th>Pantalla</th><th>Dispositivo</th></tr></thead><tbody>
        ${l.map((e) => `<tr><td class="nowrap">${esc(new Date(e.creado_en).toLocaleString("es-PE"))}</td><td>${badge(e.app || "—", e.app === "estudiante" ? "amber" : "neutral")}</td>
          <td><details><summary>${esc((e.mensaje || "").slice(0, 110))}</summary><pre class="err-stack">${esc(e.detalle || "(sin detalle)")}</pre></details></td>
          <td class="muted">${esc((e.url || "").replace(/^https?:\/\/[^/]+/, ""))}</td><td class="muted">${esc((e.agente || "").match(/\(([^)]*)\)/)?.[1]?.slice(0, 40) || "")}</td></tr>`).join("")}</tbody></table></div>`
        : emptyState("Sin errores registrados", "Todo funciona bien. Aquí aparecerán los fallos que ocurran en los teléfonos.", "check");
    } catch (e) { root.querySelector("#err-lista").innerHTML = emptyState("No se pudo cargar", /does not exist|schema cache/i.test(e.message) ? "Falta aplicar la migración 004 en Supabase." : e.message, "alert"); }
  },
};

/* ============================== Acciones ============================== */
registerActions({
  "diag-repetir": () => pintar(document.getElementById("page-root")),
  "diag-copiar": async () => {
    const t = `Diagnóstico ${new Date().toLocaleString("es-PE")}\n` + ultimoInforme.map((x) => `[${x.estado.toUpperCase()}] ${x.nombre}: ${x.detalle}`).join("\n");
    try { await navigator.clipboard.writeText(t); toast("Informe copiado", "success"); } catch { toast("No se pudo copiar", "error"); }
  },
  "diag-camara": async () => {
    try {
      const s = await navigator.mediaDevices.getUserMedia({ video: true });
      const t = s.getVideoTracks()[0]; const nombre = t?.label || "cámara"; s.getTracks().forEach((x) => x.stop());
      toast(`Cámara funciona: ${nombre}`, "success");
    } catch (e) { toast(e.name === "NotAllowedError" ? "Permiso de cámara denegado: actívalo en los ajustes." : "No se pudo abrir la cámara: " + e.message, "error"); }
  },
  "respaldo-json": descargarRespaldo,
  "respaldo-csv-alumnos": async () => {
    await downloadFile(`alumnos_${todayStr()}.csv`, toCSV(DB.alumnos, [
      { label: "Código", key: "codigo" }, { label: "Nombre", key: "nombre" }, { label: "Carrera", key: "nivel" }, { label: "Ciclo", key: "grado" },
      { label: "Apoderado", key: "apoderado" }, { label: "Teléfono apoderado", key: "apoderado_telefono" }, { label: "Correo apoderado", key: "apoderado_email" },
      { label: "DNI", key: "dni" }, { label: "Estado", key: "estado" }, { label: "Aprobado", value: (a) => (a.aprobado === false ? "NO" : "SI") }]));
  },
  "respaldo-csv-asistencias": async (el) => {
    el.disabled = true;
    try {
      const { asistencias } = await api.exportarTodo(DB.cid);
      const al = new Map(DB.alumnos.map((a) => [a.id, a]));
      await downloadFile(`asistencias_${todayStr()}.csv`, toCSV(asistencias, [
        { label: "Fecha", key: "fecha" }, { label: "Hora", value: (r) => String(r.hora).slice(0, 5) }, { label: "Código", value: (r) => al.get(r.alumno_id)?.codigo },
        { label: "Alumno", value: (r) => al.get(r.alumno_id)?.nombre }, { label: "Carrera", value: (r) => al.get(r.alumno_id)?.nivel }, { label: "Ciclo", value: (r) => al.get(r.alumno_id)?.grado }, { label: "Origen", key: "origen" }]));
      toast(`${asistencias.length} asistencias exportadas`, "success");
    } catch (e) { toast("No se pudo exportar: " + e.message, "error"); }
    el.disabled = false;
  },
  "err-borrar": async () => {
    if (!(await confirmDialog({ title: "Borrar registro de errores", message: "Se eliminarán todos los errores registrados. ¿Continuar?", confirmLabel: "Borrar" }))) return;
    try { await api.borrarErrores(DB.cid); erroresPage.render(document.getElementById("page-root")); toast("Registro vaciado", "success"); } catch (e) { toast(e.message, "error"); }
  },
});
