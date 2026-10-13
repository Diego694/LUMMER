-- =====================================================================
--  019 · Asistencia por curso con QR del docente + bitácora de registros con segundos
--  · El docente (o administrador) abre una sesión de QR para un curso: el QR cambia cada 30 s y lo firma el servidor
--    con un secreto que nunca sale de la base. El estudiante lo escanea desde su portal o app y queda marcado.
--  · Cada marca guarda la sesión (quién abrió el QR) y la hora exacta del servidor (registrado_en, con segundos).
--  · bitacora_registros: administrador, coordinador (solo su carrera) y administrador superior ven cada registro con
--    hora, minuto y segundo, quién lo hizo y desde dónde.
--  Requiere 004, 011 y 013. Migración ADITIVA y re-ejecutable.
-- =====================================================================

alter table public.asistencias_curso add column if not exists sesion_id uuid;

create table if not exists public.sesiones_qr_curso (
  id          uuid primary key default gen_random_uuid(),
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  curso_id    uuid not null references public.cursos(id) on delete cascade,
  abierta_por uuid not null default auth.uid() references auth.users(id) on delete cascade,
  secreto     text not null default replace(gen_random_uuid()::text, '-', ''),
  creada_en   timestamptz not null default now(),
  expira_en   timestamptz not null,
  cerrada_en  timestamptz
);
create index if not exists idx_sesiones_qr_curso on public.sesiones_qr_curso (colegio_id, creada_en desc);
create index if not exists idx_asist_curso_sesion on public.asistencias_curso (sesion_id);
alter table public.sesiones_qr_curso enable row level security;   -- sin políticas: solo las funciones SECURITY DEFINER la usan
revoke all on public.sesiones_qr_curso from anon, authenticated;

-- Firma de una ventana de 30 s (el secreto solo lo conoce el servidor)
create or replace function public._firma_qr_curso(p_secreto text, p_sesion uuid, p_ventana bigint) returns text
language sql immutable set search_path = public as $$
  select left(encode(sha256(convert_to(p_secreto || ':' || p_sesion::text || ':' || p_ventana::text, 'utf8')), 'hex'), 12)
$$;
revoke all on function public._firma_qr_curso(text, uuid, bigint) from public, anon, authenticated;

-- ¿Puede el usuario abrir el QR de este curso? Administrador, coordinador de la carrera o docente asignado.
create or replace function public._puede_qr_curso(p_curso uuid) returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((
    select c.colegio_id = public.mi_colegio() and (
      public.es_admin()
      or (public.es_coordinador() and c.nivel = public.mi_carrera())
      or exists (select 1 from public.curso_docentes d where d.curso_id = c.id and d.user_id = auth.uid()))
    from public.cursos c where c.id = p_curso and c.activo is not false
  ), false)
$$;
revoke all on function public._puede_qr_curso(uuid) from public, anon;
grant execute on function public._puede_qr_curso(uuid) to authenticated;

-- Abre (o reemplaza) la sesión de QR del curso. Devuelve { id, expira_en }.
create or replace function public.qr_curso_abrir(p_curso uuid, p_minutos int default 20) returns json
language plpgsql volatile security definer set search_path = public as $$
declare s public.sesiones_qr_curso; n int := greatest(1, least(coalesce(p_minutos, 20), 120));
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if not public._puede_qr_curso(p_curso) then raise exception 'No puedes abrir el QR de este curso'; end if;
  update public.sesiones_qr_curso set cerrada_en = now()
    where curso_id = p_curso and abierta_por = auth.uid() and cerrada_en is null;
  insert into public.sesiones_qr_curso (colegio_id, curso_id, expira_en)
    values (public.mi_colegio(), p_curso, now() + make_interval(mins => n))
    returning * into s;
  return json_build_object('id', s.id, 'expira_en', s.expira_en);
end $$;

-- Token vigente de la sesión (solo quien la abrió). El cliente lo pide cada ~25 s.
create or replace function public.qr_curso_token(p_sesion uuid) returns json
language plpgsql volatile security definer set search_path = public as $$
declare s public.sesiones_qr_curso; w bigint := floor(extract(epoch from now()) / 30);
begin
  select * into s from public.sesiones_qr_curso where id = p_sesion and abierta_por = auth.uid();
  if not found or s.cerrada_en is not null or s.expira_en <= now() then return json_build_object('cerrada', true); end if;
  return json_build_object(
    'token', 'LQ.' || replace(s.id::text, '-', '') || '.' || w::text || '.' || public._firma_qr_curso(s.secreto, s.id, w),
    'segundos', 30 - (extract(epoch from now())::bigint % 30),
    'expira_en', s.expira_en);
end $$;

create or replace function public.qr_curso_cerrar(p_sesion uuid) returns void
language sql volatile security definer set search_path = public as $$
  update public.sesiones_qr_curso set cerrada_en = coalesce(cerrada_en, now())
  where id = p_sesion and abierta_por = auth.uid()
$$;

revoke all on function public.qr_curso_abrir(uuid, int), public.qr_curso_token(uuid), public.qr_curso_cerrar(uuid) from public, anon;
grant execute on function public.qr_curso_abrir(uuid, int), public.qr_curso_token(uuid), public.qr_curso_cerrar(uuid) to authenticated;

-- El estudiante escanea el QR del docente y queda marcado en ese curso. Hora y fecha las pone el servidor (Lima).
create or replace function public.marcar_asistencia_qr_curso(p_token text) returns json
language plpgsql volatile security definer set search_path = public as $$
declare
  a public.alumnos; s public.sesiones_qr_curso; c public.cursos;
  partes text[]; sid uuid; w bigint; ahora_w bigint := floor(extract(epoch from now()) / 30);
  v_fecha date := (now() at time zone 'America/Lima')::date;
  v_hora time := date_trunc('second', (now() at time zone 'America/Lima'))::time;
  previa time; n int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select * into a from public.alumnos where user_id = auth.uid();
  if not found then raise exception 'Esta cuenta no es de un estudiante'; end if;
  if a.estado <> 'ACTIVO' then raise exception 'Tu registro está inactivo'; end if;
  if a.aprobado is false then raise exception 'Tu registro aún no fue aprobado'; end if;
  select count(*) into n from public.intentos_codigo where user_id = auth.uid() and creado_en > now() - interval '10 minutes';
  if n >= 30 then raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.'; end if;

  partes := string_to_array(coalesce(p_token, ''), '.');
  if array_length(partes, 1) is distinct from 4 or partes[1] <> 'LQ' or partes[2] !~ '^[0-9a-f]{32}$' or partes[3] !~ '^[0-9]{1,12}$' then
    insert into public.intentos_codigo (user_id) values (auth.uid());
    return json_build_object('estado', 'invalido', 'mensaje', 'Ese código QR no es de asistencia.');
  end if;
  sid := (substr(partes[2], 1, 8) || '-' || substr(partes[2], 9, 4) || '-' || substr(partes[2], 13, 4) || '-' || substr(partes[2], 17, 4) || '-' || substr(partes[2], 21))::uuid;
  w := partes[3]::bigint;
  select * into s from public.sesiones_qr_curso where id = sid and colegio_id = a.colegio_id;
  if not found or partes[4] <> public._firma_qr_curso(s.secreto, s.id, w) then
    insert into public.intentos_codigo (user_id) values (auth.uid());
    return json_build_object('estado', 'invalido', 'mensaje', 'Código QR no válido.');
  end if;
  if s.cerrada_en is not null or s.expira_en <= now() then
    return json_build_object('estado', 'cerrado', 'mensaje', 'El docente ya cerró esta asistencia.');
  end if;
  if abs(ahora_w - w) > 1 then
    return json_build_object('estado', 'vencido', 'mensaje', 'El QR venció. Escanea el que se muestra ahora en pantalla.');
  end if;
  select * into c from public.cursos where id = s.curso_id;
  if c.nivel <> a.nivel or (c.grado is not null and c.grado <> a.grado) then
    return json_build_object('estado', 'no_pertenece', 'mensaje', 'Este curso no corresponde a tu carrera o ciclo.');
  end if;

  select hora into previa from public.asistencias_curso where alumno_id = a.id and curso_id = c.id and fecha = v_fecha;
  if found then
    return json_build_object('estado', 'ya_marcado', 'curso', c.nombre, 'hora', previa::text, 'mensaje', 'Ya habías marcado tu asistencia en este curso.');
  end if;
  insert into public.asistencias_curso (colegio_id, alumno_id, curso_id, fecha, hora, registrado_por, origen, sesion_id)
    values (a.colegio_id, a.id, c.id, v_fecha, v_hora, auth.uid(), 'qr-docente', s.id)
    on conflict (alumno_id, curso_id, fecha) do nothing;
  return json_build_object('estado', 'ok', 'curso', c.nombre, 'hora', v_hora::text, 'mensaje', 'Asistencia registrada.');
end $$;
revoke all on function public.marcar_asistencia_qr_curso(text) from public, anon;
grant execute on function public.marcar_asistencia_qr_curso(text) to authenticated;

-- ---------------------------------------------------------------------
-- Bitácora de registros (administrador, coordinador de su carrera y administrador superior)
-- Cada fila: cuándo (con segundos, en registrado_en), qué, a quién, quién lo registró y por qué medio.
-- ---------------------------------------------------------------------
create or replace function public.bitacora_registros(
  p_desde date default null, p_hasta date default null, p_tipo text default null,
  p_limite int default 500, p_colegio uuid default null) returns json
language plpgsql stable security definer set search_path = public as $$
declare
  cid uuid := public.mi_colegio();
  carrera text := case when public.es_coordinador() then public.mi_carrera() else null end;
  d1 timestamptz; d2 timestamptz; lim int := greatest(1, least(coalesce(p_limite, 500), 2000));
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if public.es_superadmin() and p_colegio is not null then cid := p_colegio; carrera := null;
  elsif not (public.es_admin() or public.es_coordinador() or public.es_superadmin()) then
    raise exception 'La bitácora es solo para coordinación y administración';
  end if;
  if cid is null then raise exception 'Sin instituto'; end if;
  d1 := ((coalesce(p_desde, (now() at time zone 'America/Lima')::date))::timestamp) at time zone 'America/Lima';
  d2 := ((coalesce(p_hasta, (now() at time zone 'America/Lima')::date) + 1)::timestamp) at time zone 'America/Lima';

  return coalesce((select json_agg(x order by x.cuando desc) from (
    select * from (
      -- Ingreso al instituto
      select 'ingreso'::text as tipo, s.registrado_en as cuando, al.nombre as persona, al.nivel as carrera, al.grado as ciclo,
             null::text as curso, s.hora::text as hora, s.origen,
             coalesce(pf.nombre, case when s.registrado_por = al.user_id then al.nombre end, 'Sistema') as actor,
             case when s.registrado_por = al.user_id then 'Estudiante' else coalesce(pf.rol, '—') end as actor_rol
      from public.asistencias s join public.alumnos al on al.id = s.alumno_id
      left join public.perfiles pf on pf.id = s.registrado_por
      where s.colegio_id = cid and s.registrado_en >= d1 and s.registrado_en < d2 and (carrera is null or al.nivel = carrera)
      union all
      -- Asistencia por curso
      select 'curso', k.registrado_en, al.nombre, al.nivel, al.grado, cu.nombre, k.hora::text, k.origen,
             coalesce(pf.nombre, case when k.registrado_por = al.user_id then al.nombre end, 'Sistema'),
             case when k.registrado_por = al.user_id then 'Estudiante' else coalesce(pf.rol, '—') end
      from public.asistencias_curso k join public.alumnos al on al.id = k.alumno_id join public.cursos cu on cu.id = k.curso_id
      left join public.perfiles pf on pf.id = k.registrado_por
      where k.colegio_id = cid and k.registrado_en >= d1 and k.registrado_en < d2 and (carrera is null or al.nivel = carrera)
      union all
      -- Docente abrió el QR de un curso
      select 'qr_abierto', q.creada_en, null, cu.nivel, cu.grado, cu.nombre, null, 'qr-docente',
             coalesce(pf.nombre, 'Docente'), coalesce(pf.rol, '—')
      from public.sesiones_qr_curso q join public.cursos cu on cu.id = q.curso_id
      left join public.perfiles pf on pf.id = q.abierta_por
      where q.colegio_id = cid and q.creada_en >= d1 and q.creada_en < d2 and (carrera is null or cu.nivel = carrera)
      union all
      -- Justificaciones
      select 'justificacion', j.creado_en, al.nombre, al.nivel, al.grado, null, j.tipo, null,
             coalesce(pf.nombre, 'Sistema'), coalesce(pf.rol, '—')
      from public.justificaciones j join public.alumnos al on al.id = j.alumno_id
      left join public.perfiles pf on pf.id = j.registrado_por
      where j.colegio_id = cid and j.creado_en >= d1 and j.creado_en < d2 and (carrera is null or al.nivel = carrera)
      union all
      -- Avisos a apoderados
      select 'aviso', v.creado_en, al.nombre, al.nivel, al.grado, null, v.tipo, null,
             coalesce(pf.nombre, 'Sistema'), coalesce(pf.rol, '—')
      from public.avisos_apoderados v join public.alumnos al on al.id = v.alumno_id
      left join public.perfiles pf on pf.id = v.enviado_por
      where v.colegio_id = cid and v.creado_en >= d1 and v.creado_en < d2 and (carrera is null or al.nivel = carrera)
    ) u
    where p_tipo is null or p_tipo = '' or u.tipo = p_tipo
    order by u.cuando desc limit lim
  ) x), '[]'::json);
end $$;
revoke all on function public.bitacora_registros(date, date, text, int, uuid) from public, anon;
grant execute on function public.bitacora_registros(date, date, text, int, uuid) to authenticated;
