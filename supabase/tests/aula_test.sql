-- Pruebas de seguridad (RLS) del Aula: migraciones 013 (cursos, material, actividades, archivos) y 014 (entregas y notas).
-- Autónomo y SIN EFECTOS: todo ocurre dentro de una transacción que termina en ROLLBACK (no deja datos).
--  · Local:    python supabase/tests/run_tests.py   (lo ejecuta después de aplicar las migraciones)
--  · Supabase: pegar TODO este archivo en SQL Editor y ejecutar (después de aplicar 013 y 014). Resultado esperado: «AULA: TODAS LAS PRUEBAS PASARON».
--    (Supabase prohíbe DELETE directo en storage.objects: el borrado se controla por la API de Storage.)
--    Si algo falla, el mensaje dice cuál comprobación («FALLO: …» o «DEBIA FALLAR: …»).
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
  ('f0000000-0000-0000-0000-0000000000a1', 'zz-admin@aula.test'), ('f0000000-0000-0000-0000-0000000000a2', 'zz-adminb@aula.test'),
  ('f0000000-0000-0000-0000-0000000000d1', 'zz-docente1@aula.test'), ('f0000000-0000-0000-0000-0000000000d2', 'zz-docente2@aula.test'),
  ('f0000000-0000-0000-0000-0000000000e1', 'zz-est1@aula.test'), ('f0000000-0000-0000-0000-0000000000e2', 'zz-est2@aula.test'),
  ('f0000000-0000-0000-0000-0000000000e3', 'zz-est3@aula.test'), ('f0000000-0000-0000-0000-0000000000e4', 'zz-estb@aula.test');
insert into colegios (id, nombre) values
  ('fa000000-0000-0000-0000-000000000001', 'ZZ Instituto A'), ('fb000000-0000-0000-0000-000000000001', 'ZZ Instituto B');
insert into perfiles (id, colegio_id, nombre, rol) values
  ('f0000000-0000-0000-0000-0000000000a1', 'fa000000-0000-0000-0000-000000000001', 'Admin A', 'Administrador'),
  ('f0000000-0000-0000-0000-0000000000a2', 'fb000000-0000-0000-0000-000000000001', 'Admin B', 'Administrador'),
  ('f0000000-0000-0000-0000-0000000000d1', 'fa000000-0000-0000-0000-000000000001', 'Docente 1', 'Docente'),
  ('f0000000-0000-0000-0000-0000000000d2', 'fa000000-0000-0000-0000-000000000001', 'Docente 2', 'Docente');
insert into alumnos (id, colegio_id, nombre, codigo, nivel, grado, user_id, aprobado) values
  ('f1000000-0000-0000-0000-000000000001', 'fa000000-0000-0000-0000-000000000001', 'Est1 APSTI', 'zz1', 'APSTI', 'APSTI · I CICLO', 'f0000000-0000-0000-0000-0000000000e1', true),
  ('f1000000-0000-0000-0000-000000000002', 'fa000000-0000-0000-0000-000000000001', 'Est2 MEC', 'zz2', 'MECANICA', 'MECANICA · I CICLO', 'f0000000-0000-0000-0000-0000000000e2', true),
  ('f1000000-0000-0000-0000-000000000003', 'fa000000-0000-0000-0000-000000000001', 'Est3 APSTI', 'zz3', 'APSTI', 'APSTI · I CICLO', 'f0000000-0000-0000-0000-0000000000e3', true),
  ('f1000000-0000-0000-0000-000000000004', 'fb000000-0000-0000-0000-000000000001', 'EstB', 'zzb', 'APSTI', 'APSTI · I CICLO', 'f0000000-0000-0000-0000-0000000000e4', true);
insert into cursos (id, colegio_id, nivel, grado, nombre) values
  ('fc000000-0000-0000-0000-00000000000a', 'fa000000-0000-0000-0000-000000000001', 'APSTI', null, 'ZZ Redes'),
  ('fc000000-0000-0000-0000-00000000000b', 'fa000000-0000-0000-0000-000000000001', 'MECANICA', null, 'ZZ Motores'),
  ('fc000000-0000-0000-0000-00000000000c', 'fb000000-0000-0000-0000-000000000001', 'APSTI', null, 'ZZ Curso B');

-- ===== 1. Admin asigna docentes (solo en su instituto) =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000a1');
  perform ta.eq(ta.dml($q$insert into curso_docentes (curso_id, user_id) values ('fc000000-0000-0000-0000-00000000000a', 'f0000000-0000-0000-0000-0000000000d1')$q$)::text, '1', 'admin asigna docente 1 al curso A');
  perform ta.act('f0000000-0000-0000-0000-0000000000a2');
  perform ta.falla($q$insert into curso_docentes (curso_id, user_id) values ('fc000000-0000-0000-0000-00000000000a', 'f0000000-0000-0000-0000-0000000000d2')$q$, 'admin de otro instituto no asigna docentes');
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.falla($q$insert into curso_docentes (curso_id, user_id) values ('fc000000-0000-0000-0000-00000000000a', 'f0000000-0000-0000-0000-0000000000d2')$q$, 'un docente no asigna docentes');
  perform ta.root();
end $$;

-- ===== 2. Material: solo el docente asignado (o el admin) publica =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.dml($q$insert into curso_materiales (curso_id, tema, titulo) values ('fc000000-0000-0000-0000-00000000000a', 'Semana 1', 'Guía 1')$q$)::text, '1', 'docente asignado publica material');
  perform ta.eq(ta.dml($q$insert into curso_materiales (curso_id, tema, titulo, publicado) values ('fc000000-0000-0000-0000-00000000000a', 'Semana 2', 'Borrador', false)$q$)::text, '1', 'docente guarda borrador');
  perform ta.falla($q$insert into curso_materiales (curso_id, titulo) values ('fc000000-0000-0000-0000-00000000000b', 'Intruso')$q$, 'docente no publica en un curso que no tiene asignado');
  perform ta.act('f0000000-0000-0000-0000-0000000000d2');
  perform ta.falla($q$insert into curso_materiales (curso_id, titulo) values ('fc000000-0000-0000-0000-00000000000a', 'Intruso')$q$, 'docente NO asignado no publica');
  perform ta.act('f0000000-0000-0000-0000-0000000000a1');
  perform ta.eq(ta.dml($q$insert into curso_materiales (curso_id, titulo) values ('fc000000-0000-0000-0000-00000000000b', 'Del admin')$q$)::text, '1', 'admin publica en cualquier curso de su instituto');
  perform ta.act('f0000000-0000-0000-0000-0000000000a2');
  perform ta.falla($q$insert into curso_materiales (curso_id, titulo) values ('fc000000-0000-0000-0000-00000000000a', 'Intruso')$q$, 'admin de otro instituto no publica');
  perform ta.eq(ta.n('select 1 from curso_materiales')::text, '0', 'admin de otro instituto no ve material ajeno');
  perform ta.root();
end $$;

-- ===== 3. Qué ve cada estudiante =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.n('select 1 from cursos')::text, '1', 'est1 (APSTI) ve solo su curso');
  perform ta.eq(ta.n('select 1 from curso_materiales')::text, '1', 'est1 ve el material publicado y no el borrador ni el de otra carrera');
  perform ta.falla($q$insert into curso_materiales (curso_id, titulo) values ('fc000000-0000-0000-0000-00000000000a', 'Hack')$q$, 'estudiante no publica material');
  perform ta.eq(ta.dml($q$update curso_materiales set titulo = 'Hack'$q$)::text, '0', 'estudiante no edita material');
  perform ta.eq(ta.dml($q$delete from curso_materiales$q$)::text, '0', 'estudiante no borra material');
  perform ta.falla($q$insert into curso_docentes (curso_id, user_id) values ('fc000000-0000-0000-0000-00000000000a', 'f0000000-0000-0000-0000-0000000000e1')$q$, 'estudiante no se asigna como docente');
  perform ta.act('f0000000-0000-0000-0000-0000000000e2');
  perform ta.eq(ta.n('select 1 from cursos')::text, '1', 'est2 (MECANICA) ve solo su curso');
  perform ta.eq(ta.n($q$select 1 from curso_materiales where curso_id = 'fc000000-0000-0000-0000-00000000000a'$q$)::text, '0', 'est2 no ve el material de otra carrera');
  perform ta.act('f0000000-0000-0000-0000-0000000000e4');
  perform ta.eq(ta.n($q$select 1 from cursos where colegio_id = 'fa000000-0000-0000-0000-000000000001'$q$)::text, '0', 'estudiante de otro instituto no ve cursos ajenos');
  perform ta.eq(ta.n('select 1 from curso_materiales where titulo <> ''zz''')::text, '0', 'estudiante de otro instituto no ve material ajeno');
  perform ta.root();
end $$;

-- ===== 4. Archivos del curso (bucket «cursos») =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.dml($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000a/guia.pdf')$q$)::text, '1', 'docente sube archivo a su curso');
  perform ta.falla($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000b/x.pdf')$q$, 'docente no sube a un curso ajeno');
  perform ta.falla($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fb000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000a/x.pdf')$q$, 'ruta con otro colegio falla');
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '1', 'est1 ve el archivo de su curso');
  perform ta.falla($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000a/hack.pdf')$q$, 'estudiante no sube material');
  perform ta.act('f0000000-0000-0000-0000-0000000000e2');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '0', 'est2 no ve archivos de otra carrera');
  perform ta.act('f0000000-0000-0000-0000-0000000000e4');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '0', 'otro instituto no ve archivos');
  perform ta.root();
end $$;

-- ===== 5. Actividad + entregas (014) =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.dml($q$insert into curso_actividades (id, curso_id, titulo, puntaje_max, fecha_limite) values ('fd000000-0000-0000-0000-000000000001', 'fc000000-0000-0000-0000-00000000000a', 'Práctica 1', 20, now() - interval '1 day')$q$)::text, '1', 'docente crea actividad');
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.n('select 1 from curso_actividades')::text, '1', 'est1 ve la actividad');
  perform ta.falla($q$select public.entregar_actividad('fd000000-0000-0000-0000-000000000001', '')$q$, 'entrega vacía falla');
  perform ta.falla($q$select public.entregar_actividad('fd000000-0000-0000-0000-000000000001', 'x', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000a/entregas/f0000000-0000-0000-0000-0000000000e3/a.pdf', 'a.pdf', 10)$q$, 'archivo en la carpeta de otro estudiante falla');
  perform ta.eq(ta.n($q$select public.entregar_actividad('fd000000-0000-0000-0000-000000000001', 'Mi respuesta')$q$)::text, '1', 'est1 entrega');
  perform ta.eq(ta.n('select 1 from curso_entregas')::text, '1', 'est1 ve su entrega');
  perform ta.eq((select tardia::text from curso_entregas), 'true', 'entrega después de la fecha límite queda marcada como tardía');
  perform ta.falla($q$insert into curso_entregas (colegio_id, curso_id, actividad_id, alumno_id, texto) values ('fa000000-0000-0000-0000-000000000001', 'fc000000-0000-0000-0000-00000000000a', 'fd000000-0000-0000-0000-000000000001', 'f1000000-0000-0000-0000-000000000003', 'suplantar')$q$, 'insert directo en entregas falla');
  perform ta.eq(ta.dml($q$update curso_entregas set nota = 20$q$)::text, '0', 'el estudiante no se pone nota');
  perform ta.act('f0000000-0000-0000-0000-0000000000e2');
  perform ta.falla($q$select public.entregar_actividad('fd000000-0000-0000-0000-000000000001', 'x')$q$, 'estudiante de otra carrera no entrega');
  perform ta.act('f0000000-0000-0000-0000-0000000000e3');
  perform ta.eq(ta.n('select 1 from curso_entregas')::text, '0', 'un compañero no ve la entrega de est1');
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.n('select 1 from curso_entregas')::text, '1', 'docente del curso ve la entrega');
  perform ta.act('f0000000-0000-0000-0000-0000000000d2');
  perform ta.eq(ta.n('select 1 from curso_entregas')::text, '0', 'docente NO asignado no ve entregas');
  perform ta.root();
end $$;

-- ===== 6. Calificar =====
do $$ declare v uuid; begin
  select id into v from curso_entregas limit 1;
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.falla(format($q$select public.calificar_entrega(%L, 20, 'x')$q$, v), 'estudiante no califica');
  perform ta.act('f0000000-0000-0000-0000-0000000000d2');
  perform ta.falla(format($q$select public.calificar_entrega(%L, 10, 'x')$q$, v), 'docente no asignado no califica');
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.falla(format($q$select public.calificar_entrega(%L, 25, 'x')$q$, v), 'nota mayor al puntaje máximo falla');
  perform ta.falla(format($q$select public.calificar_entrega(%L, -1, 'x')$q$, v), 'nota negativa falla');
  perform ta.eq(ta.n(format($q$select public.calificar_entrega(%L, 15, 'Bien')$q$, v))::text, '1', 'docente califica');
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.eq((select nota::text from curso_entregas), '15.00', 'est1 ve su nota');
  perform ta.falla($q$select public.entregar_actividad('fd000000-0000-0000-0000-000000000001', 'cambio')$q$, 'entrega calificada no se puede cambiar');
  perform ta.act('f0000000-0000-0000-0000-0000000000a1');
  perform ta.eq(ta.n(format($q$select public.calificar_entrega(%L, null, '')$q$, v))::text, '1', 'admin quita la calificación');
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.n($q$select public.entregar_actividad('fd000000-0000-0000-0000-000000000001', 'Corregido')$q$)::text, '1', 'sin calificación se puede reenviar');
  perform ta.eq((select texto from curso_entregas), 'Corregido', 'el reenvío reemplaza la entrega (una por alumno)');
  perform ta.root();
end $$;

-- ===== 7. Archivos de entrega: privados entre compañeros =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000e1');
  perform ta.eq(ta.dml($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000a/entregas/f0000000-0000-0000-0000-0000000000e1/tarea.pdf')$q$)::text, '1', 'est1 sube su entrega a su carpeta');
  perform ta.falla($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000a/entregas/f0000000-0000-0000-0000-0000000000e3/falsa.pdf')$q$, 'est1 no sube a la carpeta de otro');
  perform ta.falla($q$insert into storage.objects (bucket_id, name) values ('cursos', 'fa000000-0000-0000-0000-000000000001/fc000000-0000-0000-0000-00000000000b/entregas/f0000000-0000-0000-0000-0000000000e1/x.pdf')$q$, 'est1 no sube a un curso que no ve');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '2', 'est1 ve el material y su propia entrega');
  perform ta.act('f0000000-0000-0000-0000-0000000000e3');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '1', 'el compañero ve el material pero NO la entrega de est1');
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '2', 'el docente del curso ve material y entrega');
  perform ta.act('f0000000-0000-0000-0000-0000000000d2');
  perform ta.eq(ta.n($q$select 1 from storage.objects where bucket_id = 'cursos'$q$)::text, '1', 'docente no asignado ve el material (personal del instituto) pero NO la entrega');
  perform ta.act('f0000000-0000-0000-0000-0000000000e3');
  perform ta.root();
end $$;

-- ===== 8. Periodos del libro de notas (015) =====
do $$ begin
  perform ta.act('f0000000-0000-0000-0000-0000000000d1');
  perform ta.eq(ta.dml($q$insert into curso_actividades (curso_id, titulo, periodo) values ('fc000000-0000-0000-0000-00000000000a', 'Examen P3', 3)$q$)::text, '1', 'docente publica una actividad en el periodo 3');
  perform ta.falla($q$insert into curso_actividades (curso_id, titulo, periodo) values ('fc000000-0000-0000-0000-00000000000a', 'Periodo invalido', 9)$q$, 'el periodo debe estar entre 1 y 8');
  perform ta.falla($q$insert into curso_actividades (curso_id, titulo, periodo) values ('fc000000-0000-0000-0000-00000000000a', 'Periodo cero', 0)$q$, 'el periodo 0 no es valido');
  perform ta.eq(ta.n($q$select 1 from curso_actividades where titulo = 'Examen P3' and periodo = 3$q$)::text, '1', 'el periodo se guarda');
  perform ta.root();
end $$;

rollback;
select 'AULA: TODAS LAS PRUEBAS PASARON' as resultado;
