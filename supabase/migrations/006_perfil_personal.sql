-- =====================================================================
--  006 · Foto de perfil del personal (docentes, coordinadores, administradores)
--  Migración ADITIVA y re-ejecutable. No borra datos.
--  La creación de usuarios (correo + contraseña) la hace la Edge Function «gestionar-personal»
--  (supabase/functions/gestionar-personal), porque crear cuentas exige la clave de servicio, que nunca debe
--  estar en el navegador.
-- =====================================================================

alter table public.perfiles add column if not exists foto_path text;

-- Bucket privado: cada persona sube SOLO a su carpeta (<id de usuario>/…); el personal del mismo instituto la ve.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('fotos-personal', 'fotos-personal', false, 524288, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "fotos personal: cada uno su carpeta" on storage.objects;
create policy "fotos personal: cada uno su carpeta" on storage.objects for all to authenticated
  using (bucket_id = 'fotos-personal' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'fotos-personal' and (storage.foldername(name))[1] = auth.uid()::text);

-- (La tabla perfiles solo deja ver la propia fila, por eso la comparación entre personas va en una función SECURITY DEFINER.)
create or replace function public.es_personal_de_mi_instituto(p_id text) returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.perfiles where id::text = p_id and colegio_id = public.mi_colegio())
$$;
revoke all on function public.es_personal_de_mi_instituto(text) from public, anon;
grant execute on function public.es_personal_de_mi_instituto(text) to authenticated;

drop policy if exists "fotos personal: ve el mismo instituto" on storage.objects;
create policy "fotos personal: ve el mismo instituto" on storage.objects for select to authenticated
  using (bucket_id = 'fotos-personal' and public.es_personal_de_mi_instituto((storage.foldername(name))[1]));

-- Cada persona actualiza SU nombre y foto (la tabla perfiles no se puede escribir directamente: el rol y la carrera
-- solo los cambia el administrador con personal_asignar).
create or replace function public.actualizar_mi_perfil(p_nombre text default null, p_path text default null) returns void
language plpgsql volatile security definer set search_path = public as $$
begin
  if not exists (select 1 from public.perfiles where id = auth.uid()) then raise exception 'Esta cuenta no pertenece al personal'; end if;
  if p_path is not null and p_path not like auth.uid()::text || '/%' then raise exception 'Ruta de foto no válida'; end if;
  update public.perfiles
     set nombre = coalesce(nullif(left(trim(coalesce(p_nombre, '')), 80), ''), nombre),
         foto_path = coalesce(p_path, foto_path)
   where id = auth.uid();
end $$;
revoke all on function public.actualizar_mi_perfil(text, text) from public, anon;
grant execute on function public.actualizar_mi_perfil(text, text) to authenticated;

-- El listado del personal incluye la foto
create or replace function public.personal_listar() returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede ver el personal'; end if;
  return coalesce((select json_agg(json_build_object('id', p.id, 'email', u.email, 'nombre', p.nombre, 'rol', p.rol, 'carrera', p.carrera, 'foto_path', p.foto_path)
                          order by lower(coalesce(p.nombre, u.email)))
                   from public.perfiles p join auth.users u on u.id = p.id where p.colegio_id = public.mi_colegio()), '[]'::json);
end $$;
