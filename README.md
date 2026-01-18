# ExtraCrates

Base inicial para un plugin de Paper 1.21 orientado a un sistema complejo de cajas.
La estructura está preparada para escalar y adaptarse a proxies en el futuro.

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

## API pública (Bukkit services)
El servicio `ExtraCratesApi` expone métodos para consultar el estado de las cutscenes activas.
Para compatibilidad hacia atrás, los métodos nuevos tienen implementaciones por defecto
que devuelven valores seguros cuando el plugin que integra aún no los implementa.

```java
ExtraCratesApi api = ...;

boolean active = api.hasActiveSession(player);
Reward reward = api.getCurrentReward(player); // null si no hay sesión activa
int remainingTicks = api.getRemainingTicks(player); // -1 si no hay sesión activa
```

## Próximos pasos sugeridos
- Implementar carga de crates y recompensas desde `config.yml` o archivos dedicados.
- Añadir persistencia y sincronización con proxy para redes.
- Crear comandos administrativos para gestionar crates y llaves.
