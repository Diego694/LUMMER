// Contratos de retorno para funciones puras de utils, calendario y stats.
// Ejecutable en Node (ESM): `node tests/tipos-contrato.js`.
import assert from "node:assert/strict";
import { dateStr, parsearCiclo } from "../assets/js/utils.js";
import { horarioDe, salidaPermitida, tablaLimites } from "../assets/js/calendario.js";
import { decidirAccion, matrizAsistencia, resumenDia } from "../assets/js/stats.js";

// 1. utils.dateStr -> string con formato YYYY-MM-DD
const fecha = dateStr(new Date());
assert.equal(typeof fecha, "string");
assert.match(fecha, /^\d{4}-\d{2}-\d{2}$/);

// 2. utils.parsearCiclo -> { ciclo: string | null, seccion: string | null }
const cicloParsed = parsearCiclo("APSTI · IV CICLO");
assert.equal(typeof cicloParsed, "object");
assert.equal(typeof cicloParsed.ciclo, "string");

// 3. calendario.horarioDe -> HorarioEfectivo ({ definido: boolean, limite: string, permanencia: number })
const hor = horarioDe([], "APSTI");
assert.equal(typeof hor.definido, "boolean");
assert.equal(typeof hor.limite, "string");
assert.equal(typeof hor.permanencia, "number");

// 4. calendario.salidaPermitida -> SalidaPermitidaResult ({ ok: boolean, desde: string })
const salida = salidaPermitida("08:00", "09:00", 60);
assert.equal(typeof salida.ok, "boolean");
assert.equal(typeof salida.desde, "string");

// 5. calendario.tablaLimites -> TablaLimites ({ general: string, porNivel: Record<string, string> })
const limites = tablaLimites([]);
assert.equal(typeof limites.general, "string");
assert.equal(typeof limites.porNivel, "object");

// 6. stats.resumenDia -> ResumenDia ({ activos: number, presentes: number, pct: number, ... })
const resDia = resumenDia([], [], "08:00");
assert.equal(typeof resDia.activos, "number");
assert.equal(typeof resDia.presentes, "number");
assert.equal(typeof resDia.pct, "number");

// 7. stats.matrizAsistencia -> MatrizAsistenciaResult ({ dias: string[], filas: MatrizFila[], resumen: {...} })
const mat = matrizAsistencia([], [], [], ["2026-10-05"], "08:00");
assert.ok(Array.isArray(mat.dias));
assert.ok(Array.isArray(mat.filas));
assert.equal(typeof mat.resumen?.pct, "number");

// 8. stats.decidirAccion -> AccionQuiosco ('entrada' | 'salida' | 'ya_ingreso' | 'dup_salida')
const accion = decidirAccion(undefined, "08:00", 60);
assert.equal(typeof accion, "string");
assert.equal(accion, "entrada");

console.log("✔ 8 contratos de tipo verificados correctamente");
