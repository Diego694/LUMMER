# Modo quiosco

Una tablet (o teléfono) fija en la puerta donde **cada estudiante muestra su carnet y se registra solo**, sin que el docente escanee uno por uno.

## Cómo usarlo
1. Entra con una cuenta del personal (docente o administrador) en la **app Android**, la **web** o el **programa de PC**.
2. Menú **Registro → Modo quiosco**. Elige qué registra, la cámara (frontal para tablet de pie) y un **PIN de 4 a 6 números** para poder salir.
3. Pulsa **Iniciar modo quiosco**. La pantalla pasa a pantalla completa y se queda así.

## Qué ve el estudiante
- **Acerca tu carnet**: muestra el QR a la cámara (o acerca su tag NFC en la app Android; o un lector USB/Bluetooth).
- Un panel grande con su **foto**, nombre (**apellidos protegidos**), ciclo y hora, y un color:
  verde *¡Bienvenido/a!* (puntual) · ámbar *Llegada tardía* · azul *Ya registraste…* · azul oscuro *¡Hasta luego!* (salida) · rojo error (QR vencido, código desconocido, sin ingreso).
- **Sonido** de confirmación y, si se activa, **saludo por voz** («Bienvenido, Juan»).
- Contadores abajo: ingresos del día, salidas y, si no hay internet, cuántos registros están por enviar.

## Ingreso y salida automáticos
En modo *Ingreso y salida (automático)*: el primer pase del día es el **ingreso**; si vuelve a pasar el carnet **antes de 45 minutos** se toma como repetido (evita salidas por error); pasado ese tiempo es la **salida**, que se guarda una sola vez. También se puede fijar el quiosco a *solo ingreso* o *solo salida*.
La salida se muestra en el historial del alumno, en el reporte y en el portal de apoderados.

## Seguridad y robustez
- **Solo se sale con el PIN.** Quien lo olvide puede salir con la contraseña de la cuenta con la que se inició sesión.
- Sin menús ni navegación; la pantalla **no se apaga** (la app Android lo impide y la web usa Wake Lock).
- **Funciona sin internet**: cada registro queda en la cola del equipo y se envía solo al volver la conexión (sin duplicar).
- Si el aparato se **reinicia o se cierra la app**, el quiosco **vuelve a abrirse solo** al iniciar sesión.
- El QR dinámico (`QR_MODO`) se valida igual que en el escáner del docente: una captura de pantalla caduca.
- Para el aviso de «sin conexión» y las pruebas de datos: el quiosco registra con origen `quiosco`.

## Recomendaciones
Tablet con soporte fijo, cargador conectado, brillo medio y la cuenta del **docente de turno** (no la del administrador). Probar una semana con un salón antes de generalizarlo (ver `PILOTO.md`).
