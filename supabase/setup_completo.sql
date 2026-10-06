-- Instalación completa (GENERADO por scripts/build_setup.py; no editar a mano).
-- Ejecutar en Supabase → SQL Editor sobre un proyecto NUEVO. Luego, el bloque «Alta de un instituto» de schema.sql.


-- ======================== schema.sql ========================
-- =====================================================================
--  LUMMER — esquema de base de datos (PostgreSQL / Supabase)
--  Ejecutar completo en: Supabase → SQL Editor → New query → Run.
--  Es idempotente: se puede volver a ejecutar sin perder datos.
--  Modelo multi-instituto: cada usuario pertenece a un instituto (tabla perfiles) y las
--  políticas RLS solo le dejan ver/modificar filas de SU instituto.
-- =====================================================================

create extension if not exists pgcrypto;

-- ---------- Tablas ----------
create table if not exists public.colegios (
  id          uuid primary key default gen_random_uuid(),
  nombre      text not null,
  created_at  timestamptz not null default now()
);

create table if not exists public.perfiles (
  id          uuid primary key references auth.users(id) on delete cascade,
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  rol         text not null default 'Administrador' check (lower(rol) in ('admin', 'administrador', 'docente', 'coordinador', 'auxiliar')),
  carrera     text,                                   -- solo para el rol coordinador (ver migración 004)
  nombre      text
);

create table if not exists public.niveles (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nombre      text not null,
  unique (colegio_id, nombre)
);

create table if not exists public.grados (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nivel       text not null,
  nombre      text not null,
  unique (colegio_id, nivel, nombre)
);

create table if not exists public.alumnos (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  codigo      text not null,
  nombre      text not null,
  nivel       text not null default '',
  grado       text not null default '',
  apoderado   text not null default '',
  estado      text not null default 'ACTIVO' check (estado in ('ACTIVO', 'INACTIVO')),
  unique (colegio_id, codigo)
);

create table if not exists public.docentes (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nombre      text not null,
  profesion   text not null default '',
  rol         text not null default 'Docente' check (rol in ('Docente', 'Coordinador', 'Auxiliar', 'Administrativo')),
  estado      text not null default 'ACTIVO' check (estado in ('ACTIVO', 'INACTIVO'))
);

create table if not exists public.comunicados (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  titulo      text not null,
  mensaje     text not null,
  fecha       date not null default current_date
);

create table if not exists public.asistencias (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  alumno_id      uuid not null references public.alumnos(id) on delete cascade,
  fecha          date not null,
  hora           time not null,
  registrado_por uuid references auth.users(id) on delete set null,
  unique (alumno_id, fecha)           -- una asistencia por alumno y día
);

-- ---------- Índices ----------
create index if not exists idx_alumnos_colegio_grado   on public.alumnos (colegio_id, nivel, grado);
create index if not exists idx_asistencias_colegio_fecha on public.asistencias (colegio_id, fecha);
create index if not exists idx_comunicados_colegio_fecha on public.comunicados (colegio_id, fecha desc);

-- ---------- Funciones auxiliares (SECURITY DEFINER evita recursión de RLS en perfiles) ----------
create or replace function public.mi_colegio() returns uuid
language sql stable security definer set search_path = public as $$
  select colegio_id from public.perfiles where id = auth.uid()
$$;

create or replace function public.es_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((select lower(trim(rol)) in ('admin', 'administrador') from public.perfiles where id = auth.uid()), false)
$$;

revoke all on function public.mi_colegio(), public.es_admin() from public, anon;
grant execute on function public.mi_colegio(), public.es_admin() to authenticated;

-- ---------- Row Level Security ----------
alter table public.colegios     enable row level security;
alter table public.perfiles     enable row level security;
alter table public.niveles      enable row level security;
alter table public.grados       enable row level security;
alter table public.alumnos      enable row level security;
alter table public.docentes     enable row level security;
alter table public.comunicados  enable row level security;
alter table public.asistencias  enable row level security;

drop policy if exists colegios_select on public.colegios;
create policy colegios_select on public.colegios for select to authenticated using (id = public.mi_colegio());

drop policy if exists colegios_update on public.colegios;
create policy colegios_update on public.colegios for update to authenticated using (id = public.mi_colegio() and public.es_admin()) with check (id = public.mi_colegio());

drop policy if exists perfiles_select on public.perfiles;
create policy perfiles_select on public.perfiles for select to authenticated using (id = auth.uid());

-- Catálogos y padrón: todos los usuarios del colegio leen; solo 'admin' escribe.
do $$
declare t text;
begin
  foreach t in array array['niveles', 'grados', 'alumnos', 'docentes', 'comunicados'] loop
    execute format('drop policy if exists %I_select on public.%I', t, t);
    execute format('create policy %I_select on public.%I for select to authenticated using (colegio_id = public.mi_colegio())', t, t);
    execute format('drop policy if exists %I_write on public.%I', t, t);
    execute format('create policy %I_write on public.%I for all to authenticated using (colegio_id = public.mi_colegio() and public.es_admin()) with check (colegio_id = public.mi_colegio() and public.es_admin())', t, t);
  end loop;
end $$;

-- Asistencias: cualquier usuario del colegio registra y consulta; solo 'admin' corrige o borra.
drop policy if exists asistencias_select on public.asistencias;
create policy asistencias_select on public.asistencias for select to authenticated using (colegio_id = public.mi_colegio());
drop policy if exists asistencias_insert on public.asistencias;
create policy asistencias_insert on public.asistencias for insert to authenticated
  with check (colegio_id = public.mi_colegio() and registrado_por = auth.uid());
drop policy if exists asistencias_admin on public.asistencias;
create policy asistencias_admin on public.asistencias for update to authenticated
  using (colegio_id = public.mi_colegio() and public.es_admin()) with check (colegio_id = public.mi_colegio());
drop policy if exists asistencias_delete on public.asistencias;
create policy asistencias_delete on public.asistencias for delete to authenticated
  using (colegio_id = public.mi_colegio() and public.es_admin());

-- ---------- Permisos ----------
grant usage on schema public to authenticated;
grant select on public.colegios, public.perfiles to authenticated;
grant update on public.colegios to authenticated;
grant select, insert, update, delete on public.niveles, public.grados, public.alumnos, public.docentes, public.comunicados, public.asistencias to authenticated;

-- =====================================================================
--  ALTA DE UN INSTITUTO Y SU PRIMER ADMINISTRADOR (ejecutar UNA vez, tras crear el usuario)
--  1) Supabase → Authentication → Users → Add user (correo + contraseña).
--  2) Copia el UUID del usuario y reemplázalo abajo; luego ejecuta este bloque:
--
--  with c as (insert into public.colegios (nombre) values ('Mi Instituto') returning id)
--  insert into public.perfiles (id, colegio_id, rol, nombre)
--  select 'UUID-DEL-USUARIO', c.id, 'Administrador', 'Administrador' from c;
--
--  insert into public.niveles (colegio_id, nombre)
--  select colegio_id, n from public.perfiles, unnest(array['MI CARRERA']) n
--  where id = 'UUID-DEL-USUARIO';
-- =====================================================================


-- ======================== 002_estudiantes.sql ========================
-- =====================================================================
--  002 · Portal de estudiantes (auto‑registro, foto y QR único)
--  Migración ADITIVA y re‑ejecutable: no borra ni modifica datos ni políticas existentes.
--  Ejecutar en Supabase → SQL Editor.
--
--  Diseño de seguridad:
--   * Los estudiantes NO tienen fila en `perfiles` (las políticas existentes dan acceso a todo el
--     colegio a quien la tenga). Operan solo mediante las funciones de abajo (SECURITY DEFINER).
--   * Un estudiante únicamente puede LEER su propio registro; no puede modificar nada directamente.
--   * Para registrarse necesita el "código de registro" que el colegio genera; el administrador debe
--     APROBARLO antes de que su QR registre asistencia.
--   * Fotos en un bucket PRIVADO; el personal las ve con URLs firmadas temporales.
-- =====================================================================

-- ---------- Columnas nuevas ----------
alter table public.colegios add column if not exists codigo_registro text;
create unique index if not exists colegios_codigo_registro_uq
  on public.colegios (upper(codigo_registro)) where codigo_registro is not null;

alter table public.alumnos
  add column if not exists user_id uuid references auth.users(id) on delete set null,
  add column if not exists nombres text,
  add column if not exists apellidos text,
  add column if not exists dni text,
  add column if not exists foto_path text,
  add column if not exists aprobado boolean not null default true,   -- los alumnos existentes quedan aprobados
  add column if not exists consentimiento_en timestamptz,
  add column if not exists registrado_en timestamptz not null default now();
create unique index if not exists alumnos_user_id_uq on public.alumnos (user_id) where user_id is not null;

-- ---------- Funciones (el estudiante solo puede usar estas) ----------
create or replace function public.info_colegio(p_codigo text) returns json
language plpgsql stable security definer set search_path = public as $$
declare c record;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select id, nombre into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo));
  if not found then return null; end if;
  return json_build_object(
    'nombre', c.nombre,
    'niveles', (select coalesce(json_agg(nombre order by nombre), '[]'::json) from public.niveles where colegio_id = c.id),
    'grados',  (select coalesce(json_agg(json_build_object('nivel', nivel, 'nombre', nombre) order by nivel, nombre), '[]'::json)
                from public.grados where colegio_id = c.id));
end $$;

create or replace function public.registrar_estudiante(
  p_codigo_colegio text, p_nombres text, p_apellidos text, p_nivel text, p_grado text,
  p_apoderado text default '', p_dni text default null) returns json
language plpgsql security definer set search_path = public as $$
declare c uuid; v_codigo text; a public.alumnos;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if exists (select 1 from public.alumnos where user_id = auth.uid()) then raise exception 'Ya tienes un registro'; end if;
  if exists (select 1 from public.perfiles where id = auth.uid()) then raise exception 'Esta cuenta pertenece al personal del instituto'; end if;
  select id into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo_colegio));
  if c is null then raise exception 'Código de instituto inválido'; end if;
  if length(trim(coalesce(p_nombres, ''))) < 2 or length(trim(coalesce(p_apellidos, ''))) < 2 then
    raise exception 'Nombres y apellidos son obligatorios';
  end if;
  if not exists (select 1 from public.grados where colegio_id = c and nivel = p_nivel and nombre = p_grado) then
    raise exception 'Carrera o ciclo inválido';
  end if;
  loop  -- código QR único y no adivinable
    v_codigo := 'e' || substr(md5(gen_random_uuid()::text), 1, 10);
    exit when not exists (select 1 from public.alumnos where colegio_id = c and codigo = v_codigo);
  end loop;
  insert into public.alumnos (colegio_id, codigo, nombre, nivel, grado, apoderado, estado,
                              user_id, nombres, apellidos, dni, aprobado, consentimiento_en)
  values (c, v_codigo, trim(p_nombres) || ' ' || trim(p_apellidos), p_nivel, p_grado, coalesce(trim(p_apoderado), ''), 'ACTIVO',
          auth.uid(), trim(p_nombres), trim(p_apellidos), nullif(trim(p_dni), ''), false, now())
  returning * into a;
  return row_to_json(a);
end $$;

create or replace function public.mi_registro() returns json
language sql stable security definer set search_path = public as $$
  select json_build_object('alumno', row_to_json(a), 'colegio', c.nombre)
  from public.alumnos a join public.colegios c on c.id = a.colegio_id
  where a.user_id = auth.uid()
$$;

create or replace function public.actualizar_mi_foto(p_path text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if p_path is null or split_part(p_path, '/', 1) <> auth.uid()::text then raise exception 'Ruta de foto inválida'; end if;
  update public.alumnos set foto_path = p_path where user_id = auth.uid();
end $$;

revoke all on function public.info_colegio(text), public.registrar_estudiante(text, text, text, text, text, text, text),
  public.mi_registro(), public.actualizar_mi_foto(text) from public, anon;
grant execute on function public.info_colegio(text), public.registrar_estudiante(text, text, text, text, text, text, text),
  public.mi_registro(), public.actualizar_mi_foto(text) to authenticated;

-- ---------- Política: el estudiante lee SOLO su propio registro ----------
drop policy if exists "estudiante ve su registro" on public.alumnos;
create policy "estudiante ve su registro" on public.alumnos for select to authenticated using (user_id = auth.uid());

-- ---------- Fotos (bucket privado, máx. 512 KB, solo imágenes) ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('fotos-alumnos', 'fotos-alumnos', false, 524288, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "fotos: estudiante gestiona su carpeta" on storage.objects;
create policy "fotos: estudiante gestiona su carpeta" on storage.objects for all to authenticated
  using (bucket_id = 'fotos-alumnos' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'fotos-alumnos' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "fotos: personal del instituto ve fotos" on storage.objects;
create policy "fotos: personal del instituto ve fotos" on storage.objects for select to authenticated
  using (bucket_id = 'fotos-alumnos' and exists (
    select 1 from public.alumnos a join public.perfiles p on p.colegio_id = a.colegio_id
    where p.id = auth.uid() and a.user_id::text = (storage.foldername(name))[1]));


-- ======================== 004_operacion.sql ========================
-- =====================================================================
--  004 · Operación real: hora del servidor, roles, justificaciones, cursos, avisos,
--        registro de errores, protección contra abuso y borrado de datos.
--  Migración ADITIVA y re-ejecutable. No borra datos. Las políticas nuevas son RESTRICTIVAS: solo
--  pueden quitar permisos (nunca dar de más) y los administradores quedan siempre con acceso total.
--
--  Roles (perfiles.rol, sin distinguir mayúsculas):
--    administrador | admin   → todo
--    docente                 → leer; registrar asistencia, justificaciones y avisos
--    coordinador             → como docente, pero SOLO ve la carrera indicada en perfiles.carrera
-- =====================================================================

-- ---------- Hora del servidor (reloj confiable para la asistencia) ----------
create or replace function public.hora_servidor() returns timestamptz
language sql stable as $$ select now() $$;
grant execute on function public.hora_servidor() to authenticated;

-- ---------- Columnas nuevas ----------
alter table public.asistencias
  add column if not exists registrado_en timestamptz not null default now(),   -- momento en que el servidor lo recibió
  add column if not exists origen text;                                         -- qr | nfc | manual | alumno | masivo
alter table public.perfiles add column if not exists carrera text;             -- para el rol coordinador
alter table public.alumnos
  add column if not exists apoderado_telefono text,
  add column if not exists apoderado_email text,
  add column if not exists qr_secreto text not null default replace(gen_random_uuid()::text, '-', '');

create index if not exists idx_asistencias_colegio_fecha on public.asistencias (colegio_id, fecha);
create index if not exists idx_alumnos_colegio_carrera on public.alumnos (colegio_id, nivel, grado);

-- ---------- Funciones auxiliares de rol (SECURITY DEFINER: no dependen de las políticas de perfiles) ----------
create or replace function public.mi_colegio() returns uuid
language sql stable security definer set search_path = public as $$
  select colegio_id from public.perfiles where id = auth.uid()
$$;
create or replace function public.mi_rol() returns text
language sql stable security definer set search_path = public as $$
  select lower(trim(rol)) from public.perfiles where id = auth.uid()
$$;
create or replace function public.es_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.mi_rol() in ('administrador', 'admin'), false)
$$;
create or replace function public.es_coordinador() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.mi_rol() = 'coordinador', false)
$$;
create or replace function public.mi_carrera() returns text
language sql stable security definer set search_path = public as $$
  select carrera from public.perfiles where id = auth.uid()
$$;
revoke all on function public.mi_colegio(), public.mi_rol(), public.es_admin(), public.es_coordinador(), public.mi_carrera() from public, anon;
grant execute on function public.mi_colegio(), public.mi_rol(), public.es_admin(), public.es_coordinador(), public.mi_carrera() to authenticated;

-- ---------- Tablas nuevas ----------
create table if not exists public.justificaciones (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  alumno_id      uuid not null references public.alumnos(id) on delete cascade,
  fecha          date not null,
  tipo           text not null default 'Falta justificada' check (tipo in ('Falta justificada', 'Permiso', 'Tardanza justificada')),
  motivo         text not null default '',
  registrado_por uuid references auth.users(id) on delete set null,
  creado_en      timestamptz not null default now(),
  unique (alumno_id, fecha)
);

create table if not exists public.cursos (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nivel       text not null,                    -- carrera
  grado       text,                             -- ciclo/salón (opcional: curso válido para toda la carrera)
  nombre      text not null,
  docente     text not null default '',
  activo      boolean not null default true
);
create unique index if not exists cursos_unico on public.cursos (colegio_id, nivel, coalesce(grado, ''), lower(nombre));

create table if not exists public.asistencias_curso (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  alumno_id      uuid not null references public.alumnos(id) on delete cascade,
  curso_id       uuid not null references public.cursos(id) on delete cascade,
  fecha          date not null,
  hora           time not null,
  registrado_por uuid references auth.users(id) on delete set null,
  registrado_en  timestamptz not null default now(),
  origen         text,
  unique (alumno_id, curso_id, fecha)
);
create index if not exists idx_asist_curso on public.asistencias_curso (colegio_id, curso_id, fecha);

create table if not exists public.avisos_apoderados (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  alumno_id   uuid not null references public.alumnos(id) on delete cascade,
  fecha       date not null,
  tipo        text not null check (tipo in ('Falta', 'Tardanza', 'Llegada', 'Otro')),
  canal       text not null default 'WhatsApp',
  enviado_por uuid references auth.users(id) on delete set null,
  creado_en   timestamptz not null default now()
);

create table if not exists public.logs_cliente (
  id         uuid primary key default gen_random_uuid(),
  colegio_id uuid references public.colegios(id) on delete cascade,
  user_id    uuid default auth.uid(),
  app        text,
  mensaje    text not null,
  detalle    text,
  url        text,
  agente     text,
  creado_en  timestamptz not null default now()
);
create index if not exists idx_logs_cliente on public.logs_cliente (colegio_id, creado_en desc);

create table if not exists public.intentos_codigo (
  id        bigserial primary key,
  user_id   uuid not null default auth.uid(),
  creado_en timestamptz not null default now()
);
create index if not exists idx_intentos_codigo on public.intentos_codigo (user_id, creado_en);

-- logs_cliente: el servidor completa el instituto (del personal o del estudiante) y limita el tamaño
create or replace function public.logs_cliente_completar() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.user_id := auth.uid();
  new.colegio_id := coalesce(public.mi_colegio(), (select colegio_id from public.alumnos where user_id = auth.uid() limit 1));
  new.mensaje := left(new.mensaje, 500);
  new.detalle := left(new.detalle, 4000);
  new.url := left(new.url, 300);
  new.agente := left(new.agente, 300);
  return new;
end $$;
drop trigger if exists trg_logs_cliente on public.logs_cliente;
create trigger trg_logs_cliente before insert on public.logs_cliente for each row execute function public.logs_cliente_completar();

-- ---------- RLS ----------
alter table public.justificaciones    enable row level security;
alter table public.cursos             enable row level security;
alter table public.asistencias_curso  enable row level security;
alter table public.avisos_apoderados  enable row level security;
alter table public.logs_cliente       enable row level security;
alter table public.intentos_codigo    enable row level security;   -- sin políticas: solo las funciones SECURITY DEFINER la usan

-- Acceso base (permisivo): el personal del instituto
do $$
declare t text;
begin
  foreach t in array array['justificaciones', 'cursos', 'asistencias_curso', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'personal del instituto', t);
    execute format('create policy %I on public.%I for all to authenticated using (colegio_id = public.mi_colegio()) with check (colegio_id = public.mi_colegio())', 'personal del instituto', t);
  end loop;
end $$;

drop policy if exists "cualquier usuario registra errores" on public.logs_cliente;
create policy "cualquier usuario registra errores" on public.logs_cliente for insert to authenticated with check (true);  -- el trigger fija user_id/colegio_id
drop policy if exists "admin ve errores" on public.logs_cliente;
create policy "admin ve errores" on public.logs_cliente for select to authenticated using (public.es_admin() and colegio_id = public.mi_colegio());
drop policy if exists "admin borra errores" on public.logs_cliente;
create policy "admin borra errores" on public.logs_cliente for delete to authenticated using (public.es_admin() and colegio_id = public.mi_colegio());

-- Restricciones (RESTRICTIVAS): solo el administrador modifica catálogos, padrón y corrige/borra asistencia
do $$
declare t text;
begin
  foreach t in array array['alumnos', 'niveles', 'grados', 'docentes', 'comunicados', 'cursos'] loop
    execute format('drop policy if exists %I on public.%I', 'solo admin inserta', t);
    execute format('create policy %I on public.%I as restrictive for insert to authenticated with check (public.es_admin())', 'solo admin inserta', t);
    execute format('drop policy if exists %I on public.%I', 'solo admin modifica', t);
    execute format('create policy %I on public.%I as restrictive for update to authenticated using (public.es_admin()) with check (public.es_admin())', 'solo admin modifica', t);
    execute format('drop policy if exists %I on public.%I', 'solo admin elimina', t);
    execute format('create policy %I on public.%I as restrictive for delete to authenticated using (public.es_admin())', 'solo admin elimina', t);
  end loop;
  foreach t in array array['asistencias', 'asistencias_curso', 'justificaciones', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'solo admin modifica', t);
    execute format('create policy %I on public.%I as restrictive for update to authenticated using (public.es_admin()) with check (public.es_admin())', 'solo admin modifica', t);
    execute format('drop policy if exists %I on public.%I', 'solo admin elimina', t);
    execute format('create policy %I on public.%I as restrictive for delete to authenticated using (public.es_admin())', 'solo admin elimina', t);
  end loop;
end $$;

drop policy if exists "solo admin edita el instituto" on public.colegios;
create policy "solo admin edita el instituto" on public.colegios as restrictive for update to authenticated using (public.es_admin()) with check (public.es_admin());

-- Coordinador: solo ve su carrera (el resto de roles y los estudiantes no se ven afectados)
drop policy if exists "coordinador solo su carrera" on public.alumnos;
create policy "coordinador solo su carrera" on public.alumnos as restrictive for select to authenticated
  using (user_id = (select auth.uid()) or not (select public.es_coordinador()) or nivel = (select public.mi_carrera()));
drop policy if exists "coordinador solo su carrera" on public.niveles;
create policy "coordinador solo su carrera" on public.niveles as restrictive for select to authenticated
  using (not (select public.es_coordinador()) or nombre = (select public.mi_carrera()));
drop policy if exists "coordinador solo su carrera" on public.grados;
create policy "coordinador solo su carrera" on public.grados as restrictive for select to authenticated
  using (not (select public.es_coordinador()) or nivel = (select public.mi_carrera()));
drop policy if exists "coordinador solo su carrera" on public.cursos;
create policy "coordinador solo su carrera" on public.cursos as restrictive for select to authenticated
  using (not (select public.es_coordinador()) or nivel = (select public.mi_carrera()));
do $$
declare t text;
begin
  foreach t in array array['asistencias', 'asistencias_curso', 'justificaciones', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'coordinador solo su carrera', t);
    execute format($f$create policy %I on public.%I as restrictive for select to authenticated
      using (not (select public.es_coordinador()) or exists (select 1 from public.alumnos a where a.id = %I.alumno_id and a.nivel = (select public.mi_carrera())))$f$,
      'coordinador solo su carrera', t, t);
  end loop;
end $$;

-- ---------- Registro de estudiantes: protección contra abuso + datos del apoderado ----------
create or replace function public.info_colegio(p_codigo text) returns json
language plpgsql volatile security definer set search_path = public as $$
declare c record; n int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select count(*) into n from public.intentos_codigo where user_id = auth.uid() and creado_en > now() - interval '1 hour';
  if n >= 10 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;
  select id, nombre into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo));
  if not found then
    insert into public.intentos_codigo (user_id) values (auth.uid());   -- se conserva: la función retorna normalmente
    return null;
  end if;
  return json_build_object(
    'nombre', c.nombre,
    'niveles', (select coalesce(json_agg(nombre order by nombre), '[]'::json) from public.niveles where colegio_id = c.id),
    'grados',  (select coalesce(json_agg(json_build_object('nivel', nivel, 'nombre', nombre) order by nivel, nombre), '[]'::json)
                from public.grados where colegio_id = c.id));
end $$;

drop function if exists public.registrar_estudiante(text, text, text, text, text, text, text);
create or replace function public.registrar_estudiante(
  p_codigo_colegio text, p_nombres text, p_apellidos text, p_nivel text, p_grado text,
  p_apoderado text default '', p_dni text default null,
  p_apoderado_tel text default null, p_apoderado_email text default null) returns json
language plpgsql security definer set search_path = public as $$
declare c uuid; v_codigo text; a public.alumnos; n int; tel text; mail text;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if exists (select 1 from public.alumnos where user_id = auth.uid()) then raise exception 'Ya tienes un registro'; end if;
  if exists (select 1 from public.perfiles where id = auth.uid()) then raise exception 'Esta cuenta pertenece al personal del instituto'; end if;
  select count(*) into n from public.intentos_codigo where user_id = auth.uid() and creado_en > now() - interval '1 hour';
  if n >= 10 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;
  select id into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo_colegio));
  if c is null then
    insert into public.intentos_codigo (user_id) values (auth.uid());
    return json_build_object('error', 'codigo_invalido');
  end if;
  if (select count(*) from public.alumnos where colegio_id = c and aprobado = false) >= 500 then
    raise exception 'Hay demasiados registros pendientes de aprobación. Avisa al instituto.';
  end if;
  if length(trim(coalesce(p_nombres, ''))) < 2 or length(trim(coalesce(p_apellidos, ''))) < 2 then raise exception 'Nombres y apellidos son obligatorios'; end if;
  if not exists (select 1 from public.grados where colegio_id = c and nivel = p_nivel and nombre = p_grado) then raise exception 'Carrera o ciclo inválido'; end if;
  tel := nullif(regexp_replace(coalesce(p_apoderado_tel, ''), '[^0-9+]', '', 'g'), '');
  if tel is not null and (length(tel) < 6 or length(tel) > 16) then raise exception 'Teléfono del apoderado inválido'; end if;
  mail := nullif(lower(trim(coalesce(p_apoderado_email, ''))), '');
  if mail is not null and mail !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'Correo del apoderado inválido'; end if;
  loop
    v_codigo := 'e' || substr(md5(gen_random_uuid()::text), 1, 10);
    exit when not exists (select 1 from public.alumnos where colegio_id = c and codigo = v_codigo);
  end loop;
  insert into public.alumnos (colegio_id, codigo, nombre, nivel, grado, apoderado, estado,
                              user_id, nombres, apellidos, dni, aprobado, consentimiento_en, apoderado_telefono, apoderado_email)
  values (c, v_codigo, trim(p_nombres) || ' ' || trim(p_apellidos), p_nivel, p_grado, coalesce(trim(p_apoderado), ''), 'ACTIVO',
          auth.uid(), trim(p_nombres), trim(p_apellidos), nullif(trim(p_dni), ''), false, now(), tel, mail)
  returning * into a;
  return row_to_json(a);
end $$;
revoke all on function public.registrar_estudiante(text, text, text, text, text, text, text, text, text) from public, anon;
grant execute on function public.registrar_estudiante(text, text, text, text, text, text, text, text, text) to authenticated;

-- ---------- Derecho de supresión: el estudiante borra su registro y su cuenta ----------
create or replace function public.eliminar_mi_registro() returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if exists (select 1 from public.perfiles where id = auth.uid()) then
    raise exception 'Las cuentas del personal se eliminan desde la administración';
  end if;
  delete from public.alumnos where user_id = auth.uid();   -- en cascada: asistencias, justificaciones, avisos
  delete from auth.users where id = auth.uid();
end $$;
revoke all on function public.eliminar_mi_registro() from public, anon;
grant execute on function public.eliminar_mi_registro() to authenticated;

-- El administrador puede borrar la foto de un estudiante de su instituto (al eliminar su registro)
drop policy if exists "fotos: admin borra fotos del instituto" on storage.objects;
create policy "fotos: admin borra fotos del instituto" on storage.objects for delete to authenticated
  using (bucket_id = 'fotos-alumnos' and public.es_admin() and exists (
    select 1 from public.alumnos a where a.user_id::text = (storage.foldername(name))[1] and a.colegio_id = public.mi_colegio()));


-- ======================== 005_personal_avisos.sql ========================
-- =====================================================================
--  005 · Personal y accesos (asignar roles desde la app) + comunicados del personal + avisos al teléfono
--  Migración ADITIVA y re-ejecutable. No borra datos.
--
--  · Los docentes y coordinadores pueden PUBLICAR comunicados (eliminarlos sigue siendo solo del administrador).
--  · El administrador asigna/quita roles a cuentas ya creadas en Supabase Auth, sin tocar SQL (personal_*).
--  · Avisos al teléfono: cada instituto tiene un token de avisos; la app Android (docente o estudiante) lo usa para
--    consultar comunicados nuevos en segundo plano y mostrarlos como notificación (comunicados_desde).
-- =====================================================================

-- ---------- Comunicados: momento exacto de publicación y quién los publicó ----------
alter table public.comunicados
  add column if not exists creado_en timestamptz not null default now(),
  add column if not exists publicado_por uuid default auth.uid();
create index if not exists idx_comunicados_creado on public.comunicados (colegio_id, creado_en desc);

-- El personal (cualquier rol) puede insertar; modificar y borrar sigue reservado al administrador (política de la 004).
drop policy if exists "solo admin inserta" on public.comunicados;
drop policy if exists "solo personal inserta" on public.comunicados;
create policy "solo personal inserta" on public.comunicados as restrictive for insert to authenticated
  with check (public.mi_colegio() is not null);
drop policy if exists "personal publica comunicados" on public.comunicados;
create policy "personal publica comunicados" on public.comunicados for insert to authenticated
  with check (colegio_id = public.mi_colegio());

-- ---------- Token de avisos del instituto ----------
alter table public.colegios add column if not exists aviso_token text not null default replace(gen_random_uuid()::text, '-', '');

-- Lo reciben el personal y los estudiantes aprobados de ese instituto (para configurar las notificaciones del teléfono).
create or replace function public.token_avisos() returns text
language sql stable security definer set search_path = public as $$
  select c.aviso_token from public.colegios c
  where c.id = coalesce(public.mi_colegio(), (select a.colegio_id from public.alumnos a where a.user_id = auth.uid() and a.aprobado limit 1))
$$;
revoke all on function public.token_avisos() from public, anon;
grant execute on function public.token_avisos() to authenticated;

-- Consulta ligera para el segundo plano del teléfono (sin sesión): solo comunicados, solo con el token del instituto.
create or replace function public.comunicados_desde(p_token text, p_desde timestamptz default null) returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if p_token is null or length(p_token) <> 32 then return '[]'::json; end if;
  return coalesce((
    select json_agg(x order by x.creado_en) from (
      select m.id, m.titulo, left(m.mensaje, 500) as mensaje, m.creado_en
      from public.comunicados m join public.colegios c on c.id = m.colegio_id
      where c.aviso_token = p_token and m.creado_en > coalesce(p_desde, now() - interval '1 day')
      order by m.creado_en desc limit 10
    ) x), '[]'::json);
end $$;
grant execute on function public.comunicados_desde(text, timestamptz) to anon, authenticated;

-- ---------- Personal y accesos (solo administrador) ----------
create or replace function public.personal_listar() returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede ver el personal'; end if;
  return coalesce((select json_agg(json_build_object('id', p.id, 'email', u.email, 'nombre', p.nombre, 'rol', p.rol, 'carrera', p.carrera) order by lower(coalesce(p.nombre, u.email)))
                   from public.perfiles p join auth.users u on u.id = p.id where p.colegio_id = public.mi_colegio()), '[]'::json);
end $$;

create or replace function public.personal_asignar(p_email text, p_rol text, p_carrera text default null, p_nombre text default null) returns json
language plpgsql volatile security definer set search_path = public as $$
declare uid uuid; rol text; car text := nullif(trim(coalesce(p_carrera, '')), ''); cid uuid := public.mi_colegio();
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede asignar roles'; end if;
  rol := case lower(trim(coalesce(p_rol, '')))
    when 'administrador' then 'Administrador' when 'admin' then 'Administrador'
    when 'docente' then 'Docente' when 'coordinador' then 'Coordinador' else null end;
  if rol is null then raise exception 'Rol no válido (usa Administrador, Docente o Coordinador)'; end if;
  if rol = 'Coordinador' then
    if car is null or not exists (select 1 from public.niveles where colegio_id = cid and nombre = car) then
      raise exception 'El coordinador necesita una carrera existente'; end if;
  else car := null; end if;
  select id into uid from auth.users where lower(email) = lower(trim(coalesce(p_email, '')));
  if uid is null then raise exception 'No existe una cuenta con ese correo. Créala primero en Supabase → Authentication → Users.'; end if;
  if exists (select 1 from public.alumnos where user_id = uid) then raise exception 'Esa cuenta pertenece a un estudiante'; end if;
  if exists (select 1 from public.perfiles where id = uid and colegio_id <> cid) then raise exception 'Esa cuenta pertenece a otro instituto'; end if;
  if uid = auth.uid() and rol <> 'Administrador' then raise exception 'No puedes quitarte a ti mismo el rol de administrador'; end if;
  insert into public.perfiles (id, colegio_id, rol, nombre, carrera)
  values (uid, cid, rol, nullif(trim(coalesce(p_nombre, '')), ''), car)
  on conflict (id) do update set rol = excluded.rol, carrera = excluded.carrera, nombre = coalesce(excluded.nombre, public.perfiles.nombre);
  return json_build_object('id', uid, 'rol', rol, 'carrera', car);
end $$;

create or replace function public.personal_quitar(p_id uuid) returns void
language plpgsql volatile security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede quitar accesos'; end if;
  if p_id = auth.uid() then raise exception 'No puedes quitarte el acceso a ti mismo'; end if;
  delete from public.perfiles where id = p_id and colegio_id = public.mi_colegio();
end $$;

revoke all on function public.personal_listar(), public.personal_asignar(text, text, text, text), public.personal_quitar(uuid) from public, anon;
grant execute on function public.personal_listar(), public.personal_asignar(text, text, text, text), public.personal_quitar(uuid) to authenticated;


-- ======================== 006_perfil_personal.sql ========================
-- =====================================================================
--  006 · Foto de perfil del personal (docentes, coordinadores, administradores)
--  Migración ADITIVA y re-ejecutable. No borra datos.
--  La creación de usuarios (correo + contraseña) la hace la Edge Function «gestionar-personal»
--  (supabase/functions/gestionar-personal), porque crear cuentas exige la clave de servicio, que nunca debe
--  estar en el navegador.
-- =====================================================================

alter table public.perfiles add column if not exists foto_path text;

-- Bucket privado: cada persona sube SOLO a su carpeta (<id de usuario>/…); el personal del mismo instituto la ve.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('fotos-personal', 'fotos-personal', false, 524288, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "fotos personal: cada uno su carpeta" on storage.objects;
create policy "fotos personal: cada uno su carpeta" on storage.objects for all to authenticated
  using (bucket_id = 'fotos-personal' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'fotos-personal' and (storage.foldername(name))[1] = auth.uid()::text);

-- (La tabla perfiles solo deja ver la propia fila, por eso la comparación entre personas va en una función SECURITY DEFINER.)
create or replace function public.es_personal_de_mi_instituto(p_id text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.perfiles where id::text = p_id and colegio_id = public.mi_colegio())
$$;
revoke all on function public.es_personal_de_mi_instituto(text) from public, anon;
grant execute on function public.es_personal_de_mi_instituto(text) to authenticated;

drop policy if exists "fotos personal: ve el mismo instituto" on storage.objects;
create policy "fotos personal: ve el mismo instituto" on storage.objects for select to authenticated
  using (bucket_id = 'fotos-personal' and public.es_personal_de_mi_instituto((storage.foldername(name))[1]));

-- Cada persona actualiza SU nombre y foto (la tabla perfiles no se puede escribir directamente: el rol y la carrera
-- solo los cambia el administrador con personal_asignar).
create or replace function public.actualizar_mi_perfil(p_nombre text default null, p_path text default null) returns void
language plpgsql volatile security definer set search_path = public as $$
begin
  if not exists (select 1 from public.perfiles where id = auth.uid()) then raise exception 'Esta cuenta no pertenece al personal'; end if;
  if p_path is not null and p_path not like auth.uid()::text || '/%' then raise exception 'Ruta de foto no válida'; end if;
  update public.perfiles
     set nombre = coalesce(nullif(left(trim(coalesce(p_nombre, '')), 80), ''), nombre),
         foto_path = coalesce(p_path, foto_path)
   where id = auth.uid();
end $$;
revoke all on function public.actualizar_mi_perfil(text, text) from public, anon;
grant execute on function public.actualizar_mi_perfil(text, text) to authenticated;

-- El listado del personal incluye la foto
create or replace function public.personal_listar() returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede ver el personal'; end if;
  return coalesce((select json_agg(json_build_object('id', p.id, 'email', u.email, 'nombre', p.nombre, 'rol', p.rol, 'carrera', p.carrera, 'foto_path', p.foto_path)
                          order by lower(coalesce(p.nombre, u.email)))
                   from public.perfiles p join auth.users u on u.id = p.id where p.colegio_id = public.mi_colegio()), '[]'::json);
end $$;


-- ======================== 007_operacion_avanzada.sql ========================
-- =====================================================================
--  007 · Operación avanzada: calendario, horarios por carrera, periodos, hora de salida (quiosco),
--        historial de cambios (auditoría) y consulta para apoderados.
--  Migración ADITIVA y re-ejecutable. No borra datos. Requiere 004 (roles) y 005.
-- =====================================================================

-- ---------- Periodos académicos ----------
create table if not exists public.periodos (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nombre      text not null,
  inicio      date not null,
  fin         date,
  activo      boolean not null default true,
  cerrado_en  timestamptz,
  resumen     jsonb,
  unique (colegio_id, nombre)
);

-- ---------- Calendario (feriados y días sin clases) ----------
create table if not exists public.calendario (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  fecha       date not null,
  tipo        text not null default 'Feriado' check (tipo in ('Feriado', 'Sin clases', 'Evento')),
  nombre      text not null,
  unique (colegio_id, fecha)
);

-- ---------- Horarios (general y por carrera) ----------
create table if not exists public.horarios (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  nivel          text,                                   -- carrera; null = horario general del instituto
  hora_ingreso   text not null default '08:00' check (hora_ingreso ~ '^[0-2][0-9]:[0-5][0-9]$'),
  tolerancia_min int  not null default 0 check (tolerancia_min between 0 and 120),
  hora_salida    text check (hora_salida is null or hora_salida ~ '^[0-2][0-9]:[0-5][0-9]$')
);
create unique index if not exists horarios_unico on public.horarios (colegio_id, coalesce(nivel, ''));

alter table public.periodos   enable row level security;
alter table public.calendario enable row level security;
alter table public.horarios   enable row level security;

do $$
declare t text;
begin
  foreach t in array array['periodos', 'calendario', 'horarios'] loop
    execute format('drop policy if exists %I on public.%I', 'personal ve', t);
    execute format('create policy %I on public.%I for select to authenticated using (colegio_id = public.mi_colegio())', 'personal ve', t);
    execute format('drop policy if exists %I on public.%I', 'admin escribe', t);
    execute format('create policy %I on public.%I for all to authenticated using (colegio_id = public.mi_colegio() and public.es_admin()) with check (colegio_id = public.mi_colegio() and public.es_admin())', 'admin escribe', t);
  end loop;
end $$;

-- ---------- Hora de salida ----------
alter table public.asistencias add column if not exists hora_salida text;

-- Límite de puntualidad (HH:MM) de una carrera: su horario, o el general, o 08:00.
create or replace function public.limite_ingreso(p_colegio uuid, p_nivel text) returns text
language sql stable security definer set search_path = public as $$
  select to_char(h.hora_ingreso::time + make_interval(mins => h.tolerancia_min), 'HH24:MI')
  from public.horarios h
  where h.colegio_id = p_colegio and (h.nivel = p_nivel or h.nivel is null)
  order by (h.nivel is null) limit 1
$$;
revoke all on function public.limite_ingreso(uuid, text) from public, anon;
grant execute on function public.limite_ingreso(uuid, text) to authenticated;

-- Registrar salidas (quiosco y escáner). El personal NO puede actualizar asistencias directamente (solo el administrador
-- corrige), así que la salida pasa por esta función: solo escribe hora_salida, solo si hay ingreso y solo una vez.
create or replace function public.registrar_salidas(p_rows jsonb) returns json
language plpgsql volatile security definer set search_path = public as $$
declare r jsonb; ok int := 0; dup int := 0; sin int := 0; a record; n int;
begin
  if public.mi_colegio() is null then raise exception 'Solo el personal puede registrar salidas'; end if;
  for r in select * from jsonb_array_elements(coalesce(p_rows, '[]'::jsonb)) loop
    select s.id, s.hora_salida into a
    from public.asistencias s join public.alumnos al on al.id = s.alumno_id
    where s.alumno_id = (r->>'alumno_id')::uuid and s.fecha = (r->>'fecha')::date and s.colegio_id = public.mi_colegio()
      and (not public.es_coordinador() or al.nivel = public.mi_carrera());
    if not found then sin := sin + 1;
    elsif a.hora_salida is not null then dup := dup + 1;
    else
      update public.asistencias set hora_salida = left(r->>'hora', 5) where id = a.id and hora_salida is null;
      get diagnostics n = row_count; ok := ok + n;
    end if;
  end loop;
  return json_build_object('ok', ok, 'dup', dup, 'sin_entrada', sin);
end $$;
revoke all on function public.registrar_salidas(jsonb) from public, anon;
grant execute on function public.registrar_salidas(jsonb) to authenticated;

-- ---------- Historial de cambios (auditoría) ----------
create table if not exists public.auditoria (
  id          bigserial primary key,
  colegio_id  uuid,
  user_id     uuid,
  usuario     text,
  accion      text not null,           -- INSERT | UPDATE | DELETE
  tabla       text not null,
  registro_id text,
  detalle     jsonb,
  creado_en   timestamptz not null default now()
);
create index if not exists idx_auditoria on public.auditoria (colegio_id, creado_en desc);
alter table public.auditoria enable row level security;
drop policy if exists "admin ve el historial" on public.auditoria;
create policy "admin ve el historial" on public.auditoria for select to authenticated
  using (public.es_admin() and colegio_id = public.mi_colegio());
revoke insert, update, delete on public.auditoria from authenticated, anon;   -- solo los disparadores escriben

create or replace function public.auditar() returns trigger
language plpgsql security definer set search_path = public as $$
declare fila jsonb; viejo jsonb; det jsonb := '{}'::jsonb; k text; cid uuid; ocultos text[] := array['qr_secreto', 'foto_data', 'foto_path', 'aviso_token', 'codigo_apoderado', 'hora_salida'];
begin
  fila := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
  cid := case when tg_table_name = 'colegios' then (fila->>'id')::uuid else (fila->>'colegio_id')::uuid end;
  if tg_op = 'UPDATE' then
    viejo := to_jsonb(old);
    for k in select jsonb_object_keys(fila) loop
      if not (k = any(ocultos)) and (fila->k) is distinct from (viejo->k) then det := det || jsonb_build_object(k, jsonb_build_object('de', viejo->k, 'a', fila->k)); end if;
    end loop;
    if det = '{}'::jsonb then return null; end if;               -- cambio solo en columnas internas: no se registra
    if tg_table_name = 'alumnos' and (det ? 'nombre' or det ? 'apoderado' or det ? 'dni') then   -- datos personales: se anota el campo, no el valor
      det := (select coalesce(jsonb_object_agg(key, case when key in ('nombre', 'apoderado', 'dni') then '"(dato personal modificado)"'::jsonb else value end), '{}'::jsonb) from jsonb_each(det));
    end if;
  elsif tg_op = 'DELETE' then
    det := case when tg_table_name = 'alumnos' then jsonb_build_object('codigo', fila->>'codigo')            -- sin datos personales (derecho de supresión)
           else fila - ocultos end;
    if tg_table_name = 'alumnos' then
      update public.auditoria set detalle = '{"purgado": "datos personales eliminados"}'::jsonb where tabla = 'alumnos' and registro_id = fila->>'id' and detalle is not null;
    end if;
  else
    det := case when tg_table_name = 'alumnos' then jsonb_build_object('codigo', fila->>'codigo', 'nivel', fila->>'nivel', 'grado', fila->>'grado') else fila - ocultos end;
  end if;
  insert into public.auditoria (colegio_id, user_id, usuario, accion, tabla, registro_id, detalle)
  values (cid, auth.uid(), coalesce((select email from auth.users where id = auth.uid()), 'sistema'), tg_op, tg_table_name, fila->>'id', det);
  return null;
end $$;
revoke all on function public.auditar() from public, anon, authenticated;

do $$
declare t text;
begin
  foreach t in array array['alumnos', 'niveles', 'grados', 'docentes', 'comunicados', 'cursos', 'justificaciones', 'perfiles', 'calendario', 'horarios', 'periodos'] loop
    execute format('drop trigger if exists trg_auditar on public.%I', t);
    execute format('create trigger trg_auditar after insert or update or delete on public.%I for each row execute function public.auditar()', t);
  end loop;
  drop trigger if exists trg_auditar on public.asistencias;
  create trigger trg_auditar after update or delete on public.asistencias for each row execute function public.auditar();   -- los ingresos normales no se registran (volumen)
  drop trigger if exists trg_auditar on public.colegios;
  create trigger trg_auditar after update on public.colegios for each row execute function public.auditar();
end $$;

-- ---------- Consulta para apoderados (sin cuenta: con el código del alumno) ----------
alter table public.alumnos add column if not exists codigo_apoderado text;
update public.alumnos set codigo_apoderado = upper(substr(md5(random()::text || clock_timestamp()::text || id::text), 1, 12)) where codigo_apoderado is null;
alter table public.alumnos alter column codigo_apoderado set default upper(substr(md5(random()::text || clock_timestamp()::text || gen_random_uuid()::text), 1, 12));
create unique index if not exists alumnos_codigo_apoderado_uq on public.alumnos (codigo_apoderado);

create table if not exists public.intentos_apoderado (
  id bigserial primary key, ip text not null, creado_en timestamptz not null default now()
);
create index if not exists idx_intentos_apoderado on public.intentos_apoderado (ip, creado_en);
alter table public.intentos_apoderado enable row level security;   -- sin políticas: solo la función

create or replace function public.consulta_apoderado(p_codigo text) returns json
language plpgsql volatile security definer set search_path = public as $$
declare
  cod text := upper(regexp_replace(coalesce(p_codigo, ''), '[^0-9A-Za-z]', '', 'g'));
  v_ip text := coalesce(nullif(current_setting('request.headers', true), '')::json->>'x-forwarded-for', 'sin-ip');
  a record; per record; hoy date := (now() at time zone 'America/Lima')::date; desde date; lim text;
  dias json; res json; n int;
begin
  select count(*) into n from public.intentos_apoderado where ip = v_ip and creado_en > now() - interval '1 hour';
  if n >= 20 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;
  select al.id, al.nombre, al.nivel, al.grado, al.colegio_id, c.nombre as instituto into a
  from public.alumnos al join public.colegios c on c.id = al.colegio_id
  where al.codigo_apoderado = cod and length(cod) = 12;
  if not found then
    insert into public.intentos_apoderado (ip) values (v_ip);
    return null;
  end if;
  select nombre, inicio into per from public.periodos where colegio_id = a.colegio_id and activo order by inicio desc limit 1;
  desde := greatest(coalesce(per.inicio, hoy - 60), hoy - 120);
  lim := coalesce(public.limite_ingreso(a.colegio_id, a.nivel), '08:00');
  with dias_clase as (
    select d::date as fecha from generate_series(desde, hoy, interval '1 day') d
    where extract(isodow from d) < 6
      and not exists (select 1 from public.calendario c where c.colegio_id = a.colegio_id and c.fecha = d::date and c.tipo in ('Feriado', 'Sin clases'))
  ), est as (
    select dc.fecha,
      case when s.id is not null then (case when left(s.hora::text, 5) > lim then 'T' else 'P' end)
           when j.id is not null then 'J'
           when dc.fecha = hoy then 'pendiente' else 'F' end as estado,
      left(s.hora::text, 5) as hora, s.hora_salida
    from dias_clase dc
    left join public.asistencias s on s.alumno_id = a.id and s.fecha = dc.fecha
    left join public.justificaciones j on j.alumno_id = a.id and j.fecha = dc.fecha
  )
  select json_build_object(
      'alumno', json_build_object('nombre', a.nombre, 'carrera', a.nivel, 'ciclo', a.grado),
      'instituto', a.instituto, 'periodo', per.nombre, 'desde', desde, 'hasta', hoy, 'limite', lim,
      'resumen', json_build_object(
        'dias', count(*) filter (where estado <> 'pendiente'),
        'presentes', count(*) filter (where estado in ('P', 'T')),
        'tardanzas', count(*) filter (where estado = 'T'),
        'justificadas', count(*) filter (where estado = 'J'),
        'faltas', count(*) filter (where estado = 'F'),
        'pct', case when count(*) filter (where estado <> 'pendiente') = 0 then null
                    else round(100.0 * count(*) filter (where estado in ('P', 'T')) / count(*) filter (where estado <> 'pendiente')) end),
      'dias', (select coalesce(json_agg(json_build_object('fecha', fecha, 'estado', estado, 'hora', hora, 'salida', hora_salida) order by fecha desc), '[]'::json)
               from (select * from est order by fecha desc limit 45) x),
      'comunicados', (select coalesce(json_agg(json_build_object('titulo', titulo, 'mensaje', mensaje, 'fecha', fecha) order by fecha desc), '[]'::json)
                      from (select titulo, left(mensaje, 400) as mensaje, fecha from public.comunicados where colegio_id = a.colegio_id order by fecha desc limit 5) y)
    ) into res from est;
  return res;
end $$;
grant execute on function public.consulta_apoderado(text) to anon, authenticated;


-- ======================== 008_directorio_personal.sql ========================
-- =====================================================================
--  008 · Directorio del personal (solo lectura): cualquier persona del personal ve quién es docente o coordinador
--  del instituto (nombre, rol, carrera y foto), SIN correos. Reemplaza al registro manual de «Docentes».
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================
create or replace function public.personal_directorio() returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if public.mi_colegio() is null then raise exception 'Solo el personal puede ver el directorio'; end if;
  return coalesce((
    select json_agg(json_build_object('id', p.id, 'nombre', p.nombre, 'rol', p.rol, 'carrera', p.carrera, 'foto_path', p.foto_path)
                    order by lower(coalesce(p.nombre, ''))
    ) from public.perfiles p
    where p.colegio_id = public.mi_colegio() and lower(trim(p.rol)) in ('docente', 'coordinador')), '[]'::json);
end $$;
revoke all on function public.personal_directorio() from public, anon;
grant execute on function public.personal_directorio() to authenticated;


-- ======================== 009_aviso_aprobacion.sql ========================
-- =====================================================================
--  009 · Aviso de aprobación al estudiante
--  Cada alumno tiene un token privado (solo lo ve él en su carnet). Con ese token, su teléfono o su navegador
--  consulta —sin sesión— si el instituto ya aprobó su registro y le muestra una notificación.
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================
alter table public.alumnos add column if not exists notif_token text;
do $$ begin
  -- el relleno inicial no debe llenar el historial de cambios con una fila por alumno
  if exists (select 1 from pg_trigger where tgname = 'trg_auditar' and tgrelid = 'public.alumnos'::regclass) then
    alter table public.alumnos disable trigger trg_auditar;
    update public.alumnos set notif_token = replace(gen_random_uuid()::text, '-', '') where notif_token is null;
    alter table public.alumnos enable trigger trg_auditar;
  else
    update public.alumnos set notif_token = replace(gen_random_uuid()::text, '-', '') where notif_token is null;
  end if;
end $$;
alter table public.alumnos alter column notif_token set default replace(gen_random_uuid()::text, '-', '');
create unique index if not exists alumnos_notif_token_uq on public.alumnos (notif_token);

-- Estado de la solicitud de un estudiante, por su token (32 caracteres aleatorios). Devuelve solo si está aprobado y su primer nombre.
create or replace function public.estado_solicitud(p_token text) returns json
language sql stable security definer set search_path = public as $$
  select json_build_object('aprobado', a.aprobado, 'nombre', split_part(a.nombre, ' ', 1))
  from public.alumnos a
  where p_token is not null and length(p_token) = 32 and a.notif_token = p_token
$$;
grant execute on function public.estado_solicitud(text) to anon, authenticated;


-- ======================== 010_horario_ventana.sql ========================
-- =====================================================================
--  010 · Horario con ventana de ingreso y permanencia mínima
--  · ingreso_desde / ingreso_hasta: entre qué horas se puede marcar el INGRESO en el quiosco (antes y después se rechaza).
--  · permanencia_min: minutos mínimos desde el ingreso para poder marcar la SALIDA (evita fugas; por defecto 2 horas).
--  Lo ya existente: hora_ingreso (inicio de clases) + tolerancia_min = límite de puntualidad; hora_salida = fin de clases.
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================
alter table public.horarios
  add column if not exists ingreso_desde text check (ingreso_desde is null or ingreso_desde ~ '^[0-2][0-9]:[0-5][0-9]$'),
  add column if not exists ingreso_hasta text check (ingreso_hasta is null or ingreso_hasta ~ '^[0-2][0-9]:[0-5][0-9]$'),
  add column if not exists permanencia_min int not null default 120 check (permanencia_min between 0 and 600);


-- ======================== 011_instituciones.sql ========================
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


-- ======================== 012_qr_modo.sql ========================
-- =====================================================================
--  012 · QR dinámico por institución
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================

alter table public.colegios
  add column if not exists qr_modo text not null default 'obligatorio';

do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conname = 'colegios_qr_modo_chk'
      and conrelid = 'public.colegios'::regclass
  ) then
    alter table public.colegios
      add constraint colegios_qr_modo_chk check (qr_modo in ('off', 'opcional', 'obligatorio'));
  end if;
end $$;

-- mi_registro: devuelve los datos del alumno, el nombre del instituto y el modo del QR
create or replace function public.mi_registro() returns json
language sql stable security definer set search_path = public as $$
  select json_build_object(
    'alumno', row_to_json(a),
    'colegio', c.nombre,
    'qr_modo', c.qr_modo
  )
  from public.alumnos a join public.colegios c on c.id = a.colegio_id
  where a.user_id = auth.uid()
$$;

revoke all on function public.mi_registro() from public, anon;
grant execute on function public.mi_registro() to authenticated;

