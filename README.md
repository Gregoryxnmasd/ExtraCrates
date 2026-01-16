# ExtraCrates

Diseño de un plugin de crates con interfaz gráfica avanzada, animaciones tipo cutscene y compatibilidad con resourcepacks.

Esta base describe todas las características esperadas, comandos y permisos para implementar un sistema de crates altamente configurable.

- Revisa [docs/PluginDesign.md](docs/PluginDesign.md) para los detalles técnicos y flujos de trabajo.
- Consulta `config/examples/` para ejemplos de configuración inicial (crates, recompensas, rutas y ajustes globales).

## Modos de apertura (`open-mode`)
Las crates pueden definir cómo se comporta la apertura:

- `reward-only`: entrega la recompensa al instante sin cutscene.
- `full`: reproduce la cutscene y luego entrega la recompensa.
- `preview-only`: reproduce la cutscene pero no entrega recompensa.

El valor se define en cada crate usando `open-mode` y es sensible a texto en minúsculas.

## Gradle en IntelliJ (sin wrapper jar en Git)
Por restricciones de GitHub, el repositorio no incluye `gradle/wrapper/gradle-wrapper.jar`.
Para regenerarlo localmente:

- Linux/macOS: `./scripts/bootstrap-wrapper.sh`
- Windows: `scripts\bootstrap-wrapper.bat`

Esto ejecuta `gradle wrapper` y crea el `gradle-wrapper.jar` necesario para que IntelliJ detecte el proyecto como Gradle.
