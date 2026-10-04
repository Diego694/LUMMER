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
