-- =====================================================================
--  002 · Portal de estudiantes (auto‑registro, foto y QR único)
--  Migración ADITIVA y re‑ejecutable: no borra ni modifica datos ni políticas existentes.
--  Ejecutar en Supabase → SQL Editor.
--
--  Diseño de seguridad:
--   * Los estudiantes NO tienen fila en `perfiles` (las políticas existentes dan acceso a todo el
--     colegio a quien la tenga). Operan solo mediante las funciones de abajo (SECURITY DEFINER).
--   * Un estudiante únicamente puede LEER su propio registro; no puede modificar nada directamente.
--   * Para registrarse necesita el "código de registro" que el colegio genera; el administrador debe
--     APROBARLO antes de que su QR registre asistencia.
--   * Fotos en un bucket PRIVADO; el personal las ve con URLs firmadas temporales.
-- =====================================================================

-- ---------- Columnas nuevas ----------
alter table public.colegios add column if not exists codigo_registro text;
create unique index if not exists colegios_codigo_registro_uq
  on public.colegios (upper(codigo_registro)) where codigo_registro is not null;

alter table public.alumnos
  add column if not exists user_id uuid references auth.users(id) on delete set null,
  add column if not exists nombres text,
  add column if not exists apellidos text,
  add column if not exists dni text,
  add column if not exists foto_path text,
  add column if not exists aprobado boolean not null default true,   -- los alumnos existentes quedan aprobados
  add column if not exists consentimiento_en timestamptz,
  add column if not exists registrado_en timestamptz not null default now();
create unique index if not exists alumnos_user_id_uq on public.alumnos (user_id) where user_id is not null;

-- ---------- Funciones (el estudiante solo puede usar estas) ----------
create or replace function public.info_colegio(p_codigo text) returns json
language plpgsql stable security definer set search_path = public as $$
declare c record;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select id, nombre into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo));
  if not found then return null; end if;
  return json_build_object(
    'nombre', c.nombre,
    'niveles', (select coalesce(json_agg(nombre order by nombre), '[]'::json) from public.niveles where colegio_id = c.id),
    'grados',  (select coalesce(json_agg(json_build_object('nivel', nivel, 'nombre', nombre) order by nivel, nombre), '[]'::json)
                from public.grados where colegio_id = c.id));
end $$;

create or replace function public.registrar_estudiante(
  p_codigo_colegio text, p_nombres text, p_apellidos text, p_nivel text, p_grado text,
  p_apoderado text default '', p_dni text default null) returns json
language plpgsql security definer set search_path = public as $$
declare c uuid; v_codigo text; a public.alumnos;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if exists (select 1 from public.alumnos where user_id = auth.uid()) then raise exception 'Ya tienes un registro'; end if;
  if exists (select 1 from public.perfiles where id = auth.uid()) then raise exception 'Esta cuenta pertenece al personal del instituto'; end if;
  select id into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo_colegio));
  if c is null then raise exception 'Código de instituto inválido'; end if;
  if length(trim(coalesce(p_nombres, ''))) < 2 or length(trim(coalesce(p_apellidos, ''))) < 2 then
    raise exception 'Nombres y apellidos son obligatorios';
  end if;
  if not exists (select 1 from public.grados where colegio_id = c and nivel = p_nivel and nombre = p_grado) then
    raise exception 'Carrera o ciclo inválido';
  end if;
  loop  -- código QR único y no adivinable
    v_codigo := 'e' || substr(md5(gen_random_uuid()::text), 1, 10);
    exit when not exists (select 1 from public.alumnos where colegio_id = c and codigo = v_codigo);
  end loop;
  insert into public.alumnos (colegio_id, codigo, nombre, nivel, grado, apoderado, estado,
                              user_id, nombres, apellidos, dni, aprobado, consentimiento_en)
  values (c, v_codigo, trim(p_nombres) || ' ' || trim(p_apellidos), p_nivel, p_grado, coalesce(trim(p_apoderado), ''), 'ACTIVO',
          auth.uid(), trim(p_nombres), trim(p_apellidos), nullif(trim(p_dni), ''), false, now())
  returning * into a;
  return row_to_json(a);
end $$;

create or replace function public.mi_registro() returns json
language sql stable security definer set search_path = public as $$
  select json_build_object('alumno', row_to_json(a), 'colegio', c.nombre)
  from public.alumnos a join public.colegios c on c.id = a.colegio_id
  where a.user_id = auth.uid()
$$;

create or replace function public.actualizar_mi_foto(p_path text) returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if p_path is null or split_part(p_path, '/', 1) <> auth.uid()::text then raise exception 'Ruta de foto inválida'; end if;
  update public.alumnos set foto_path = p_path where user_id = auth.uid();
end $$;

revoke all on function public.info_colegio(text), public.registrar_estudiante(text, text, text, text, text, text, text),
  public.mi_registro(), public.actualizar_mi_foto(text) from public, anon;
grant execute on function public.info_colegio(text), public.registrar_estudiante(text, text, text, text, text, text, text),
  public.mi_registro(), public.actualizar_mi_foto(text) to authenticated;

-- ---------- Política: el estudiante lee SOLO su propio registro ----------
drop policy if exists "estudiante ve su registro" on public.alumnos;
create policy "estudiante ve su registro" on public.alumnos for select to authenticated using (user_id = auth.uid());

-- ---------- Fotos (bucket privado, máx. 512 KB, solo imágenes) ----------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('fotos-alumnos', 'fotos-alumnos', false, 524288, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists "fotos: estudiante gestiona su carpeta" on storage.objects;
create policy "fotos: estudiante gestiona su carpeta" on storage.objects for all to authenticated
  using (bucket_id = 'fotos-alumnos' and (storage.foldername(name))[1] = auth.uid()::text)
  with check (bucket_id = 'fotos-alumnos' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "fotos: personal del instituto ve fotos" on storage.objects;
create policy "fotos: personal del instituto ve fotos" on storage.objects for select to authenticated
  using (bucket_id = 'fotos-alumnos' and exists (
    select 1 from public.alumnos a join public.perfiles p on p.colegio_id = a.colegio_id
    where p.id = auth.uid() and a.user_id::text = (storage.foldername(name))[1]));
