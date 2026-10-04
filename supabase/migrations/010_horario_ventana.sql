-- =====================================================================
--  010 · Horario con ventana de ingreso y permanencia mínima
--  · ingreso_desde / ingreso_hasta: entre qué horas se puede marcar el INGRESO en el quiosco (antes y después se rechaza).
--  · permanencia_min: minutos mínimos desde el ingreso para poder marcar la SALIDA (evita fugas; por defecto 2 horas).
--  Lo ya existente: hora_ingreso (inicio de clases) + tolerancia_min = límite de puntualidad; hora_salida = fin de clases.
--  Migración ADITIVA y re-ejecutable.
-- =====================================================================
alter table public.horarios
  add column if not exists ingreso_desde text check (ingreso_desde is null or ingreso_desde ~ '^[0-2][0-9]:[0-5][0-9]$'),
  add column if not exists ingreso_hasta text check (ingreso_hasta is null or ingreso_hasta ~ '^[0-2][0-9]:[0-5][0-9]$'),
  add column if not exists permanencia_min int not null default 120 check (permanencia_min between 0 and 600);
