# ExtraCrates

Diseño de un plugin de crates con interfaz gráfica avanzada, animaciones tipo cutscene y compatibilidad con resourcepacks.

Esta base describe todas las características esperadas, comandos y permisos para implementar un sistema de crates altamente configurable.

- Revisa [docs/PluginDesign.md](docs/PluginDesign.md) para los detalles técnicos y flujos de trabajo.
- Consulta `config/examples/` para ejemplos de configuración inicial (crates, recompensas, rutas y ajustes globales).

## Gradle en IntelliJ (sin wrapper jar en Git)
Por restricciones de GitHub, el repositorio no incluye `gradle/wrapper/gradle-wrapper.jar`.
Para regenerarlo localmente:

- Linux/macOS: `./scripts/bootstrap-wrapper.sh`
- Windows: `scripts\bootstrap-wrapper.bat`

Esto ejecuta `gradle wrapper` y crea el `gradle-wrapper.jar` necesario para que IntelliJ detecte el proyecto como Gradle.
