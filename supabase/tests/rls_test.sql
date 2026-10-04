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
  perform t.eq(t.dml($q$insert into comunicados (colegio_id, titulo, mensaje) values ('aaaaaaaa-0000-0000-0000-000000000001','t','m')$q$)::text, '1', 'docente SÍ publica comunicados');
  perform t.falla($q$insert into comunicados (colegio_id, titulo, mensaje) values ('bbbbbbbb-0000-0000-0000-000000000001','t','m')$q$, 'docente no publica en otro instituto');
  perform t.eq(t.dml($q$delete from comunicados$q$)::text, '0', 'docente no elimina comunicados');
  perform t.falla($q$select public.personal_asignar('est1@x.pe','Administrador')$q$, 'docente no asigna roles');
  perform t.falla($q$select public.personal_listar()$q$, 'docente no lista al personal');
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
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(json_array_length(public.personal_listar())::text, '3', 'admin lista a su personal (3 de su instituto)');
  perform t.root();
end $$;
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.falla($q$select public.personal_asignar('noexiste@x.pe','Docente')$q$, 'correo inexistente da error claro');
  perform t.falla($q$select public.personal_asignar('admin@b.pe','Docente')$q$, 'no se roba personal de otro instituto');
  perform t.falla($q$select public.personal_asignar('admin@a.pe','Docente')$q$, 'el admin no se degrada a sí mismo');
  perform t.falla($q$select public.personal_asignar('coord@a.pe','Coordinador','NOEXISTE')$q$, 'coordinador exige carrera existente');
  perform t.falla($q$select public.personal_asignar('coord@a.pe','Superman')$q$, 'rol inválido rechazado');
  perform t.falla($q$select public.personal_quitar('00000000-0000-0000-0000-0000000000a1')$q$, 'el admin no se quita a sí mismo');
  perform t.root();
  insert into auth.users (id, email) values ('00000000-0000-0000-0000-0000000000f1', 'nuevo@a.pe');
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform public.personal_asignar('NUEVO@a.pe', 'coordinador', 'MECANICA', 'Nuevo Coord');
  perform t.root();
  perform t.eq((select rol || '/' || carrera from perfiles where id = '00000000-0000-0000-0000-0000000000f1'), 'Coordinador/MECANICA', 'admin crea un coordinador por correo');
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform public.personal_asignar('nuevo@a.pe', 'Docente');
  perform t.root();
  perform t.eq((select rol || '/' || coalesce(carrera, '-') from perfiles where id = '00000000-0000-0000-0000-0000000000f1'), 'Docente/-', 'cambiar a docente limpia la carrera');
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform public.personal_quitar('00000000-0000-0000-0000-0000000000f1');
  perform t.root();
  perform t.eq((select count(*) from perfiles where id = '00000000-0000-0000-0000-0000000000f1')::text, '0', 'admin quita un acceso');
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.root();
end $$;

-- ===== 12. Avisos al teléfono (token por instituto) =====
do $$ declare tok text; tokb text; begin
  perform t.root();
  select aviso_token into tok from colegios where id = 'aaaaaaaa-0000-0000-0000-000000000001';
  select aviso_token into tokb from colegios where id = 'bbbbbbbb-0000-0000-0000-000000000001';
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq((public.token_avisos() = tok)::text, 'true', 'el docente recibe el token de SU instituto');
  perform t.root();
  execute 'set local role anon';
  perform t.eq(json_array_length(public.comunicados_desde(tok, now() - interval '1 hour'))::text, '1', 'con el token se leen los comunicados nuevos (sin sesión)');
  perform t.eq(json_array_length(public.comunicados_desde(tokb, now() - interval '1 hour'))::text, '0', 'el token de otro instituto no ve los comunicados ajenos');
  perform t.eq(json_array_length(public.comunicados_desde('x', null))::text, '0', 'token inválido → vacío');
  perform t.falla($q$select public.token_avisos()$q$, 'anónimos no piden el token');
  perform t.falla($q$select * from comunicados$q$, 'anónimos no leen la tabla de comunicados');
  perform t.root();
end $$;

-- ===== 13. Foto de perfil del personal =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform public.actualizar_mi_perfil('Docente Nuevo Nombre', '00000000-0000-0000-0000-0000000000d1/foto-1.jpg');
  perform t.falla($q$select public.actualizar_mi_perfil(null, '00000000-0000-0000-0000-0000000000a1/foto-x.jpg')$q$, 'no se puede apuntar a la carpeta de otra persona');
  perform t.eq(t.dml($q$insert into storage.objects (bucket_id, name) values ('fotos-personal','00000000-0000-0000-0000-0000000000d1/foto-1.jpg')$q$)::text, '1', 'el docente sube a SU carpeta');
  perform t.falla($q$insert into storage.objects (bucket_id, name) values ('fotos-personal','00000000-0000-0000-0000-0000000000a1/foto.jpg')$q$, 'el docente no sube a la carpeta de otro');
  perform t.root();
  perform t.eq((select nombre || '|' || foto_path from perfiles where id = '00000000-0000-0000-0000-0000000000d1'), 'Docente Nuevo Nombre|00000000-0000-0000-0000-0000000000d1/foto-1.jpg', 'nombre y foto guardados');
  insert into storage.objects (bucket_id, name) values ('fotos-personal', '00000000-0000-0000-0000-0000000000d1/foto-1.jpg'), ('fotos-personal', '00000000-0000-0000-0000-0000000000a2/foto.jpg');
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq((t.n($q$select 1 from storage.objects where name like '%0000000000d1/%'$q$) > 0)::text, 'true', 'el admin ve la foto de su personal');
  perform t.eq(t.n($q$select 1 from storage.objects where name like '%0000000000a2/%'$q$)::text, '0', 'el admin NO ve la foto de otro instituto');
  perform t.eq(t.dml($q$update perfiles set rol = 'Administrador'$q$)::text, '0', 'perfiles no se escribe directamente (nadie se auto-asciende)');
  perform t.root();
end $$;

select 'TODAS LAS PRUEBAS DE SEGURIDAD PASARON' as resultado;
