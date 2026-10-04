# Operación: respaldos, pausa del plan Free y recuperación

## Pausa por inactividad (plan Free)
Supabase pausa el proyecto tras ~7 días sin actividad. El workflow `keepalive.yml` llama cada 3 días a `hora_servidor` (sin datos personales) para evitarlo. Compruébalo en *Actions → Mantener Supabase activo*. Si falla, reactiva el proyecto desde el panel de Supabase.
**Recomendado para uso real:** plan **Pro** (copias diarias automáticas, sin pausas).

## Respaldos
- **Sistema → Respaldo**: descarga un JSON con todas las tablas del instituto. Hazlo **semanalmente** y guárdalo fuera del equipo. Contiene datos personales: trátalo como confidencial.
- Con plan Pro, Supabase además conserva copias diarias (restauración desde el panel).

## Sin internet varios días
Exporta cada día un **respaldo offline** (`.rabackup`) y, al volver la conexión, impórtalo: queda en las fechas originales. Ver [RESPALDO-OFFLINE.md](RESPALDO-OFFLINE.md).

## Restauración
1. Crea un proyecto nuevo y aplica el esquema y las migraciones de `supabase/` en orden.
2. Importa las tablas del JSON de respaldo desde el SQL Editor.
3. Actualiza `URL_PRODUCCION` / `KEY_PRODUCCION` en `assets/js/config.js` y publica: las apps (también las APK) toman el cambio solas.

## Hora y registros sin internet
El teléfono guarda las asistencias localmente y las envía al volver la red (sin duplicar). La hora proviene del servidor, así que cambiar la hora del teléfono no altera los registros.
