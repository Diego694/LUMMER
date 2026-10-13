-- =====================================================================
--  021 · Notificaciones push (Firebase Cloud Messaging) para las apps nativas de docentes y estudiantes
--  · dispositivos_push: el token FCM de cada teléfono, ligado a su cuenta (solo se escribe con las funciones de abajo).
--  · cola_push: cada aviso por enviar. Se llena sola cuando (a) se publica un comunicado, (b) se aprueba el acceso de un
--    estudiante y (c) llega una solicitud de ingreso nueva (a administradores y coordinadores).
--  · Al encolar, un disparador llama a la Edge Function «enviar-push» (pg_net) con un secreto propio; la función envía a FCM.
--  Migración ADITIVA y re-ejecutable. Requiere 004 y 009. La URL de la función se fija en config_push (ver docs/NOTIFICACIONES.md).
-- =====================================================================
create extension if not exists pg_net with schema extensions;

create table if not exists public.dispositivos_push (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null references auth.users(id) on delete cascade,
  colegio_id    uuid references public.colegios(id) on delete cascade,
  app           text not null check (app in ('docente', 'estudiante')),
  token         text not null unique,
  actualizado_en timestamptz not null default now()
);
create index if not exists idx_dispositivos_push_user on public.dispositivos_push (user_id);
create index if not exists idx_dispositivos_push_colegio on public.dispositivos_push (colegio_id, app);
alter table public.dispositivos_push enable row level security;      -- sin políticas: solo las funciones SECURITY DEFINER
revoke all on public.dispositivos_push from anon, authenticated;

create table if not exists public.cola_push (
  id          bigint generated always as identity primary key,
  colegio_id  uuid not null references public.colegios(id) on delete cascade,
  audiencia   text not null check (audiencia in ('todos', 'estudiantes', 'personal', 'admins', 'usuario')),
  user_id     uuid references auth.users(id) on delete cascade,
  tipo        text not null default 'aviso',
  titulo      text not null,
  cuerpo      text not null default '',
  datos       jsonb not null default '{}'::jsonb,
  creado_en   timestamptz not null default now(),
  enviado_en  timestamptz,
  resultado   text
);
create index if not exists idx_cola_push_pend on public.cola_push (creado_en) where enviado_en is null;
alter table public.cola_push enable row level security;
revoke all on public.cola_push from anon, authenticated;

-- Una sola fila: URL de la Edge Function y secreto compartido con ella (la función lo compara contra esta tabla)
create table if not exists public.config_push (
  id      boolean primary key default true check (id),
  url     text,
  secreto text not null default replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '')
);
insert into public.config_push (id) values (true) on conflict do nothing;
alter table public.config_push enable row level security;
revoke all on public.config_push from anon, authenticated;

-- ---------- El teléfono registra / quita su token ----------
create or replace function public.registrar_dispositivo_push(p_token text, p_app text) returns void
language plpgsql volatile security definer set search_path = public as $$
declare cid uuid;
begin
  if auth.uid() is null then raise exception 'No autenticado'; end if;
  if p_app not in ('docente', 'estudiante') then raise exception 'Aplicación no válida'; end if;
  if p_token is null or length(p_token) < 20 or length(p_token) > 4096 then raise exception 'Token no válido'; end if;
  cid := coalesce(public.mi_colegio(), (select a.colegio_id from public.alumnos a where a.user_id = auth.uid() limit 1));
  insert into public.dispositivos_push (user_id, colegio_id, app, token) values (auth.uid(), cid, p_app, p_token)
    on conflict (token) do update set user_id = excluded.user_id, colegio_id = excluded.colegio_id, app = excluded.app, actualizado_en = now();
end $$;

create or replace function public.quitar_dispositivo_push(p_token text) returns void
language sql volatile security definer set search_path = public as $$
  delete from public.dispositivos_push where token = p_token and user_id = auth.uid()
$$;
revoke all on function public.registrar_dispositivo_push(text, text), public.quitar_dispositivo_push(text) from public, anon;
grant execute on function public.registrar_dispositivo_push(text, text), public.quitar_dispositivo_push(text) to authenticated;

-- ---------- Encolar y despachar ----------
create or replace function public._despachar_push() returns trigger
language plpgsql volatile security definer set search_path = public as $$
declare c public.config_push;
begin
  select * into c from public.config_push where id;
  if c.url is not null and c.url <> '' then
    perform net.http_post(url := c.url, body := jsonb_build_object('id', new.id),
      headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', c.secreto));
  end if;
  return new;
exception when others then return new;     -- un fallo del aviso nunca debe impedir la operación original
end $$;
drop trigger if exists trg_despachar_push on public.cola_push;
create trigger trg_despachar_push after insert on public.cola_push for each row execute function public._despachar_push();

create or replace function public._push_comunicado() returns trigger
language plpgsql volatile security definer set search_path = public as $$
begin
  insert into public.cola_push (colegio_id, audiencia, tipo, titulo, cuerpo) values (new.colegio_id, 'todos', 'comunicado', left(new.titulo, 120), left(new.mensaje, 200));
  return new;
exception when others then return new;
end $$;
drop trigger if exists trg_push_comunicado on public.comunicados;
create trigger trg_push_comunicado after insert on public.comunicados for each row execute function public._push_comunicado();

create or replace function public._push_aprobado() returns trigger
language plpgsql volatile security definer set search_path = public as $$
begin
  if new.user_id is not null and new.aprobado is true and old.aprobado is distinct from true then
    insert into public.cola_push (colegio_id, audiencia, user_id, tipo, titulo, cuerpo)
      values (new.colegio_id, 'usuario', new.user_id, 'acceso_aprobado', '¡Tu acceso fue aprobado!', 'Ya puedes ingresar a LUMMER con tu cuenta.');
  end if;
  return new;
exception when others then return new;
end $$;
drop trigger if exists trg_push_aprobado on public.alumnos;
create trigger trg_push_aprobado after update of aprobado on public.alumnos for each row execute function public._push_aprobado();

create or replace function public._push_solicitud() returns trigger
language plpgsql volatile security definer set search_path = public as $$
begin
  if new.aprobado is false then
    insert into public.cola_push (colegio_id, audiencia, tipo, titulo, cuerpo)
      values (new.colegio_id, 'admins', 'solicitud', 'Nueva solicitud de ingreso', left(coalesce(new.nombre, 'Un estudiante'), 80) || ' espera tu aprobación.');
  end if;
  return new;
exception when others then return new;
end $$;
drop trigger if exists trg_push_solicitud on public.alumnos;
create trigger trg_push_solicitud after insert on public.alumnos for each row execute function public._push_solicitud();
