# Diseño técnico del plugin ExtraCrates

Este documento describe cómo implementar el plugin solicitado: un sistema de crates con GUI completa, animaciones tipo FIFA y compatibilidad con resourcepacks. No se incluye código Java, pero sí la organización sugerida, flujos de datos y configuración.

## Objetivos clave
- GUI para crear, editar y previsualizar crates y recompensas sin tocar archivos manualmente.
- Animaciones tipo cinemática con cámara suave sobre una ruta configurada (estilo apertura de sobres de FIFA).
- Soporte completo para resourcepacks: modelos, texturas, animaciones e imágenes personalizadas.
- Recompensas flotantes con holograma coloreado y aislamiento entre jugadores concurrentes.
- Comandos y permisos exhaustivos para cada acción.
- Apertura sin “caja física”: solo se muestra la recompensa flotando y su cinemática.

## Arquitectura propuesta
- **Módulo Core**: carga configs, gestiona crates, recompensa y pool de animaciones.
- **Módulo GUI**: builder de menús con vistas (lista de crates, editor de crate, editor de recompensas, vista previa de cutscene).
- **Módulo Cutscene**: interpola rutas de cámara y gestiona armor stands usados como cámara y la lógica de spectator + fake equip.
- **Módulo ResourcePack**: API para mapear modelos personalizados a ítems y para cargar metadatos de animación/partículas.
- **Módulo Runtime Session**: aísla la sesión de apertura de cada jugador para evitar fugas entre usuarios concurrentes.
- **Módulo Comandos/Permisos**: registra comandos con subcomandos y auto-completado, junto a permisos específicos.
- **Módulo Hologramas**: interfaz para proveedores (decides si usas displays nativos o integra HolographicDisplays).
- **Módulo Render de Recompensa**: ItemDisplay/ArmorStand con rotación y animación del ítem.

### Modelos de datos
- `CrateDefinition`: id, nombre, tipo (normal, llave, evento, temporada, caja misteriosa), requisitos, animación, tabla de recompensas, ubicaciones de aparición.
- `Reward`: id, nombre visible, peso, comandos, ítems, modelos personalizados, probabilidades, mensajes, partículas, sonidos, duración flotando, animaciones locales.
- `CutscenePath`: puntos de control (XYZ + yaw/pitch), duración total, interpolación lineal uniforme (misma velocidad), suavizado (catmull-rom), partículas de guía y previsualización en GUI.
- `SessionContext`: jugador, crate, ruta, armor stand de cámara, holograma, ítem flotante, estado (progreso, cancelaciones, timeouts).

## Flujo de apertura de crate (sin caja física)
1. El jugador abre la crate desde GUI o con comando. Se crea un `SessionContext` aislado.
2. Se prepara la ruta de cámara: se instancia un armor stand invisible que seguirá el `CutscenePath` con velocidad constante.
3. El jugador pasa a modo espectador del armor stand. Se aplica fake equip de calabaza tallada (modelo en resourcepack) y `GENERIC_MOVEMENT_SPEED` -10.
4. El armor stand sigue la ruta con interpolación suave (tween lineal uniforme). Partículas opcionales marcan la trayectoria.
5. Al final de la ruta, aparece la recompensa flotando (ArmorStand o ItemDisplay) con holograma de nombre coloreado.
6. Se ejecutan comandos/acciones de recompensa. Se retira el fake equip y el modificador de velocidad. Se restaura la cámara.
7. El `SessionContext` se limpia automáticamente; se asegura que otros jugadores no vean hologramas ni ítems ajenos.

## GUI propuesta
- **/crate gui**: menú principal para listar crates, crear nuevas, clonar o borrar.
- **Editor de crate**
  - Nombre, tipo, llave requerida, cooldown, coste, requerimientos de permisos, límites por jugador y por día.
  - Configuración de animación: selección de ruta, velocidad, partículas, modelo del casco (resourcepack), sonido.
  - Ubicaciones de aparición y posición de recompensa: coordenadas exactas, offset relativo, mundo y orientación.
  - Flags avanzadas: bloqueo de movimiento, ocultar HUD, ocultar otros jugadores, pausa en desconexión.
  - Previsualización en vivo de la cutscene y de la recompensa final.
- **Editor de recompensas**
- Tabla de pesos (chance), vista previa de ítem/modelo, comandos, mensajes, cantidad, títulos, partículas personalizadas.
  - Botón de "añadir animación local" (por ejemplo, giro del ítem flotante o pulsos de escala).
- Soporte a imágenes en mapas o displays (resourcepack) y partículas propias.
- **Editor de rutas**
  - Modo de puntos: clic en bloques para añadir puntos de control; cada punto almacena posición y orientación.
  - Velocidad uniforme con indicador de duración total; vista previa fantasma con armor stand y partículas.
  - Opción de guardar y exportar rutas a archivo o clipboard.

## Configuración en archivos
- `config.yml`: opciones globales, ajustes de hologramas, partículas por defecto, duración de cutscenes, aislamiento de sesiones.
- `crates.yml`: definición de crates y referencia a rutas y pools de recompensas.
- `rewards.yml`: definición reutilizable de recompensas.
- `paths.yml`: rutas de cutscenes para reutilizar en múltiples crates.

Se incluyen ejemplos mínimos en `config/examples/`.

### Modos de apertura (`open-mode`)
Valores válidos en `crates.yml` para decidir si se entrega recompensa o si se requiere llave/economía:
- `reward-only`: modo estándar. Entrega recompensa al final. Si el `type` es `keyed`, requiere llave.
- `preview-only`: solo reproduce la cutscene y muestra el ítem flotante, sin entregar recompensa ni consumir llave/economía.
- `key-required`: requiere y consume llave (ignora el coste de economía).
- `economy-required`: requiere saldo de economía (usa el campo `cost`).

## Concurrencia y aislamiento
- Cada apertura crea IDs únicos para hologramas e ítems de recompensa usando `NamespacedKey`.
- Se usan canales de paquetes o `adventure` para enviar entidades fantasma solo al jugador involucrado (Packets/ProtocolLib). Al resto se les ocultan.
- El scheduler asocia tareas al jugador y las cancela si desconecta.

## Cutscene y fake equip
- El armor stand se crea con marcador y sin colisión. El jugador se pone en espectador apuntando al armor stand.
- Fake equip: aplicar `EquipmentSlot.HEAD` vía paquetes, guardando el casco anterior y restituyéndolo al terminar; si no tenía, no se altera.
- Modificador de velocidad: `Attribute.GENERIC_MOVEMENT_SPEED` con UUID propio para poder quitarlo al finalizar o al desconectar.
- Sincronización de cámara: la ruta usa distancia total y un step fijo para garantizar velocidad constante sin aceleraciones.

## Comandos y permisos (sugerencia)
- `/crate gui` – `extracrates.gui`
- `/crate give <jugador> <crate> [cantidad]` – `extracrates.give`
- `/crate key <jugador> <crate> [cantidad]` – `extracrates.key`
- `/crate preview <crate>` – `extracrates.preview`
- `/crate reload` – `extracrates.reload`
- `/crate route editor` – `extracrates.route.editor`
- `/crate reward editor` – `extracrates.reward.editor`
- `/crate open <crate>` – `extracrates.open`
- `/crate setlocation <crate> <anchor|reward|start>` – `extracrates.location`
- `/crate cutscene test <path>` – `extracrates.cutscene.test`
- Permisos granulares para editar, borrar, clonar, ajustar rutas, cooldowns, economía, etc.

## Compatibilidad con resourcepack
- Mapear modelos personalizados mediante CustomModelData y animaciones definidas en el pack.
- Soporte para texturas dinámicas: ítems, imágenes en mapas, texturas de holograma (font glyphs).
- Configurable por crate: modelo de llave, modelo de casco de cutscene, modelo de recompensa flotante.
- Imagen/animación para “overlay” de cutscene (calabaza tallada con líneas de trayectoria).

## Optimización
- Reutilizar armor stands e ItemDisplay con pools de objetos para evitar picos de entidades.
- Uso de interpolaciones programadas y tareas asíncronas para cargas de configuración.
- Filtrado de espectadores por paquete para que cada jugador vea solo su animación y holograma.
- Caché de rutas interpoladas para reducir cálculos cuando múltiples jugadores usan la misma cutscene.

## Extensiones futuras
- Soporte de "battle pass" o temporadas de crates.
- Integración con economías y placeholderAPI.
- API pública para que otros plugins disparen cutscenes y recompensas personalizadas.
