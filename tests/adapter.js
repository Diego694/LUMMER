// Tests del adaptador SupabaseBackend contra un cliente simulado (no requiere red ni credenciales).
import { CONFIG } from "../assets/js/config.js";

try { localStorage.removeItem("ra-force-demo"); } catch { /* sin storage */ }  // el modo demo forzado (?demo=1) falsearía estas pruebas
CONFIG.SUPABASE_URL = "https://fake.supabase.co";
CONFIG.SUPABASE_ANON_KEY = "fake-key";

/** Cliente falso: registra cada llamada encadenada y responde lo que decida `script(consulta)`. */
function fakeClient(script) {
  const queries = [];
  const from = (tabla) => {
    const q = { tabla, ops: [] };
    queries.push(q);
    const proxy = new Proxy({}, {
      get: (_, k) => (k === "then" ? (res) => res(script(q)) : (...args) => { q.ops.push([k, args]); return proxy; }),
    });
    return proxy;
  };
  return { from, queries, auth: { getUser: async () => ({ data: { user: { id: "u1" } } }) } };
}

const out = [];
const check = async (name, fn) => {
  try { await fn(); out.push({ name, ok: true }); } catch (e) { out.push({ name, ok: false, msg: e.message }); }
};
const assert = (c, m = "aserción fallida") => { if (!c) throw new Error(m); };
const op = (q, name) => q.ops.find(([k]) => k === name)?.[1];

async function backendCon(script) {
  window.supabase = { createClient: () => fakeClient(script) };
  const { api } = await import("../assets/js/api.js?t=" + Math.random());
  assert(api.mode === "supabase", "debería usar el adaptador Supabase");
  return api;
}

await check("loadAll pagina de a 1000 filas", async () => {
  const api = await backendCon((q) => {
    if (q.tabla !== "alumnos") return { data: [], error: null };
    const [from] = op(q, "range");
    return { data: Array.from({ length: from === 0 ? 1000 : 5 }, (_, i) => ({ id: from + i })), error: null };
  });
  const d = await api.loadAll("c1");
  assert(d.alumnos.length === 1005, `alumnos=${d.alumnos.length}`);
});

await check("loadAll filtra siempre por colegio_id", async () => {
  const api = await backendCon(() => ({ data: [], error: null }));
  api.sb.queries.length = 0;
  await api.loadAll("c1");
  assert(api.sb.queries.length === 9 && api.sb.queries.every((q) => op(q, "eq")?.[0] === "colegio_id" && op(q, "eq")?.[1] === "c1"));
});

await check("registrarAsistencia traduce 23505 a 'duplicate'", async () => {
  const api = await backendCon(() => ({ error: { code: "23505", message: "dup" } }));
  try { await api.registrarAsistencia({}); assert(false, "debía lanzar"); } catch (e) { assert(e.code === "duplicate", e.code); }
});

await check("registrarMasivo hace upsert ignorando duplicados y devuelve los insertados", async () => {
  const api = await backendCon(() => ({ data: [{ id: 1 }, { id: 2 }], error: null }));
  const n = await api.registrarMasivo([{}, {}, {}]);
  const [, opts] = op(api.sb.queries.at(-1), "upsert");
  assert(n === 2 && opts.onConflict === "alumno_id,fecha" && opts.ignoreDuplicates === true);
});

await check("save: insert incluye colegio_id y update usa eq(id)", async () => {
  const api = await backendCon(() => ({ error: null }));
  await api.save("alumnos", { colegio_id: "c1", nombre: "A" });
  assert(op(api.sb.queries.at(-1), "insert")[0].colegio_id === "c1");
  await api.save("alumnos", { colegio_id: "c1", nombre: "B" }, "id9");
  const q = api.sb.queries.at(-1);
  assert(op(q, "update") && op(q, "eq")[1] === "id9");
});

await check("save rechaza tablas no permitidas", async () => {
  const api = await backendCon(() => ({ error: null }));
  try { await api.save("perfiles", {}); assert(false, "debía lanzar"); } catch (e) { assert(/Tabla inválida/.test(e.message)); }
});

await check("getProfile sin perfil lanza code 'profile'", async () => {
  const api = await backendCon(() => ({ data: null, error: { message: "no rows" } }));
  try { await api.getProfile({ id: "u" }); assert(false, "debía lanzar"); } catch (e) { assert(e.code === "profile"); }
});

await check("upsertAlumnos procesa en lotes de 200", async () => {
  const api = await backendCon(() => ({ error: null }));
  api.sb.queries.length = 0;
  const n = await api.upsertAlumnos(Array.from({ length: 450 }, (_, i) => ({ codigo: "c" + i })));
  assert(n === 450 && api.sb.queries.length === 3, `lotes=${api.sb.queries.length}`);
});

/* ---- Portal del estudiante (adaptador Supabase, cliente simulado) ---- */
function clienteEstudiante(rpcResp = { data: {}, error: null }) {
  const rpcs = [], uploads = [], removed = [];
  const bucket = {
    upload: async (path, blob, opts) => { uploads.push({ path, opts }); return { error: null }; },
    list: async () => ({ data: [{ name: "foto-vieja.jpg" }, { name: uploads.at(-1)?.path.split("/")[1] }] }),
    remove: async (p) => { removed.push(...p); return { error: null }; },
    createSignedUrl: async (path, s) => ({ data: { signedUrl: `https://x.supabase.co/sign/${path}?exp=${s}` }, error: null }),
  };
  return { rpcs, uploads, removed, client: { auth: {}, rpc: async (n, a) => { rpcs.push({ n, a }); return rpcResp; }, storage: { from: () => bucket } } };
}
async function estudianteCon(rpcResp) {
  const s = clienteEstudiante(rpcResp);
  window.supabase = { createClient: () => s.client };
  const { api } = await import("../assets/js/estudiante/api.js?t=" + Math.random());
  assert(api.mode === "supabase", "debería usar el adaptador Supabase");
  return { api, ...s };
}

await check("Estudiante: registrar llama a la función SQL con los parámetros correctos (sin escribir tablas)", async () => {
  const { api, rpcs } = await estudianteCon();
  await api.registrar({ id: "u1" }, { codigoColegio: "K7M3QX9P", nombres: "Lucía", apellidos: "Quispe", nivel: "Primaria", grado: "1er grado", apoderado: "", dni: "" });
  const r = rpcs[0];
  assert(r.n === "registrar_estudiante" && r.a.p_codigo_colegio === "K7M3QX9P" && r.a.p_nombres === "Lucía" && r.a.p_dni === null, JSON.stringify(r));
});

await check("Estudiante: subirFoto sube a <uid>/foto-*.jpg, registra la ruta y borra las fotos anteriores", async () => {
  const { api, uploads, rpcs, removed } = await estudianteCon();
  await api.subirFoto({ id: "u1" }, new Blob(["x"], { type: "image/jpeg" }));
  const up = uploads[0];
  assert(/^u1\/foto-\d+\.jpg$/.test(up.path) && up.opts.contentType === "image/jpeg", up.path);
  assert(rpcs.at(-1).n === "actualizar_mi_foto" && rpcs.at(-1).a.p_path === up.path);
  assert(removed.includes("u1/foto-vieja.jpg") && !removed.includes(up.path), JSON.stringify(removed));
});

await check("Estudiante: errores de las funciones SQL llegan como mensaje legible", async () => {
  const { api } = await estudianteCon({ data: null, error: { message: "Código de colegio inválido", code: "P0001" } });
  // El servidor responde con "colegio"; el portal lo muestra con el vocabulario del instituto.
  try { await api.infoColegio("x"); assert(false, "debía lanzar"); } catch (e) { assert(e.message === "Código de instituto inválido", e.message); }
  const { vocabulario } = await import("../assets/js/estudiante/api.js");
  assert(vocabulario("Nivel o grado inválido") === "Carrera o ciclo inválido" && vocabulario("Esta cuenta pertenece al personal del colegio") === "Esta cuenta pertenece al personal del instituto");
});

await check("Docente: fotoUrl pide URL firmada de 1 h, la cachea y devuelve null sin foto", async () => {
  let llamadas = 0;
  const api = await backendCon(() => ({ error: null }));
  api.sb.storage = { from: () => ({ createSignedUrl: async (p, s) => { llamadas++; return { data: { signedUrl: `u/${p}?${s}` }, error: null }; } }) };
  const a = { foto_path: "u1/foto-1.jpg" };
  const u1 = await api.fotoUrl(a), u2 = await api.fotoUrl(a);
  assert(u1 === "u/u1/foto-1.jpg?3600" && u2 === u1 && llamadas === 1, `llamadas=${llamadas} url=${u1}`);
  assert((await api.fotoUrl({})) === null);
});

await check("Docente: código de registro duplicado → mensaje claro; lectura sin migración → null", async () => {
  const api = await backendCon((q) => (op(q, "update") ? { error: { code: "23505", message: "dup" } } : { data: null, error: { message: "column codigo_registro does not exist" } }));
  try { await api.setCodigoRegistro("c1", "ABC"); assert(false, "debía lanzar"); } catch (e) { assert(e.code === "duplicate" && /ya está en uso/.test(e.message)); }
  assert((await api.getCodigoRegistro("c1")) === null);
});

const fail = out.filter((r) => !r.ok);
const html = `<h2>Adaptador Supabase (simulado)</h2><p id="summary-adapter" data-failed="${fail.length}">${out.length - fail.length}/${out.length} correctos</p><ul>${out.map((r) => `<li style="color:${r.ok ? "#127a4f" : "#b3261e"}">${r.ok ? "✔" : "✘"} ${r.name}${r.msg ? " — " + r.msg : ""}</li>`).join("")}</ul>`;
document.body.insertAdjacentHTML("beforeend", html);
globalThis.__ADAPTER_RESULTS__ = { total: out.length, failed: fail.length };
