// Pruebas del modo sin conexión: cola local, guardado offline y sincronización (con el backend demo y fallos simulados).
import { api } from "../assets/js/api.js";
import { cola, asistenciasPendientesDe } from "../assets/js/cola.js";
import { construirPaquete, nombreArchivo, planImportacion, validarPaquete } from "../assets/js/respaldo-offline.js";
import "../assets/js/pages/registro.js";   // registra el envío de tipo «salida»
import { alSincronizar, estadoSync, guardarAsistencias, red, sincronizar } from "../assets/js/sync.js";

const out = [];
const check = async (name, fn) => {
  try { await fn(); out.push({ name, ok: true }); } catch (e) { out.push({ name, ok: false, msg: e.message }); }
  localStorage.removeItem("ra-cola-v1"); globalThis.__simOffline = false;
};
const assert = (c, m = "aserción fallida") => { if (!c) throw new Error(m); };
const fila = (n, f = "2026-10-05") => ({ colegio_id: "c", alumno_id: "al" + n, fecha: f, hora: "07:30", registrado_por: "u", origen: "qr" });

const original = api.registrarMasivo;
const restaurar = () => { api.registrarMasivo = original; };

localStorage.removeItem("ra-cola-v1");

await check("Cola: no encola dos veces la misma asistencia (clave) y la lista del día funciona", async () => {
  assert(cola.agregar({ tipo: "asistencia", row: fila(1), clave: "a|al1|2026-10-05" }) === true);
  assert(cola.agregar({ tipo: "asistencia", row: fila(1), clave: "a|al1|2026-10-05" }) === false);
  cola.agregar({ tipo: "asistencia", row: fila(2, "2026-10-06"), clave: "a|al2|2026-10-06" });
  assert(cola.pendientes().length === 2 && asistenciasPendientesDe("2026-10-05").length === 1);
});

await check("Sin red: guardarAsistencias deja los registros en la cola y no llama al servidor", async () => {
  let llamadas = 0; api.registrarMasivo = async () => { llamadas++; return 1; };
  globalThis.__simOffline = true;
  assert(red.online() === false);
  const r = await guardarAsistencias([fila(1), fila(2)]);
  restaurar();
  assert(r.offline === true && r.n === 2 && llamadas === 0 && cola.pendientes().length === 2, JSON.stringify({ r, llamadas }));
});

await check("Red que falla a mitad: el registro no se pierde, queda en cola", async () => {
  api.registrarMasivo = async () => { throw new TypeError("Failed to fetch"); };
  const r = await guardarAsistencias([fila(3)]);
  restaurar();
  assert(r.offline === true && cola.pendientes().length === 1);
});

await check("Un error de permisos NO se oculta como 'sin conexión'", async () => {
  api.registrarMasivo = async () => { throw Object.assign(new Error("violates row-level security policy"), { code: "42501" }); };
  let lanzo = false;
  try { await guardarAsistencias([fila(4)]); } catch { lanzo = true; }
  restaurar();
  assert(lanzo && cola.pendientes().length === 0, "debía propagar el error y no encolar");
});

await check("Sincronizar: envía todo, vacía la cola y avisa para refrescar", async () => {
  [1, 2, 3].forEach((n) => cola.agregar({ tipo: "asistencia", row: fila(n), clave: "k" + n }));
  const enviados = [];
  api.registrarMasivo = async (rows) => { enviados.push(...rows); return rows.length; };
  let avisado = false; alSincronizar(async () => { avisado = true; });
  const r = await sincronizar();
  restaurar();
  assert(r.n === 3 && enviados.length === 3 && cola.pendientes().length === 0 && avisado, JSON.stringify({ r, e: enviados.length }));
});

await check("Sincronizar: una fila inválida se aísla y las demás sí se envían; tras 3 intentos se marca rechazada", async () => {
  [1, 2, 3].forEach((n) => cola.agregar({ tipo: "asistencia", row: fila(n), clave: "k" + n }));
  const ok = [];
  api.registrarMasivo = async (rows) => {
    if (rows.some((r) => r.alumno_id === "al2")) throw Object.assign(new Error("datos inválidos"), { code: "23503" });
    ok.push(...rows.map((r) => r.alumno_id)); return rows.length;
  };
  await sincronizar();
  assert(ok.sort().join() === "al1,al3" && cola.pendientes().length === 1, "tras el 1.er intento: " + JSON.stringify({ ok, p: cola.pendientes().length }));
  await sincronizar(); await sincronizar();
  restaurar();
  assert(cola.pendientes().length === 0 && cola.rechazados().length === 1 && cola.rechazados()[0].intentos === 3, JSON.stringify(cola.todos().map((x) => x.intentos)));
});

await check("Sincronizar con la red caída: no pierde nada y lo informa", async () => {
  cola.agregar({ tipo: "asistencia", row: fila(1), clave: "k1" });
  api.registrarMasivo = async () => { throw new TypeError("Failed to fetch"); };
  await sincronizar();
  restaurar();
  const s = estadoSync();
  assert(cola.pendientes().length === 1 && s.error === "red" && s.pendientes === 1, JSON.stringify(s));
});

await check("Sincronizar con sesión vencida: conserva la cola y pide iniciar sesión", async () => {
  cola.agregar({ tipo: "asistencia", row: fila(1), clave: "k1" });
  api.registrarMasivo = async () => { throw Object.assign(new Error("JWT expired"), { status: 401 }); };
  await sincronizar();
  restaurar();
  assert(cola.pendientes().length === 1 && estadoSync().error === "sesion");
});

await check("Demo sin conexión simulada: las llamadas de red del backend fallan como un corte real", async () => {
  globalThis.__simOffline = true;
  let msg = "";
  try { await api.asistenciasPorFecha("c", "2026-10-05"); } catch (e) { msg = e.message; }
  assert(/fetch|conexi/i.test(msg), msg);
});

await check("Salida sin conexión: se encola y, al volver la red, se guarda en la asistencia del día", async () => {
  const a = api.db.alumnos[0];
  api.db.asistencias = api.db.asistencias.filter((x) => !(x.alumno_id === a.id && x.fecha === "2030-01-07"));
  await api.registrarAsistencia({ colegio_id: "c", alumno_id: a.id, fecha: "2030-01-07", hora: "07:30", registrado_por: "u", origen: "quiosco" });
  cola.agregar({ tipo: "salida", row: { alumno_id: a.id, fecha: "2030-01-07", hora: "13:10" }, clave: `s|${a.id}|2030-01-07` });
  cola.agregar({ tipo: "salida", row: { alumno_id: a.id, fecha: "2030-01-07", hora: "13:10" }, clave: `s|${a.id}|2030-01-07` });   // repetida: no se duplica
  assert(cola.pendientes().length === 1, "la salida repetida no debe encolarse dos veces");
  const r = await sincronizar();
  const fila = api.db.asistencias.find((x) => x.alumno_id === a.id && x.fecha === "2030-01-07");
  api.db.asistencias = api.db.asistencias.filter((x) => x !== fila); api.persist();
  assert(r.n === 1 && fila.hora_salida === "13:10" && cola.pendientes().length === 0, JSON.stringify({ r, salida: fila.hora_salida }));
});

await check("Respaldo offline: se exporta con fechas originales, se valida y detecta archivos alterados o futuros", async () => {
  const als = [{ id: "u1", codigo: "a1", nombre: "Ana", nivel: "APSTI", grado: "G1" }, { id: "u2", codigo: "a2", nombre: "Beto", nivel: "APSTI", grado: "G1" }];
  const texto = await construirPaquete({
    instituto: { id: "c", nombre: "Instituto X" }, origen: { modo: "supabase", app: "android" }, rango: { desde: "2026-10-04", hasta: "2026-10-05" }, alumnos: als,
    asistencias: [{ alumno_id: "u1", fecha: "2026-10-04", hora: "14:05:00", origen: "qr" }, { alumno_id: "u2", fecha: "2026-10-05", hora: "14:30" }, { alumno_id: "u2", fecha: "2026-10-01", hora: "14:00" }],
    salidas: [{ alumno_id: "u1", fecha: "2026-10-04", hora: "16:10" }],
  });
  const p = JSON.parse(texto);
  assert(p.conteo.asistencias === 2 && p.conteo.dias === 2 && p.conteo.alumnos === 2, JSON.stringify(p.conteo));   // el 1 de oct queda fuera del rango
  assert(p.datos.asistencias[0].hora === "14:05" && p.datos.asistencias[0].hora_salida === "16:10" && p.datos.asistencias[0].codigo === "a1", "la salida se une a su asistencia y usa el código");
  assert(nombreArchivo("2026-10-04", "2026-10-05") === "asistencias_2026-10-04_a_2026-10-05.rabackup");
  assert((await validarPaquete(texto, { hoy: "2026-10-06" })).ok === true);
  const mal = JSON.parse(texto); mal.datos.asistencias[0].hora = "23:59";
  assert((await validarPaquete(JSON.stringify(mal))).ok === false, "una alteración debe detectarse");
  assert((await validarPaquete("no es json")).ok === false && (await validarPaquete(JSON.stringify({ formato: "otro" }))).ok === false);
  const futuro = await validarPaquete(texto, { hoy: "2026-10-04" });   // el registro del día 5 sería «futuro»
  assert(futuro.ok && futuro.paquete.datos.asistencias.length === 1 && futuro.avisos.length === 1, "las fechas futuras se descartan");
});

await check("Respaldo offline: el plan de importación no duplica, completa salidas y avisa de lo desconocido", async () => {
  const texto = await construirPaquete({
    instituto: { id: "c", nombre: "I" }, origen: { modo: "local" }, rango: { desde: "2026-10-04", hasta: "2026-10-06" },
    alumnos: [{ id: "L1", codigo: "a1" }, { id: "L2", codigo: "a2" }, { id: "L3", codigo: "zz9" }],
    asistencias: [{ alumno_id: "L1", fecha: "2026-10-04", hora: "14:00", hora_salida: "16:30" }, { alumno_id: "L2", fecha: "2026-10-04", hora: "14:20" }, { alumno_id: "L2", fecha: "2026-10-05", hora: "14:10" }, { alumno_id: "L3", fecha: "2026-10-05", hora: "14:11" }],
    cursos: [{ id: "k1", nombre: "Redes", nivel: "APSTI", grado: null }],
    asistenciasCurso: [{ alumno_id: "L1", curso_id: "k1", fecha: "2026-10-05", hora: "15:00" }],
    justificaciones: [{ alumno_id: "L2", fecha: "2026-10-06", tipo: "Permiso", motivo: "x" }],
  });
  const { paquete } = await validarPaquete(texto, { hoy: "2026-10-10" });
  const online = [{ id: "R1", codigo: "a1" }, { id: "R2", codigo: "a2" }];
  const plan = planImportacion(paquete, { alumnos: online, cursos: [{ id: "K9", nombre: "REDES", nivel: "APSTI", grado: null }],
    existentes: { asistencias: new Map([["R1|2026-10-04", { hora: "14:00", hora_salida: null }], ["R2|2026-10-04", { hora: "14:20", hora_salida: null }]]), cursos: new Set(), just: new Set() } });
  assert(plan.asistencias.length === 1 && plan.asistencias[0].alumno_id === "R2" && plan.asistencias[0].fecha === "2026-10-05" && plan.asistencias[0].origen === "importado", "solo la asistencia que faltaba, en su fecha");
  assert(plan.salidas.length === 1 && plan.salidas[0].alumno_id === "R1" && plan.salidas[0].hora === "16:30", "completa la salida del que ya estaba sin salida");
  assert(plan.cursos.length === 1 && plan.cursos[0].curso_id === "K9" && plan.justificaciones.length === 1);
  assert(plan.desconocidos.join() === "zz9" && plan.resumen.repetidas === 2, JSON.stringify(plan.resumen));
  assert(plan.porFecha.map((x) => x.fecha).join() === "2026-10-04,2026-10-05,2026-10-06");
});

const fail = out.filter((r) => !r.ok);
document.body.insertAdjacentHTML("beforeend", `<h2>Modo sin conexión</h2><p id="summary-offline" data-failed="${fail.length}">${out.length - fail.length}/${out.length} correctos</p><ul>${out.map((r) => `<li style="color:${r.ok ? "#127a4f" : "#b3261e"}">${r.ok ? "✔" : "✘"} ${r.name}${r.msg ? " — " + r.msg : ""}</li>`).join("")}</ul>`);
