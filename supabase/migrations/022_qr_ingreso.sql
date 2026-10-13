-- =====================================================================
--  022 · QR rotativo de ingreso al instituto y modo quiosco proyectado
--  · El quiosco (o personal autorizado) abre una sesión de QR de ingreso: el token rota cada 15 o 30 s
--    y lo firma el servidor con un secreto que nunca sale de la base.
--  · El estudiante escanea el código proyectado desde la app LUMMER (Escanear) y el servidor registra
--    su asistencia con la hora exacta en America/Lima, calculando la puntualidad o tardanza según
--    el horario de su carrera (o el general).
--  · Respeta la ventana de ingreso (ingreso_desde e ingreso_hasta), previene duplicados en el día
--    (on conflict alumno_id, fecha) y limita los intentos contra fuerza bruta con intentos_codigo.
--  · Registra en la bitácora institucional con origen 'qr-ingreso'.
--  Requiere 004, 011, 012 y 019. Migración ADITIVA y re-ejecutable.
-- =====================================================================

alter table public.asistencias add column if not exists origen text;
alter table public.asistencias add column if not exists sesion_id uuid;
alter table public.asistencias add column if not exists registrado_en timestamptz not null default now();

create table if not exists public.sesiones_qr_ingreso (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  abierta_por    uuid references auth.users(id) on delete cascade default auth.uid(),
  secreto        text not null default replace(gen_random_uuid()::text, '-', ''),
  intervalo_seg  int not null default 30 check (intervalo_seg between 10 and 60),
  creada_en      timestamptz not null default now(),
  expira_en      timestamptz not null,
  cerrada_en     timestamptz
);
create index if not exists idx_sesiones_qr_ingreso on public.sesiones_qr_ingreso (colegio_id, creada_en desc);
create index if not exists idx_asist_ingreso_sesion on public.asistencias (sesion_id);
alter table public.sesiones_qr_ingreso enable row level security;   -- sin políticas: solo funciones SECURITY DEFINER
revoke all on public.sesiones_qr_ingreso from anon, authenticated;

-- Firma de una ventana de rotación (el secreto solo lo conoce el servidor)
create or replace function public._firma_qr_ingreso(p_secreto text, p_sesion text, p_ventana bigint) returns text
language sql immutable set search_path = public as $$
  select left(encode(sha256(convert_to(p_secreto || ':' || replace(p_sesion, '-', '') || ':' || p_ventana::text, 'utf8')), 'hex'), 12)
$$;

create or replace function public._firma_qr_ingreso(p_secreto text, p_sesion uuid, p_ventana bigint) returns text
language sql immutable set search_path = public as $$
  select public._firma_qr_ingreso(p_secreto, replace(p_sesion::text, '-', ''), p_ventana)
$$;
revoke all on function public._firma_qr_ingreso(text, text, bigint), public._firma_qr_ingreso(text, uuid, bigint) from public, anon, authenticated;

-- Helper de permiso a nivel colegio: rol admin, administrador, docente, coordinador, auxiliar o registrador
create or replace function public._puede_qr_ingreso() returns boolean
language sql stable security definer set search_path = public as $$
  select coalesce((
    select (
      p.colegio_id = public.mi_colegio()
      and (
        public.es_admin()
        or lower(trim(p.rol)) in ('admin', 'administrador', 'docente', 'coordinador', 'auxiliar', 'registrador')
      )
    )
    from public.perfiles p where p.id = auth.uid()
  ), false)
$$;
revoke all on function public._puede_qr_ingreso() from public, anon;
grant execute on function public._puede_qr_ingreso() to authenticated;

-- Abre (o reemplaza) la sesión de QR de ingreso del colegio. Devuelve { sesion, intervalo, expira_en }.
create or replace function public.qr_ingreso_abrir(p_segundos int default 30, p_minutos int default 600) returns json
language plpgsql volatile security definer set search_path = public as $$
declare
  s public.sesiones_qr_ingreso;
  cid uuid := public.mi_colegio();
  seg int := greatest(10, least(coalesce(p_segundos, 30), 60));
  mins int := greatest(1, least(coalesce(p_minutos, 600), 1440));
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if cid is null or not public._puede_qr_ingreso() then
    raise exception 'No tienes permiso para abrir la sesión de ingreso en este instituto';
  end if;

  update public.sesiones_qr_ingreso
    set cerrada_en = now()
    where colegio_id = cid and cerrada_en is null;

  insert into public.sesiones_qr_ingreso (colegio_id, abierta_por, intervalo_seg, expira_en)
    values (cid, auth.uid(), seg, now() + make_interval(mins => mins))
    returning * into s;

  return json_build_object(
    'sesion', s.id,
    'id', s.id,
    'intervalo', s.intervalo_seg,
    'expira_en', s.expira_en
  );
end $$;

-- Token vigente de la sesión. El cliente lo solicita cada ciclo (segundos). Error si cerrada o expirada.
create or replace function public.qr_ingreso_token(p_sesion uuid) returns json
language plpgsql volatile security definer set search_path = public as $$
declare
  s public.sesiones_qr_ingreso;
  w bigint;
  sesion32 text;
  seg_restantes int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  select * into s from public.sesiones_qr_ingreso
    where id = p_sesion and colegio_id = public.mi_colegio();
  if not found then
    raise exception 'Sesión no encontrada o no pertenece a tu instituto';
  end if;
  if s.cerrada_en is not null or s.expira_en <= now() then
    raise exception 'Sesión cerrada o expirada';
  end if;

  w := floor(extract(epoch from now()) / s.intervalo_seg);
  seg_restantes := (s.intervalo_seg - (extract(epoch from now())::bigint % s.intervalo_seg))::int;
  sesion32 := replace(s.id::text, '-', '');

  return json_build_object(
    'token', 'LI.' || sesion32 || '.' || w::text || '.' || public._firma_qr_ingreso(s.secreto, sesion32, w),
    'segundos', seg_restantes,
    'expira_en', s.expira_en
  );
end $$;

-- Cierra la sesión activa de QR de ingreso
create or replace function public.qr_ingreso_cerrar(p_sesion uuid) returns json
language plpgsql volatile security definer set search_path = public as $$
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  update public.sesiones_qr_ingreso
    set cerrada_en = coalesce(cerrada_en, now())
    where id = p_sesion and colegio_id = public.mi_colegio();
  return json_build_object('ok', true);
end $$;

revoke all on function public.qr_ingreso_abrir(int, int), public.qr_ingreso_token(uuid), public.qr_ingreso_cerrar(uuid) from public, anon;
grant execute on function public.qr_ingreso_abrir(int, int), public.qr_ingreso_token(uuid), public.qr_ingreso_cerrar(uuid) to authenticated;

-- El estudiante escanea el QR proyectado de ingreso. Hora y fecha las fija el servidor (Lima).
create or replace function public.marcar_asistencia_qr_ingreso(p_token text) returns json
language plpgsql volatile security definer set search_path = public as $$
declare
  a public.alumnos;
  s public.sesiones_qr_ingreso;
  h public.horarios;
  partes text[];
  sid uuid;
  w bigint;
  ahora_w bigint;
  v_fecha date := (now() at time zone 'America/Lima')::date;
  v_hora time := date_trunc('second', (now() at time zone 'America/Lima'))::time;
  v_limite time := '08:00'::time;
  v_tarde boolean := false;
  previa time;
  n int;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;

  select * into a from public.alumnos where user_id = auth.uid();
  if not found then
    return json_build_object('estado', 'no_pertenece', 'mensaje', 'Esta cuenta no está vinculada a un estudiante.');
  end if;
  if a.estado <> 'ACTIVO' then
    return json_build_object('estado', 'inactivo', 'nombre', a.nombre, 'mensaje', 'Tu registro está inactivo.');
  end if;
  if a.aprobado is false then
    return json_build_object('estado', 'pendiente', 'nombre', a.nombre, 'mensaje', 'Tu registro aún no fue aprobado.');
  end if;

  select count(*) into n from public.intentos_codigo
    where user_id = auth.uid() and creado_en > now() - interval '10 minutes';
  if n >= 30 then
    raise exception 'Demasiados intentos. Espera un momento e inténtalo de nuevo.';
  end if;

  partes := string_to_array(coalesce(p_token, ''), '.');
  if array_length(partes, 1) is distinct from 4
     or partes[1] <> 'LI'
     or partes[2] !~ '^[0-9a-f]{32}$'
     or partes[3] !~ '^[0-9]{1,12}$' then
    insert into public.intentos_codigo (user_id) values (auth.uid());
    return json_build_object('estado', 'invalido', 'mensaje', 'Ese código QR no es de ingreso.');
  end if;

  sid := (substr(partes[2], 1, 8) || '-' || substr(partes[2], 9, 4) || '-' || substr(partes[2], 13, 4) || '-' || substr(partes[2], 17, 4) || '-' || substr(partes[2], 21))::uuid;
  w := partes[3]::bigint;

  select * into s from public.sesiones_qr_ingreso where id = sid and colegio_id = a.colegio_id;
  if not found or partes[4] <> public._firma_qr_ingreso(s.secreto, partes[2], w) then
    insert into public.intentos_codigo (user_id) values (auth.uid());
    return json_build_object('estado', 'invalido', 'mensaje', 'Código QR no válido.');
  end if;

  if s.cerrada_en is not null or s.expira_en <= now() then
    return json_build_object('estado', 'cerrado', 'mensaje', 'El quiosco ya cerró este registro de ingreso.');
  end if;

  ahora_w := floor(extract(epoch from now()) / s.intervalo_seg);
  if abs(ahora_w - w) > 1 then
    return json_build_object('estado', 'vencido', 'mensaje', 'El QR venció. Escanea el que se muestra ahora en pantalla.');
  end if;

  -- Horario del nivel del alumno o general (nivel null)
  select * into h from public.horarios
    where colegio_id = a.colegio_id and (nivel = a.nivel or nivel is null)
    order by case when nivel = a.nivel then 0 else 1 end
    limit 1;

  if found then
    if h.ingreso_desde is not null and v_hora < h.ingreso_desde::time then
      return json_build_object('estado', 'temprano', 'nombre', a.nombre, 'hora', v_hora::text, 'mensaje', 'Aún no es hora de ingreso.');
    end if;
    if h.ingreso_hasta is not null and v_hora > h.ingreso_hasta::time then
      return json_build_object('estado', 'fuera_horario', 'nombre', a.nombre, 'hora', v_hora::text, 'mensaje', 'El horario de ingreso ya cerró.');
    end if;
    v_limite := coalesce(h.hora_ingreso, '08:00')::time + make_interval(mins => coalesce(h.tolerancia_min, 0));
  else
    v_limite := '08:00'::time;
  end if;

  v_tarde := (v_hora > v_limite);

  select hora into previa from public.asistencias where alumno_id = a.id and fecha = v_fecha;
  if found then
    return json_build_object(
      'estado', 'ya_marcado',
      'nombre', a.nombre,
      'hora', previa::text,
      'tarde', (previa > v_limite),
      'mensaje', 'Ya habías registrado tu ingreso hoy.'
    );
  end if;

  insert into public.asistencias (colegio_id, alumno_id, fecha, hora, registrado_por, origen, sesion_id)
    values (a.colegio_id, a.id, v_fecha, v_hora, auth.uid(), 'qr-ingreso', s.id)
    on conflict (alumno_id, fecha) do nothing;

  if not found then
    select hora into previa from public.asistencias where alumno_id = a.id and fecha = v_fecha;
    return json_build_object(
      'estado', 'ya_marcado',
      'nombre', a.nombre,
      'hora', previa::text,
      'tarde', (previa > v_limite),
      'mensaje', 'Ya habías registrado tu ingreso hoy.'
    );
  end if;

  return json_build_object(
    'estado', 'ok',
    'nombre', a.nombre,
    'hora', v_hora::text,
    'tarde', v_tarde,
    'mensaje', case when v_tarde then 'Ingreso registrado con tardanza.' else 'Ingreso puntual registrado.' end
  );
end $$;

revoke all on function public.marcar_asistencia_qr_ingreso(text) from public, anon;
grant execute on function public.marcar_asistencia_qr_ingreso(text) to authenticated;

-- ---------------------------------------------------------------------
-- Bitácora de registros: incluye sesiones de QR de ingreso además de las de curso
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
      -- Personal abrió el QR rotativo de ingreso al instituto
      select 'qr_abierto', q.creada_en, null, null, null, 'Ingreso al instituto', null, 'qr-ingreso',
             coalesce(pf.nombre, 'Personal'), coalesce(pf.rol, '—')
      from public.sesiones_qr_ingreso q
      left join public.perfiles pf on pf.id = q.abierta_por
      where q.colegio_id = cid and q.creada_en >= d1 and q.creada_en < d2 and carrera is null
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
