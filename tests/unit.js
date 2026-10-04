// Tests unitarios sin dependencias. Se ejecutan abriendo tests/tests.html (o con scripts/check.py en CI).
import { CICLOS, addDays, censurarNombre, cicloCorto, compararCiclos, dateStr, esc, etiquetaCiclo, nombreCiclo, parsearCiclo, initials, isWeekend, lastWeekdays, norm, pct, toCSV } from "../assets/js/utils.js";
import { bajaAsistencia, esTardanza, normalizarFilasImport, porGrado, resumenAlumno, resumenDia, serieDiaria } from "../assets/js/stats.js";

const results = [];
const eq = (a, b) => JSON.stringify(a) === JSON.stringify(b);
function test(name, fn) {
  try { fn(); results.push({ name, ok: true }); } catch (e) { results.push({ name, ok: false, msg: e.message }); }
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
