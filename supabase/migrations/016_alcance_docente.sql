-- =====================================================================
--  016 · Alcance del docente por curso asignado
--  Un docente (rol distinto de administrador y coordinador) solo LEE a los alumnos, ciclos, cursos y asistencias
--  de los cursos que el administrador le asignó (tabla curso_docentes). Sin cursos asignados no ve ninguno.
--  El administrador y el coordinador (que ya tiene su restricción por carrera) y los estudiantes (sin perfil de
--  personal) no se ven afectados. Solo añade políticas RESTRICTIVAS de lectura; no toca datos ni otras políticas.
--  Requiere la 004 y la 013. Re-ejecutable.
-- =====================================================================

-- ¿Es personal con rol de docente? (perfil existente que no es administrador ni coordinador)
create or replace function public.es_docente() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.mi_rol() is not null and public.mi_rol() not in ('administrador', 'admin', 'coordinador'), false)
$$;

-- ¿El alumno pertenece a alguno de mis cursos? (misma carrera y, si el curso tiene ciclo, el mismo ciclo)
create or replace function public.alumno_en_mis_cursos(p_alumno uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (
    select 1
    from public.alumnos a
    join public.cursos c on c.nivel = a.nivel and (c.grado is null or c.grado = a.grado)
    join public.curso_docentes d on d.curso_id = c.id
    where a.id = p_alumno and d.user_id = auth.uid() and c.colegio_id = public.mi_colegio()
  )
$$;

revoke all on function public.es_docente(), public.alumno_en_mis_cursos(uuid) from public, anon;
grant execute on function public.es_docente(), public.alumno_en_mis_cursos(uuid) to authenticated;

-- Alumnos: el estudiante sigue viendo su propio registro
drop policy if exists "docente solo sus cursos" on public.alumnos;
create policy "docente solo sus cursos" on public.alumnos as restrictive for select to authenticated
  using (user_id = (select auth.uid()) or not (select public.es_docente()) or public.alumno_en_mis_cursos(id));

-- Cursos: solo los asignados (los estudiantes y el resto de roles no se ven afectados)
drop policy if exists "docente solo sus cursos" on public.cursos;
create policy "docente solo sus cursos" on public.cursos as restrictive for select to authenticated
  using (not (select public.es_docente()) or exists (select 1 from public.curso_docentes d where d.curso_id = cursos.id and d.user_id = (select auth.uid())));

-- Carreras y ciclos: solo los de sus cursos
drop policy if exists "docente solo sus cursos" on public.niveles;
create policy "docente solo sus cursos" on public.niveles as restrictive for select to authenticated
  using (not (select public.es_docente()) or exists (
    select 1 from public.curso_docentes d join public.cursos c on c.id = d.curso_id
    where d.user_id = (select auth.uid()) and c.nivel = niveles.nombre));

drop policy if exists "docente solo sus cursos" on public.grados;
create policy "docente solo sus cursos" on public.grados as restrictive for select to authenticated
  using (not (select public.es_docente()) or exists (
    select 1 from public.curso_docentes d join public.cursos c on c.id = d.curso_id
    where d.user_id = (select auth.uid()) and c.nivel = grados.nivel and (c.grado is null or c.grado = grados.nombre)));

-- Directorio de docentes: no es para docentes
drop policy if exists "docente no ve el directorio" on public.docentes;
create policy "docente no ve el directorio" on public.docentes as restrictive for select to authenticated
  using (not (select public.es_docente()));

-- Asistencias, justificaciones y avisos: solo de alumnos de sus cursos
do $$
declare t text;
begin
  foreach t in array array['asistencias', 'justificaciones', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'docente solo sus cursos', t);
    execute format('create policy %I on public.%I as restrictive for select to authenticated using (not (select public.es_docente()) or public.alumno_en_mis_cursos(alumno_id))', 'docente solo sus cursos', t);
  end loop;
end $$;

-- Asistencia por curso: solo la de sus cursos asignados
drop policy if exists "docente solo sus cursos" on public.asistencias_curso;
create policy "docente solo sus cursos" on public.asistencias_curso as restrictive for select to authenticated
  using (not (select public.es_docente()) or exists (select 1 from public.curso_docentes d where d.curso_id = asistencias_curso.curso_id and d.user_id = (select auth.uid())));
