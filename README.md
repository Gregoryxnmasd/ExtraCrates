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

## Despliegue con sincronización (Redis + Postgres)
Para habilitar sincronización entre servidores (cooldowns globales, consumo de llaves e historial), configura `config.yml` con:

- `sync.enabled: true`
- `sync.mode`: `eventual` (baja latencia) o `strong` (prioriza consistencia)
- `sync.server-id`: identificador único por servidor
- `sync.redis.*`: host/puerto/clave/canal para eventos en tiempo real
- `sync.postgres.*`: host/puerto/credenciales/schema para persistencia global

Ejemplo rápido:

```yaml
sync:
  enabled: true
  mode: "eventual"
  server-id: "lobby-01"
  redis:
    host: "redis.local"
    port: 6379
    password: ""
    channel: "extracrates:sync"
  postgres:
    host: "postgres.local"
    port: 5432
    database: "extracrates"
    user: "extracrates"
    password: "secret"
    schema: "public"
```

Usa `/crate sync status` para validar el estado, `/crate sync reload` para recargar configuración y `/crate sync flush` para limpiar caches locales.
