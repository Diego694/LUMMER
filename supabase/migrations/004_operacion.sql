-- =====================================================================
--  004 · Operación real: hora del servidor, roles, justificaciones, cursos, avisos,
--        registro de errores, protección contra abuso y borrado de datos.
--  Migración ADITIVA y re-ejecutable. No borra datos. Las políticas nuevas son RESTRICTIVAS: solo
--  pueden quitar permisos (nunca dar de más) y los administradores quedan siempre con acceso total.
--
--  Roles (perfiles.rol, sin distinguir mayúsculas):
--    administrador | admin   → todo
--    docente                 → leer; registrar asistencia, justificaciones y avisos
--    coordinador             → como docente, pero SOLO ve la carrera indicada en perfiles.carrera
-- =====================================================================

-- ---------- Hora del servidor (reloj confiable para la asistencia) ----------
create or replace function public.hora_servidor() returns timestamptz
language sql stable as $$ select now() $$;
grant execute on function public.hora_servidor() to authenticated;

-- ---------- Columnas nuevas ----------
alter table public.asistencias
  add column if not exists registrado_en timestamptz not null default now(),   -- momento en que el servidor lo recibió
  add column if not exists origen text;                                         -- qr | nfc | manual | alumno | masivo
alter table public.perfiles add column if not exists carrera text;             -- para el rol coordinador
alter table public.alumnos
  add column if not exists apoderado_telefono text,
  add column if not exists apoderado_email text,
  add column if not exists qr_secreto text not null default replace(gen_random_uuid()::text, '-', '');

create index if not exists idx_asistencias_colegio_fecha on public.asistencias (colegio_id, fecha);
create index if not exists idx_alumnos_colegio_carrera on public.alumnos (colegio_id, nivel, grado);

-- ---------- Funciones auxiliares de rol (SECURITY DEFINER: no dependen de las políticas de perfiles) ----------
create or replace function public.mi_colegio() returns uuid
language sql stable security definer set search_path = public as $$
  select colegio_id from public.perfiles where id = auth.uid()
$$;
create or replace function public.mi_rol() returns text
language sql stable security definer set search_path = public as $$
  select lower(trim(rol)) from public.perfiles where id = auth.uid()
$$;
create or replace function public.es_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.mi_rol() in ('administrador', 'admin'), false)
$$;
create or replace function public.es_coordinador() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce(public.mi_rol() = 'coordinador', false)
$$;
create or replace function public.mi_carrera() returns text
language sql stable security definer set search_path = public as $$
  select carrera from public.perfiles where id = auth.uid()
$$;
revoke all on function public.mi_colegio(), public.mi_rol(), public.es_admin(), public.es_coordinador(), public.mi_carrera() from public, anon;
grant execute on function public.mi_colegio(), public.mi_rol(), public.es_admin(), public.es_coordinador(), public.mi_carrera() to authenticated;

-- ---------- Tablas nuevas ----------
create table if not exists public.justificaciones (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  alumno_id      uuid not null references public.alumnos(id) on delete cascade,
  fecha          date not null,
  tipo           text not null default 'Falta justificada' check (tipo in ('Falta justificada', 'Permiso', 'Tardanza justificada')),
  motivo         text not null default '',
  registrado_por uuid references auth.users(id) on delete set null,
  creado_en      timestamptz not null default now(),
  unique (alumno_id, fecha)
);

create table if not exists public.cursos (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nivel       text not null,                    -- carrera
  grado       text,                             -- ciclo/salón (opcional: curso válido para toda la carrera)
  nombre      text not null,
  docente     text not null default '',
  activo      boolean not null default true
);
create unique index if not exists cursos_unico on public.cursos (colegio_id, nivel, coalesce(grado, ''), lower(nombre));

create table if not exists public.asistencias_curso (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  alumno_id      uuid not null references public.alumnos(id) on delete cascade,
  curso_id       uuid not null references public.cursos(id) on delete cascade,
  fecha          date not null,
  hora           time not null,
  registrado_por uuid references auth.users(id) on delete set null,
  registrado_en  timestamptz not null default now(),
  origen         text,
  unique (alumno_id, curso_id, fecha)
);
create index if not exists idx_asist_curso on public.asistencias_curso (colegio_id, curso_id, fecha);

create table if not exists public.avisos_apoderados (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  alumno_id   uuid not null references public.alumnos(id) on delete cascade,
  fecha       date not null,
  tipo        text not null check (tipo in ('Falta', 'Tardanza', 'Llegada', 'Otro')),
  canal       text not null default 'WhatsApp',
  enviado_por uuid references auth.users(id) on delete set null,
  creado_en   timestamptz not null default now()
);

create table if not exists public.logs_cliente (
  id         uuid primary key default gen_random_uuid(),
  colegio_id uuid references public.colegios(id) on delete cascade,
  user_id    uuid default auth.uid(),
  app        text,
  mensaje    text not null,
  detalle    text,
  url        text,
  agente     text,
  creado_en  timestamptz not null default now()
);
create index if not exists idx_logs_cliente on public.logs_cliente (colegio_id, creado_en desc);

create table if not exists public.intentos_codigo (
  id        bigserial primary key,
  user_id   uuid not null default auth.uid(),
  creado_en timestamptz not null default now()
);
create index if not exists idx_intentos_codigo on public.intentos_codigo (user_id, creado_en);

-- logs_cliente: el servidor completa el instituto (del personal o del estudiante) y limita el tamaño
create or replace function public.logs_cliente_completar() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  new.user_id := auth.uid();
  new.colegio_id := coalesce(public.mi_colegio(), (select colegio_id from public.alumnos where user_id = auth.uid() limit 1));
  new.mensaje := left(new.mensaje, 500);
  new.detalle := left(new.detalle, 4000);
  new.url := left(new.url, 300);
  new.agente := left(new.agente, 300);
  return new;
end $$;
drop trigger if exists trg_logs_cliente on public.logs_cliente;
create trigger trg_logs_cliente before insert on public.logs_cliente for each row execute function public.logs_cliente_completar();

-- ---------- RLS ----------
alter table public.justificaciones    enable row level security;
alter table public.cursos             enable row level security;
alter table public.asistencias_curso  enable row level security;
alter table public.avisos_apoderados  enable row level security;
alter table public.logs_cliente       enable row level security;
alter table public.intentos_codigo    enable row level security;   -- sin políticas: solo las funciones SECURITY DEFINER la usan

-- Acceso base (permisivo): el personal del instituto
do $$
declare t text;
begin
  foreach t in array array['justificaciones', 'cursos', 'asistencias_curso', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'personal del instituto', t);
    execute format('create policy %I on public.%I for all to authenticated using (colegio_id = public.mi_colegio()) with check (colegio_id = public.mi_colegio())', 'personal del instituto', t);
  end loop;
end $$;

drop policy if exists "cualquier usuario registra errores" on public.logs_cliente;
create policy "cualquier usuario registra errores" on public.logs_cliente for insert to authenticated with check (true);  -- el trigger fija user_id/colegio_id
drop policy if exists "admin ve errores" on public.logs_cliente;
create policy "admin ve errores" on public.logs_cliente for select to authenticated using (public.es_admin() and colegio_id = public.mi_colegio());
drop policy if exists "admin borra errores" on public.logs_cliente;
create policy "admin borra errores" on public.logs_cliente for delete to authenticated using (public.es_admin() and colegio_id = public.mi_colegio());

-- Restricciones (RESTRICTIVAS): solo el administrador modifica catálogos, padrón y corrige/borra asistencia
do $$
declare t text;
begin
  foreach t in array array['alumnos', 'niveles', 'grados', 'docentes', 'comunicados', 'cursos'] loop
    execute format('drop policy if exists %I on public.%I', 'solo admin inserta', t);
    execute format('create policy %I on public.%I as restrictive for insert to authenticated with check (public.es_admin())', 'solo admin inserta', t);
    execute format('drop policy if exists %I on public.%I', 'solo admin modifica', t);
    execute format('create policy %I on public.%I as restrictive for update to authenticated using (public.es_admin()) with check (public.es_admin())', 'solo admin modifica', t);
    execute format('drop policy if exists %I on public.%I', 'solo admin elimina', t);
    execute format('create policy %I on public.%I as restrictive for delete to authenticated using (public.es_admin())', 'solo admin elimina', t);
  end loop;
  foreach t in array array['asistencias', 'asistencias_curso', 'justificaciones', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'solo admin modifica', t);
    execute format('create policy %I on public.%I as restrictive for update to authenticated using (public.es_admin()) with check (public.es_admin())', 'solo admin modifica', t);
    execute format('drop policy if exists %I on public.%I', 'solo admin elimina', t);
    execute format('create policy %I on public.%I as restrictive for delete to authenticated using (public.es_admin())', 'solo admin elimina', t);
  end loop;
end $$;

drop policy if exists "solo admin edita el instituto" on public.colegios;
create policy "solo admin edita el instituto" on public.colegios as restrictive for update to authenticated using (public.es_admin()) with check (public.es_admin());

-- Coordinador: solo ve su carrera (el resto de roles y los estudiantes no se ven afectados)
drop policy if exists "coordinador solo su carrera" on public.alumnos;
create policy "coordinador solo su carrera" on public.alumnos as restrictive for select to authenticated
  using (user_id = (select auth.uid()) or not (select public.es_coordinador()) or nivel = (select public.mi_carrera()));
drop policy if exists "coordinador solo su carrera" on public.niveles;
create policy "coordinador solo su carrera" on public.niveles as restrictive for select to authenticated
  using (not (select public.es_coordinador()) or nombre = (select public.mi_carrera()));
drop policy if exists "coordinador solo su carrera" on public.grados;
create policy "coordinador solo su carrera" on public.grados as restrictive for select to authenticated
  using (not (select public.es_coordinador()) or nivel = (select public.mi_carrera()));
drop policy if exists "coordinador solo su carrera" on public.cursos;
create policy "coordinador solo su carrera" on public.cursos as restrictive for select to authenticated
  using (not (select public.es_coordinador()) or nivel = (select public.mi_carrera()));
do $$
declare t text;
begin
  foreach t in array array['asistencias', 'asistencias_curso', 'justificaciones', 'avisos_apoderados'] loop
    execute format('drop policy if exists %I on public.%I', 'coordinador solo su carrera', t);
    execute format($f$create policy %I on public.%I as restrictive for select to authenticated
      using (not (select public.es_coordinador()) or exists (select 1 from public.alumnos a where a.id = %I.alumno_id and a.nivel = (select public.mi_carrera())))$f$,
      'coordinador solo su carrera', t, t);
  end loop;
end $$;

-- ---------- Registro de estudiantes: protección contra abuso + datos del apoderado ----------
create or replace function public.info_colegio(p_codigo text) returns json
language plpgsql volatile security definer set search_path = public as $$
declare c record; n int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select count(*) into n from public.intentos_codigo where user_id = auth.uid() and creado_en > now() - interval '1 hour';
  if n >= 10 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;
  select id, nombre into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo));
  if not found then
    insert into public.intentos_codigo (user_id) values (auth.uid());   -- se conserva: la función retorna normalmente
    return null;
  end if;
  return json_build_object(
    'nombre', c.nombre,
    'niveles', (select coalesce(json_agg(nombre order by nombre), '[]'::json) from public.niveles where colegio_id = c.id),
    'grados',  (select coalesce(json_agg(json_build_object('nivel', nivel, 'nombre', nombre) order by nivel, nombre), '[]'::json)
                from public.grados where colegio_id = c.id));
end $$;

drop function if exists public.registrar_estudiante(text, text, text, text, text, text, text);
create or replace function public.registrar_estudiante(
  p_codigo_colegio text, p_nombres text, p_apellidos text, p_nivel text, p_grado text,
  p_apoderado text default '', p_dni text default null,
  p_apoderado_tel text default null, p_apoderado_email text default null) returns json
language plpgsql security definer set search_path = public as $$
declare c uuid; v_codigo text; a public.alumnos; n int; tel text; mail text;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if exists (select 1 from public.alumnos where user_id = auth.uid()) then raise exception 'Ya tienes un registro'; end if;
  if exists (select 1 from public.perfiles where id = auth.uid()) then raise exception 'Esta cuenta pertenece al personal del instituto'; end if;
  select count(*) into n from public.intentos_codigo where user_id = auth.uid() and creado_en > now() - interval '1 hour';
  if n >= 10 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;
  select id into c from public.colegios where upper(codigo_registro) = upper(trim(p_codigo_colegio));
  if c is null then
    insert into public.intentos_codigo (user_id) values (auth.uid());
    return json_build_object('error', 'codigo_invalido');
  end if;
  if (select count(*) from public.alumnos where colegio_id = c and aprobado = false) >= 500 then
    raise exception 'Hay demasiados registros pendientes de aprobación. Avisa al instituto.';
  end if;
  if length(trim(coalesce(p_nombres, ''))) < 2 or length(trim(coalesce(p_apellidos, ''))) < 2 then raise exception 'Nombres y apellidos son obligatorios'; end if;
  if not exists (select 1 from public.grados where colegio_id = c and nivel = p_nivel and nombre = p_grado) then raise exception 'Carrera o ciclo inválido'; end if;
  tel := nullif(regexp_replace(coalesce(p_apoderado_tel, ''), '[^0-9+]', '', 'g'), '');
  if tel is not null and (length(tel) < 6 or length(tel) > 16) then raise exception 'Teléfono del apoderado inválido'; end if;
  mail := nullif(lower(trim(coalesce(p_apoderado_email, ''))), '');
  if mail is not null and mail !~ '^[^@\s]+@[^@\s]+\.[^@\s]+$' then raise exception 'Correo del apoderado inválido'; end if;
  loop
    v_codigo := 'e' || substr(md5(gen_random_uuid()::text), 1, 10);
    exit when not exists (select 1 from public.alumnos where colegio_id = c and codigo = v_codigo);
  end loop;
  insert into public.alumnos (colegio_id, codigo, nombre, nivel, grado, apoderado, estado,
                              user_id, nombres, apellidos, dni, aprobado, consentimiento_en, apoderado_telefono, apoderado_email)
  values (c, v_codigo, trim(p_nombres) || ' ' || trim(p_apellidos), p_nivel, p_grado, coalesce(trim(p_apoderado), ''), 'ACTIVO',
          auth.uid(), trim(p_nombres), trim(p_apellidos), nullif(trim(p_dni), ''), false, now(), tel, mail)
  returning * into a;
  return row_to_json(a);
end $$;
revoke all on function public.registrar_estudiante(text, text, text, text, text, text, text, text, text) from public, anon;
grant execute on function public.registrar_estudiante(text, text, text, text, text, text, text, text, text) to authenticated;

-- ---------- Derecho de supresión: el estudiante borra su registro y su cuenta ----------
create or replace function public.eliminar_mi_registro() returns void
language plpgsql security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if exists (select 1 from public.perfiles where id = auth.uid()) then
    raise exception 'Las cuentas del personal se eliminan desde la administración';
  end if;
  delete from public.alumnos where user_id = auth.uid();   -- en cascada: asistencias, justificaciones, avisos
  delete from auth.users where id = auth.uid();
end $$;
revoke all on function public.eliminar_mi_registro() from public, anon;
grant execute on function public.eliminar_mi_registro() to authenticated;

-- El administrador puede borrar la foto de un estudiante de su instituto (al eliminar su registro)
drop policy if exists "fotos: admin borra fotos del instituto" on storage.objects;
create policy "fotos: admin borra fotos del instituto" on storage.objects for delete to authenticated
  using (bucket_id = 'fotos-alumnos' and public.es_admin() and exists (
    select 1 from public.alumnos a where a.user_id::text = (storage.foldername(name))[1] and a.colegio_id = public.mi_colegio()));
