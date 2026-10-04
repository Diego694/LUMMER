-- =====================================================================
--  005 · Personal y accesos (asignar roles desde la app) + comunicados del personal + avisos al teléfono
--  Migración ADITIVA y re-ejecutable. No borra datos.
--
--  · Los docentes y coordinadores pueden PUBLICAR comunicados (eliminarlos sigue siendo solo del administrador).
--  · El administrador asigna/quita roles a cuentas ya creadas en Supabase Auth, sin tocar SQL (personal_*).
--  · Avisos al teléfono: cada instituto tiene un token de avisos; la app Android (docente o estudiante) lo usa para
--    consultar comunicados nuevos en segundo plano y mostrarlos como notificación (comunicados_desde).
-- =====================================================================

-- ---------- Comunicados: momento exacto de publicación y quién los publicó ----------
alter table public.comunicados
  add column if not exists creado_en timestamptz not null default now(),
  add column if not exists publicado_por uuid default auth.uid();
create index if not exists idx_comunicados_creado on public.comunicados (colegio_id, creado_en desc);

-- El personal (cualquier rol) puede insertar; modificar y borrar sigue reservado al administrador (política de la 004).
drop policy if exists "solo admin inserta" on public.comunicados;
drop policy if exists "solo personal inserta" on public.comunicados;
create policy "solo personal inserta" on public.comunicados as restrictive for insert to authenticated
  with check (public.mi_colegio() is not null);
drop policy if exists "personal publica comunicados" on public.comunicados;
create policy "personal publica comunicados" on public.comunicados for insert to authenticated
  with check (colegio_id = public.mi_colegio());

-- ---------- Token de avisos del instituto ----------
alter table public.colegios add column if not exists aviso_token text not null default replace(gen_random_uuid()::text, '-', '');

-- Lo reciben el personal y los estudiantes aprobados de ese instituto (para configurar las notificaciones del teléfono).
create or replace function public.token_avisos() returns text
language sql stable security definer set search_path = public as $$
  select c.aviso_token from public.colegios c
  where c.id = coalesce(public.mi_colegio(), (select a.colegio_id from public.alumnos a where a.user_id = auth.uid() and a.aprobado limit 1))
$$;
revoke all on function public.token_avisos() from public, anon;
grant execute on function public.token_avisos() to authenticated;

-- Consulta ligera para el segundo plano del teléfono (sin sesión): solo comunicados, solo con el token del instituto.
create or replace function public.comunicados_desde(p_token text, p_desde timestamptz default null) returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if p_token is null or length(p_token) <> 32 then return '[]'::json; end if;
  return coalesce((
    select json_agg(x order by x.creado_en) from (
      select m.id, m.titulo, left(m.mensaje, 500) as mensaje, m.creado_en
      from public.comunicados m join public.colegios c on c.id = m.colegio_id
      where c.aviso_token = p_token and m.creado_en > coalesce(p_desde, now() - interval '1 day')
      order by m.creado_en desc limit 10
    ) x), '[]'::json);
end $$;
grant execute on function public.comunicados_desde(text, timestamptz) to anon, authenticated;

-- ---------- Personal y accesos (solo administrador) ----------
create or replace function public.personal_listar() returns json
language plpgsql stable security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede ver el personal'; end if;
  return coalesce((select json_agg(json_build_object('id', p.id, 'email', u.email, 'nombre', p.nombre, 'rol', p.rol, 'carrera', p.carrera) order by lower(coalesce(p.nombre, u.email)))
                   from public.perfiles p join auth.users u on u.id = p.id where p.colegio_id = public.mi_colegio()), '[]'::json);
end $$;

create or replace function public.personal_asignar(p_email text, p_rol text, p_carrera text default null, p_nombre text default null) returns json
language plpgsql volatile security definer set search_path = public as $$
declare uid uuid; rol text; car text := nullif(trim(coalesce(p_carrera, '')), ''); cid uuid := public.mi_colegio();
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede asignar roles'; end if;
  rol := case lower(trim(coalesce(p_rol, '')))
    when 'administrador' then 'Administrador' when 'admin' then 'Administrador'
    when 'docente' then 'Docente' when 'coordinador' then 'Coordinador' else null end;
  if rol is null then raise exception 'Rol no válido (usa Administrador, Docente o Coordinador)'; end if;
  if rol = 'Coordinador' then
    if car is null or not exists (select 1 from public.niveles where colegio_id = cid and nombre = car) then
      raise exception 'El coordinador necesita una carrera existente'; end if;
  else car := null; end if;
  select id into uid from auth.users where lower(email) = lower(trim(coalesce(p_email, '')));
  if uid is null then raise exception 'No existe una cuenta con ese correo. Créala primero en Supabase → Authentication → Users.'; end if;
  if exists (select 1 from public.alumnos where user_id = uid) then raise exception 'Esa cuenta pertenece a un estudiante'; end if;
  if exists (select 1 from public.perfiles where id = uid and colegio_id <> cid) then raise exception 'Esa cuenta pertenece a otro instituto'; end if;
  if uid = auth.uid() and rol <> 'Administrador' then raise exception 'No puedes quitarte a ti mismo el rol de administrador'; end if;
  insert into public.perfiles (id, colegio_id, rol, nombre, carrera)
  values (uid, cid, rol, nullif(trim(coalesce(p_nombre, '')), ''), car)
  on conflict (id) do update set rol = excluded.rol, carrera = excluded.carrera, nombre = coalesce(excluded.nombre, public.perfiles.nombre);
  return json_build_object('id', uid, 'rol', rol, 'carrera', car);
end $$;

create or replace function public.personal_quitar(p_id uuid) returns void
language plpgsql volatile security definer set search_path = public as $$
begin
  if not public.es_admin() then raise exception 'Solo el administrador puede quitar accesos'; end if;
  if p_id = auth.uid() then raise exception 'No puedes quitarte el acceso a ti mismo'; end if;
  delete from public.perfiles where id = p_id and colegio_id = public.mi_colegio();
end $$;

revoke all on function public.personal_listar(), public.personal_asignar(text, text, text, text), public.personal_quitar(uuid) from public, anon;
grant execute on function public.personal_listar(), public.personal_asignar(text, text, text, text), public.personal_quitar(uuid) to authenticated;
