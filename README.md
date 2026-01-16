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
El artefacto resultante se generará en `target/extracrates-<version>.jar`.

## Próximos pasos sugeridos
- Implementar carga de crates y recompensas desde `config.yml` o archivos dedicados.
- Añadir persistencia y sincronización con proxy para redes.
- Crear comandos administrativos para gestionar crates y llaves.
