# ExtraCrates

Base inicial para un plugin de Paper 1.21 orientado a un sistema complejo de cajas.
La estructura está preparada para escalar y adaptarse a proxies en el futuro.

## Configuración
En `config.yml` puedes definir cómo se muestran los mensajes de recompensas con `ui-mode`:

| Valor | Descripción |
| --- | --- |
| `bossbar` | Muestra el mensaje como BossBar (si no hay soporte, hace fallback a ActionBar). |
| `actionbar` | Envía el mensaje por ActionBar. |
| `both` | Usa BossBar + ActionBar simultáneamente (con fallback a ActionBar si BossBar no está disponible). |
| `none` | Desactiva la UI de mensajes. |

## Características incluidas
- `plugin.yml` configurado con nombre **ExtraCrates** y paquete base `me.savaduki.extracrates`.
- Clase principal `ExtraCratesPlugin` con comando `/extracrates` para verificar el estado.
- Registros iniciales `CrateRegistry` y `RewardRegistry` para futuros datos y lógica.
- Configuración Maven lista para construir un JAR sombreado (shade) compatible con Paper 1.21.

## Requisitos
- Java 21
- Maven 3.9+

## Cómo compilar
```bash
mvn clean package
```
ProtocolLib se descarga desde el repositorio público de dmulloy2 configurado en el `pom.xml`.
Si Maven mantiene en caché una resolución fallida, fuerza la actualización de dependencias con:
```bash
mvn -U clean package
```
El artefacto resultante se generará en `target/extracrates-<version>.jar`.

## Placeholders de idioma
Los mensajes en `lang/*.yml` soportan los siguientes placeholders globales (se rellenan automáticamente cuando la información está disponible):
- `%player%`: nombre del jugador.
- `%crate_id%`: ID de la crate.
- `%crate_name%`: nombre visible de la crate.
- `%reward%`: nombre visible de la recompensa.
- `%cooldown%`: segundos restantes de cooldown.

## Próximos pasos sugeridos
- Implementar carga de crates y recompensas desde `config.yml` o archivos dedicados.
- Añadir persistencia y sincronización con proxy para redes.
- Crear comandos administrativos para gestionar crates y llaves.
