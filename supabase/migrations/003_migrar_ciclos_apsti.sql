-- =====================================================================
--  003 · Migración de datos (ejecutada una vez, 4-oct-2026)
--  Instituto con id ddfdc750-755c-423c-b09c-7aa03af47a2c:
--    * carrera  "APSTI 4TO CICLO"            → "APSTI"
--    * ciclos   "APSTI 4TO CICLO I … VI"      → "APSTI · I CICLO … APSTI · VI CICLO"
--    * alumnos  con esos nombres              → ídem
--    * instituto "Jose Andres Razuri"         → "Jorge Desmaison Seminario"
--  Solo toca filas que cumplen EXACTAMENTE el formato antiguo; re-ejecutarla no cambia nada más.
--  Reversa (por si hiciera falta): ver el final del archivo.
-- =====================================================================
begin;

update public.niveles
   set nombre = 'APSTI'
 where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c' and nombre = 'APSTI 4TO CICLO';

update public.grados
   set nivel = 'APSTI',
       nombre = 'APSTI · ' || substring(nombre from 'CICLO (I|II|III|IV|V|VI)$') || ' CICLO'
 where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c'
   and nivel = 'APSTI 4TO CICLO'
   and nombre ~ '^APSTI 4TO CICLO (I|II|III|IV|V|VI)$';

update public.alumnos
   set nivel = 'APSTI',
       grado = 'APSTI · ' || substring(grado from 'CICLO (I|II|III|IV|V|VI)$') || ' CICLO'
 where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c'
   and nivel = 'APSTI 4TO CICLO'
   and grado ~ '^APSTI 4TO CICLO (I|II|III|IV|V|VI)$';

update public.colegios
   set nombre = 'Jorge Desmaison Seminario'
 where id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c' and nombre = 'Jose Andres Razuri';

commit;

-- Comprobación (debe mostrar 1 carrera APSTI, 6 ciclos "APSTI · X CICLO" y el instituto renombrado):
-- select 'niveles' t, nombre from public.niveles where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c'
-- union all select 'grados', nivel || ' | ' || nombre from public.grados where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c'
-- union all select 'alumnos', nombre || ' | ' || nivel || ' | ' || grado from public.alumnos where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c'
-- union all select 'colegios', nombre from public.colegios where id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c';

-- REVERSA:
--   update public.niveles set nombre = 'APSTI 4TO CICLO' where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c' and nombre = 'APSTI';
--   update public.grados  set nivel = 'APSTI 4TO CICLO', nombre = 'APSTI 4TO CICLO ' || substring(nombre from '· (I|II|III|IV|V|VI) CICLO$') where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c' and nivel = 'APSTI';
--   update public.alumnos set nivel = 'APSTI 4TO CICLO', grado = 'APSTI 4TO CICLO ' || substring(grado from '· (I|II|III|IV|V|VI) CICLO$') where colegio_id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c' and nivel = 'APSTI';
--   update public.colegios set nombre = 'Jose Andres Razuri' where id = 'ddfdc750-755c-423c-b09c-7aa03af47a2c';
