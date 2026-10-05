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

-- ===== 14. Calendario, horarios, periodos (solo el administrador escribe) =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(t.dml($q$insert into calendario (colegio_id, fecha, tipo, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','2026-10-08','Feriado','Combate de Angamos')$q$)::text, '1', 'admin crea un feriado');
  perform t.eq(t.dml($q$insert into horarios (colegio_id, nivel, hora_ingreso, tolerancia_min) values ('aaaaaaaa-0000-0000-0000-000000000001','APSTI','07:30',10)$q$)::text, '1', 'admin define horario por carrera');
  perform t.eq(t.dml($q$insert into periodos (colegio_id, nombre, inicio) values ('aaaaaaaa-0000-0000-0000-000000000001','2026-II','2026-08-03')$q$)::text, '1', 'admin crea un periodo');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.n('select 1 from calendario')::text, '1', 'el docente lee el calendario');
  perform t.falla($q$insert into calendario (colegio_id, fecha, nombre) values ('aaaaaaaa-0000-0000-0000-000000000001','2026-12-25','X')$q$, 'el docente no edita el calendario');
  perform t.falla($q$insert into horarios (colegio_id, nivel, hora_ingreso) values ('aaaaaaaa-0000-0000-0000-000000000001','MECANICA','09:00')$q$, 'el docente no cambia horarios');
  perform t.falla($q$insert into periodos (colegio_id, nombre, inicio) values ('aaaaaaaa-0000-0000-0000-000000000001','2027-I','2027-03-01')$q$, 'el docente no crea periodos');
  perform t.eq(public.limite_ingreso('aaaaaaaa-0000-0000-0000-000000000001', 'APSTI'), '07:40', 'límite = ingreso + tolerancia (07:30 + 10 min)');
  perform t.eq(coalesce(public.limite_ingreso('aaaaaaaa-0000-0000-0000-000000000001', 'MECANICA'), 'null'), 'null', 'sin horario → null (la app usa 08:00)');
  perform t.root();
end $$;

-- ===== 15. Salidas (quiosco) =====
do $$ declare r json; begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  r := public.registrar_salidas('[{"alumno_id":"11111111-0000-0000-0000-000000000001","fecha":"2026-10-05","hora":"13:05"}]'::jsonb);
  perform t.eq(r->>'ok', '1', 'el docente registra una salida');
  r := public.registrar_salidas('[{"alumno_id":"11111111-0000-0000-0000-000000000001","fecha":"2026-10-05","hora":"13:30"}]'::jsonb);
  perform t.eq(r->>'dup', '1', 'una segunda salida no sobrescribe la primera');
  r := public.registrar_salidas('[{"alumno_id":"11111111-0000-0000-0000-000000000001","fecha":"2026-10-20","hora":"13:30"}]'::jsonb);
  perform t.eq(r->>'sin_entrada', '1', 'salida sin ingreso ese día se rechaza');
  r := public.registrar_salidas('[{"alumno_id":"11111111-0000-0000-0000-000000000003","fecha":"2026-10-05","hora":"13:30"}]'::jsonb);
  perform t.eq(r->>'ok', '0', 'no se registra la salida de un alumno de otro instituto');
  perform t.root();
  perform t.eq((select hora_salida from asistencias where alumno_id = '11111111-0000-0000-0000-000000000001' and fecha = '2026-10-05'), '13:05', 'la salida quedó guardada');
  perform t.act('00000000-0000-0000-0000-0000000000c1');
  r := public.registrar_salidas('[{"alumno_id":"11111111-0000-0000-0000-000000000002","fecha":"2026-10-05","hora":"13:30"}]'::jsonb);
  perform t.eq(r->>'ok', '0', 'el coordinador no registra salidas de otra carrera');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.dml($q$update asistencias set hora_salida = '23:59'$q$)::text, '0', 'el docente sigue sin poder editar asistencias directamente');
  perform t.root();
  execute 'set local role anon';
  perform t.falla($q$select public.registrar_salidas('[]'::jsonb)$q$, 'anónimos no registran salidas');
  perform t.root();
end $$;

-- ===== 16. Historial de cambios =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  update alumnos set nombre = 'Nombre Nuevo', grado = 'APSTI · II CICLO' where id = '11111111-0000-0000-0000-000000000002';
  update asistencias set hora = '07:45' where alumno_id = '11111111-0000-0000-0000-000000000002' and fecha = '2026-10-05';
  perform t.root();
  perform t.eq((select usuario from auditoria where tabla = 'alumnos' and accion = 'UPDATE' and registro_id = '11111111-0000-0000-0000-000000000002'), 'admin@a.pe', 'se anota QUIÉN hizo el cambio');
  perform t.eq((select detalle->'grado'->>'a' from auditoria where tabla = 'alumnos' and accion = 'UPDATE' and registro_id = '11111111-0000-0000-0000-000000000002'), 'APSTI · II CICLO', 'se anota el valor nuevo de lo no personal');
  perform t.eq((select detalle->'nombre'::text from auditoria where tabla = 'alumnos' and accion = 'UPDATE' and registro_id = '11111111-0000-0000-0000-000000000002')::text, '"(dato personal modificado)"', 'los datos personales no se copian al historial');
  perform t.eq((select count(*) from auditoria where tabla = 'asistencias' and accion = 'UPDATE')::text, '1', 'se registra la corrección de una asistencia');
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.eq(t.n('select 1 from auditoria')::text, '0', 'el docente no ve el historial');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq((t.n('select 1 from auditoria') > 0)::text, 'true', 'el administrador ve el historial de su instituto');
  perform t.falla($q$insert into auditoria (accion, tabla) values ('INSERT','x')$q$, 'nadie escribe en el historial directamente');
  perform t.falla($q$delete from auditoria$q$, 'nadie borra el historial');
  perform t.root();
  perform t.act('00000000-0000-0000-0000-0000000000a2');
  perform t.eq(t.n($q$select 1 from auditoria where colegio_id = 'aaaaaaaa-0000-0000-0000-000000000001'$q$)::text, '0', 'el admin de otro instituto no ve el historial de este');
  perform t.root();
  delete from alumnos where id = '11111111-0000-0000-0000-000000000002';
  perform t.eq((select count(*) from auditoria where registro_id = '11111111-0000-0000-0000-000000000002' and detalle ? 'nombre')::text, '0', 'al eliminar un alumno se purgan sus datos personales del historial');
  perform t.eq((select detalle->>'codigo' from auditoria where tabla = 'alumnos' and accion = 'DELETE' and registro_id = '11111111-0000-0000-0000-000000000002'), 'a2', 'queda constancia de la eliminación sin datos personales');
end $$;

-- ===== 17. Consulta para apoderados =====
do $$ declare cod text; r json; i int; begin
  select codigo_apoderado into cod from alumnos where id = '11111111-0000-0000-0000-000000000001';
  perform t.eq(length(cod)::text, '12', 'cada alumno tiene un código de apoderado de 12 caracteres');
  execute 'set local role anon';
  r := public.consulta_apoderado(cod);
  perform t.eq(r->'alumno'->>'carrera', 'APSTI', 'el apoderado ve la carrera de su hijo con el código');
  perform t.eq((r->'resumen' is not null)::text, 'true', 'incluye el resumen de asistencia');
  perform t.eq((public.consulta_apoderado(lower(substr(cod,1,4) || '-' || substr(cod,5,4) || '-' || substr(cod,9,4))) is not null)::text, 'true', 'acepta minúsculas y guiones');
  perform t.eq((public.consulta_apoderado('ZZZZZZZZZZZZ') is null)::text, 'true', 'un código inexistente devuelve vacío');
  perform t.falla($q$select * from alumnos$q$, 'anónimos no leen alumnos directamente');
  for i in 1..25 loop begin perform public.consulta_apoderado('NOEXISTE0000'); exception when others then null; end; end loop;
  perform t.falla($q$select public.consulta_apoderado('NOEXISTE0000')$q$, 'tras demasiados intentos fallidos se bloquea');
  perform t.root();
end $$;

-- ===== 18. Directorio del personal (solo lectura, sin correos) =====
do $$ declare r json; begin
  perform t.act('00000000-0000-0000-0000-0000000000d1');
  r := public.personal_directorio();
  perform t.eq(json_array_length(r)::text, '2', 'el docente ve a los docentes y coordinadores de SU instituto (no admins ni otros institutos)');
  perform t.eq(((r->0)::text like '%@%' or (r->1)::text like '%@%')::text, 'false', 'el directorio no expone correos');
  perform t.root();
  execute 'set local role anon';
  perform t.falla($q$select public.personal_directorio()$q$, 'anónimos no ven el directorio');
  perform t.root();
end $$;

-- ===== 19. Aviso de aprobación al estudiante =====
do $$ declare tok text; r json; begin
  update alumnos set aprobado = false where id = '11111111-0000-0000-0000-000000000001';
  select notif_token into tok from alumnos where id = '11111111-0000-0000-0000-000000000001';
  perform t.eq(length(tok)::text, '32', 'cada alumno tiene un token de aviso de 32 caracteres');
  execute 'set local role anon';
  r := public.estado_solicitud(tok);
  perform t.eq(r->>'aprobado', 'false', 'con el token se ve que la solicitud sigue pendiente');
  perform t.eq(((r->>'nombre') is not null and (r->>'nombre') not like '% %')::text, 'true', 'solo expone el primer nombre');
  perform t.eq((public.estado_solicitud('0123456789abcdef0123456789abcdef') is null)::text, 'true', 'un token inexistente devuelve vacío');
  perform t.eq((public.estado_solicitud('corto') is null)::text, 'true', 'un token con formato inválido devuelve vacío');
  perform t.falla($q$select notif_token from alumnos$q$, 'anónimos no leen los tokens directamente');
  perform t.root();
  update alumnos set aprobado = true where id = '11111111-0000-0000-0000-000000000001';
  execute 'set local role anon';
  perform t.eq(public.estado_solicitud(tok)->>'aprobado', 'true', 'tras aprobar, el token informa «aprobado»');
  perform t.root();
  perform t.eq((select count(*) from auditoria where detalle::text like '%' || tok || '%')::text, '0', 'el token no aparece en el historial de cambios');
end $$;

-- ===== 20. Horario tarde/noche: ventana de ingreso y permanencia mínima =====
do $$ begin
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.eq(t.dml($q$insert into horarios (colegio_id, nivel, hora_ingreso, tolerancia_min, hora_salida, ingreso_desde, ingreso_hasta, permanencia_min) values ('aaaaaaaa-0000-0000-0000-000000000001','MECANICA','14:00',10,'20:00','13:00','19:00',120)$q$)::text, '1', 'admin define horario de tarde con ventana y permanencia');
  perform t.falla($q$insert into horarios (colegio_id, nivel, hora_ingreso, permanencia_min) values ('aaaaaaaa-0000-0000-0000-000000000001','OTRA','14:00',9999)$q$, 'la permanencia mínima tiene tope');
  perform t.falla($q$insert into horarios (colegio_id, nivel, hora_ingreso, ingreso_desde) values ('aaaaaaaa-0000-0000-0000-000000000001','OTRA2','14:00','25:99')$q$, 'una hora de apertura inválida se rechaza');
  perform t.root();
  perform t.eq((select permanencia_min::text from horarios where nivel = 'APSTI'), '120', 'por defecto la salida se habilita 2 horas después del ingreso');
  perform t.eq(public.limite_ingreso('aaaaaaaa-0000-0000-0000-000000000001', 'MECANICA'), '14:10', 'tardanza desde 14:10 (14:00 + 10 min)');
end $$;

-- ===== 21. Instituciones (superadmin) =====
do $$
declare
  r json;
  r_creado json;
  cid_nuevo uuid;
  cod_generado text;
  r_root json;
  cid_root uuid;
begin
  -- Fixtures de usuarios nuevos para esta sección
  insert into auth.users (id, email) values
    ('00000000-0000-0000-0000-0000000000a3', 'nuevo_admin@test.pe'),
    ('00000000-0000-0000-0000-0000000000a4', 'admin_crear_inst@test.pe')
  on conflict (id) do nothing;

  -- (a) admin normal, docente y anon NO pueden llamar sa_*
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.falla($q$select public.sa_listar()$q$, 'admin normal no puede sa_listar');
  perform t.falla($q$select public.sa_crear('Colegio Invalido')$q$, 'admin normal no puede sa_crear');
  perform t.falla($q$select public.sa_renombrar('aaaaaaaa-0000-0000-0000-000000000001', 'Nuevo Nombre')$q$, 'admin normal no puede sa_renombrar');
  perform t.falla($q$select public.sa_entrar('bbbbbbbb-0000-0000-0000-000000000001')$q$, 'admin normal no puede sa_entrar');
  perform t.falla($q$select public.sa_asignar_admin('aaaaaaaa-0000-0000-0000-000000000001', 'nuevo_admin@test.pe')$q$, 'admin normal no puede sa_asignar_admin');
  perform t.root();

  perform t.act('00000000-0000-0000-0000-0000000000d1');
  perform t.falla($q$select public.sa_listar()$q$, 'docente no puede sa_listar');
  perform t.falla($q$select public.sa_crear('Colegio Invalido')$q$, 'docente no puede sa_crear');
  perform t.falla($q$select public.sa_renombrar('aaaaaaaa-0000-0000-0000-000000000001', 'Nuevo Nombre')$q$, 'docente no puede sa_renombrar');
  perform t.falla($q$select public.sa_entrar('bbbbbbbb-0000-0000-0000-000000000001')$q$, 'docente no puede sa_entrar');
  perform t.falla($q$select public.sa_asignar_admin('aaaaaaaa-0000-0000-0000-000000000001', 'nuevo_admin@test.pe')$q$, 'docente no puede sa_asignar_admin');
  perform t.root();

  execute 'set local role anon';
  perform t.falla($q$select public.sa_listar()$q$, 'anon no puede sa_listar');
  perform t.falla($q$select public.sa_crear('Colegio Invalido')$q$, 'anon no puede sa_crear');
  perform t.falla($q$select public.sa_renombrar('aaaaaaaa-0000-0000-0000-000000000001', 'Nuevo Nombre')$q$, 'anon no puede sa_renombrar');
  perform t.falla($q$select public.sa_entrar('bbbbbbbb-0000-0000-0000-000000000001')$q$, 'anon no puede sa_entrar');
  perform t.falla($q$select public.sa_asignar_admin('aaaaaaaa-0000-0000-0000-000000000001', 'nuevo_admin@test.pe')$q$, 'anon no puede sa_asignar_admin');
  perform t.root();

  -- (b) Insert into superadmins como root de un usuario existente
  insert into superadmins (user_id) values ('00000000-0000-0000-0000-0000000000a1')
  on conflict (user_id) do nothing;

  perform t.act('00000000-0000-0000-0000-0000000000a1');
  -- sa_listar devuelve >= 2 colegios
  r := public.sa_listar();
  perform t.eq((json_array_length(r) >= 2)::text, 'true', 'sa_listar devuelve >= 2 colegios');

  -- sa_crear autogenera un código de 8 caracteres
  r_creado := public.sa_crear('Instituto Tres');
  cid_nuevo := (r_creado->>'id')::uuid;
  cod_generado := r_creado->>'codigo_registro';
  perform t.eq(length(cod_generado)::text, '8', 'sa_crear autogenera codigo de 8 caracteres');

  -- nombre de 2 caracteres falla
  perform t.falla($q$select public.sa_crear('AB')$q$, 'nombre de 2 caracteres falla');

  -- código duplicado falla
  perform t.falla(format($q$select public.sa_crear('Colegio Cuatro', '%s')$q$, cod_generado), 'codigo duplicado falla');

  -- código inválido falla
  perform t.falla($q$select public.sa_crear('Colegio Cinco', 'ABC')$q$, 'codigo menor a 6 caracteres falla');
  perform t.falla($q$select public.sa_crear('Colegio Seis', 'CODIGO_CON_GUION')$q$, 'codigo con caracter no alfanumerico falla');

  -- sa_renombrar cambia el nombre (comprobación como root)
  perform public.sa_renombrar(cid_nuevo, 'Instituto Tres Renombrado');
  perform t.root();
  perform t.eq((select nombre from colegios where id = cid_nuevo), 'Instituto Tres Renombrado', 'sa_renombrar cambia el nombre');
  perform t.act('00000000-0000-0000-0000-0000000000a1');

  -- (c) sa_entrar cambia public.mi_colegio() del superadmin
  perform public.sa_entrar('bbbbbbbb-0000-0000-0000-000000000001');
  perform t.eq(public.mi_colegio()::text, 'bbbbbbbb-0000-0000-0000-000000000001', 'sa_entrar cambia public.mi_colegio()');
  perform t.root();
  perform t.eq((select colegio_id::text from perfiles where id = '00000000-0000-0000-0000-0000000000a1'), 'bbbbbbbb-0000-0000-0000-000000000001', 'sa_entrar persiste colegio_id en perfiles');
  perform t.act('00000000-0000-0000-0000-0000000000a1');

  -- no permite un colegio inactivo (usa sa_activar(id, false))
  perform public.sa_activar(cid_nuevo, false);
  perform t.root();
  perform t.eq((select activo::text from colegios where id = cid_nuevo), 'false', 'sa_activar desactiva colegio');
  perform t.act('00000000-0000-0000-0000-0000000000a1');

  perform t.falla(format($q$select public.sa_entrar('%s')$q$, cid_nuevo), 'sa_entrar a colegio inactivo falla');

  perform public.sa_activar(cid_nuevo, true);
  perform t.root();
  perform t.eq((select activo::text from colegios where id = cid_nuevo), 'true', 'sa_activar reactiva colegio');
  perform t.act('00000000-0000-0000-0000-0000000000a1');

  -- (d) sa_asignar_admin: correo inexistente falla; correo válido deja rol 'Administrador' en el colegio
  perform t.falla(format($q$select public.sa_asignar_admin('%s', 'no_existe_cuenta@correo.com')$q$, cid_nuevo), 'sa_asignar_admin con correo inexistente falla');
  perform public.sa_asignar_admin(cid_nuevo, 'nuevo_admin@test.pe');
  perform t.root();
  perform t.eq((select rol from perfiles where id = '00000000-0000-0000-0000-0000000000a3' and colegio_id = cid_nuevo), 'Administrador', 'sa_asignar_admin asigna rol Administrador en el colegio');

  -- (e) crear_instituto NO es ejecutable por authenticated (t.falla) pero sí por root
  perform t.act('00000000-0000-0000-0000-0000000000a1');
  perform t.falla($q$select public.crear_instituto('Instituto Prohibido', 'admin_crear_inst@test.pe')$q$, 'crear_instituto no ejecutable por authenticated');
  perform t.root();

  r_root := public.crear_instituto('Instituto Creado Root', 'admin_crear_inst@test.pe', 'ROOT1234');
  cid_root := (r_root->>'id')::uuid;
  perform t.eq((select nombre from colegios where id = cid_root), 'Instituto Creado Root', 'crear_instituto crea colegio desde root');
  perform t.eq((select rol from perfiles where id = '00000000-0000-0000-0000-0000000000a4' and colegio_id = cid_root), 'Administrador', 'crear_instituto asigna admin desde root');

  -- (f) un admin normal SÍ puede renombrar SU propio instituto con update directo sobre colegios
  perform t.act('00000000-0000-0000-0000-0000000000a2');
  perform t.eq(t.dml($q$update colegios set nombre = 'Instituto B Renombrado' where id = 'bbbbbbbb-0000-0000-0000-000000000001'$q$)::text, '1', 'admin normal renombra su propio colegio con update directo');
  perform t.eq(t.dml($q$update colegios set nombre = 'Hack A' where id = 'aaaaaaaa-0000-0000-0000-000000000001'$q$)::text, '0', 'admin normal no renombra otro colegio con update directo');
  perform t.root();
  perform t.eq((select nombre from colegios where id = 'bbbbbbbb-0000-0000-0000-000000000001'), 'Instituto B Renombrado', 'admin normal renombra su propio colegio');
end $$;

select 'TODAS LAS PRUEBAS DE SEGURIDAD PASARON' as resultado;
