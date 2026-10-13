# QR rotativo de ingreso al instituto (migración 022)

Documentación del módulo de asistencia institucional con código QR dinámico de ingreso y modo quiosco proyectado.

---

## 1. Funcionamiento

El sistema provee dos modalidades operativas en el **Modo Quiosco** (`assets/js/pages/quiosco.js`):

1. **Cámara (frontal o trasera)**: La tablet o teléfono fijo en recepción escanea los carnets de los estudiantes (QR estático o dinámico) o tags NFC.
2. **QR Proyectado en pantalla**: El quiosco abre una sesión criptográfica en el servidor (`qr_ingreso_abrir`) y proyecta un código QR de alta resolución que rota automáticamente cada **15 o 30 segundos**. Los estudiantes abren su app LUMMER y escanean la pantalla del quiosco para registrar su asistencia individualmente.

### Alternancia en vivo
Dentro del overlay del quiosco, el botón conmutador **«Mostrar QR» / «Cámara frontal»** permite cambiar entre ambas vistas sin salir del quiosco ni solicitar PIN:
- Al pasar a **Mostrar QR**: se detiene la cámara web, se solicita sesión al servidor y se inicia el renderizado rotativo con barra de cuenta atrás.
- Al volver a **Cámara frontal**: se limpian los temporizadores, se cierra la sesión en el servidor (`qr_ingreso_cerrar`, best-effort) y se reanuda la cámara.
- Al salir del quiosco con el PIN de seguridad: se cancelan todos los temporizadores y se finaliza la sesión activa.

---

## 2. Formato del Token

```text
LI.<sesion32hex>.<ventana>.<firma12>
```

- **Prefijo `LI`**: Identificador exclusivo para ingreso al instituto (diferente de `LQ` para cursos).
- **`sesion32hex`**: Identificador UUID de la sesión sin guiones (32 caracteres hexadecimales en minúscula).
- **`ventana`**: Entero decimal calculado como `floor(epoch_segundos / intervalo_seg)`.
- **`firma12`**: Primeros 12 caracteres hexadecimales de `sha256(secreto || ':' || sesion32hex || ':' || ventana)`. El secreto reside únicamente en la base de datos y nunca se transmite al cliente.

---

## 3. Base de Datos y RPCs (`022_qr_ingreso.sql`)

- **`sesiones_qr_ingreso`**: Almacena las sesiones de QR abiertas con su intervalo (`10` a `60` segundos, por defecto `30` o `15`), expiración y fecha de cierre. RLS habilitado sin políticas (acceso exclusivo mediante funciones `SECURITY DEFINER`).
- **`qr_ingreso_abrir(p_segundos int, p_minutos int)`**: Valida pertenencia y rol del personal institucional, cierra sesiones anteriores del colegio y devuelve `{ sesion, intervalo, expira_en }`.
- **`qr_ingreso_token(p_sesion uuid)`**: Genera el token para la ventana actual y calcula los segundos restantes hasta la siguiente rotación. Error controlado si la sesión fue cerrada o expiró.
- **`qr_ingreso_cerrar(p_sesion uuid)`**: Marca la sesión como cerrada (`cerrada_en = now()`).
- **`marcar_asistencia_qr_ingreso(p_token text)`**: Valida autenticación del estudiante (estado `ACTIVO` y aprobado), verifica firma y tolerancia de ventana (|Δw| ≤ 1), comprueba horario institucional (`ingreso_desde`/`ingreso_hasta` y límite de puntualidad para marcar `tarde`) e inserta en `public.asistencias` con origen `'qr-ingreso'`.
