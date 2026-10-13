-- =====================================================================
--  023 · El estudiante puede leer su propia asistencia
--  · Las políticas de lectura de asistencias y asistencias_curso se basan en mi_colegio(), que sale de
--    public.perfiles; un estudiante no tiene perfil, así que la app veía su historial siempre vacío.
--  · Se añade una política permisiva de SELECT: cada estudiante (activo y aprobado) ve solo sus filas.
--  Requiere 014 (mi_alumno_id). Migración ADITIVA y re-ejecutable.
-- =====================================================================
drop policy if exists alumno_lee_su_asistencia on public.asistencias;
create policy alumno_lee_su_asistencia on public.asistencias for select to authenticated
  using (alumno_id = (select public.mi_alumno_id()));

drop policy if exists alumno_lee_su_asistencia_curso on public.asistencias_curso;
create policy alumno_lee_su_asistencia_curso on public.asistencias_curso for select to authenticated
  using (alumno_id = (select public.mi_alumno_id()));
