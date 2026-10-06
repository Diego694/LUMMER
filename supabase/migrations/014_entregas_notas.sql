-- =====================================================================
--  014 · Entregas y notas de las actividades del aula
--  El estudiante entrega (texto y/o un archivo ≤ 10 MB) y el docente del curso (o el admin) califica.
--  Seguridad: nadie escribe la tabla directamente; el estudiante usa entregar_actividad() y el docente
--  calificar_entrega() (SECURITY DEFINER: validan colegio, curso, nota máxima y archivo propio).
--  Los archivos de entrega viven en el bucket «cursos»: <colegio>/<curso>/entregas/<user_id>/<archivo>
--  y solo los ve su autor y quien gestiona el curso (los compañeros NO).
--  Requiere la 013. Migración ADITIVA y re-ejecutable.
-- =====================================================================

-- Alumno (fila de public.alumnos) del usuario actual; null si no es un estudiante activo y aprobado
create or replace function public.mi_alumno_id() returns uuid
language sql stable security definer set search_path = public as $$
  select id from public.alumnos where user_id = auth.uid() and estado = 'ACTIVO' and aprobado is not false
$$;

create or replace function public.curso_colegio(p_curso uuid) returns uuid
language sql stable security definer set search_path = public as $$
  select colegio_id from public.cursos where id = p_curso
$$;

create table if not exists public.curso_entregas (
  id             uuid primary key default gen_random_uuid(),
  colegio_id     uuid not null references public.colegios(id) on delete cascade,
  curso_id       uuid not null references public.cursos(id) on delete cascade,
  actividad_id   uuid not null references public.curso_actividades(id) on delete cascade,
  alumno_id      uuid not null references public.alumnos(id) on delete cascade,
  texto          text not null default '' check (char_length(texto) <= 8000),
  archivo_path   text,
  archivo_nombre text,
  archivo_bytes  integer check (archivo_bytes is null or archivo_bytes between 0 and 10485760),
  enviado_en     timestamptz not null default now(),
  tardia         boolean not null default false,
  nota           numeric(5, 2) check (nota is null or nota >= 0),
  comentario     text not null default '' check (char_length(comentario) <= 2000),
  calificado_por uuid references auth.users(id) on delete set null,
  calificado_en  timestamptz,
  unique (actividad_id, alumno_id)
);
create index if not exists idx_entregas_curso on public.curso_entregas (curso_id, actividad_id);
create index if not exists idx_entregas_alumno on public.curso_entregas (alumno_id);

alter table public.curso_entregas enable row level security;

-- Lectura: el propio estudiante o quien gestiona el curso. Sin políticas de escritura: se escribe solo por las funciones.
drop policy if exists "ver entregas" on public.curso_entregas;
create policy "ver entregas" on public.curso_entregas for select to authenticated
  using (alumno_id = public.mi_alumno_id() or public.puede_gestionar_curso(curso_id));

-- ---------- El estudiante entrega (crea o reemplaza mientras no esté calificada) ----------
create or replace function public.entregar_actividad(p_actividad uuid, p_texto text, p_archivo_path text default null,
                                                    p_archivo_nombre text default null, p_archivo_bytes integer default null)
returns uuid language plpgsql security definer set search_path = public as $$
declare
  v_alumno uuid := public.mi_alumno_id();
  a public.curso_actividades%rowtype;
  v_id uuid;
begin
  if v_alumno is null then raise exception 'Solo los estudiantes aprobados pueden entregar'; end if;
  select * into a from public.curso_actividades where id = p_actividad;
  if not found or not a.publicado or not public.puede_ver_curso(a.curso_id) then raise exception 'Actividad no disponible'; end if;
  if coalesce(trim(p_texto), '') = '' and p_archivo_path is null then raise exception 'Escribe una respuesta o adjunta un archivo'; end if;
  if p_archivo_path is not null and left(p_archivo_path, length(a.colegio_id::text || '/' || a.curso_id::text || '/entregas/' || auth.uid()::text || '/'))
       <> a.colegio_id::text || '/' || a.curso_id::text || '/entregas/' || auth.uid()::text || '/' then
    raise exception 'Archivo fuera de tu carpeta de entregas';
  end if;
  if exists (select 1 from public.curso_entregas where actividad_id = p_actividad and alumno_id = v_alumno and nota is not null) then
    raise exception 'La entrega ya fue calificada y no se puede cambiar';
  end if;
  insert into public.curso_entregas (colegio_id, curso_id, actividad_id, alumno_id, texto, archivo_path, archivo_nombre, archivo_bytes, tardia)
  values (a.colegio_id, a.curso_id, a.id, v_alumno, coalesce(p_texto, ''), p_archivo_path, p_archivo_nombre, p_archivo_bytes,
          a.fecha_limite is not null and now() > a.fecha_limite)
  on conflict (actividad_id, alumno_id) do update
    set texto = excluded.texto, archivo_path = excluded.archivo_path, archivo_nombre = excluded.archivo_nombre,
        archivo_bytes = excluded.archivo_bytes, enviado_en = now(), tardia = excluded.tardia
  returning id into v_id;
  return v_id;
end $$;

-- ---------- El docente del curso (o el admin) califica; nota null = quitar la calificación ----------
create or replace function public.calificar_entrega(p_entrega uuid, p_nota numeric, p_comentario text default '')
returns void language plpgsql security definer set search_path = public as $$
declare e public.curso_entregas%rowtype; v_max numeric;
begin
  select * into e from public.curso_entregas where id = p_entrega;
  if not found or not public.puede_gestionar_curso(e.curso_id) then raise exception 'No puedes calificar esta entrega'; end if;
  select puntaje_max into v_max from public.curso_actividades where id = e.actividad_id;
  if p_nota is not null and (p_nota < 0 or p_nota > v_max) then raise exception 'La nota debe estar entre 0 y %', v_max; end if;
  update public.curso_entregas
     set nota = p_nota, comentario = coalesce(p_comentario, ''),
         calificado_por = case when p_nota is null then null else auth.uid() end,
         calificado_en = case when p_nota is null then null else now() end
   where id = p_entrega;
end $$;

revoke all on function public.mi_alumno_id(), public.curso_colegio(uuid), public.entregar_actividad(uuid, text, text, text, integer), public.calificar_entrega(uuid, numeric, text) from public, anon;
grant execute on function public.mi_alumno_id(), public.curso_colegio(uuid), public.entregar_actividad(uuid, text, text, text, integer), public.calificar_entrega(uuid, numeric, text) to authenticated;

-- ---------- Archivos: las entregas son privadas (solo su autor y el docente/admin del curso) ----------
drop policy if exists "cursos: ve archivos de su curso" on storage.objects;
create policy "cursos: ve archivos de su curso" on storage.objects for select to authenticated
  using (bucket_id = 'cursos' and (
    case when (storage.foldername(name))[3] = 'entregas'
         then (storage.foldername(name))[4] = auth.uid()::text or public.puede_gestionar_curso(public.curso_de_ruta(name))
         else public.puede_ver_curso(public.curso_de_ruta(name)) end));

-- El estudiante solo sube a su propia carpeta de entregas, en un curso que ve y dentro del colegio del curso
drop policy if exists "cursos: estudiante sube su entrega" on storage.objects;
create policy "cursos: estudiante sube su entrega" on storage.objects for insert to authenticated
  with check (bucket_id = 'cursos' and (storage.foldername(name))[3] = 'entregas' and (storage.foldername(name))[4] = auth.uid()::text
              and public.mi_alumno_id() is not null and public.puede_ver_curso(public.curso_de_ruta(name))
              and (storage.foldername(name))[1] = public.curso_colegio(public.curso_de_ruta(name))::text);
drop policy if exists "cursos: estudiante borra su entrega" on storage.objects;
create policy "cursos: estudiante borra su entrega" on storage.objects for delete to authenticated
  using (bucket_id = 'cursos' and (storage.foldername(name))[3] = 'entregas' and (storage.foldername(name))[4] = auth.uid()::text);
