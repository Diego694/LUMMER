-- =====================================================================
--  009 · Aviso de aprobación al estudiante
--  Cada alumno tiene un token privado (solo lo ve él en su carnet). Con ese token, su teléfono o su navegador
--  consulta —sin sesión— si el instituto ya aprobó su registro y le muestra una notificación.
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================
alter table public.alumnos add column if not exists notif_token text;
do $$ begin
  -- el relleno inicial no debe llenar el historial de cambios con una fila por alumno
  if exists (select 1 from pg_trigger where tgname = 'trg_auditar' and tgrelid = 'public.alumnos'::regclass) then
    alter table public.alumnos disable trigger trg_auditar;
    update public.alumnos set notif_token = replace(gen_random_uuid()::text, '-', '') where notif_token is null;
    alter table public.alumnos enable trigger trg_auditar;
  else
    update public.alumnos set notif_token = replace(gen_random_uuid()::text, '-', '') where notif_token is null;
  end if;
end $$;
alter table public.alumnos alter column notif_token set default replace(gen_random_uuid()::text, '-', '');
create unique index if not exists alumnos_notif_token_uq on public.alumnos (notif_token);

-- Estado de la solicitud de un estudiante, por su token (32 caracteres aleatorios). Devuelve solo si está aprobado y su primer nombre.
create or replace function public.estado_solicitud(p_token text) returns json
language sql stable security definer set search_path = public as $$
  select json_build_object('aprobado', a.aprobado, 'nombre', split_part(a.nombre, ' ', 1))
  from public.alumnos a
  where p_token is not null and length(p_token) = 32 and a.notif_token = p_token
$$;
grant execute on function public.estado_solicitud(text) to anon, authenticated;
