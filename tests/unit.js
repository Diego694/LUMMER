// Tests unitarios sin dependencias. Se ejecutan abriendo tests/tests.html (o con scripts/check.py en CI).
import { TABLAS, aFirestore, clasificarLlave, crearPaquete, idDeFila, importarPaquete, lotes, normalizarDestino, probarConexion, validarPaquete } from "../assets/js/conectores.js";
import { alcanceDocente } from "../assets/js/alcance.js";
import { ahora, esErrorRed, esErrorSesion, fechaZona, horaZona, sincronizarReloj, todayStr, nowHHMM } from "../assets/js/utils.js";
import { CICLOS, addDays, censurarNombre, cicloCorto, compararCiclos, dateStr, esc, etiquetaCiclo, nombreCiclo, parsearCiclo, initials, isWeekend, lastWeekdays, norm, pct, toCSV } from "../assets/js/utils.js";
import { enlaceWhatsApp, matrizAsistencia, mensajeAviso, numeroWhatsApp, perteneceACurso } from "../assets/js/stats.js";
import { bajaAsistencia, esTardanza, normalizarFilasImport, porGrado, resumenAlumno, resumenDia, serieDiaria } from "../assets/js/stats.js";

const results = [];
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);
const pendientes = [];
function test(name, fn) {
  try {
    const r = fn();
    if (r && typeof r.then === "function") pendientes.push(r.then(() => results.push({ name, ok: true }), (e) => results.push({ name, ok: false, msg: e.message })));
    else results.push({ name, ok: true });
  } catch (e) { results.push({ name, ok: false, msg: e.message }); }
}
const assert = (c, m = "aserción fallida") => { if (!c) throw new Error(m); };
const same = (a, b) => assert(eq(a, b), `esperado ${JSON.stringify(b)} y se obtuvo ${JSON.stringify(a)}`);

const A = (id, o = {}) => ({ id, nombre: `Alumno ${id}`, nivel: "Primaria", grado: "1er grado", estado: "ACTIVO", ...o });
const X = (alumno_id, fecha, hora = "07:30") => ({ alumno_id, fecha, hora });

/* utils */
test("esc escapa HTML y comillas", () => same(esc(`<a href="x">'&'</a>`), "&lt;a href=&quot;x&quot;&gt;&#39;&amp;&#39;&lt;/a&gt;"));
test("esc tolera null/undefined", () => same([esc(null), esc(undefined)], ["", ""]));
test("dateStr usa fecha local (sin desfase UTC)", () => same(dateStr(new Date(2026, 0, 5, 23, 59)), "2026-01-05"));
test("addDays cruza mes y año", () => { same(addDays("2026-12-31", 1), "2027-01-01"); same(addDays("2026-03-01", -1), "2026-02-28"); });
test("isWeekend", () => { assert(isWeekend("2026-10-03")); assert(isWeekend("2026-10-04")); assert(!isWeekend("2026-10-05")); });
test("lastWeekdays devuelve n días hábiles ascendentes", () => {
  const d = lastWeekdays(7, "2026-10-04");
  same(d, ["2026-09-24", "2026-09-25", "2026-09-28", "2026-09-29", "2026-09-30", "2026-10-01", "2026-10-02"]);
  assert(d.every((x) => !isWeekend(x)));
});
test("pct redondea y protege división por cero", () => same([pct(1, 3), pct(2, 3), pct(5, 0)], [33, 67, 0]));
test("norm quita tildes y mayúsculas", () => same(norm("ÁÉÍóú Ñandú"), "aeiou nandu"));
test("initials", () => same([initials("ana maría pérez"), initials("")], ["AM", "?"]));
test("toCSV escapa comas, comillas y saltos; incluye BOM", () => {
  const csv = toCSV([{ a: 'x,"y"', b: "l1\nl2" }], [{ label: "A", key: "a" }, { label: "B", key: "b" }]);
  assert(csv.startsWith("﻿")); same(csv.slice(1), 'A,B\r\n"x,""y""","l1\nl2"');
});

test("censurarNombre: usa nombres/apellidos y enmascara solo los apellidos", () => {
  same(censurarNombre({ nombres: "Lucía María", apellidos: "Quispe Flores" }), "Lucía María Qu**** Fl****");
});
test("censurarNombre: deduce apellidos de un nombre completo (3+ palabras, 2 y 1)", () => {
  same(censurarNombre({ nombre: "Juan Carlos Pérez Ríos" }), "Juan Carlos Pé*** Rí**");
  same(censurarNombre({ nombre: "Ana Torres" }), "Ana To****");
  same(censurarNombre({ nombre: "Madonna" }), "Madonna");
});
test("censurarNombre: apellidos cortos y vacío no fallan", () => {
  same(censurarNombre({ nombres: "Li", apellidos: "Wu Xi" }), "Li W* X*");
  same(censurarNombre({}), "");
  assert(!censurarNombre({ nombres: "Eva", apellidos: "Castillo" }).includes("Castillo"));
});

test("Zona Lima: fecha y hora no dependen de la zona del equipo ni del horario UTC", () => {
  // 03:00 UTC del 6-oct = 22:00 del 5-oct en Lima (UTC-5): la fecha NO debe saltar al día siguiente.
  const d = new Date(Date.UTC(2026, 9, 6, 3, 0));
  same([fechaZona(d), horaZona(d)], ["2026-10-05", "22:00"]);
  const e = new Date(Date.UTC(2026, 9, 6, 5, 0));
  same([fechaZona(e), horaZona(e)], ["2026-10-06", "00:00"]); // medianoche exacta: "00", nunca "24"
});
test("Reloj confiable: usa la hora del servidor aunque el equipo esté desfasado", () => {
  sincronizarReloj(Date.UTC(2026, 9, 5, 12, 30)); // el servidor dice 07:30 en Lima
  same([todayStr(), nowHHMM()], ["2026-10-05", "07:30"]);
  const a = ahora().getTime(), b = ahora().getTime();
  assert(b >= a && b - a < 50, "el reloj debe avanzar sin saltos");
  sincronizarReloj(Date.now()); // restaura
});
test("esErrorRed / esErrorSesion clasifican los errores", () => {
  same([esErrorRed(new TypeError("Failed to fetch")), esErrorRed({ message: "AbortError: signal is aborted" }), esErrorRed({ message: "x", code: "23505" }), esErrorRed({ message: "violates row-level security", code: "42501" })], [true, true, false, false]);
  same([esErrorSesion({ message: "JWT expired" }), esErrorSesion({ message: "otro" })], [true, false]);
});
test("CICLOS son exactamente del I al VI", () => same(CICLOS, ["I", "II", "III", "IV", "V", "VI"]));
test("nombreCiclo: formato fijo, con y sin salón", () => {
  same(nombreCiclo("APSTI", "IV"), "APSTI · IV CICLO");
  same(nombreCiclo("MECANICA ELECTRICA", "III", "A"), "MECANICA ELECTRICA · III CICLO · SECCIÓN A");
});
test("parsearCiclo: distingue VI/IV/V/III/II/I y la sección; nombres libres → null", () => {
  same(["I", "II", "III", "IV", "V", "VI"].map((c) => parsearCiclo(`APSTI · ${c} CICLO`).ciclo), ["I", "II", "III", "IV", "V", "VI"]);
  same(parsearCiclo("APSTI · IV CICLO · SECCIÓN B"), { ciclo: "IV", seccion: "B" });
  // formatos antiguos o libres no se interpretan (no se adivina el ciclo)
  same([parsearCiclo("APSTI 4TO CICLO I").ciclo, parsearCiclo("MECANICA ELECTRICA I").ciclo], [null, null]);
});
test("compararCiclos: ordena I→VI, luego salón; lo libre al final", () => {
  const lista = ["X · VI CICLO", "X · I CICLO · SECCIÓN B", "X · III CICLO", "X · I CICLO · SECCIÓN A", "OTRO NOMBRE", "X · II CICLO"];
  same([...lista].sort(compararCiclos), ["X · I CICLO · SECCIÓN A", "X · I CICLO · SECCIÓN B", "X · II CICLO", "X · III CICLO", "X · VI CICLO", "OTRO NOMBRE"]);
});
test("cicloCorto: quita 'CARRERA · ' solo cuando coincide", () => {
  same(cicloCorto("APSTI · IV CICLO", "APSTI"), "IV CICLO");
  same(cicloCorto("apsti · IV CICLO", "APSTI"), "IV CICLO");
  same(cicloCorto("APSTI · IV CICLO", "MECANICA"), "APSTI · IV CICLO");
  same(cicloCorto("APSTI · IV CICLO", ""), "APSTI · IV CICLO");
});
test("etiquetaCiclo con el nombre canónico no repite la carrera", () => same(etiquetaCiclo("APSTI", "APSTI · IV CICLO"), "APSTI · IV CICLO"));
test("etiquetaCiclo: no repite la carrera cuando el ciclo ya la incluye", () => {
  same(etiquetaCiclo("MECANICA ELECTRICA", "MECANICA ELECTRICA III"), "MECANICA ELECTRICA III");
  same(etiquetaCiclo("Mecánica Eléctrica", "MECANICA ELECTRICA I"), "MECANICA ELECTRICA I"); // sin tildes ni mayúsculas
  same(etiquetaCiclo("APSTI", "III"), "APSTI · III");
  same([etiquetaCiclo("", "I"), etiquetaCiclo("APSTI", "")], ["I", "APSTI"]);
});

/* stats */
test("esTardanza compara HH:MM contra el límite", () => same([esTardanza("08:00", "08:00"), esTardanza("08:01", "08:00"), esTardanza("07:59:30", "08:00"), esTardanza("", "08:00")], [false, true, false, false]));
test("resumenDia ignora inactivos y cuenta tardanzas", () => {
  const al = [A(1), A(2), A(3), A(4, { estado: "INACTIVO" })];
  const r = resumenDia(al, [X(1, "d"), X(2, "d", "08:20"), X(4, "d")], "08:00");
  same(r, { activos: 3, presentes: 2, tardes: 1, puntuales: 1, ausentes: 1, pct: 67 });
});
test("resumenDia sin alumnos no divide por cero", () => same(resumenDia([], [], "08:00").pct, 0));
test("serieDiaria agrega por fecha", () => {
  const s = serieDiaria(["d1", "d2"], [X(1, "d1"), X(2, "d1", "09:00"), X(1, "d2")], 4, "08:00");
  same(s, [{ fecha: "d1", presentes: 2, tardes: 1, pct: 50 }, { fecha: "d2", presentes: 1, tardes: 0, pct: 25 }]);
});
test("porGrado agrupa y ordena naturalmente", () => {
  const al = [A(1, { grado: "10mo" }), A(2, { grado: "2do" }), A(3, { grado: "2do" })];
  const g = porGrado(al, [X(2, "d")]);
  same(g.map((x) => [x.grado, x.total, x.presentes, x.pct]), [["2do", 2, 1, 50], ["10mo", 1, 0, 0]]);
});
test("bajaAsistencia usa días con registros como días de clase", () => {
  const al = [A(1), A(2), A(3)];
  const as = [X(1, "d1"), X(1, "d2"), X(1, "d3"), X(1, "d4"), X(2, "d1"), X(3, "d1"), X(3, "d2"), X(3, "d3"), X(3, "d4")];
  const r = bajaAsistencia(al, as, 85);
  same(r.map((x) => [x.alumno.id, x.pct]), [[2, 25]]);
});
test("bajaAsistencia sin registros devuelve vacío", () => same(bajaAsistencia([A(1)], [], 85), []));
test("resumenAlumno calcula ausencias", () => same(resumenAlumno([X(1, "a"), X(1, "b", "08:30")], 5, "08:00"), { presentes: 2, tardes: 1, ausentes: 3, pct: 40 }));
test("matrizAsistencia: P/T/J/F, solo cuenta días de clase y calcula porcentajes", () => {
  const al = [A(1), A(2), A(3)];
  const dias = ["d1", "d2", "d3", "d4"];
  const asis = [X(1, "d1"), X(1, "d2", "08:30"), X(2, "d1"), X(3, "d1")]; // d3 y d4 sin registros → no hubo clase
  const just = [{ alumno_id: 2, fecha: "d2", tipo: "Permiso" }];
  const m = matrizAsistencia(al, asis, just, dias, "08:00");
  same(m.dias, ["d1", "d2"]);
  same(m.filas.map((f) => Object.values(f.celdas).join("")), ["PT", "PJ", "PF"]);
  same(m.filas.map((f) => [f.p, f.t, f.j, f.f, f.pct, f.pctJust]), [[2, 1, 0, 0, 100, 100], [1, 0, 1, 0, 50, 100], [1, 0, 0, 1, 50, 50]]);
  same(m.resumen, { alumnos: 3, dias: 2, pct: 67 });
});
test("matrizAsistencia sin registros no divide por cero", () => same(matrizAsistencia([A(1)], [], [], ["d1"], "08:00").filas[0].pct, 0));
test("matrizAsistencia: tardanza cuenta como presente (1 día con hora tardía ⇒ p=1, t=1, pct 100)", () => {
  const m = matrizAsistencia([A(1)], [X(1, "d1", "08:30")], [], ["d1"], "08:00");
  same([m.filas[0].p, m.filas[0].t, m.filas[0].pct], [1, 1, 100]);
});
test("numeroWhatsApp / enlaceWhatsApp: formato peruano e internacional", () => {
  same([numeroWhatsApp("999 888 777"), numeroWhatsApp("+51 999-888-777"), numeroWhatsApp("+34 600 111 222"), numeroWhatsApp("51999888777"), numeroWhatsApp("")], ["51999888777", "51999888777", "34600111222", "51999888777", ""]);
  same(enlaceWhatsApp("999888777", "Hola & chao"), "https://wa.me/51999888777?text=Hola%20%26%20chao");
  same(enlaceWhatsApp("", "x"), "");
});
test("mensajeAviso rellena variables y deja intactas las desconocidas", () => same(mensajeAviso("{alumno} faltó el {fecha} ({otra})", { alumno: "Ana", fecha: "05/10" }), "Ana faltó el 05/10 ({otra})"));
test("perteneceACurso: carrera y, si el curso fija ciclo, ciclo", () => {
  const al = { nivel: "APSTI", grado: "APSTI · I CICLO" };
  same([perteneceACurso(al, { nivel: "APSTI" }), perteneceACurso(al, { nivel: "APSTI", grado: "APSTI · I CICLO" }), perteneceACurso(al, { nivel: "APSTI", grado: "APSTI · II CICLO" }), perteneceACurso(al, { nivel: "OTRA" })], [true, true, false, false]);
});
test("normalizarFilasImport acepta las columnas carrera y ciclo", () => {
  const r = normalizarFilasImport([{ nombre: "Ana", codigo: "a1", carrera: "APSTI", ciclo: "APSTI III" }], ["APSTI"], [{ nivel: "APSTI", nombre: "APSTI III" }]);
  same(r.validas.map((v) => [v.nivel, v.grado, v.aviso.length]), [["APSTI", "APSTI III", 0]]);
});
test("normalizarFilasImport valida, deduplica y avisa", () => {
  const rows = [
    { Nombre: " Ana ", CODIGO: "a1", nivel: "Primaria", grado: "1er grado", estado: "inactivo" },
    { nombre: "Sin código", codigo: "" },
    { nombre: "Repetido", codigo: "a1" },
    { nombre: "Raro", codigo: "a2", nivel: "Zeta" },
  ];
  const r = normalizarFilasImport(rows, ["Primaria"], [{ nivel: "Primaria", nombre: "1er grado" }]);
  same(r.validas.map((v) => [v.codigo, v.estado, v.aviso.length]), [["a1", "INACTIVO", 0], ["a2", "ACTIVO", 1]]);
  same(r.errores.map((e) => e.linea), [3, 4]);
});

/* calendario, horarios, periodos, riesgo, fusión, quiosco (v2.8) */
import { diasLectivos, esDiaLectivo, estadoIngreso, feriadosPeru, horarioDe, limiteDeHorario, mapaNoLectivos, salidaPermitida, tablaLimites } from "../assets/js/calendario.js";
import { cambiosPromocion, planPromocion } from "../assets/js/promocion.js";
import { asistenciasParaOnline, planFusion } from "../assets/js/fusion.js";
import { calcularRiesgo, faltasRestantes } from "../assets/js/riesgo.js";
import { configurarLimites, decidirAccion } from "../assets/js/stats.js";

test("feriadosPeru: fijos y Semana Santa 2026 (Pascua 5 abr → jueves 2 y viernes 3)", () => {
  const f = feriadosPeru(2026);
  const por = Object.fromEntries(f.map((x) => [x.fecha, x.nombre]));
  assert(por["2026-07-28"] === "Fiestas Patrias" && por["2026-10-08"] === "Combate de Angamos" && por["2026-12-25"] === "Navidad");
  assert(por["2026-04-02"] === "Jueves Santo" && por["2026-04-03"] === "Viernes Santo", "Semana Santa mal calculada");
  assert(f.length === 16 && f.every((x, i) => i === 0 || f[i - 1].fecha <= x.fecha), "debe haber 16 feriados ordenados");
});
test("feriadosPeru 2027: Pascua 28 mar → jueves 25 y viernes 26", () => {
  const por = Object.fromEntries(feriadosPeru(2027).map((x) => [x.fecha, x.nombre]));
  assert(por["2027-03-25"] === "Jueves Santo" && por["2027-03-26"] === "Viernes Santo");
});
test("diasLectivos: sin fines de semana ni feriados; «Evento» sí tiene clases", () => {
  const nl = mapaNoLectivos([{ fecha: "2026-10-08", tipo: "Feriado", nombre: "Angamos" }, { fecha: "2026-10-09", tipo: "Evento", nombre: "Feria" }]);
  same(diasLectivos("2026-10-05", "2026-10-12", nl), ["2026-10-05", "2026-10-06", "2026-10-07", "2026-10-09", "2026-10-12"]);
  assert(!esDiaLectivo("2026-10-08", nl) && esDiaLectivo("2026-10-09", nl));
});
test("limiteDeHorario: ingreso + tolerancia; la carrera tiene prioridad sobre el general; sin horario → por defecto", () => {
  const h = [{ nivel: null, hora_ingreso: "08:00", tolerancia_min: 5 }, { nivel: "APSTI", hora_ingreso: "07:30", tolerancia_min: 10 }];
  same([limiteDeHorario(h, "APSTI"), limiteDeHorario(h, "MECANICA"), limiteDeHorario([], "X", "08:00")], ["07:40", "08:05", "08:00"]);
  same(tablaLimites(h), { general: "08:05", porNivel: { APSTI: "07:40" } });
});
test("esTardanza usa el horario de la carrera cuando existe", () => {
  configurarLimites({ general: "08:05", porNivel: { APSTI: "07:40" } });
  assert(esTardanza("07:50", "08:05", "APSTI") && !esTardanza("07:50", "08:05", "MECANICA") && !esTardanza("07:50", "08:05"));
  configurarLimites(null);
  assert(esTardanza("08:30", "08:00", "APSTI") && !esTardanza("08:00", "08:00", "APSTI"));
});
test("promoción: cada ciclo pasa al siguiente conservando el salón; el VI egresa; ciclos libres no se tocan", () => {
  const al = [
    A("1", { nivel: "APSTI", grado: "APSTI · I CICLO · SECCIÓN A" }), A("2", { nivel: "APSTI", grado: "APSTI · VI CICLO" }),
    A("3", { nivel: "APSTI", grado: "Grupo libre" }), A("4", { nivel: "APSTI", grado: "APSTI · IV CICLO", estado: "INACTIVO" }),
    A("5", { nivel: "MEC", grado: "MEC · II CICLO" }),
  ];
  const p = planPromocion(al, [{ nivel: "APSTI", nombre: "APSTI · II CICLO · SECCIÓN A" }]);
  same(p.mover.map((m) => [m.alumno.id, m.a]), [["1", "APSTI · II CICLO · SECCIÓN A"], ["5", "MEC · III CICLO"]]);
  same(p.egresan.map((a) => a.id), ["2"]); same(p.sinCiclo.map((a) => a.id), ["3"]);
  same(p.gradosNuevos, [{ nivel: "MEC", nombre: "MEC · III CICLO" }]);   // el II de APSTI ya existía; el inactivo no se mueve
  same(cambiosPromocion(p), [{ id: "1", cambios: { grado: "APSTI · II CICLO · SECCIÓN A" } }, { id: "5", cambios: { grado: "MEC · III CICLO" } }, { id: "2", cambios: { estado: "EGRESADO" } }]);
  same(planPromocion(al, [], ["MEC"]).mover.map((m) => m.alumno.id), ["5"]);   // solo una carrera
});
test("fusión local→online: solo agrega lo que falta y nunca pisa lo existente", () => {
  const local = { niveles: [{ nombre: "APSTI" }, { nombre: "NUEVA" }], grados: [{ nivel: "APSTI", nombre: "G1" }, { nivel: "NUEVA", nombre: "G2" }],
    alumnos: [{ id: "l1", codigo: "a1", nombre: "Ya existe" }, { id: "l2", codigo: "a2", nombre: "Nuevo" }],
    asistencias: [{ alumno_id: "l2", fecha: "2026-10-05", hora: "07:30", hora_salida: "13:00" }, { alumno_id: "zzz", fecha: "2026-10-05", hora: "07:31" }],
    comunicados: [{ fecha: "2026-10-01", titulo: "Igual" }, { fecha: "2026-10-02", titulo: "Otro" }], cursos: [{ nivel: "APSTI", grado: "", nombre: "Redes" }] };
  const remoto = { niveles: [{ nombre: "APSTI" }], grados: [{ nivel: "APSTI", nombre: "G1" }], alumnos: [{ id: "r1", codigo: "a1" }], comunicados: [{ fecha: "2026-10-01", titulo: "Igual" }], cursos: [{ nivel: "APSTI", grado: null, nombre: "REDES" }] };
  const p = planFusion(local, remoto);
  same(p.resumen, { niveles: 1, grados: 1, alumnos: 1, alumnosExistentes: 1, asistencias: 2, comunicados: 1, cursos: 0 });
  const r = asistenciasParaOnline(local.asistencias, local.alumnos, [{ id: "r1", codigo: "a1" }, { id: "r2", codigo: "a2" }], "C1", "U1");
  same(r.filas, [{ colegio_id: "C1", alumno_id: "r2", fecha: "2026-10-05", hora: "07:30", hora_salida: "13:00", registrado_por: "U1", origen: "local" }]);
  assert(r.sinAlumno === 1);
});
test("riesgo: % de faltas sobre días lectivos, justificadas no cuentan, hoy no cuenta, feriados no cuentan", () => {
  const al = [A("a"), A("b"), A("c"), A("z", { estado: "INACTIVO" })];
  // lunes 5 a viernes 9 oct (jueves 8 feriado) + lunes 12 = 5 días lectivos; hoy = 12 (no cuenta) → 4 días
  const nl = mapaNoLectivos([{ fecha: "2026-10-08", tipo: "Feriado", nombre: "Angamos" }]);
  const asis = [X("a", "2026-10-05"), X("a", "2026-10-06"), X("a", "2026-10-07"), X("a", "2026-10-09"), X("b", "2026-10-05"), X("b", "2026-10-06")];
  const r = calcularRiesgo({ alumnos: al, asistencias: asis, justificaciones: [{ alumno_id: "c", fecha: "2026-10-05" }], noLectivos: nl, desde: "2026-10-05", hasta: "2026-10-12", hoy: "2026-10-12", limite: 30 });
  same(r.map((x) => [x.alumno.id, x.faltas, x.justificadas, x.pctFaltas, x.nivel]), [["c", 3, 1, 75, "critico"], ["b", 2, 0, 50, "critico"], ["a", 0, 0, 0, "ok"]]);
  assert(r.every((x) => x.dias === 4) && !r.some((x) => x.alumno.id === "z"));
  const r2 = calcularRiesgo({ alumnos: [A("q")], asistencias: [X("q", "2026-10-05"), X("q", "2026-10-06"), X("q", "2026-10-07")], noLectivos: nl, desde: "2026-10-05", hasta: "2026-10-09", limite: 30 });
  same([r2[0].dias, r2[0].faltas, r2[0].nivel, faltasRestantes(r2[0], 30)], [4, 1, "alerta", 0]);
  same(calcularRiesgo({ alumnos: al, asistencias: [], desde: "2026-10-10", hasta: "2026-10-11" }), []);
});
test("riesgo: nivel «alerta» entre 70 % del límite y el límite", () => {
  const dias = ["2026-10-05", "2026-10-06", "2026-10-07", "2026-10-09", "2026-10-12", "2026-10-13", "2026-10-14", "2026-10-15", "2026-10-16", "2026-10-19"];
  const asis = dias.slice(0, 7).map((d) => X("a", d));            // 3 faltas de 10 = 30 % → crítico; con 8 asistencias, 2 de 10 = 20 % → alerta (≥21 no) → ok
  const r = calcularRiesgo({ alumnos: [A("a")], asistencias: asis, desde: "2026-10-05", hasta: "2026-10-19", limite: 30, noLectivos: mapaNoLectivos([{ fecha: "2026-10-08", tipo: "Feriado", nombre: "x" }]) });
  same([r[0].dias, r[0].faltas, r[0].pctFaltas, r[0].nivel], [10, 3, 30, "critico"]);
  const asis2 = dias.slice(0, 8).map((d) => X("a", d));
  const r3 = calcularRiesgo({ alumnos: [A("a")], asistencias: asis2, desde: "2026-10-05", hasta: "2026-10-19", limite: 30, aviso: 20, noLectivos: mapaNoLectivos([{ fecha: "2026-10-08", tipo: "Feriado", nombre: "x" }]) });
  same([r3[0].pctFaltas, r3[0].nivel], [20, "alerta"]);
});
test("quiosco · decidirAccion: ingreso, repetido, salida tras la permanencia mínima, salida repetida", () => {
  same(decidirAccion(undefined, "07:50", 45), "entrada");
  same(decidirAccion({ hora: "07:50" }, "08:10", 45), "ya_ingreso");
  same(decidirAccion({ hora: "07:50" }, "08:35", 45), "salida");
  same(decidirAccion({ hora: "07:50", hora_salida: "13:00" }, "13:30", 45), "dup_salida");
});

test("horario tarde/noche: puntual hasta 14:10, tardanza hasta el cierre, ventana de ingreso y valores por defecto", () => {
  const h = horarioDe([{ nivel: null, hora_ingreso: "14:00", tolerancia_min: 10, hora_salida: "20:00", ingreso_desde: "13:00", ingreso_hasta: "19:00", permanencia_min: 120 }], "APSTI");
  same([h.limite, h.desde, h.hasta, h.salida, h.permanencia], ["14:10", "13:00", "19:00", "20:00", 120]);
  same(["12:59", "13:00", "14:10", "14:11", "19:00", "19:01"].map((x) => estadoIngreso(h, x)), ["temprano", "puntual", "puntual", "tarde", "tarde", "cerrado"]);
  const d = horarioDe([], "X");
  same([d.definido, d.limite, d.permanencia], [false, "08:00", 120]);
  assert(estadoIngreso(d, "03:00") === "puntual" && estadoIngreso(d, "23:00") === "tarde", "sin ventana definida no se rechaza nada");
  same(horarioDe([{ nivel: null, hora_ingreso: "14:00" }, { nivel: "APSTI", hora_ingreso: "15:00", tolerancia_min: 5 }], "APSTI").limite, "15:05");   // la carrera manda
});
test("salida: solo pasadas 2 horas desde el ingreso (quien sale temprano también espera)", () => {
  same(salidaPermitida("14:05", "15:59", 120), { ok: false, desde: "16:05" });
  same(salidaPermitida("14:05", "16:05", 120), { ok: true, desde: "16:05" });
  same(salidaPermitida("14:05", "14:06", 0).ok, true);
  same(salidaPermitida("23:00", "23:58", 120), { ok: false, desde: "23:59" });   // no se pasa de medianoche
});

/* permisos, qr-seguro, api, estudiante (v4.0 Lote 2) */
import { esAdmin, puede, puedeEntrarAlAula, rolActual } from "../assets/js/permisos.js";
import { DB } from "../assets/js/state.js";
import { qrModoEfectivo, ventana } from "../assets/js/qr-seguro.js";
import { codigoApoderado } from "../assets/js/api.js";
import { vocabulario } from "../assets/js/estudiante/api.js";
import { validarArchivoAula, rutaArchivoAula, rutaEntrega, validarNota, libroNotas, alumnosDelCurso, periodoDe, periodosDe, agendaEstudiante, cursosParaPasarLista, resumenLista } from "../assets/js/api-aula.js";

test("permisos · puede: rol docente no puede acciones de administración", () => {
  const previo = DB.perfil;
  DB.perfil = { colegio_id: "c", rol: "docente" };
  assert(!puede("al-edit"), "docente no debe editar alumnos");
  assert(!puede("pers-crear"), "docente no debe gestionar personal");
  assert(puede("registrar"), "docente debe poder acciones no restringidas");
  DB.perfil = { colegio_id: "c", rol: "Administrador" };
  assert(puede("al-edit"), "administrador debe poder editar alumnos");
  DB.perfil = previo;
});

test("permisos · esAdmin y rolActual: normalización y detección de administrador", () => {
  const previo = DB.perfil;
  const con = (rol) => { DB.perfil = rol === null ? null : { colegio_id: "c", rol }; return [esAdmin(), rolActual()]; };
  same(con("admin"), [true, "admin"]);
  same(con("Administrador"), [true, "admin"]);
  same(con("Coordinador"), [false, "coordinador"]);
  same(con("docente"), [false, "docente"]);
  same(con(null), [false, "docente"]);
  DB.perfil = previo;
});

test("permisos · puedeEntrarAlAula: solo personal docente y administrativo; rechaza estudiante, apoderado y vacíos", () => {
  same(puedeEntrarAlAula("admin"), true);
  same(puedeEntrarAlAula("Administrador"), true);
  same(puedeEntrarAlAula("docente"), true);
  same(puedeEntrarAlAula("Docente"), true);
  same(puedeEntrarAlAula("coordinador"), true);
  same(puedeEntrarAlAula("Coordinador"), true);
  same(puedeEntrarAlAula("auxiliar"), false);
  same(puedeEntrarAlAula("administrativo"), false);
  same(puedeEntrarAlAula("estudiante"), false);
  same(puedeEntrarAlAula("Estudiante"), false);
  same(puedeEntrarAlAula("apoderado"), false);
  same(puedeEntrarAlAula("alumno"), false);
  same(puedeEntrarAlAula(null), false);
  same(puedeEntrarAlAula(undefined), false);
  same(puedeEntrarAlAula(""), false);
  same(puedeEntrarAlAula("invitado"), false);
});

test("qr-seguro · qrModoEfectivo y ventana", () => {
  same(qrModoEfectivo("off"), "off");
  same(qrModoEfectivo(" Opcional "), "opcional");
  same(qrModoEfectivo("desconocido"), "obligatorio");
  same(qrModoEfectivo(null), "obligatorio");
  same(ventana(30000), 1);
  same(ventana(29999), 0);
});

test("api · codigoApoderado: genera código hexadecimal de 12 caracteres", () => {
  const cod = codigoApoderado();
  assert(typeof cod === "string" && cod.length === 12, "debe tener 12 caracteres");
  assert(/^[0-9A-F]{12}$/.test(cod), "debe ser hexadecimal en mayúsculas");
  const cod2 = codigoApoderado();
  assert(cod !== cod2, "dos códigos sucesivos deben ser distintos");
});

test("estudiante api · vocabulario: reemplaza términos colegiales por institucionales", () => {
  same(vocabulario("Error en el colegio: Nivel o grado no existe"), "Error en el instituto: Carrera o ciclo no existe");
  same(vocabulario("Colegio registrado"), "Instituto registrado");
  same(vocabulario(""), "");
});

test("aula · rutaEntrega y validarNota", () => {
  const r = rutaEntrega("col", "cur", "uid", "Mi Tarea.pdf");
  assert(/^col\/cur\/entregas\/uid\/[^/]+\.pdf$/.test(r), "ruta de entrega: " + r);
  assert(validarNota(15, 20) === null && validarNota(0, 20) === null && validarNota(20, 20) === null, "notas válidas");
  assert(validarNota(21, 20) !== null && validarNota(-1, 20) !== null && validarNota(NaN, 20) !== null, "notas inválidas");
});

test("aula · validarArchivoAula: acepta documentos válidos y rechaza tipo, tamaño y vacíos", () => {
  assert(validarArchivoAula({ name: "guia.PDF", size: 1024 }) === null, "pdf válido");
  assert(/no permitido/.test(String(validarArchivoAula({ name: "virus.exe", size: 10 }))), "exe rechazado");
  assert(/10 MB/.test(String(validarArchivoAula({ name: "a.pdf", size: 11 * 1024 * 1024 }))), "más de 10 MB rechazado");
  assert(validarArchivoAula({ name: "a.pdf", size: 0 }) !== null, "vacío rechazado");
});

test("aula · rutaArchivoAula: <colegio>/<curso>/ y nombre sin acentos ni caracteres raros", () => {
  const r = rutaArchivoAula("col-1", "cur-2", "Guía de Matemática #1.pdf");
  assert(r.startsWith("col-1/cur-2/"), "carpetas colegio/curso");
  assert(/^col-1\/cur-2\/[^/]+-Guia-de-Matematica-1\.pdf$/.test(r), "nombre limpio: " + r);
});

test("aula · libroNotas y alumnosDelCurso: promedio sobre 20 con lo calificado", () => {
  const al = [{ id: "a", nombre: "Ana", nivel: "N", grado: "G1" }, { id: "b", nombre: "Beto", nivel: "N", grado: "G2" }, { id: "c", nombre: "Cris", nivel: "N", grado: "G1", aprobado: false }, { id: "d", nombre: "Dan", nivel: "X", grado: "G1" }];
  same(alumnosDelCurso(al, { nivel: "N", grado: "G1" }).map((x) => x.id), ["a"]);
  same(alumnosDelCurso(al, { nivel: "N", grado: null }).map((x) => x.id), ["a", "b"]);
  const acts = [{ id: "1", puntaje_max: 20 }, { id: "2", puntaje_max: 10 }, { id: "3", puntaje_max: 20 }];
  const l = libroNotas(al.slice(0, 2), acts, [{ actividad_id: "1", alumno_id: "a", nota: 16 }, { actividad_id: "2", alumno_id: "a", nota: 8 }, { actividad_id: "1", alumno_id: "b", nota: null }]);
  same(l[0].promedio, 16, "(16+8)/(20+10)*20 = 16");
  same(l[0].notas["3"], null, "actividad sin calificar");
  same(l[1].promedio, null, "sin notas no hay promedio");
});

test("aula · periodos: las actividades antiguas cuentan como periodo 1 y el promedio se calcula por periodo", () => {
  const acts = [{ id: "1", puntaje_max: 20 }, { id: "2", puntaje_max: 20, periodo: 2 }, { id: "3", puntaje_max: 10, periodo: 2 }];
  same(acts.map(periodoDe), [1, 2, 2]);
  same(periodosDe(acts), [1, 2]);
  const ent = [{ actividad_id: "1", alumno_id: "a", nota: 10 }, { actividad_id: "2", alumno_id: "a", nota: 20 }, { actividad_id: "3", alumno_id: "a", nota: 5 }];
  const alu = [{ id: "a", nombre: "Ana" }];
  same(libroNotas(alu, acts.filter((a) => periodoDe(a) === 1), ent)[0].promedio, 10, "periodo 1");
  same(libroNotas(alu, acts.filter((a) => periodoDe(a) === 2), ent)[0].promedio, 16.67, "(20+5)/(20+10)*20");
  same(libroNotas(alu, acts, ent)[0].promedio, 14, "(10+20+5)/50*20");
});

test("aula · agendaEstudiante separa por entregar, vencidas y entregadas con su orden", () => {
  const ahora = new Date("2026-06-10T12:00:00Z").getTime();
  const it = (id, f, entrega = null) => ({ actividad: { id, fecha_limite: f }, entrega });
  const r = agendaEstudiante([
    it("sinfecha", null), it("lejos", "2026-06-30T00:00:00Z"), it("cerca", "2026-06-11T00:00:00Z"),
    it("v-vieja", "2026-05-01T00:00:00Z"), it("v-reciente", "2026-06-09T00:00:00Z"), it("hecha", "2026-05-01T00:00:00Z", { nota: null }),
  ], ahora);
  same(r.porEntregar.map((x) => x.actividad.id), ["cerca", "lejos", "sinfecha"], "próxima primero, sin fecha al final");
  same(r.vencidas.map((x) => x.actividad.id), ["v-reciente", "v-vieja"], "la más reciente primero");
  same(r.entregadas.map((x) => x.actividad.id), ["hecha"], "entregada aunque venció");
});

test("aula · pasar lista: el docente solo ve sus cursos y el resumen cuenta presentes", () => {
  const cursos = [{ id: "c1" }, { id: "c2" }, { id: "c3" }];
  same(cursosParaPasarLista(cursos, true, []).length, 3, "el admin ve todos");
  same(cursosParaPasarLista(cursos, false, ["c2"]).map((c) => c.id), ["c2"], "el docente, los asignados");
  same(cursosParaPasarLista(cursos, false, []).length, 0, "sin asignar no ve ninguno");
  const al = [{ id: "a" }, { id: "b" }, { id: "c" }];
  same(resumenLista(al, new Set(["a", "c", "zz"])), { total: 3, presentes: 2, faltan: 1, pct: 67 });
  same(resumenLista([], new Map()), { total: 0, presentes: 0, faltan: 0, pct: 0 });
});

test("alcance del docente: solo alumnos, carreras y ciclos de sus cursos asignados", () => {
  const datos = {
    alumnos: [{ nivel: "APSTI", grado: "I" }, { nivel: "APSTI", grado: "II" }, { nivel: "MEC", grado: "I" }],
    niveles: [{ nombre: "APSTI" }, { nombre: "MEC" }],
    grados: [{ nivel: "APSTI", nombre: "I" }, { nivel: "APSTI", nombre: "II" }, { nivel: "MEC", nombre: "I" }],
    cursos: [{ id: "c1", nivel: "APSTI", grado: "II" }, { id: "c2", nivel: "MEC", grado: null }, { id: "c3", nivel: "APSTI", grado: "I" }],
  };
  const r = alcanceDocente(datos, ["c1"]);
  same(r.alumnos.length, 1, "solo el ciclo del curso"); same(r.niveles.map((n) => n.nombre), ["APSTI"]); same(r.grados.length, 1); same(r.cursos.length, 1);
  same(alcanceDocente(datos, ["c2"]).alumnos.length, 1, "curso sin ciclo = toda la carrera");
  same(alcanceDocente(datos, []).alumnos.length, 0, "sin asignar no ve nada");
});

test("conectores · clasificarLlave rechaza llaves secretas y acepta las públicas", () => {
  const jwt = (rol) => `x.${Buffer.from(JSON.stringify({ role: rol })).toString("base64url")}.y`;
  assert(!clasificarLlave("").ok); assert(!clasificarLlave("sb_secret_abc123").ok);
  assert(!clasificarLlave(jwt("service_role")).ok, "service_role"); assert(clasificarLlave(jwt("anon")).ok, "anon");
  assert(clasificarLlave("sb_publishable_abc123").ok); assert(clasificarLlave("AIzaSyExample123").ok);
  assert(!clasificarLlave("-----BEGIN PRIVATE KEY----- abc").ok); assert(!clasificarLlave('{"type": "service_account"}').ok);
});
test("conectores · normalizarDestino valida URL e ID de proyecto", () => {
  same(normalizarDestino("supabase", "https://a.supabase.co/").valor, "https://a.supabase.co");
  assert(!normalizarDestino("rest", "http://evil.com").ok, "http solo en localhost"); assert(normalizarDestino("rest", "http://localhost:3000").ok);
  assert(normalizarDestino("firestore", "mi-instituto-1").ok); assert(!normalizarDestino("firestore", "Mi Instituto").ok);
});
test("conectores · formato Firestore, lotes e ids de fila", () => {
  same(aFirestore(3), { integerValue: "3" }); same(aFirestore(1.5), { doubleValue: 1.5 }); same(aFirestore(null), { nullValue: null });
  same(aFirestore(["a"]), { arrayValue: { values: [{ stringValue: "a" }] } });
  same(lotes([1, 2, 3, 4, 5], 2), [[1, 2], [3, 4], [5]]);
  same(idDeFila("curso_docentes", { curso_id: "c", user_id: "u" }, 0), "c_u"); same(idDeFila("alumnos", { id: "x" }, 0), "x"); same(idDeFila("alumnos", {}, 4), "alumnos-4");
});
test("conectores · paquete: se crea, valida e importa por lotes con upsert", async () => {
  const p = await crearPaquete(async (t) => (t === "alumnos" ? [{ id: "1" }, { id: "2" }, { id: "3" }] : t === "niveles" ? [{ id: "n" }] : t === "grados" ? (() => { throw new Error("no existe"); })() : []));
  same(p.conteos.alumnos, 3); assert(p.avisos.length === 1, "aviso por tabla ilegible"); assert(validarPaquete(p).ok); assert(!validarPaquete({}).ok);
  const llamadas = [];
  const f = async (url, init) => { llamadas.push({ url, init }); return { ok: true, status: 200 }; };
  const inf = await importarPaquete({ tipo: "supabase", url: "https://d.supabase.co", llave: "k" }, p, { f, lote: 2 });
  same(inf.map((i) => [i.tabla, i.enviadas]), [["niveles", 1], ["alumnos", 3]]);
  same(llamadas.length, 3, "alumnos en 2 lotes + niveles");
  assert(llamadas[0].url === "https://d.supabase.co/rest/v1/niveles" && /merge-duplicates/.test(llamadas[0].init.headers.Prefer));
  const rest = []; await importarPaquete({ tipo: "rest", url: "https://api.x/l", llave: "t" }, p, { f: async (u) => { rest.push(u); return { ok: true }; } });
  assert(rest[0] === "https://api.x/l/importar/niveles");
});
test("conectores · importar se detiene ante RLS y probar traduce errores", async () => {
  const p = { tablas: { niveles: [{ id: "n" }], alumnos: [{ id: "a" }] } };
  const inf = await importarPaquete({ tipo: "supabase", url: "https://d", llave: "k" }, p, { f: async () => ({ ok: false, status: 403 }) });
  same(inf.length, 1, "no sigue tras el primer fallo"); assert(/RLS/.test(inf[0].error));
  assert((await probarConexion({ tipo: "supabase", url: "https://d", llave: "k" }, async () => ({ ok: false, status: 401 }))).detalle.includes("rechazó"));
  assert(!(await probarConexion({ tipo: "supabase", url: "https://d", llave: "k" }, async () => ({ ok: false, status: 401, text: async () => '{"message":"Invalid API key"}' }))).ok, "llave inválida se rechaza");
  assert((await probarConexion({ tipo: "supabase", url: "https://d", llave: "k" }, async () => ({ ok: false, status: 401, text: async () => '{"message":"Access to schema denied"}' }))).ok, "401 por esquema restringido no es llave mala");
  let cab = {};
  await probarConexion({ tipo: "supabase", url: "https://d", llave: "sb_publishable_x" }, async (_u, i) => { cab = i?.headers ?? {}; return { ok: true, status: 200 }; });
  assert(cab.apikey === "sb_publishable_x" && !cab.Authorization, "llave sb_ solo va en apikey");
  assert((await probarConexion({ tipo: "firestore", url: "mi-proyecto-1", llave: "k" }, async () => ({ ok: false, status: 403 }))).ok, "403 = alcanzable");
  assert(!(await probarConexion({ tipo: "rest", url: "https://d", llave: "k" }, async () => { throw new Error("cors"); })).ok);
  assert(TABLAS[0] === "colegios");
});

await Promise.all(pendientes);   // pruebas asíncronas
/* render */
const fail = results.filter((r) => !r.ok);
if (typeof document !== "undefined") {
  document.body.innerHTML = `<h1>Tests unitarios</h1><p id="summary" data-failed="${fail.length}">${results.length - fail.length}/${results.length} correctos</p><ul>${results.map((r) => `<li style="color:${r.ok ? "#127a4f" : "#b3261e"}">${r.ok ? "✔" : "✘"} ${r.name}${r.msg ? " — " + r.msg : ""}</li>`).join("")}</ul>`;
}
globalThis.__TEST_RESULTS__ = { total: results.length, failed: fail.length, results };
if (typeof document === "undefined") {
  // Ejecución en Node (CI): `npm test`
  results.forEach((r) => console.log(`${r.ok ? "✔" : "✘"} ${r.name}${r.msg ? " — " + r.msg : ""}`));
  console.log(`\n${results.length - fail.length}/${results.length} correctos`);
  process.exitCode = fail.length ? 1 : 0;
}
