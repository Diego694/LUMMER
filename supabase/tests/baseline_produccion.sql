-- Reproduce el esquema y las políticas que YA existían en producción antes de las migraciones 002+ (según el panel de Supabase):
-- 8 tablas con RLS y políticas "… por colegio" (ALL, rol public), "ver mi perfil", "ver mi colegio" y "editar mi colegio".
create table colegios (id uuid primary key default gen_random_uuid(), nombre text not null, creado_en timestamptz default now());
create table perfiles (id uuid primary key references auth.users(id) on delete cascade, colegio_id uuid references colegios(id) on delete cascade, nombre text, rol text default 'Administrador');
create table niveles (id uuid primary key default gen_random_uuid(), colegio_id uuid references colegios(id) on delete cascade, nombre text not null);
create table grados (id uuid primary key default gen_random_uuid(), colegio_id uuid references colegios(id) on delete cascade, nivel text, nombre text not null);
create table alumnos (id uuid primary key default gen_random_uuid(), colegio_id uuid references colegios(id) on delete cascade, nombre text not null, codigo text not null,
  nivel text, grado text, apoderado text, estado text default 'ACTIVO', creado_en timestamptz default now(), unique (colegio_id, codigo));
create table docentes (id uuid primary key default gen_random_uuid(), colegio_id uuid references colegios(id) on delete cascade, nombre text not null, profesion text, rol text, estado text default 'ACTIVO');
create table comunicados (id uuid primary key default gen_random_uuid(), colegio_id uuid references colegios(id) on delete cascade, titulo text, mensaje text, fecha date default current_date);
create table asistencias (id uuid primary key default gen_random_uuid(), colegio_id uuid references colegios(id) on delete cascade,
  alumno_id uuid references alumnos(id) on delete cascade, fecha date not null, hora text, registrado_por uuid, unique (alumno_id, fecha));

do $$ declare t text; begin
  foreach t in array array['colegios','perfiles','niveles','grados','alumnos','docentes','comunicados','asistencias'] loop
    execute format('alter table %I enable row level security', t);
  end loop;
end $$;

create policy "ver mi perfil" on perfiles for select using (id = auth.uid());
create policy "ver mi colegio" on colegios for select using (id = (select colegio_id from perfiles where id = auth.uid()));
create policy "editar mi colegio" on colegios for update using (id = (select colegio_id from perfiles where id = auth.uid()));
create policy "alumnos por colegio" on alumnos for all using (colegio_id = (select colegio_id from perfiles where id = auth.uid())) with check (colegio_id = (select colegio_id from perfiles where id = auth.uid()));
create policy "asistencias por colegio" on asistencias for all using (colegio_id = (select colegio_id from perfiles where id = auth.uid())) with check (colegio_id = (select colegio_id from perfiles where id = auth.uid()));
create policy "comunicados por colegio" on comunicados for all using (colegio_id = (select colegio_id from perfiles where id = auth.uid())) with check (colegio_id = (select colegio_id from perfiles where id = auth.uid()));
create policy "docentes por colegio" on docentes for all using (colegio_id = (select colegio_id from perfiles where id = auth.uid())) with check (colegio_id = (select colegio_id from perfiles where id = auth.uid()));
create policy "grados por colegio" on grados for all using (colegio_id = (select colegio_id from perfiles where id = auth.uid())) with check (colegio_id = (select colegio_id from perfiles where id = auth.uid()));
create policy "niveles por colegio" on niveles for all using (colegio_id = (select colegio_id from perfiles where id = auth.uid())) with check (colegio_id = (select colegio_id from perfiles where id = auth.uid()));

grant select, insert, update, delete on all tables in schema public to authenticated;
