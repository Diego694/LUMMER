-- =====================================================================
--  024 · El estudiante ve el docente asignado a cada uno de sus cursos
--  · El docente asignado vive en public.curso_docentes (+ perfiles.nombre); la columna cursos.docente es un
--    texto libre que casi siempre queda vacío, por eso la app mostraba «Docente por asignar».
--  · El estudiante no puede leer curso_docentes ni perfiles (RLS), así que se expone SOLO el nombre mediante una
--    función SECURITY DEFINER limitada a cursos que el propio usuario puede ver (puede_ver_curso).
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================
create or replace function public.docentes_de_mis_cursos()
returns table (curso_id uuid, docente text)
language sql stable security definer set search_path = public as $$
  select d.curso_id,
         string_agg(coalesce(nullif(btrim(p.nombre), ''), 'Docente'), ', ' order by p.nombre) as docente
  from public.curso_docentes d
  join public.perfiles p on p.id = d.user_id
  where public.puede_ver_curso(d.curso_id)
  group by d.curso_id
$$;
revoke all on function public.docentes_de_mis_cursos() from public, anon;
grant execute on function public.docentes_de_mis_cursos() to authenticated;
