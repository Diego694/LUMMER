# Programa de PC (Windows)

Un solo programa (`Registro-Academico-PC-Portable.exe`, o el instalador) para docentes y administradores que quieran trabajar en el computador aunque no haya internet estable o Supabase esté saturado.

**Descarga:** [Releases → pc-latest](https://github.com/Diego694/LUMMER/releases/tag/pc-latest). El portable no necesita instalación; el instalador crea acceso directo y desinstalador.

## Dos modos
| | Online | Local |
|---|---|---|
| Base de datos | Supabase (en línea) | Propia de este equipo |
| Acceso | Correo y contraseña | Sin cuenta |
| Internet | Necesario | No necesario |
| Primera vez | Tus datos de siempre | Vacía: crea carreras, ciclos y alumnos |

Cambia de modo con el selector **Modo online | Modo local** de la pantalla de inicio o con el botón del menú lateral. **Siempre pide confirmación.** Cada modo guarda sus datos por separado: **al cambiar no se borra nada**, y lo que ingresaste en local sigue ahí cuando vuelvas.

## Datos y respaldo
Los datos locales viven en el perfil del programa (`%APPDATA%\LUMMER`). Haz copias con **Sistema → Respaldo** (JSON). Los datos locales **no se sincronizan solos con Supabase**: son una base aparte.

## Qué incluye sin internet
La web completa y sus librerías (gráficos, QR, lector de cámara, PDF, CSV). Los avisos por WhatsApp abren el navegador y sí requieren internet.

## Pruebas automáticas
El workflow *Programa de PC (Windows)* ejecuta el programa **sin red** y verifica: carga, librerías, selector, entrada en modo local, vuelta a online y conservación de los datos locales; luego repite la prueba con el ejecutable empaquetado.

## Compilar a mano
```bash
cd desktop
npm install
npm run dist     # genera desktop/dist/*.exe
```
**SmartScreen:** el .exe no está firmado digitalmente, así que Windows puede mostrar «Windows protegió su PC»: *Más información → Ejecutar de todas formas*. Firmarlo exige un certificado de pago.

**Actualizaciones:** el .exe lleva la web incluida; para recibir una versión nueva, descarga de nuevo el .exe de la release.
