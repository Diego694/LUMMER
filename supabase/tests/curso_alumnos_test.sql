-- Pruebas de seguridad (RLS) para matrícula manual de alumnos por curso (migración 020).
-- Autónomo y SIN EFECTOS: todo ocurre dentro de una transacción que termina en ROLLBACK.
begin;

create schema ta;
grant usage on schema ta to authenticated, anon;
create function ta.act(u uuid) returns void language plpgsql as $$
begin
  perform set_config('request.jwt.claim.sub', coalesce(u::text, ''), true);
  perform set_config('request.jwt.claims', json_build_object('sub', coalesce(u::text, ''), 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
end $$;
create function ta.root() returns void language plpgsql as $$
begin execute 'reset role'; perform set_config('request.jwt.claim.sub', '', true); perform set_config('request.jwt.claims', '', true); end $$;
create function ta.n(q text) returns bigint language plpgsql as $$ declare r bigint; begin execute 'select count(*) from (' || q || ') s' into r; return r; end $$;
create function ta.dml(q text) returns bigint language plpgsql as $$ declare r bigint; begin execute q; get diagnostics r = row_count; return r; end $$;
create function ta.falla(q text, msg text) returns void language plpgsql as $$
begin
  begin execute q; exception when others then return; end;
  raise exception 'DEBIA FALLAR: % -> %', msg, q;
end $$;
create function ta.eq(a text, b text, msg text) returns void language plpgsql as $$
begin if a is distinct from b then raise exception 'FALLO: % (esperado %, obtenido %)', msg, b, a; end if; end $$;
grant execute on all functions in schema ta to authenticated, anon;

-- ---------- Datos de prueba (como superusuario) ----------
insert into auth.users (id, email) values
  ('e0000000-0000-0000-0000-0000000000a1', 'ca-admin1@test.pe'),
  ('e0000000-0000-0000-0000-0000000000a2', 'ca-admin2@test.pe'),
  ('e0000000-0000-0000-0000-0000000000d1', 'ca-doc1@test.pe'),
  ('e0000000-0000-0000-0000-0000000000d2', 'ca-doc2@test.pe'),
  ('e0000000-0000-0000-0000-0000000000e1', 'ca-est1@test.pe'),
  ('e0000000-0000-0000-0000-0000000000e2', 'ca-est2@test.pe'),
  ('e0000000-0000-0000-0000-0000000000eb', 'ca-estb@test.pe');

insert into colegios (id, nombre) values
  ('ea000000-0000-0000-0000-000000000001', 'CA Instituto A'),
  ('eb000000-0000-0000-0000-000000000001', 'CA Instituto B');

insert into perfiles (id, colegio_id, nombre, rol) values
  ('e0000000-0000-0000-0000-0000000000a1', 'ea000000-0000-0000-0000-000000000001', 'Admin A', 'Administrador'),
  ('e0000000-0000-0000-0000-0000000000a2', 'eb000000-0000-0000-0000-000000000001', 'Admin B', 'Administrador'),
  ('e0000000-0000-0000-0000-0000000000d1', 'ea000000-0000-0000-0000-000000000001', 'Docente 1', 'Docente'),
  ('e0000000-0000-0000-0000-0000000000d2', 'ea000000-0000-0000-0000-000000000001', 'Docente 2', 'Docente');

insert into cursos (id, colegio_id, nivel, grado, nombre) values
  ('ec000000-0000-0000-0000-00000000000a', 'ea000000-0000-0000-0000-000000000001', 'APSTI', 'APSTI · I CICLO', 'CA Redes'),
  ('ec000000-0000-0000-0000-00000000000b', 'ea000000-0000-0000-0000-000000000001', 'MECANICA', 'MECANICA · I CICLO', 'CA Motores'),
  ('ec000000-0000-0000-0000-00000000000c', 'eb000000-0000-0000-0000-000000000001', 'APSTI', 'APSTI · I CICLO', 'CA Curso B');

insert into curso_docentes (curso_id, user_id, colegio_id) values
  ('ec000000-0000-0000-0000-00000000000a', 'e0000000-0000-0000-0000-0000000000d1', 'ea000000-0000-0000-0000-000000000001');

insert into alumnos (id, colegio_id, nombre, codigo, nivel, grado, user_id, aprobado, estado) values
  ('e1000000-0000-0000-0000-000000000001', 'ea000000-0000-0000-0000-000000000001', 'Alumno APSTI', 'ca1', 'APSTI', 'APSTI · I CICLO', 'e0000000-0000-0000-0000-0000000000e1', true, 'ACTIVO'),
  ('e1000000-0000-0000-0000-000000000002', 'ea000000-0000-0000-0000-000000000001', 'Alumno MEC', 'ca2', 'MECANICA', 'MECANICA · I CICLO', 'e0000000-0000-0000-0000-0000000000e2', true, 'ACTIVO'),
  ('e1000000-0000-0000-0000-00000000000b', 'eb000000-0000-0000-0000-000000000001', 'Alumno Inst B', 'cab', 'APSTI', 'APSTI · I CICLO', 'e0000000-0000-0000-0000-0000000000eb', true, 'ACTIVO');

-- ===== 1. Estado inicial: sin fila en curso_alumnos, alumno de otro ciclo NO ve el curso =====
do $$ begin
  -- Est1 (APSTI) ve Curso A automáticamente por ciclo
  perform ta.act('e0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.n($q$select 1 from cursos where id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '1', 'estudiante 1 ve su curso automático por ciclo');

  -- Est2 (MECANICA) NO ve Curso A porque es de otra carrera/ciclo
  perform ta.act('e0000000-0000-0000-0000-0000000000e2');
  perform ta.eq(ta.n($q$select 1 from cursos where id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '0', 'alumno de otro ciclo NO ve curso A sin fila');

  -- Docente 1: alumno_en_mis_cursos es false para alumno 2
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(public.alumno_en_mis_cursos('e1000000-0000-0000-0000-000000000002')::text, 'false', 'sin fila: alumno_en_mis_cursos es false para alumno 2');
  if (select count(*) from pg_policies where policyname = 'docente solo sus cursos' and tablename = 'alumnos') > 0 then
    perform ta.eq(ta.n($q$select 1 from alumnos where id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '0', 'sin fila: docente no ve a alumno 2 de otra carrera');
  end if;
  perform ta.root();
end $$;

-- ===== 2. Validaciones de inserción: restricciones de seguridad =====
do $$ begin
  -- Docente 1 no puede agregar a un curso que no es suyo (Curso B)
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.falla($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000b', 'e1000000-0000-0000-0000-000000000002')$q$, 'docente no agrega a curso que no es suyo');

  -- Docente 2 (no asignado a Curso A) no puede agregar a Curso A
  perform ta.act('e0000000-0000-0000-0000-0000000000d2');
  perform ta.falla($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-000000000002')$q$, 'docente no asignado no puede agregar alumnos');

  -- Un docente NO puede agregar un alumno de OTRO instituto (sigue prohibido)
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.falla($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-00000000000b')$q$, 'un docente NO puede agregar un alumno de OTRO instituto');

  perform ta.act('e0000000-0000-0000-0000-0000000000a1');
  perform ta.falla($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-00000000000b')$q$, 'admin no puede agregar alumno de otro instituto');

  -- Estudiante no puede agregar a curso_alumnos
  perform ta.act('e0000000-0000-0000-0000-0000000000e1');
  perform ta.falla($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-000000000002')$q$, 'estudiante no inserta en curso_alumnos');
  perform ta.root();
end $$;

-- ===== 3. Agregar manual: alumno ve el curso y docente ve al alumno =====
do $$ begin
  -- Docente SÍ puede agregar un alumno de otro ciclo de su instituto (Alumno 2 es de MECANICA, Curso A es de APSTI)
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.dml($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-000000000002')$q$)::text, '1', 'un docente SÍ puede agregar un alumno de otro ciclo de su instituto');

  -- El alumno de otro ciclo ahora VEE el curso
  perform ta.act('e0000000-0000-0000-0000-0000000000e2');
  perform ta.eq(ta.n($q$select 1 from cursos where id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '1', 'alumno de otro ciclo VEE el curso tras ser agregado manualmente');
  perform ta.eq(ta.n($q$select 1 from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '1', 'alumno ve su propia fila en curso_alumnos');

  -- Privacidad: otro estudiante NO ve la fila de curso_alumnos del compañero
  perform ta.act('e0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.n($q$select 1 from curso_alumnos where alumno_id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '0', 'otro estudiante NO ve la fila de curso_alumnos del compañero');

  -- Docente 1 ahora VEE al alumno agregado
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(public.alumno_en_mis_cursos('e1000000-0000-0000-0000-000000000002')::text, 'true', 'con fila: alumno_en_mis_cursos da true');
  perform ta.eq(ta.n($q$select 1 from alumnos where id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '1', 'docente VEE al alumno agregado');
  perform ta.eq(ta.n($q$select 1 from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '1', 'docente ve la fila en curso_alumnos de su curso');

  -- Docente 2 (no asignado al Curso A) NO ve las filas de curso_alumnos del Curso A
  perform ta.act('e0000000-0000-0000-0000-0000000000d2');
  perform ta.eq(ta.n($q$select 1 from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '0', 'docente no asignado no ve filas de curso_alumnos');
  perform ta.root();
end $$;

-- ===== 4. Borrar la fila revoca: alumno ya no ve curso, docente ya no ve alumno =====
do $$ begin
  -- Docente no asignado no puede borrar la fila
  perform ta.act('e0000000-0000-0000-0000-0000000000d2');
  perform ta.eq(ta.dml($q$delete from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a' and alumno_id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '0', 'docente no asignado no borra fila');

  -- Estudiante no puede borrar la fila
  perform ta.act('e0000000-0000-0000-0000-0000000000e2');
  perform ta.eq(ta.dml($q$delete from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a' and alumno_id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '0', 'estudiante no borra fila');

  -- Docente 1 borra la fila
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.dml($q$delete from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a' and alumno_id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '1', 'docente 1 borra la fila de curso_alumnos');

  -- Alumno 2 ya NO ve Curso A
  perform ta.act('e0000000-0000-0000-0000-0000000000e2');
  perform ta.eq(ta.n($q$select 1 from cursos where id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '0', 'al borrar la fila, alumno de otro ciclo YA NO ve el curso');

  -- Docente 1 ya NO ve a Alumno 2
  perform ta.act('e0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(public.alumno_en_mis_cursos('e1000000-0000-0000-0000-000000000002')::text, 'false', 'al borrar la fila, alumno_en_mis_cursos vuelve a false');
  if (select count(*) from pg_policies where policyname = 'docente solo sus cursos' and tablename = 'alumnos') > 0 then
    perform ta.eq(ta.n($q$select 1 from alumnos where id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '0', 'al borrar la fila, docente 1 ya no ve a alumno 2');
  end if;
  perform ta.root();
end $$;

-- ===== 5. Administrador gestiona y aislamiento entre institutos =====
do $$ begin
  -- Admin A agrega alumno manualmente
  perform ta.act('e0000000-0000-0000-0000-0000000000a1');
  perform ta.eq(ta.dml($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-000000000002')$q$)::text, '1', 'admin agrega alumno a curso');
  perform ta.eq(ta.n($q$select 1 from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '1', 'admin ve la fila');

  -- Admin B (otro instituto) no ve la fila ni puede agregar
  perform ta.act('e0000000-0000-0000-0000-0000000000a2');
  perform ta.eq(ta.n($q$select 1 from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a'$q$)::text, '0', 'admin de otro instituto no ve filas ajenas');
  perform ta.falla($q$insert into curso_alumnos (curso_id, alumno_id) values ('ec000000-0000-0000-0000-00000000000a', 'e1000000-0000-0000-0000-00000000000b')$q$, 'admin ajeno no inserta');

  -- Admin A borra la fila
  perform ta.act('e0000000-0000-0000-0000-0000000000a1');
  perform ta.eq(ta.dml($q$delete from curso_alumnos where curso_id = 'ec000000-0000-0000-0000-00000000000a' and alumno_id = 'e1000000-0000-0000-0000-000000000002'$q$)::text, '1', 'admin borra fila de curso_alumnos');
  perform ta.root();
end $$;

rollback;
select 'CURSO_ALUMNOS: TODAS LAS PRUEBAS PASARON' as resultado;
