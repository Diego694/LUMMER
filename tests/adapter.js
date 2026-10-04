// Tests del adaptador SupabaseBackend contra un cliente simulado (no requiere red ni credenciales).
import { CONFIG } from "../assets/js/config.js";

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
  assert(api.sb.queries.length === 5 && api.sb.queries.every((q) => op(q, "eq")?.[0] === "colegio_id" && op(q, "eq")?.[1] === "c1"));
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

const fail = out.filter((r) => !r.ok);
const html = `<h2>Adaptador Supabase (simulado)</h2><p id="summary-adapter" data-failed="${fail.length}">${out.length - fail.length}/${out.length} correctos</p><ul>${out.map((r) => `<li style="color:${r.ok ? "#127a4f" : "#b3261e"}">${r.ok ? "✔" : "✘"} ${r.name}${r.msg ? " — " + r.msg : ""}</li>`).join("")}</ul>`;
document.body.insertAdjacentHTML("beforeend", html);
globalThis.__ADAPTER_RESULTS__ = { total: out.length, failed: fail.length };
