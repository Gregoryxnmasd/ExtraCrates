# ExtraCrates

Diseño de un plugin de crates con interfaz gráfica avanzada, animaciones tipo cutscene y compatibilidad con resourcepacks.

Esta base describe todas las características esperadas, comandos y permisos para implementar un sistema de crates altamente configurable.

- Revisa [docs/PluginDesign.md](docs/PluginDesign.md) para los detalles técnicos y flujos de trabajo.
- Consulta `config/examples/` para ejemplos de configuración inicial (crates, recompensas, rutas y ajustes globales).

## Storage SQL

El módulo `storage` permite persistir cooldowns, llaves y aperturas en una base de datos SQL. Cuando `storage.enabled` es `true`, el plugin usa `SqlStorage` y cambia automáticamente a modo local si la base de datos no responde.

### Configuración

```yml
storage:
  enabled: true
  type: "mysql" # mysql | postgres | mariadb | sqlite
  jdbc-url: "jdbc:mysql://localhost:3306/extracrates"
  username: "root"
  password: "password"
  pool:
    size: 10
    timeout: 30000
```

`pool.timeout` está en milisegundos.

### Migraciones

Las migraciones SQL están en `docs/migrations/`:

- `mysql_mariadb.sql`
- `postgres.sql`
- `sqlite.sql`

### Ejemplos de conexión

- MySQL/MariaDB: `jdbc:mysql://localhost:3306/extracrates`
- PostgreSQL: `jdbc:postgresql://localhost:5432/extracrates`
- SQLite: `jdbc:sqlite:plugins/ExtraCrates/extracrates.db`

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
