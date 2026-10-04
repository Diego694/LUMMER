-- =====================================================================
--  007 · Operación avanzada: calendario, horarios por carrera, periodos, hora de salida (quiosco),
--        historial de cambios (auditoría) y consulta para apoderados.
--  Migración ADITIVA y re-ejecutable. No borra datos. Requiere 004 (roles) y 005.
-- =====================================================================

-- ---------- Periodos académicos ----------
create table if not exists public.periodos (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  nombre      text not null,
  inicio      date not null,
  fin         date,
  activo      boolean not null default true,
  cerrado_en  timestamptz,
  resumen     jsonb,
  unique (colegio_id, nombre)
);

-- ---------- Calendario (feriados y días sin clases) ----------
create table if not exists public.calendario (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  fecha       date not null,
  tipo        text not null default 'Feriado' check (tipo in ('Feriado', 'Sin clases', 'Evento')),
  nombre      text not null,
  unique (colegio_id, fecha)
);

-- ---------- Horarios (general y por carrera) ----------
create table if not exists public.horarios (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  nivel          text,                                   -- carrera; null = horario general del instituto
  hora_ingreso   text not null default '08:00' check (hora_ingreso ~ '^[0-2][0-9]:[0-5][0-9]$'),
  tolerancia_min int  not null default 0 check (tolerancia_min between 0 and 120),
  hora_salida    text check (hora_salida is null or hora_salida ~ '^[0-2][0-9]:[0-5][0-9]$')
);
create unique index if not exists horarios_unico on public.horarios (colegio_id, coalesce(nivel, ''));

alter table public.periodos   enable row level security;
alter table public.calendario enable row level security;
alter table public.horarios   enable row level security;

do $$
declare t text;
begin
  foreach t in array array['periodos', 'calendario', 'horarios'] loop
    execute format('drop policy if exists %I on public.%I', 'personal ve', t);
    execute format('create policy %I on public.%I for select to authenticated using (colegio_id = public.mi_colegio())', 'personal ve', t);
    execute format('drop policy if exists %I on public.%I', 'admin escribe', t);
    execute format('create policy %I on public.%I for all to authenticated using (colegio_id = public.mi_colegio() and public.es_admin()) with check (colegio_id = public.mi_colegio() and public.es_admin())', 'admin escribe', t);
  end loop;
end $$;

-- ---------- Hora de salida ----------
alter table public.asistencias add column if not exists hora_salida text;

-- Límite de puntualidad (HH:MM) de una carrera: su horario, o el general, o 08:00.
create or replace function public.limite_ingreso(p_colegio uuid, p_nivel text) returns text
language sql stable security definer set search_path = public as $$
  select to_char(h.hora_ingreso::time + make_interval(mins => h.tolerancia_min), 'HH24:MI')
  from public.horarios h
  where h.colegio_id = p_colegio and (h.nivel = p_nivel or h.nivel is null)
  order by (h.nivel is null) limit 1
$$;
revoke all on function public.limite_ingreso(uuid, text) from public, anon;
grant execute on function public.limite_ingreso(uuid, text) to authenticated;

-- Registrar salidas (quiosco y escáner). El personal NO puede actualizar asistencias directamente (solo el administrador
-- corrige), así que la salida pasa por esta función: solo escribe hora_salida, solo si hay ingreso y solo una vez.
create or replace function public.registrar_salidas(p_rows jsonb) returns json
language plpgsql volatile security definer set search_path = public as $$
declare r jsonb; ok int := 0; dup int := 0; sin int := 0; a record; n int;
begin
  if public.mi_colegio() is null then raise exception 'Solo el personal puede registrar salidas'; end if;
  for r in select * from jsonb_array_elements(coalesce(p_rows, '[]'::jsonb)) loop
    select s.id, s.hora_salida into a
    from public.asistencias s join public.alumnos al on al.id = s.alumno_id
    where s.alumno_id = (r->>'alumno_id')::uuid and s.fecha = (r->>'fecha')::date and s.colegio_id = public.mi_colegio()
      and (not public.es_coordinador() or al.nivel = public.mi_carrera());
    if not found then sin := sin + 1;
    elsif a.hora_salida is not null then dup := dup + 1;
    else
      update public.asistencias set hora_salida = left(r->>'hora', 5) where id = a.id and hora_salida is null;
      get diagnostics n = row_count; ok := ok + n;
    end if;
  end loop;
  return json_build_object('ok', ok, 'dup', dup, 'sin_entrada', sin);
end $$;
revoke all on function public.registrar_salidas(jsonb) from public, anon;
grant execute on function public.registrar_salidas(jsonb) to authenticated;

-- ---------- Historial de cambios (auditoría) ----------
create table if not exists public.auditoria (
  id          bigserial primary key,
  colegio_id  uuid,
  user_id     uuid,
  usuario     text,
  accion      text not null,           -- INSERT | UPDATE | DELETE
  tabla       text not null,
  registro_id text,
  detalle     jsonb,
  creado_en   timestamptz not null default now()
);
create index if not exists idx_auditoria on public.auditoria (colegio_id, creado_en desc);
alter table public.auditoria enable row level security;
drop policy if exists "admin ve el historial" on public.auditoria;
create policy "admin ve el historial" on public.auditoria for select to authenticated
  using (public.es_admin() and colegio_id = public.mi_colegio());
revoke insert, update, delete on public.auditoria from authenticated, anon;   -- solo los disparadores escriben

create or replace function public.auditar() returns trigger
language plpgsql security definer set search_path = public as $$
declare fila jsonb; viejo jsonb; det jsonb := '{}'::jsonb; k text; cid uuid; ocultos text[] := array['qr_secreto', 'foto_data', 'foto_path', 'aviso_token', 'codigo_apoderado', 'hora_salida'];
begin
  fila := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
  cid := case when tg_table_name = 'colegios' then (fila->>'id')::uuid else (fila->>'colegio_id')::uuid end;
  if tg_op = 'UPDATE' then
    viejo := to_jsonb(old);
    for k in select jsonb_object_keys(fila) loop
      if not (k = any(ocultos)) and (fila->k) is distinct from (viejo->k) then det := det || jsonb_build_object(k, jsonb_build_object('de', viejo->k, 'a', fila->k)); end if;
    end loop;
    if det = '{}'::jsonb then return null; end if;               -- cambio solo en columnas internas: no se registra
    if tg_table_name = 'alumnos' and (det ? 'nombre' or det ? 'apoderado' or det ? 'dni') then   -- datos personales: se anota el campo, no el valor
      det := (select coalesce(jsonb_object_agg(key, case when key in ('nombre', 'apoderado', 'dni') then '"(dato personal modificado)"'::jsonb else value end), '{}'::jsonb) from jsonb_each(det));
    end if;
  elsif tg_op = 'DELETE' then
    det := case when tg_table_name = 'alumnos' then jsonb_build_object('codigo', fila->>'codigo')            -- sin datos personales (derecho de supresión)
           else fila - ocultos end;
    if tg_table_name = 'alumnos' then
      update public.auditoria set detalle = '{"purgado": "datos personales eliminados"}'::jsonb where tabla = 'alumnos' and registro_id = fila->>'id' and detalle is not null;
    end if;
  else
    det := case when tg_table_name = 'alumnos' then jsonb_build_object('codigo', fila->>'codigo', 'nivel', fila->>'nivel', 'grado', fila->>'grado') else fila - ocultos end;
  end if;
  insert into public.auditoria (colegio_id, user_id, usuario, accion, tabla, registro_id, detalle)
  values (cid, auth.uid(), coalesce((select email from auth.users where id = auth.uid()), 'sistema'), tg_op, tg_table_name, fila->>'id', det);
  return null;
end $$;
revoke all on function public.auditar() from public, anon, authenticated;

do $$
declare t text;
begin
  foreach t in array array['alumnos', 'niveles', 'grados', 'docentes', 'comunicados', 'cursos', 'justificaciones', 'perfiles', 'calendario', 'horarios', 'periodos'] loop
    execute format('drop trigger if exists trg_auditar on public.%I', t);
    execute format('create trigger trg_auditar after insert or update or delete on public.%I for each row execute function public.auditar()', t);
  end loop;
  drop trigger if exists trg_auditar on public.asistencias;
  create trigger trg_auditar after update or delete on public.asistencias for each row execute function public.auditar();   -- los ingresos normales no se registran (volumen)
  drop trigger if exists trg_auditar on public.colegios;
  create trigger trg_auditar after update on public.colegios for each row execute function public.auditar();
end $$;

-- ---------- Consulta para apoderados (sin cuenta: con el código del alumno) ----------
alter table public.alumnos add column if not exists codigo_apoderado text;
update public.alumnos set codigo_apoderado = upper(substr(md5(random()::text || clock_timestamp()::text || id::text), 1, 12)) where codigo_apoderado is null;
alter table public.alumnos alter column codigo_apoderado set default upper(substr(md5(random()::text || clock_timestamp()::text || gen_random_uuid()::text), 1, 12));
create unique index if not exists alumnos_codigo_apoderado_uq on public.alumnos (codigo_apoderado);

create table if not exists public.intentos_apoderado (
  id bigserial primary key, ip text not null, creado_en timestamptz not null default now()
);
create index if not exists idx_intentos_apoderado on public.intentos_apoderado (ip, creado_en);
alter table public.intentos_apoderado enable row level security;   -- sin políticas: solo la función

create or replace function public.consulta_apoderado(p_codigo text) returns json
language plpgsql volatile security definer set search_path = public as $$
declare
  cod text := upper(regexp_replace(coalesce(p_codigo, ''), '[^0-9A-Za-z]', '', 'g'));
  v_ip text := coalesce(nullif(current_setting('request.headers', true), '')::json->>'x-forwarded-for', 'sin-ip');
  a record; per record; hoy date := (now() at time zone 'America/Lima')::date; desde date; lim text;
  dias json; res json; n int;
begin
  select count(*) into n from public.intentos_apoderado where ip = v_ip and creado_en > now() - interval '1 hour';
  if n >= 20 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;
  select al.id, al.nombre, al.nivel, al.grado, al.colegio_id, c.nombre as instituto into a
  from public.alumnos al join public.colegios c on c.id = al.colegio_id
  where al.codigo_apoderado = cod and length(cod) = 12;
  if not found then
    insert into public.intentos_apoderado (ip) values (v_ip);
    return null;
  end if;
  select nombre, inicio into per from public.periodos where colegio_id = a.colegio_id and activo order by inicio desc limit 1;
  desde := greatest(coalesce(per.inicio, hoy - 60), hoy - 120);
  lim := coalesce(public.limite_ingreso(a.colegio_id, a.nivel), '08:00');
  with dias_clase as (
    select d::date as fecha from generate_series(desde, hoy, interval '1 day') d
    where extract(isodow from d) < 6
      and not exists (select 1 from public.calendario c where c.colegio_id = a.colegio_id and c.fecha = d::date and c.tipo in ('Feriado', 'Sin clases'))
  ), est as (
    select dc.fecha,
      case when s.id is not null then (case when left(s.hora::text, 5) > lim then 'T' else 'P' end)
           when j.id is not null then 'J'
           when dc.fecha = hoy then 'pendiente' else 'F' end as estado,
      left(s.hora::text, 5) as hora, s.hora_salida
    from dias_clase dc
    left join public.asistencias s on s.alumno_id = a.id and s.fecha = dc.fecha
    left join public.justificaciones j on j.alumno_id = a.id and j.fecha = dc.fecha
  )
  select json_build_object(
      'alumno', json_build_object('nombre', a.nombre, 'carrera', a.nivel, 'ciclo', a.grado),
      'instituto', a.instituto, 'periodo', per.nombre, 'desde', desde, 'hasta', hoy, 'limite', lim,
      'resumen', json_build_object(
        'dias', count(*) filter (where estado <> 'pendiente'),
        'presentes', count(*) filter (where estado in ('P', 'T')),
        'tardanzas', count(*) filter (where estado = 'T'),
        'justificadas', count(*) filter (where estado = 'J'),
        'faltas', count(*) filter (where estado = 'F'),
        'pct', case when count(*) filter (where estado <> 'pendiente') = 0 then null
                    else round(100.0 * count(*) filter (where estado in ('P', 'T')) / count(*) filter (where estado <> 'pendiente')) end),
      'dias', (select coalesce(json_agg(json_build_object('fecha', fecha, 'estado', estado, 'hora', hora, 'salida', hora_salida) order by fecha desc), '[]'::json)
               from (select * from est order by fecha desc limit 45) x),
      'comunicados', (select coalesce(json_agg(json_build_object('titulo', titulo, 'mensaje', mensaje, 'fecha', fecha) order by fecha desc), '[]'::json)
                      from (select titulo, left(mensaje, 400) as mensaje, fecha from public.comunicados where colegio_id = a.colegio_id order by fecha desc limit 5) y)
    ) into res from est;
  return res;
end $$;
grant execute on function public.consulta_apoderado(text) to anon, authenticated;
