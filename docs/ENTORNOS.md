# Entornos y dominio propio

## Entorno de pruebas (staging)
1. Crea otro proyecto Supabase gratuito y aplícale el esquema y migraciones de `supabase/`.
2. Abre `entorno.html`, pega URL y publishable key, y pulsa *Usar este entorno*. Solo ese navegador apunta a pruebas y muestra una franja roja **ENTORNO DE PRUEBAS**.
3. *Volver a producción* lo restablece. Prueba cada cambio aquí antes de publicarlo.

## URL fija / dominio propio
Las APK abren la URL publicada (`APP_URL`). Para que no dependa de `github.io`:
1. Compra un dominio (p. ej. `asistencia.tuinstituto.edu.pe`).
2. En GitHub: *Settings → Pages → Custom domain* y crea un registro **CNAME** hacia `<usuario>.github.io`.
3. Recompila las APK con `-PAPP_URL=https://asistencia.tuinstituto.edu.pe/`.
