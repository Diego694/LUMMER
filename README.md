<p align="center"><img src="assets/brand/banner.svg" alt="Registro Académico" width="100%"></p>

<p align="center">
  <a href="https://github.com/Diego694/Sistema-de-control-de-asistencia/actions/workflows/ci.yml"><img alt="CI" src="https://github.com/Diego694/Sistema-de-control-de-asistencia/actions/workflows/ci.yml/badge.svg"></a>
  <img alt="Versión" src="https://img.shields.io/badge/versi%C3%B3n-2.7.0-E8A33D">
  <img alt="Licencia" src="https://img.shields.io/badge/licencia-MIT-16223D">
</p>

<p align="center">
  Sistema de <b>control de asistencia</b> para institutos y escuelas, pensado para zonas con conexión limitada.<br>
  Funciona en el navegador, en el teléfono y en la PC, <b>con o sin internet</b>.
</p>

<p align="center">
  <a href="https://diego694.github.io/Sistema-de-control-de-asistencia/"><b>Abrir la aplicación</b></a> ·
  <a href="docs/GUIA-DE-USO.md">Guía de uso</a> ·
  <a href="docs/README.md">Documentación</a>
</p>

---

## Qué ofrece

|  |  |
|---|---|
| **Registro ágil** | Carnet con QR (que cambia cada 30 s), NFC o código manual. Alerta con foto y apellidos protegidos. |
| **Siempre disponible** | Si se cae internet, registra igual y sincroniza después. La PC puede trabajar con su propia base local. |
| **Visión clara** | Dashboard, reportes mensuales, justificaciones y avisos a apoderados. |
| **Seguro y privado** | Roles por carrera, datos protegidos en la base y derecho del estudiante a eliminar su cuenta. |

## Aplicaciones

| Para quién | Dónde |
|---|---|
| Docentes y administradores | [Web / PWA](https://diego694.github.io/Sistema-de-control-de-asistencia/) · [App Android](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/apk-latest) · [Programa de PC](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/pc-latest) |
| Estudiantes (*Mi Carnet Institucional*) | [Portal web](https://diego694.github.io/Sistema-de-control-de-asistencia/estudiante/) · [App Android](https://github.com/Diego694/Sistema-de-control-de-asistencia/releases/tag/apk-latest) |

## Probarlo en un minuto

```bash
python scripts/serve.py 8080     # abre http://127.0.0.1:8080/?demo=1
```

Entra con `demo@instituto.pe` / `demo1234`. El modo demo guarda los datos solo en tu navegador.

## Más información

Toda la documentación técnica (arquitectura, despliegue, seguridad, privacidad, operación, pruebas) está en el [índice de documentación](docs/README.md). El historial de cambios, en el [Changelog](CHANGELOG.md).

<sub>Hecho con HTML, CSS y JavaScript sin dependencias de compilación · Supabase · Electron · Android WebView · Licencia [MIT](LICENSE)</sub>
