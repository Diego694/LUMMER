// Publicada en Supabase → Edge Functions con «Verify JWT» DESACTIVADO: la llama la base (pg_net) con un secreto propio.
// Edge Function «enviar-push»: toma una fila de cola_push, busca los tokens FCM de la audiencia y envía el aviso con la
// API HTTP v1 de Firebase Cloud Messaging.
//
// Variables de entorno (Edge Functions → Secrets):
//   FCM_SERVICE_ACCOUNT  JSON completo de la cuenta de servicio de Firebase (Configuración del proyecto → Cuentas de servicio).
// SUPABASE_URL y la clave de servicio las inyecta Supabase; la clave de servicio NUNCA sale de aquí.
//
// Seguridad: la llamada debe traer el encabezado x-push-secret igual al guardado en public.config_push (solo legible con la
// clave de servicio). Sin él, 401.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const json = (cuerpo: unknown, status = 200) => new Response(JSON.stringify(cuerpo), { status, headers: { "Content-Type": "application/json" } });
const b64url = (b: ArrayBuffer | string) => {
  const bytes = typeof b === "string" ? new TextEncoder().encode(b) : new Uint8Array(b);
  let s = ""; bytes.forEach((x) => (s += String.fromCharCode(x)));
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

/** Token de acceso OAuth de Google a partir de la cuenta de servicio (JWT RS256). */
async function tokenGoogle(cuenta: { client_email: string; private_key: string }) {
  const ahora = Math.floor(Date.now() / 1000);
  const cab = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const carga = b64url(JSON.stringify({ iss: cuenta.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat: ahora, exp: ahora + 3600 }));
  const pem = cuenta.private_key.replace(/-----[A-Z ]+-----/g, "").replace(/\s+/g, "");
  const llave = await crypto.subtle.importKey("pkcs8", Uint8Array.from(atob(pem), (c) => c.charCodeAt(0)), { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const firma = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", llave, new TextEncoder().encode(`${cab}.${carga}`));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${cab}.${carga}.${b64url(firma)}` }),
  });
  const o = await r.json();
  if (!o.access_token) throw new Error("Google no dio token: " + JSON.stringify(o));
  return o.access_token as string;
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);
  const primera = (nombre: string) => { try { const o = JSON.parse(Deno.env.get(nombre) ?? "{}"); return (o.default ?? Object.values(o)[0]) as string | undefined; } catch { return undefined; } };
  const sb = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? primera("SUPABASE_SECRET_KEYS")!);

  const { data: cfg } = await sb.from("config_push").select("secreto").eq("id", true).maybeSingle();
  const dado = req.headers.get("x-push-secret") ?? "";
  if (!cfg?.secreto || dado.length !== cfg.secreto.length || dado !== cfg.secreto) return json({ error: "No autorizado" }, 401);

  const { id } = await req.json().catch(() => ({ id: null }));
  if (!id) return json({ error: "Falta id" }, 400);
  const { data: aviso } = await sb.from("cola_push").select("*").eq("id", id).maybeSingle();
  if (!aviso) return json({ error: "Aviso no encontrado" }, 404);
  if (aviso.enviado_en) return json({ ok: true, ya: true });

  const cerrar = (resultado: string) => sb.from("cola_push").update({ enviado_en: new Date().toISOString(), resultado }).eq("id", id);
  const crudo = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!crudo) { await cerrar("Falta el secreto FCM_SERVICE_ACCOUNT"); return json({ error: "Falta FCM_SERVICE_ACCOUNT" }, 500); }

  // 1) Destinatarios
  let q = sb.from("dispositivos_push").select("token, user_id").eq("colegio_id", aviso.colegio_id);
  if (aviso.audiencia === "usuario") q = q.eq("user_id", aviso.user_id);
  else if (aviso.audiencia === "estudiantes") q = q.eq("app", "estudiante");
  else if (aviso.audiencia === "personal") q = q.eq("app", "docente");
  else if (aviso.audiencia === "admins") {
    const { data: ps } = await sb.from("perfiles").select("id, rol").eq("colegio_id", aviso.colegio_id);
    const ids = (ps ?? []).filter((p: { rol: string }) => ["admin", "administrador", "coordinador"].includes(String(p.rol ?? "").trim().toLowerCase())).map((p: { id: string }) => p.id);
    q = ids.length ? q.in("user_id", ids) : q.eq("user_id", "00000000-0000-0000-0000-000000000000");
  }
  const { data: tokens } = await q;
  if (!tokens?.length) { await cerrar("Sin dispositivos registrados"); return json({ ok: true, enviados: 0 }); }

  // 2) Envío por FCM HTTP v1
  try {
    const cuenta = JSON.parse(crudo);
    const acceso = await tokenGoogle(cuenta);
    const datos = Object.fromEntries(Object.entries({ tipo: aviso.tipo, ...(aviso.datos ?? {}) }).map(([k, v]) => [k, String(v)]));
    let ok = 0, mal = 0; const muertos: string[] = [];
    for (let i = 0; i < tokens.length; i += 20) {
      await Promise.all(tokens.slice(i, i + 20).map(async (t: { token: string }) => {
        const r = await fetch(`https://fcm.googleapis.com/v1/projects/${cuenta.project_id}/messages:send`, {
          method: "POST", headers: { Authorization: `Bearer ${acceso}`, "Content-Type": "application/json" },
          body: JSON.stringify({ message: { token: t.token, notification: { title: aviso.titulo, body: aviso.cuerpo }, data: datos, android: { priority: "HIGH", notification: { channel_id: "general" } } } }),
        });
        if (r.ok) ok++; else { mal++; const e = await r.text(); if (/UNREGISTERED|NOT_FOUND|INVALID_ARGUMENT/.test(e)) muertos.push(t.token); }
      }));
    }
    if (muertos.length) await sb.from("dispositivos_push").delete().in("token", muertos);
    await cerrar(`${ok} enviados, ${mal} fallidos`);
    return json({ ok: true, enviados: ok, fallidos: mal });
  } catch (e) {
    await cerrar("Error: " + String((e as Error).message ?? e).slice(0, 300));
    return json({ error: "No se pudo enviar" }, 500);
  }
});
