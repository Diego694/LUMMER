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
  rol         text not null default 'admin' check (rol in ('admin', 'docente', 'auxiliar')),
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
  select coalesce((select rol = 'admin' from public.perfiles where id = auth.uid()), false)
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
grant select, insert, update, delete on public.niveles, public.grados, public.alumnos, public.docentes, public.comunicados, public.asistencias to authenticated;

-- =====================================================================
--  ALTA DE UN INSTITUTO Y SU PRIMER ADMINISTRADOR (ejecutar UNA vez, tras crear el usuario)
--  1) Supabase → Authentication → Users → Add user (correo + contraseña).
--  2) Copia el UUID del usuario y reemplázalo abajo; luego ejecuta este bloque:
--
--  with c as (insert into public.colegios (nombre) values ('Mi Instituto') returning id)
--  insert into public.perfiles (id, colegio_id, rol, nombre)
--  select 'UUID-DEL-USUARIO', c.id, 'admin', 'Administrador' from c;
--
--  insert into public.niveles (colegio_id, nombre)
--  select colegio_id, n from public.perfiles, unnest(array['Inicial','Primaria','Secundaria']) n
--  where id = 'UUID-DEL-USUARIO';
-- =====================================================================
