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

## Ingreso y salida automáticos (según el horario)
Todo sale del **horario** configurado en *Gestión → Calendario y horarios* (general o por carrera). Ejemplo para un instituto de **tarde/noche, de 14:00 a 20:00** (botón «Usar horario tarde/noche»):

| Hora | Qué pasa en el quiosco |
|---|---|
| Antes de 13:00 | «Aún no es hora de ingreso» (no registra) |
| 13:00 – 14:10 | **Puntual** (14:00 + 10 min de tolerancia) |
| 14:11 – 19:00 | **Tardanza** |
| Después de 19:00 | «El ingreso ya cerró» (no registra; el docente puede registrarlo a mano) |
| Segundo pase antes de 2 h | «Ya registraste tu ingreso. Podrás marcar tu salida desde las HH:MM» |
| Segundo pase pasadas **2 h** del ingreso | **Salida** («¡Hasta luego!»), una sola vez |

- **Quien sale temprano también puede marcar su salida, pero solo pasadas 2 horas desde su ingreso** (minutos configurables), para evitar fugas: si ingresó a las 14:30, podrá salir desde las 16:30, aunque las clases terminen a las 20:00.
- Si necesita salir antes, **habla con el docente**, que puede registrar la salida a mano en *Registro por QR → «Estoy registrando: Salida»* (el docente no tiene la restricción).
- También se puede fijar el quiosco a *solo ingreso* o *solo salida*.
La salida se muestra en el historial del alumno, en el reporte y en el portal de apoderados.

## Seguridad y robustez
- **Solo se sale con el PIN.** Quien lo olvide puede salir con la contraseña de la cuenta con la que se inició sesión.
- Botón **⛶ Pantalla completa / ⤡ Modo normal** arriba a la derecha (junto al candado): alterna sin salir del quiosco.
- Sin menús ni navegación; la pantalla **no se apaga** (la app Android lo impide y la web usa Wake Lock).
- **Funciona sin internet**: cada registro queda en la cola del equipo y se envía solo al volver la conexión (sin duplicar).
- Si el aparato se **reinicia o se cierra la app**, el quiosco **vuelve a abrirse solo** al iniciar sesión.
- El QR dinámico (`QR_MODO`) se valida igual que en el escáner del docente: una captura de pantalla caduca.
- Para el aviso de «sin conexión» y las pruebas de datos: el quiosco registra con origen `quiosco`.

## Recomendaciones
Tablet con soporte fijo, cargador conectado, brillo medio y la cuenta del **docente de turno** (no la del administrador). Probar una semana con un salón antes de generalizarlo (ver `PILOTO.md`).
