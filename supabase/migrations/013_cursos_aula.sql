-- =====================================================================
--  013 · Aula: material y actividades por curso
--  El administrador crea los cursos (tabla public.cursos) y asigna docentes; cada docente asignado publica
--  material y actividades de SUS cursos; el estudiante solo ve los cursos de su carrera/ciclo.
--  Aislamiento: todo cuelga de colegio_id y se valida con funciones SECURITY DEFINER.
--  Archivos: bucket privado «cursos» (máx. 10 MB por archivo), ruta  <colegio_id>/<curso_id>/<archivo>.
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================

-- ---------- Funciones de acceso al curso ----------
-- ¿Puede ver el curso? Personal de su instituto (el coordinador solo su carrera) o estudiante aprobado del mismo ciclo.
create or replace function public.puede_ver_curso(p_curso uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((
    select (
      (c.colegio_id = public.mi_colegio() and (not public.es_coordinador() or c.nivel = public.mi_carrera()))
      or exists (
        select 1 from public.alumnos a
        where a.user_id = auth.uid() and a.colegio_id = c.colegio_id
          and a.estado = 'ACTIVO' and a.aprobado is not false
          and a.nivel = c.nivel and (c.grado is null or c.grado = a.grado))
    )
    from public.cursos c where c.id = p_curso
  ), false)
$$;

-- ¿Puede publicar en el curso? Administrador del instituto o docente asignado al curso.
create or replace function public.puede_gestionar_curso(p_curso uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((
    select c.colegio_id = public.mi_colegio()
       and (public.es_admin() or exists (select 1 from public.curso_docentes d where d.curso_id = c.id and d.user_id = auth.uid()))
    from public.cursos c where c.id = p_curso
  ), false)
$$;

-- Curso al que pertenece un archivo del bucket (segunda carpeta de la ruta); null si la ruta no es válida.
create or replace function public.curso_de_ruta(p_ruta text) returns uuid
language sql immutable set search_path = public as $$
  select case when (storage.foldername(p_ruta))[2] ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
              then ((storage.foldername(p_ruta))[2])::uuid end
$$;

-- ---------- Tablas ----------
create table if not exists public.curso_docentes (
  curso_id   uuid not null references public.cursos(id) on delete cascade,
  user_id    uuid not null references auth.users(id) on delete cascade,
  colegio_id uuid not null references public.colegios(id) on delete cascade,
  creado_en  timestamptz not null default now(),
  primary key (curso_id, user_id)
);
create index if not exists idx_curso_docentes_user on public.curso_docentes (user_id);

create table if not exists public.curso_materiales (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  curso_id       uuid not null references public.cursos(id) on delete cascade,
  tema           text not null default 'General' check (char_length(tema) between 1 and 80),
  tipo           text not null default 'documento' check (tipo in ('documento', 'enlace', 'aviso')),
  titulo         text not null check (char_length(titulo) between 1 and 160),
  descripcion    text not null default '' check (char_length(descripcion) <= 4000),
  url            text check (url is null or url ~* '^https?://'),
  archivo_path   text,
  archivo_nombre text,
  archivo_bytes  integer check (archivo_bytes is null or archivo_bytes between 0 and 10485760),
  publicado      boolean not null default true,
  creado_por     uuid references auth.users(id) on delete set null default auth.uid(),
  creado_en      timestamptz not null default now()
);
create index if not exists idx_curso_materiales on public.curso_materiales (curso_id, creado_en desc);

create table if not exists public.curso_actividades (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  curso_id       uuid not null references public.cursos(id) on delete cascade,
  titulo         text not null check (char_length(titulo) between 1 and 160),
  instrucciones  text not null default '' check (char_length(instrucciones) <= 8000),
  fecha_limite   timestamptz,
  puntaje_max    numeric(5, 2) not null default 20 check (puntaje_max > 0 and puntaje_max <= 100),
  archivo_path   text,
  archivo_nombre text,
  archivo_bytes  integer check (archivo_bytes is null or archivo_bytes between 0 and 10485760),
  publicado      boolean not null default true,
  creado_por     uuid references auth.users(id) on delete set null default auth.uid(),
  creado_en      timestamptz not null default now()
);
create index if not exists idx_curso_actividades on public.curso_actividades (curso_id, fecha_limite);

-- El colegio_id siempre sale del curso (el cliente no lo decide)
create or replace function public.curso_fijar_colegio() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  select c.colegio_id into new.colegio_id from public.cursos c where c.id = new.curso_id;
  if new.colegio_id is null then raise exception 'Curso no encontrado'; end if;
  return new;
end $$;
do $$
declare t text;
begin
  foreach t in array array['curso_docentes', 'curso_materiales', 'curso_actividades'] loop
    execute format('drop trigger if exists trg_fijar_colegio on public.%I', t);
    execute format('create trigger trg_fijar_colegio before insert or update of curso_id on public.%I for each row execute function public.curso_fijar_colegio()', t);
  end loop;
end $$;

-- ---------- RLS ----------
alter table public.curso_docentes   enable row level security;
alter table public.curso_materiales enable row level security;
alter table public.curso_actividades enable row level security;

-- El estudiante ve los cursos de su carrera/ciclo (el personal ya tiene la política «personal del instituto»)
drop policy if exists "estudiante ve sus cursos" on public.cursos;
create policy "estudiante ve sus cursos" on public.cursos for select to authenticated
  using (public.puede_ver_curso(id));

-- Docentes asignados: ve el personal del instituto; asigna y quita solo el administrador
drop policy if exists "personal ve docentes del curso" on public.curso_docentes;
create policy "personal ve docentes del curso" on public.curso_docentes for select to authenticated
  using (colegio_id = public.mi_colegio());
drop policy if exists "admin asigna docentes" on public.curso_docentes;
create policy "admin asigna docentes" on public.curso_docentes for insert to authenticated
  with check (public.es_admin() and public.puede_gestionar_curso(curso_id));
drop policy if exists "admin quita docentes" on public.curso_docentes;
create policy "admin quita docentes" on public.curso_docentes for delete to authenticated
  using (public.es_admin() and colegio_id = public.mi_colegio());

-- Material y actividades: lee quien ve el curso (borradores solo quien lo gestiona); escribe quien lo gestiona
do $$
declare t text;
begin
  foreach t in array array['curso_materiales', 'curso_actividades'] loop
    execute format('drop policy if exists %I on public.%I', 'ver segun curso', t);
    execute format('create policy %I on public.%I for select to authenticated using (public.puede_ver_curso(curso_id) and (publicado or public.puede_gestionar_curso(curso_id)))', 'ver segun curso', t);
    execute format('drop policy if exists %I on public.%I', 'gestiona su curso (insertar)', t);
    execute format('create policy %I on public.%I for insert to authenticated with check (public.puede_gestionar_curso(curso_id))', 'gestiona su curso (insertar)', t);
    execute format('drop policy if exists %I on public.%I', 'gestiona su curso (editar)', t);
    execute format('create policy %I on public.%I for update to authenticated using (public.puede_gestionar_curso(curso_id)) with check (public.puede_gestionar_curso(curso_id))', 'gestiona su curso (editar)', t);
    execute format('drop policy if exists %I on public.%I', 'gestiona su curso (borrar)', t);
    execute format('create policy %I on public.%I for delete to authenticated using (public.puede_gestionar_curso(curso_id))', 'gestiona su curso (borrar)', t);
  end loop;
end $$;

revoke all on function public.puede_ver_curso(uuid), public.puede_gestionar_curso(uuid), public.curso_de_ruta(text) from public, anon;
grant execute on function public.puede_ver_curso(uuid), public.puede_gestionar_curso(uuid), public.curso_de_ruta(text) to authenticated;

-- ---------- Archivos del aula (bucket privado, máx. 10 MB) ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('cursos', 'cursos', false, 10485760, array[
  'application/pdf', 'text/plain', 'application/zip',
  'application/msword', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  'application/vnd.ms-powerpoint', 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
  'application/vnd.ms-excel', 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  'image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "cursos: ve archivos de su curso" on storage.objects;
create policy "cursos: ve archivos de su curso" on storage.objects for select to authenticated
  using (bucket_id = 'cursos' and public.puede_ver_curso(public.curso_de_ruta(name)));
drop policy if exists "cursos: sube a su curso" on storage.objects;
create policy "cursos: sube a su curso" on storage.objects for insert to authenticated
  with check (bucket_id = 'cursos' and (storage.foldername(name))[1] = public.mi_colegio()::text
              and public.puede_gestionar_curso(public.curso_de_ruta(name)));
drop policy if exists "cursos: reemplaza en su curso" on storage.objects;
create policy "cursos: reemplaza en su curso" on storage.objects for update to authenticated
  using (bucket_id = 'cursos' and public.puede_gestionar_curso(public.curso_de_ruta(name)))
  with check (bucket_id = 'cursos' and public.puede_gestionar_curso(public.curso_de_ruta(name)));
drop policy if exists "cursos: borra de su curso" on storage.objects;
create policy "cursos: borra de su curso" on storage.objects for delete to authenticated
  using (bucket_id = 'cursos' and public.puede_gestionar_curso(public.curso_de_ruta(name)));
