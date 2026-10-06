-- =====================================================================
--  015 · Periodos del libro de notas
--  Cada actividad pertenece a un periodo (1 a 8: bimestre, unidad, etc.) para promediar las notas por periodo.
--  Requiere la 013. Migración ADITIVA y re-ejecutable: las actividades existentes quedan en el periodo 1.
--  No toca políticas RLS: la columna hereda las de curso_actividades.
-- =====================================================================

alter table public.curso_actividades
  add column if not exists periodo smallint not null default 1;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'curso_actividades_periodo_chk') then
    alter table public.curso_actividades
      add constraint curso_actividades_periodo_chk check (periodo between 1 and 8);
  end if;
end $$;

create index if not exists idx_curso_actividades_periodo on public.curso_actividades (curso_id, periodo);
