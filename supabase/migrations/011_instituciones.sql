-- 011_instituciones.sql — Multi-institución (superadmin)
--
-- Migración aditiva y re-ejecutable.
--
-- ┌─────────────────────────────────────────────────────────┐
-- │  CÓMO CONVERTIRSE EN SUPERADMIN                         │
-- │                                                         │
-- │  Desde el SQL Editor de Supabase (o psql):              │
-- │                                                         │
-- │    INSERT INTO public.superadmins (user_id)             │
-- │    SELECT id FROM auth.users                            │
-- │    WHERE lower(email) = lower('TU_CORREO');             │
-- │                                                         │
-- │  Sustituye TU_CORREO por el correo real de tu cuenta.   │
-- └─────────────────────────────────────────────────────────┘

-- ═══════════════════════════════════════════════════════════
-- 1. Tabla superadmins
-- ═══════════════════════════════════════════════════════════

create table if not exists public.superadmins (
  user_id uuid primary key references auth.users(id) on delete cascade,
  creado_en timestamptz not null default now()
);

alter table public.superadmins enable row level security;

-- Sin políticas: solo funciones SECURITY DEFINER la leen.
revoke all on public.superadmins from anon, authenticated;

-- ═══════════════════════════════════════════════════════════
-- 2. Columnas adicionales en colegios
-- ═══════════════════════════════════════════════════════════

alter table public.colegios
  add column if not exists creado_en timestamptz not null default now();

alter table public.colegios
  add column if not exists activo boolean not null default true;

-- ═══════════════════════════════════════════════════════════
-- 3. es_superadmin()
-- ═══════════════════════════════════════════════════════════

create or replace function public.es_superadmin()
returns boolean
language sql stable security definer
set search_path = public
as $$
  select exists (
    select 1 from public.superadmins
    where user_id = auth.uid()
  );
$$;

grant execute on function public.es_superadmin() to authenticated;
revoke execute on function public.es_superadmin() from public;
revoke execute on function public.es_superadmin() from anon;

-- ═══════════════════════════════════════════════════════════
-- 4. Función interna _crear_colegio (reutilizada por sa_crear y crear_instituto)
-- ═══════════════════════════════════════════════════════════

create or replace function public._crear_colegio(
  p_nombre text,
  p_codigo text default null
)
returns json
language plpgsql security definer
set search_path = public
as $$
declare
  v_nombre text;
  v_codigo text;
  v_id     uuid;
  v_intentos int := 0;
begin
  -- Validar nombre
  v_nombre := trim(p_nombre);
  if length(v_nombre) < 3 or length(v_nombre) > 80 then
    raise exception 'El nombre debe tener entre 3 y 80 caracteres';
  end if;

  -- Código de registro
  if p_codigo is null or trim(p_codigo) = '' then
    -- Generar código aleatorio de 8 caracteres [A-Z0-9]
    loop
      v_codigo := '';
      for i in 1..8 loop
        v_codigo := v_codigo || substr('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789',
          floor(random() * 36 + 1)::int, 1);
      end loop;
      exit when not exists (
        select 1 from public.colegios where codigo_registro = v_codigo
      );
      v_intentos := v_intentos + 1;
      if v_intentos > 100 then
        raise exception 'No se pudo generar un código único tras 100 intentos';
      end if;
    end loop;
  else
    v_codigo := upper(trim(p_codigo));
    if v_codigo !~ '^[A-Z0-9]{6,20}$' then
      raise exception 'El código debe tener entre 6 y 20 caracteres alfanuméricos (A-Z, 0-9)';
    end if;
    if exists (select 1 from public.colegios where codigo_registro = v_codigo) then
      raise exception 'Ya existe una institución con el código «%»', v_codigo;
    end if;
  end if;

  insert into public.colegios (nombre, codigo_registro)
  values (v_nombre, v_codigo)
  returning id into v_id;

  return json_build_object('id', v_id, 'nombre', v_nombre, 'codigo_registro', v_codigo);
end;
$$;

-- Revocar acceso directo: solo otras funciones DEFINER la llaman
revoke all on function public._crear_colegio(text, text) from public, anon, authenticated;

-- ═══════════════════════════════════════════════════════════
-- 5. Funciones sa_* (superadmin, RPC desde el frontend)
-- ═══════════════════════════════════════════════════════════

-- ── sa_listar ────────────────────────────────────────────
create or replace function public.sa_listar()
returns json
language plpgsql stable security definer
set search_path = public
as $$
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;

  return (
    select coalesce(json_agg(row_to_json(x) order by x.nombre), '[]'::json)
    from (
      select
        c.id,
        c.nombre,
        c.codigo_registro,
        c.activo,
        c.creado_en,
        (select count(*) from public.alumnos a where a.colegio_id = c.id) as alumnos,
        (select count(*) from public.perfiles p where p.colegio_id = c.id) as personal,
        coalesce(c.id = public.mi_colegio(), false) as actual
      from public.colegios c
    ) x
  );
end;
$$;

grant execute on function public.sa_listar() to authenticated;
revoke execute on function public.sa_listar() from public;
revoke execute on function public.sa_listar() from anon;

-- ── sa_crear ─────────────────────────────────────────────
create or replace function public.sa_crear(
  p_nombre text,
  p_codigo text default null
)
returns json
language plpgsql security definer
set search_path = public
as $$
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;

  return public._crear_colegio(p_nombre, p_codigo);
end;
$$;

grant execute on function public.sa_crear(text, text) to authenticated;
revoke execute on function public.sa_crear(text, text) from public;
revoke execute on function public.sa_crear(text, text) from anon;

-- ── sa_renombrar ─────────────────────────────────────────
create or replace function public.sa_renombrar(
  p_id uuid,
  p_nombre text
)
returns void
language plpgsql security definer
set search_path = public
as $$
declare
  v_nombre text;
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;

  v_nombre := trim(p_nombre);
  if length(v_nombre) < 3 or length(v_nombre) > 80 then
    raise exception 'El nombre debe tener entre 3 y 80 caracteres';
  end if;

  update public.colegios set nombre = v_nombre where id = p_id;
  if not found then
    raise exception 'No se encontró la institución';
  end if;
end;
$$;

grant execute on function public.sa_renombrar(uuid, text) to authenticated;
revoke execute on function public.sa_renombrar(uuid, text) from public;
revoke execute on function public.sa_renombrar(uuid, text) from anon;

-- ── sa_activar ───────────────────────────────────────────
create or replace function public.sa_activar(
  p_id uuid,
  p_activo boolean
)
returns void
language plpgsql security definer
set search_path = public
as $$
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;

  update public.colegios set activo = p_activo where id = p_id;
  if not found then
    raise exception 'No se encontró la institución';
  end if;
end;
$$;

grant execute on function public.sa_activar(uuid, boolean) to authenticated;
revoke execute on function public.sa_activar(uuid, boolean) from public;
revoke execute on function public.sa_activar(uuid, boolean) from anon;

-- ── sa_entrar ────────────────────────────────────────────
create or replace function public.sa_entrar(
  p_colegio uuid
)
returns json
language plpgsql security definer
set search_path = public
as $$
declare
  v_colegio public.colegios%rowtype;
  v_nombre  text;
  v_email   text;
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;

  select * into v_colegio from public.colegios where id = p_colegio;
  if v_colegio is null then
    raise exception 'No se encontró la institución';
  end if;
  if not v_colegio.activo then
    raise exception 'La institución no está activa';
  end if;

  -- Rechazar si es un estudiante
  if exists (select 1 from public.alumnos where user_id = auth.uid()) then
    raise exception 'Un estudiante no puede usar esta función';
  end if;

  -- Obtener nombre actual si ya tiene perfil, sino usar parte local del correo
  select nombre into v_nombre from public.perfiles where id = auth.uid();
  if v_nombre is null then
    select split_part(email, '@', 1) into v_email from auth.users where id = auth.uid();
    v_nombre := coalesce(v_email, 'Admin');
  end if;

  -- Upsert perfil como Administrador del colegio destino
  insert into public.perfiles (id, nombre, rol, carrera, colegio_id)
  values (auth.uid(), v_nombre, 'Administrador', null, p_colegio)
  on conflict (id) do update
    set colegio_id = excluded.colegio_id,
        rol = 'Administrador',
        carrera = null;

  return json_build_object('colegio_id', p_colegio, 'nombre', v_colegio.nombre);
end;
$$;

grant execute on function public.sa_entrar(uuid) to authenticated;
revoke execute on function public.sa_entrar(uuid) from public;
revoke execute on function public.sa_entrar(uuid) from anon;

-- ── sa_asignar_admin ─────────────────────────────────────
create or replace function public.sa_asignar_admin(
  p_colegio uuid,
  p_email text
)
returns json
language plpgsql security definer
set search_path = public
as $$
declare
  v_user_id uuid;
  v_nombre  text;
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;

  -- Buscar cuenta por correo
  select id into v_user_id from auth.users where lower(email) = lower(trim(p_email));
  if v_user_id is null then
    raise exception 'No existe una cuenta con ese correo. Créala primero en Supabase → Authentication → Users.';
  end if;

  -- Rechazar si es estudiante
  if exists (select 1 from public.alumnos where user_id = v_user_id) then
    raise exception 'No se puede asignar como administrador a un estudiante';
  end if;

  -- Conservar nombre si ya tiene perfil
  select nombre into v_nombre from public.perfiles where id = v_user_id;
  if v_nombre is null then
    v_nombre := split_part(p_email, '@', 1);
  end if;

  -- Upsert perfil como Administrador
  insert into public.perfiles (id, nombre, rol, carrera, colegio_id)
  values (v_user_id, v_nombre, 'Administrador', null, p_colegio)
  on conflict (id) do update
    set colegio_id = excluded.colegio_id,
        rol = 'Administrador',
        carrera = null;

  return json_build_object('id', v_user_id, 'email', p_email);
end;
$$;

grant execute on function public.sa_asignar_admin(uuid, text) to authenticated;
revoke execute on function public.sa_asignar_admin(uuid, text) from public;
revoke execute on function public.sa_asignar_admin(uuid, text) from anon;

-- ═══════════════════════════════════════════════════════════
-- 6. crear_instituto (para SQL Editor / service_role)
-- ═══════════════════════════════════════════════════════════

create or replace function public.crear_instituto(
  p_nombre text,
  p_admin_email text default null,
  p_codigo text default null
)
returns json
language plpgsql security definer
set search_path = public
as $$
declare
  v_result  json;
  v_user_id uuid;
  v_nombre  text;
  v_colegio_id uuid;
begin
  -- Crear el colegio (validación incluida)
  v_result := public._crear_colegio(p_nombre, p_codigo);
  v_colegio_id := (v_result->>'id')::uuid;

  -- Si se dio un correo de admin, asignarlo
  if p_admin_email is not null and trim(p_admin_email) <> '' then
    select id into v_user_id from auth.users
    where lower(email) = lower(trim(p_admin_email));
    if v_user_id is null then
      raise exception 'No existe una cuenta con ese correo. Créala primero en Supabase → Authentication → Users.';
    end if;

    -- Rechazar si es estudiante
    if exists (select 1 from public.alumnos where user_id = v_user_id) then
      raise exception 'No se puede asignar como administrador a un estudiante';
    end if;

    -- Conservar nombre si ya tiene perfil
    select nombre into v_nombre from public.perfiles where id = v_user_id;
    if v_nombre is null then
      v_nombre := split_part(p_admin_email, '@', 1);
    end if;

    insert into public.perfiles (id, nombre, rol, carrera, colegio_id)
    values (v_user_id, v_nombre, 'Administrador', null, v_colegio_id)
    on conflict (id) do update
      set colegio_id = excluded.colegio_id,
          rol = 'Administrador',
          carrera = null;
  end if;

  return v_result;
end;
$$;

revoke all on function public.crear_instituto(text, text, text) from public, anon, authenticated;
