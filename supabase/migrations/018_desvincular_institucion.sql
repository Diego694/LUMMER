-- =====================================================================
--  018 · Desvincular una institución de la base de datos (superadmin)
--  «Desvincular» separa la institución SIN borrar nada: queda inactiva y su personal deja de ver o escribir datos
--  (mi_colegio() devuelve null para ella, así que todas las políticas RLS que la usan la excluyen). Los datos siguen
--  guardados y «Vincular» la restablece. Estudiantes y apoderados no cambian. Requiere la 011. Re-ejecutable.
-- =====================================================================

alter table public.colegios add column if not exists desvinculado_en timestamptz;

-- Mi institución: null si está desvinculada (el resto de funciones y políticas no cambian)
create or replace function public.mi_colegio() returns uuid
language sql stable security definer set search_path = public as $$
  select p.colegio_id from public.perfiles p
  where p.id = auth.uid()
    and not exists (select 1 from public.colegios c where c.id = p.colegio_id and c.desvinculado_en is not null)
$$;

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
        c.desvinculado_en,
        (select count(*) from public.alumnos a where a.colegio_id = c.id) as alumnos,
        (select count(*) from public.perfiles p where p.colegio_id = c.id) as personal,
        coalesce(c.id = public.mi_colegio(), false) as actual
      from public.colegios c
    ) x
  );
end;
$$;

create or replace function public.sa_desvincular(p_id uuid, p_desvincular boolean)
returns void
language plpgsql security definer
set search_path = public
as $$
begin
  if not public.es_superadmin() then
    raise exception 'Solo el administrador superior puede hacer esto';
  end if;
  if p_desvincular and exists (select 1 from public.perfiles where id = auth.uid() and colegio_id = p_id) then
    raise exception 'Estás dentro de esta institución: entra primero a otra y vuelve a intentarlo';
  end if;

  update public.colegios
     set desvinculado_en = case when p_desvincular then now() else null end,
         activo = not p_desvincular
   where id = p_id;
  if not found then
    raise exception 'No se encontró la institución';
  end if;
end;
$$;

revoke all on function public.sa_desvincular(uuid, boolean) from public, anon;
grant execute on function public.sa_desvincular(uuid, boolean) to authenticated;
