-- Instalación completa (GENERADO por scripts/build_setup.py; no editar a mano).
-- Ejecutar en Supabase → SQL Editor sobre un proyecto NUEVO. Luego, el bloque «Alta de un instituto» de schema.sql.


-- ======================== schema.sql ========================
-- =====================================================================
--  Sistema de Registro Académico — esquema de base de datos (PostgreSQL / Supabase)
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

