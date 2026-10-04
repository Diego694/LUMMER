// Publicada en Supabase → Edge Functions (Verify JWT desactivado: la función valida la sesión y el rol por sí misma).
// Edge Function «gestionar-personal»: el administrador crea cuentas de docentes (correo + contraseña), cambia sus
// contraseñas o las elimina, desde el panel. Crear cuentas exige la clave de servicio (SUPABASE_SERVICE_ROLE_KEY),
// que Supabase inyecta en el entorno de la función y NUNCA llega al navegador.
//
// Seguridad: (1) la plataforma exige un JWT válido; (2) aquí se comprueba que quien llama sea ADMINISTRADOR
// (es_admin() con su propio token); (3) solo se actúa sobre cuentas del MISMO instituto.
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { ...CORS, "Content-Type": "application/json" } });

const ROLES: Record<string, string> = { administrador: "Administrador", admin: "Administrador", docente: "Docente", coordinador: "Coordinador" };
const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);
  try {
    // Supabase inyecta las claves como SUPABASE_ANON_KEY / SUPABASE_SERVICE_ROLE_KEY (modelo clásico) o como JSON en
    // SUPABASE_PUBLISHABLE_KEYS / SUPABASE_SECRET_KEYS (modelo nuevo): se admiten ambos.
    const primera = (nombre: string) => {
      try { const o = JSON.parse(Deno.env.get(nombre) ?? "{}"); return (o.default ?? Object.values(o)[0]) as string | undefined; } catch { return undefined; }
    };
    const url = Deno.env.get("SUPABASE_URL")!;
    const anon = Deno.env.get("SUPABASE_ANON_KEY") ?? primera("SUPABASE_PUBLISHABLE_KEYS")!;
    const servicio = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? primera("SUPABASE_SECRET_KEYS")!;

    // 1) ¿Quién llama? Se usa su propio token: así es_admin()/mi_colegio() responden por esa persona.
    const quien = createClient(url, anon, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
    const { data: sesion } = await quien.auth.getUser();
    if (!sesion?.user) return json({ error: "Sesión no válida. Vuelve a iniciar sesión." }, 401);
    const { data: esAdmin } = await quien.rpc("es_admin");
    if (esAdmin !== true) return json({ error: "Solo el administrador puede gestionar al personal." }, 403);
    const { data: colegioId } = await quien.rpc("mi_colegio");
    if (!colegioId) return json({ error: "Tu cuenta no tiene instituto." }, 403);

    const admin = createClient(url, servicio, { auth: { autoRefreshToken: false, persistSession: false } });
    const b = await req.json();

    // Acciones sobre una cuenta existente: solo si es personal de ESTE instituto.
    const delMismoInstituto = async (id: string) => {
      const { data } = await admin.from("perfiles").select("id").eq("id", id).eq("colegio_id", colegioId).maybeSingle();
      return !!data;
    };

    if (b.accion === "crear") {
      const email = String(b.email ?? "").trim().toLowerCase();
      const password = String(b.password ?? "");
      const rol = ROLES[String(b.rol ?? "docente").trim().toLowerCase()];
      const carrera = String(b.carrera ?? "").trim() || null;
      const nombre = String(b.nombre ?? "").trim().slice(0, 80) || null;
      if (!EMAIL.test(email)) return json({ error: "Escribe un correo válido." }, 400);
      if (password.length < 8 || password.length > 72) return json({ error: "La contraseña debe tener entre 8 y 72 caracteres." }, 400);
      if (!rol) return json({ error: "Rol no válido." }, 400);
      if (rol === "Coordinador") {
        const { data: ok } = await admin.from("niveles").select("id").eq("colegio_id", colegioId).eq("nombre", carrera ?? "").maybeSingle();
        if (!ok) return json({ error: "El coordinador necesita una carrera existente." }, 400);
      }
      const { data: creado, error: eCrear } = await admin.auth.admin.createUser({
        email, password, email_confirm: true, user_metadata: { nombre },
      });
      if (eCrear || !creado?.user) {
        const ya = /already|registered|exists/i.test(eCrear?.message ?? "");
        return json({ error: ya ? "Ya existe una cuenta con ese correo. Usa «Dar acceso» para asignarle un rol." : (eCrear?.message ?? "No se pudo crear la cuenta.") }, ya ? 409 : 400);
      }
      const { error: ePerfil } = await admin.from("perfiles").insert({
        id: creado.user.id, colegio_id: colegioId, rol, nombre, carrera: rol === "Coordinador" ? carrera : null,
      });
      if (ePerfil) {
        await admin.auth.admin.deleteUser(creado.user.id);   // no dejar una cuenta sin perfil
        return json({ error: "No se pudo registrar al docente: " + ePerfil.message }, 500);
      }
      return json({ id: creado.user.id, email, rol });
    }

    if (b.accion === "password") {
      const password = String(b.password ?? "");
      if (password.length < 8 || password.length > 72) return json({ error: "La contraseña debe tener entre 8 y 72 caracteres." }, 400);
      if (!(await delMismoInstituto(String(b.id)))) return json({ error: "Esa cuenta no pertenece a tu instituto." }, 404);
      const { error } = await admin.auth.admin.updateUserById(String(b.id), { password });
      if (error) return json({ error: error.message }, 400);
      return json({ ok: true });
    }

    if (b.accion === "eliminar") {
      if (String(b.id) === sesion.user.id) return json({ error: "No puedes eliminar tu propia cuenta." }, 400);
      if (!(await delMismoInstituto(String(b.id)))) return json({ error: "Esa cuenta no pertenece a tu instituto." }, 404);
      const { error } = await admin.auth.admin.deleteUser(String(b.id));   // el perfil se borra en cascada
      if (error) return json({ error: error.message }, 400);
      return json({ ok: true });
    }

    return json({ error: "Acción no válida." }, 400);
  } catch (e) {
    return json({ error: "Error interno: " + (e instanceof Error ? e.message : String(e)) }, 500);
  }
});
