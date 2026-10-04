-- Pruebas de roles y seguridad (RLS) de las migraciones 002 y 004 sobre el esquema de producción.
-- Se ejecuta con: python supabase/tests/run_tests.py   (usa un PostgreSQL local; no toca ninguna base real)
\set ON_ERROR_STOP on
\set QUIET on

create schema t;
grant usage on schema t to authenticated, anon;
create function t.act(u uuid) returns void language plpgsql as $$
begin perform set_config('request.jwt.claim.sub', coalesce(u::text, ''), true); execute 'set local role authenticated'; end $$;
create function t.root() returns void language plpgsql as $$
begin execute 'reset role'; perform set_config('request.jwt.claim.sub', '', true); end $$;
create function t.n(q text) returns bigint language plpgsql as $$ declare r bigint; begin execute 'select count(*) from (' || q || ') s' into r; return r; end $$;
create function t.dml(q text) returns bigint language plpgsql as $$ declare r bigint; begin execute q; get diagnostics r = row_count; return r; end $$;
create function t.falla(q text, msg text) returns void language plpgsql as $$
begin
  begin execute q; exception when others then return; end;
  raise exception 'DEBIA FALLAR: % -> %', msg, q;
end $$;
create function t.eq(a text, b text, msg text) returns void language plpgsql as $$
begin if a is distinct from b then raise exception 'FALLO: % (esperado %, obtenido %)', msg, b, a; end if; end $$;
grant execute on all functions in schema t to authenticated, anon;

-- ---------- Datos de prueba (como superusuario) ----------
insert into auth.users (id, email) values
  ('00000000-0000-0000-0000-0000000000a1', 'admin@a.pe'), ('00000000-0000-0000-0000-0000000000d1', 'docente@a.pe'),
  ('00000000-0000-0000-0000-0000000000c1', 'coord@a.pe'), ('00000000-0000-0000-0000-0000000000e1', 'est1@x.pe'),
  ('00000000-0000-0000-0000-0000000000e2', 'est2@x.pe'), ('00000000-0000-0000-0000-0000000000a2', 'admin@b.pe');
insert into colegios (id, nombre, codigo_registro) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'Instituto A', 'ABCD1234'), ('bbbbbbbb-0000-0000-0000-000000000001', 'Instituto B', 'ZZZZ9999');
insert into perfiles (id, colegio_id, nombre, rol, carrera) values
  ('00000000-0000-0000-0000-0000000000a1', 'aaaaaaaa-0000-0000-0000-000000000001', 'Admin A', 'Administrador', null),
  ('00000000-0000-0000-0000-0000000000d1', 'aaaaaaaa-0000-0000-0000-000000000001', 'Docente A', 'Docente', null),
  ('00000000-0000-0000-0000-0000000000c1', 'aaaaaaaa-0000-0000-0000-000000000001', 'Coord A', 'Coordinador', 'APSTI'),
  ('00000000-0000-0000-0000-0000000000a2', 'bbbbbbbb-0000-0000-0000-000000000001', 'Admin B', 'Administrador', null);
insert into niveles (colegio_id, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001', 'APSTI'), ('aaaaaaaa-0000-0000-0000-000000000001', 'MECANICA'), ('bbbbbbbb-0000-0000-0000-000000000001', 'OTRA');
insert into grados (colegio_id, nivel, nombre) values
  ('aaaaaaaa-0000-0000-0000-000000000001', 'APSTI', 'APSTI · I CICLO'), ('aaaaaaaa-0000-0000-0000-000000000001', 'MECANICA', 'MECANICA · I CICLO'),
  ('bbbbbbbb-0000-0000-0000-000000000001', 'OTRA', 'OTRA · I CICLO');
insert into alumnos (id, colegio_id, nombre, codigo, nivel, grado) values
  ('11111111-0000-0000-0000-000000000001', 'aaaaaaaa-0000-0000-0000-000000000001', 'Alumno APSTI', 'a1', 'APSTI', 'APSTI · I CICLO'),
  ('11111111-0000-0000-0000-000000000002', 'aaaaaaaa-0000-0000-0000-000000000001', 'Alumno MEC', 'a2', 'MECANICA', 'MECANICA · I CICLO'),
  ('11111111-0000-0000-0000-000000000003', 'bbbbbbbb-0000-0000-0000-000000000001', 'Alumno B', 'b1', 'OTRA', 'OTRA · I CICLO');
insert into asistencias (colegio_id, alumno_id, fecha, hora) values
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000001', '2026-10-05', '07:30'),
  ('aaaaaaaa-0000-0000-0000-000000000001', '11111111-0000-0000-0000-000000000002', '2026-10-05', '07:40');

-- ===== 1. Administrador: acceso total a SU instituto, y solo al suyo =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(t.n('select 1 from alumnos')::text, '2', 'admin ve los 2 alumnos de su instituto (no los de B)');
  perform t.eq(t.dml($q$insert into alumnos (colegio_id, nombre, codigo, nivel, grado) values ('aaaaaaaa-0000-0000-0000-000000000001','Nuevo','n1','APSTI','APSTI · I CICLO')$q$)::text, '1', 'admin crea alumno');
  perform t.eq(t.dml($q$update alumnos set apoderado = 'X' where codigo = 'n1'$q$)::text, '1', 'admin edita alumno');
  perform t.eq(t.dml($q$delete from alumnos where codigo = 'n1'$q$)::text, '1', 'admin elimina alumno');
  perform t.eq(t.dml($q$insert into niveles (colegio_id, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','NUEVA')$q$)::text, '1', 'admin crea carrera');
  perform t.eq(t.dml($q$update colegios set nombre = 'Instituto A2' where id = 'aaaaaaaa-0000-0000-0000-000000000001'$q$)::text, '1', 'admin renombra su instituto');
  perform t.eq(t.dml($q$update colegios set nombre = 'HACK' where id = 'bbbbbbbb-0000-0000-0000-000000000001'$q$)::text, '0', 'admin NO toca otro instituto');
  perform t.falla($q$insert into alumnos (colegio_id, nombre, codigo) values ('bbbbbbbb-0000-0000-0000-000000000001','Intruso','i1')$q$, 'admin no inserta en otro instituto');
  perform t.root();
end $$;

-- ===== 2. Docente: lee y registra asistencia; NO modifica padrón ni catálogos =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.n('select 1 from alumnos')::text, '2', 'docente ve los alumnos');
  perform t.eq(t.dml($q$insert into asistencias (colegio_id, alumno_id, fecha, hora, registrado_por, origen) values ('aaaaaaaa-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000001','2026-10-06','07:31','00000000-0000-0000-0000-0000000000d1','qr')$q$)::text, '1', 'docente registra asistencia');
  perform t.falla($q$insert into alumnos (colegio_id, nombre, codigo) values ('aaaaaaaa-0000-0000-0000-000000000001','X','x1')$q$, 'docente no crea alumnos');
  perform t.eq(t.dml($q$update alumnos set nombre = 'HACK'$q$)::text, '0', 'docente no edita alumnos');
  perform t.eq(t.dml($q$delete from alumnos$q$)::text, '0', 'docente no elimina alumnos');
  perform t.falla($q$insert into niveles (colegio_id, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','Z')$q$, 'docente no crea carreras');
  perform t.falla($q$insert into grados (colegio_id, nivel, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','APSTI','Z')$q$, 'docente no crea ciclos');
  perform t.falla($q$insert into comunicados (colegio_id, titulo, mensaje) values ('aaaaaaaa-0000-0000-0000-000000000001','t','m')$q$, 'docente no publica comunicados');
  perform t.eq(t.dml($q$delete from asistencias$q$)::text, '0', 'docente no borra asistencias');
  perform t.eq(t.dml($q$update asistencias set hora = '06:00'$q$)::text, '0', 'docente no altera horas');
  perform t.eq(t.dml($q$update colegios set nombre = 'HACK'$q$)::text, '0', 'docente no renombra el instituto');
  perform t.root();
end $$;

-- ===== 3. Coordinador: solo ve su carrera =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000c1');
  perform t.eq(t.n('select 1 from alumnos')::text, '1', 'coordinador ve solo alumnos de APSTI');
  perform t.eq(t.n($q$select 1 from alumnos where nivel = 'MECANICA'$q$)::text, '0', 'coordinador no ve MECANICA');
  perform t.eq(t.n('select 1 from grados')::text, '1', 'coordinador ve solo los ciclos de su carrera');
  perform t.eq(t.n('select 1 from niveles')::text, '1', 'coordinador ve solo su carrera');
  perform t.eq(t.n('select 1 from asistencias')::text, '2', 'coordinador ve solo asistencias de su carrera (APSTI: 2 de 3)');
  perform t.falla($q$insert into alumnos (colegio_id, nombre, codigo) values ('aaaaaaaa-0000-0000-0000-000000000001','X','x2')$q$, 'coordinador no crea alumnos');
  perform t.root();
end $$;

-- ===== 4. Aislamiento entre institutos =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a2');
  perform t.eq(t.n('select 1 from alumnos')::text, '1', 'admin B solo ve a su alumno');
  perform t.eq(t.n('select 1 from asistencias')::text, '0', 'admin B no ve asistencias de A');
  perform t.root();
end $$;

-- ===== 5. Estudiante: registro, privacidad y protección contra abuso =====
do $$ declare r json; begin
  perform t.act('00000000-0000-0000-0000-0000000000e1');
  perform t.eq(t.n('select 1 from alumnos')::text, '0', 'estudiante sin registro no ve a nadie');
  perform t.falla($q$select hora_servidor_inexistente()$q$, 'sanidad del arnés');
  r := public.info_colegio('abcd1234');
  perform t.eq((r->>'nombre'), 'Instituto A2', 'código válido (sin distinguir mayúsculas) devuelve el instituto');
  r := public.registrar_estudiante('abcd1234', 'Ana', 'Lopez', 'APSTI', 'APSTI · I CICLO', 'Mamá', '12345678', '+51 999-888-777', 'Mama@Ejemplo.com');
  perform t.eq(left(r->>'codigo', 1), 'e', 'se genera un código QR único');
  perform t.eq((r->>'aprobado'), 'false', 'queda pendiente de aprobación');
  perform t.eq((r->>'apoderado_telefono'), '+51999888777', 'teléfono normalizado');
  perform t.eq((r->>'apoderado_email'), 'mama@ejemplo.com', 'correo normalizado');
  perform t.eq(length(r->>'qr_secreto')::text, '32', 'se genera el secreto del QR dinámico');
  perform t.eq(t.n('select 1 from alumnos')::text, '1', 'el estudiante ve SOLO su registro');
  perform t.eq(t.dml($q$update alumnos set aprobado = true$q$)::text, '0', 'el estudiante no puede auto‑aprobarse');
  perform t.falla($q$insert into alumnos (colegio_id, nombre, codigo) values ('aaaaaaaa-0000-0000-0000-000000000001','X','x3')$q$, 'el estudiante no inserta directo');
  perform t.eq(t.n('select 1 from asistencias')::text, '0', 'el estudiante no ve asistencias');
  perform t.eq(t.n('select 1 from perfiles')::text, '0', 'el estudiante no ve perfiles');
  perform t.falla($q$select public.registrar_estudiante('abcd1234','Otra','Vez','APSTI','APSTI · I CICLO')$q$, 'un segundo registro de la misma cuenta se rechaza');
  perform t.eq((public.mi_registro()->'alumno'->>'nombres'), 'Ana', 'mi_registro devuelve el propio registro');
  perform t.falla($q$select public.actualizar_mi_foto('otro-usuario/foto.jpg')$q$, 'no puede apuntar la foto a la carpeta de otro');
  perform public.actualizar_mi_foto('00000000-0000-0000-0000-0000000000e1/foto-1.jpg');
  perform t.root();
end $$;

do $$ declare r json; i int; begin
  perform t.act('00000000-0000-0000-0000-0000000000e2');
  for i in 1..10 loop
    r := public.registrar_estudiante('MALO' || i, 'Ana', 'Lopez', 'APSTI', 'APSTI · I CICLO');
    perform t.eq(r->>'error', 'codigo_invalido', 'código inválido devuelve error controlado (intento ' || i || ')');
  end loop;
  perform t.falla($q$select public.info_colegio('ABCD1234')$q$, 'tras 10 intentos fallidos se bloquea (aunque el código sea correcto)');
  perform t.root();
end $$;

-- ===== 6. Justificaciones, cursos, avisos =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.dml($q$insert into justificaciones (colegio_id, alumno_id, fecha, tipo, motivo, registrado_por) values ('aaaaaaaa-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000001','2026-10-07','Permiso','Cita médica','00000000-0000-0000-0000-0000000000d1')$q$)::text, '1', 'docente registra justificación');
  perform t.eq(t.dml($q$update justificaciones set motivo = 'x'$q$)::text, '0', 'docente no edita justificaciones');
  perform t.eq(t.dml($q$insert into avisos_apoderados (colegio_id, alumno_id, fecha, tipo, enviado_por) values ('aaaaaaaa-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000001','2026-10-07','Falta','00000000-0000-0000-0000-0000000000d1')$q$)::text, '1', 'docente registra un aviso');
  perform t.falla($q$insert into cursos (colegio_id, nivel, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','APSTI','Matemática')$q$, 'docente no crea cursos');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(t.dml($q$insert into cursos (colegio_id, nivel, grado, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','APSTI','APSTI · I CICLO','Matemática')$q$)::text, '1', 'admin crea curso');
  perform t.falla($q$insert into cursos (colegio_id, nivel, grado, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','APSTI','APSTI · I CICLO','matemática')$q$, 'curso duplicado (sin distinguir mayúsculas) se rechaza');
  perform t.eq(t.dml($q$update justificaciones set motivo = 'Cita médica (corregida)'$q$)::text, '1', 'admin sí edita justificaciones');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000c1');
  perform t.eq(t.n('select 1 from cursos')::text, '1', 'coordinador ve los cursos de su carrera');
  perform t.eq(t.n('select 1 from justificaciones')::text, '1', 'coordinador ve justificaciones de su carrera');
  perform t.root();
end $$;

do $$ declare cid uuid; begin
  select id into cid from cursos limit 1;
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.dml(format($q$insert into asistencias_curso (colegio_id, alumno_id, curso_id, fecha, hora, origen) values ('aaaaaaaa-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000001','%s','2026-10-07','08:05','qr')$q$, cid))::text, '1', 'docente registra asistencia por curso');
  perform t.falla(format($q$insert into asistencias_curso (colegio_id, alumno_id, curso_id, fecha, hora) values ('aaaaaaaa-0000-0000-0000-000000000001','11111111-0000-0000-0000-000000000001','%s','2026-10-07','09:00')$q$, cid), 'una sola asistencia por alumno, curso y día');
  perform t.root();
end $$;

-- ===== 7. Registro de errores =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000e1');
  perform t.eq(t.dml($q$insert into logs_cliente (app, mensaje, detalle) values ('estudiante', repeat('x', 900), 'stack')$q$)::text, '1', 'el estudiante registra un error');
  perform t.eq(t.n('select 1 from logs_cliente')::text, '0', 'el estudiante no puede leer los registros');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.dml($q$insert into logs_cliente (app, mensaje) values ('docente', 'boom')$q$)::text, '1', 'el docente registra un error');
  perform t.eq(t.n('select 1 from logs_cliente')::text, '0', 'el docente no lee registros (solo admin)');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(t.n('select 1 from logs_cliente')::text, '2', 'el admin A ve los errores de su instituto (estudiante y docente)');
  perform t.eq(t.n($q$select 1 from logs_cliente where length(mensaje) = 500$q$)::text, '1', 'el servidor recorta el mensaje a 500 caracteres');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a2');
  perform t.eq(t.n('select 1 from logs_cliente')::text, '0', 'el admin B no ve errores de A');
  perform t.root();
end $$;

-- ===== 8. Fotos (Storage) =====
insert into storage.buckets (id, name) values ('fotos-alumnos', 'fotos-alumnos') on conflict do nothing;
insert into storage.objects (bucket_id, name) values ('fotos-alumnos', '00000000-0000-0000-0000-0000000000e1/foto-1.jpg');
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.n($q$select 1 from storage.objects where bucket_id = 'fotos-alumnos'$q$)::text, '1', 'el personal del instituto ve la foto');
  perform t.eq(t.dml($q$delete from storage.objects where bucket_id = 'fotos-alumnos'$q$)::text, '0', 'un docente no borra fotos');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a2');
  perform t.eq(t.n($q$select 1 from storage.objects where bucket_id = 'fotos-alumnos'$q$)::text, '0', 'otro instituto no ve la foto');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(t.dml($q$delete from storage.objects where bucket_id = 'fotos-alumnos'$q$)::text, '1', 'el admin del instituto sí puede borrar la foto');
  perform t.root();
end $$;

-- ===== 9. Derecho de supresión =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.falla($q$select public.eliminar_mi_registro()$q$, 'el personal no se auto‑elimina con esta función');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000e1');
  perform public.eliminar_mi_registro();
  perform t.root();
  perform t.eq((select count(*) from alumnos where user_id = '00000000-0000-0000-0000-0000000000e1')::text, '0', 'se borra el registro del estudiante');
  perform t.eq((select count(*) from auth.users where id = '00000000-0000-0000-0000-0000000000e1')::text, '0', 'se borra su cuenta');
  perform t.eq((select count(*) from asistencias where alumno_id not in (select id from alumnos))::text, '0', 'sin asistencias huérfanas');
end $$;

-- ===== 10. Hora del servidor y funciones de rol cerradas a anónimos =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq((abs(extract(epoch from (public.hora_servidor() - now()))) < 5)::text, 'true', 'hora_servidor devuelve la hora real');
  perform t.root();
  execute 'set local role anon';
  perform t.falla($q$select public.es_admin()$q$, 'anónimos no pueden llamar a funciones de rol');
  perform t.falla($q$select public.registrar_estudiante('x','a','b','c','d')$q$, 'anónimos no pueden registrar');
  perform t.root();
end $$;

select 'TODAS LAS PRUEBAS DE SEGURIDAD PASARON' as resultado;
