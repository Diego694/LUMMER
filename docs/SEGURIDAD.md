# Seguridad

- **Roles:** administrador (todo), docente (consulta y registra asistencia), coordinador (solo su carrera). Aplicados con RLS restrictivas en la base; la interfaz solo oculta botones.
- **QR dinámico:** el carnet muestra `código.ventana.firma` (HMAC-SHA256 con un secreto por alumno, ventana de 30 s). Una captura de pantalla caduca. Se controla con `CONFIG.QR_MODO`: `"off"`, `"opcional"` o `"obligatorio"`. NFC: un tag con texto estático es clonable; para riesgo alto usa solo el QR dinámico.
- **Registros falsos:** límite de intentos, código de instituto y aprobación manual de cada estudiante. CAPTCHA opcional (Cloudflare Turnstile): pon la *site key* en `CONFIG.TURNSTILE_SITEKEY` y activa la verificación en Supabase → Auth → Attack Protection.
- **Recuperar contraseña:** usa el correo de Supabase Auth. Configura en Supabase → Auth → URL Configuration el *Site URL* y los *Redirect URLs* del portal, y un **SMTP propio** (el correo por defecto tiene un límite muy bajo).
- **Claves:** solo la publishable/anon key va en el navegador. Nunca subas la `service_role` (`scripts/check.py` lo detecta).
- **Cabeceras:** CSP y demás en `security-headers.conf` (Docker/nginx).
