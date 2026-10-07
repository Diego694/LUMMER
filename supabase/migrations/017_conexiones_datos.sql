-- =====================================================================
--  017 · Conexiones de datos (superadmin)
--  Registro de bases de datos externas (Supabase/Postgres, API REST, Firestore) a las que el superadmin puede copiar
--  los datos de LUMMER. Solo se guardan URL/ID y llaves PÚBLICAS: la restricción de abajo rechaza llaves secretas.
--  Solo el superadmin ve y modifica esta tabla. Requiere la 011 (es_superadmin). Re-ejecutable.
-- =====================================================================

create table if not exists public.conexiones_datos (
  id            uuid primary key default gen_random_uuid(),
  nombre        text not null check (length(trim(nombre)) between 2 and 60),
  tipo          text not null check (tipo in ('supabase', 'rest', 'firestore')),
  url           text not null check (length(url) between 5 and 300),
  llave_publica text not null check (length(llave_publica) between 8 and 2000),
  estado        text not null default 'pendiente' check (estado in ('pendiente', 'verificada', 'error', 'copiada')),
  destino       boolean not null default false,      -- destino marcado para la próxima copia
  detalle       text not null default '',
  ultima_prueba timestamptz,
  ultima_copia  timestamptz,
  creado_por    uuid references auth.users(id) on delete set null default auth.uid(),
  creado_en     timestamptz not null default now(),
  -- jamás llaves secretas
  constraint llave_no_secreta check (
    llave_publica !~* '^sb_secret_' and llave_publica !~* 'PRIVATE KEY' and llave_publica !~* 'service_account'
  )
);
-- un solo destino marcado a la vez
create unique index if not exists conexiones_un_destino on public.conexiones_datos ((true)) where destino;

alter table public.conexiones_datos enable row level security;
drop policy if exists "superadmin gestiona conexiones" on public.conexiones_datos;
create policy "superadmin gestiona conexiones" on public.conexiones_datos for all to authenticated
  using ((select public.es_superadmin())) with check ((select public.es_superadmin()));
revoke all on public.conexiones_datos from anon;
