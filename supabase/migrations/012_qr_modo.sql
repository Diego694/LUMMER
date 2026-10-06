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
