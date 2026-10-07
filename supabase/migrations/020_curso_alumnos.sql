-- =====================================================================
--  020 · Estudiantes del curso (matrícula manual)
--  Permite matricular alumnos manualmente en un curso específico (alumnos de otro ciclo o
--  carrera que llevan el curso: arrastres, convalidaciones, electivos) además de la regla
--  automática de ciclo.
--  Aislamiento: todo cuelga de colegio_id y se valida con funciones SECURITY DEFINER.
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================

-- ---------- 1. Tabla de matrícula manual ----------
create table if not exists public.curso_alumnos (
  curso_id     uuid not null references public.cursos(id) on delete cascade,
  alumno_id    uuid not null references public.alumnos(id) on delete cascade,
  colegio_id   uuid not null references public.colegios(id) on delete cascade,
  agregado_por uuid references auth.users(id) on delete set null default auth.uid(),
  creado_en    timestamptz not null default now(),
  primary key (curso_id, alumno_id)
);
create index if not exists idx_curso_alumnos_alumno on public.curso_alumnos (alumno_id);

-- El colegio_id siempre sale del curso (el cliente no lo decide)
create or replace function public.curso_fijar_colegio() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  select c.colegio_id into new.colegio_id from public.cursos c where c.id = new.curso_id;
  if new.colegio_id is null then raise exception 'Curso no encontrado'; end if;
  return new;
end $$;

drop trigger if exists trg_fijar_colegio on public.curso_alumnos;
create trigger trg_fijar_colegio
  before insert or update of curso_id on public.curso_alumnos
  for each row execute function public.curso_fijar_colegio();

-- ---------- 2. Funciones auxiliares ----------
-- ¿Es personal con rol de docente? (perfil existente que no es administrador ni coordinador)
create or replace function public.es_docente() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.mi_rol() is not null and public.mi_rol() not in ('administrador', 'admin', 'coordinador'), false)
$$;

-- ¿Puede ver el curso? Personal de su instituto (el coordinador solo su carrera) o
-- estudiante aprobado del mismo ciclo o matriculado manualmente en curso_alumnos (ACTIVO y aprobado).
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
      or exists (
        select 1 from public.alumnos a
        join public.curso_alumnos ca on ca.alumno_id = a.id and ca.curso_id = c.id
        where a.user_id = auth.uid() and a.colegio_id = c.colegio_id
          and a.estado = 'ACTIVO' and a.aprobado is not false)
    )
    from public.cursos c where c.id = p_curso
  ), false)
$$;

-- ¿El alumno pertenece a alguno de mis cursos? (automático por ciclo O manual por curso_alumnos)
create or replace function public.alumno_en_mis_cursos(p_alumno uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1
    from public.alumnos a
    join public.cursos c on c.nivel = a.nivel and (c.grado is null or c.grado = a.grado)
    join public.curso_docentes d on d.curso_id = c.id
    where a.id = p_alumno and d.user_id = auth.uid() and c.colegio_id = public.mi_colegio()
  ) or exists (
    select 1
    from public.curso_alumnos ca
    join public.curso_docentes d on d.curso_id = ca.curso_id
    where ca.alumno_id = p_alumno and d.user_id = auth.uid() and ca.colegio_id = public.mi_colegio()
  )
$$;

-- ¿El alumno pertenece al colegio del usuario actual? (SECURITY DEFINER para validar sin RLS del docente)
create or replace function public.alumno_es_de_mi_colegio(p_alumno uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1 from public.alumnos
    where id = p_alumno and colegio_id = public.mi_colegio()
  )
$$;

revoke all on function public.puede_ver_curso(uuid), public.alumno_en_mis_cursos(uuid), public.es_docente(), public.alumno_es_de_mi_colegio(uuid) from public, anon;
grant execute on function public.puede_ver_curso(uuid), public.alumno_en_mis_cursos(uuid), public.es_docente(), public.alumno_es_de_mi_colegio(uuid) to authenticated;

-- ---------- 3. Row Level Security ----------
alter table public.curso_alumnos enable row level security;

-- Lectura: personal del instituto con permiso para ver el curso o el propio estudiante matriculado
drop policy if exists "ver curso_alumnos" on public.curso_alumnos;
create policy "ver curso_alumnos" on public.curso_alumnos for select to authenticated
  using (
    ((select public.mi_colegio()) is not null and (select public.puede_ver_curso(curso_id)))
    or alumno_id = (select public.mi_alumno_id())
  );

-- Restricción para docentes: solo leen filas de los cursos que tienen asignados (estilo 016)
drop policy if exists "docente solo sus cursos" on public.curso_alumnos;
create policy "docente solo sus cursos" on public.curso_alumnos as restrictive for select to authenticated
  using (
    not (select public.es_docente())
    or exists (
      select 1 from public.curso_docentes d
      where d.curso_id = curso_alumnos.curso_id
        and d.user_id = (select auth.uid())
    )
  );

-- Inserción: quien gestiona el curso (admin o docente asignado), dentro de su instituto y alumno de su instituto
drop policy if exists "gestiona curso_alumnos (insertar)" on public.curso_alumnos;
create policy "gestiona curso_alumnos (insertar)" on public.curso_alumnos for insert to authenticated
  with check (
    colegio_id = (select public.mi_colegio())
    and (select public.puede_gestionar_curso(curso_id))
    and public.alumno_es_de_mi_colegio(alumno_id)
  );

-- Borrado: quien gestiona el curso (admin o docente asignado) en su propio instituto
drop policy if exists "gestiona curso_alumnos (borrar)" on public.curso_alumnos;
create policy "gestiona curso_alumnos (borrar)" on public.curso_alumnos for delete to authenticated
  using (
    colegio_id = (select public.mi_colegio())
    and (select public.puede_gestionar_curso(curso_id))
  );

-- Permisos sobre la tabla
revoke all on public.curso_alumnos from anon;
grant select, insert, delete on public.curso_alumnos to authenticated;
