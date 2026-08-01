# RedUrbana — Arquitectura del Proyecto

> Documento de diseño previo al desarrollo. Sin código de implementación todavía — solo estructura, contratos y flujo.

---

## 1. Principio rector: independencia de la fuente de datos

Toda la app depende de una **interfaz de dominio**, nunca de una API concreta. Hoy podemos no tener ninguna fuente conectada; mañana puede ser la API del GCBA, GTFS Realtime, o un mock local — el resto de la app no se entera.

```
domain/transport/TransportDataProvider.kt   ← contrato único
data/transport/providers/
    GtfsRealtimeProvider.kt                 ← implementación futura
    GcbaOfficialApiProvider.kt              ← implementación futura
    MockTransportProvider.kt                ← implementación para desarrollo/testing
    CompositeTransportProvider.kt           ← combina/prioriza varias fuentes
```

### Contrato (firma, sin implementación)

```kotlin
interface TransportDataProvider {
    fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>>
    fun observeVehiclesInBounds(bounds: LatLngBounds): Flow<List<VehiclePosition>>
    suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails>
    suspend fun getStopsNearby(location: LatLng, radiusMeters: Int): Result<List<Stop>>
    suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>>
    suspend fun getServiceAlerts(routeId: RouteId? = null): Result<List<ServiceAlert>>
    fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore>
    val providerId: String
    val capabilities: ProviderCapabilities   // qué soporta cada fuente (realtime, ETA, alerts, etc.)
}
```

Todos los modelos (`VehiclePosition`, `RouteDetails`, `Stop`, `ArrivalEstimate`, `ServiceAlert`) viven en `domain/model`, son inmutables y **no conocen ningún detalle de red o de un proveedor específico**.

`CompositeTransportProvider` permite fusionar fuentes (ej: posiciones de GTFS-RT + alertas manuales cargadas por RedUrbana) sin que ViewModels lo sepan. La inyección se resuelve vía Hilt con un `@Binds` que apunta a la implementación activa, intercambiable por config/flavor.

Consecuencia práctica: **se puede construir y demostrar toda la app con `MockTransportProvider`** generando colectivos simulados moviéndose por rutas reales, antes de tener ninguna API conectada.

---

## 2. Estructura de módulos (Gradle multi-módulo)

```
RedUrbana/
├── app/                          → ensamblado final, DI graph, Application
├── core/
│   ├── core-ui/                  → design system, componentes Compose, temas
│   ├── core-common/              → Result, dispatchers, extensiones, utils
│   ├── core-database/            → Room, DAOs, entities, migraciones
│   ├── core-network/             → Retrofit base, interceptores, serialización
│   ├── core-location/            → wrapper de FusedLocationProvider
│   └── core-testing/             → fakes y utilidades de test compartidas
├── domain/
│   ├── domain-transport/         → TransportDataProvider, modelos, use cases
│   ├── domain-favorites/
│   ├── domain-alerts/
│   └── domain-user/
├── data/
│   ├── data-transport/           → implementaciones de TransportDataProvider
│   ├── data-favorites/
│   ├── data-alerts/
│   └── data-user/
├── feature/
│   ├── feature-dashboard/
│   ├── feature-map/
│   ├── feature-lines/
│   ├── feature-stops/
│   ├── feature-vehicle-detail/
│   ├── feature-favorites/
│   ├── feature-alerts/
│   ├── feature-history/
│   ├── feature-settings/
│   └── feature-auth/
└── sync/                         → WorkManager, estrategia offline-first
```

Regla de dependencia: `feature → domain → core`. Los `feature` **nunca** importan `data` directamente, solo interfaces de `domain`. Esto es lo que garantiza que cambiar de proveedor de datos no toque una sola pantalla.

---

## 3. Patrón arquitectónico

- **Clean Architecture** por capas (presentation → domain → data)
- **MVVM** en cada feature: `Screen (Compose)` → `ViewModel (StateFlow)` → `UseCase` → `Repository (interfaz en domain)` → `TransportDataProvider`
- **Repository Pattern**: los repos de `domain-*` son interfaces; las implementaciones en `data-*` deciden si leen de Room (caché), de un `TransportDataProvider` o combinan ambos según conectividad (offline-first).
- Estado de UI modelado con `sealed interface UiState { Loading, Success(data), Error, Empty }` por pantalla — nunca booleanos sueltos.

---

## 4. Diseño offline-first

```
TransportDataProvider (red)
        │
        ▼
Repository ── escribe caché ──► Room (core-database)
        │                              │
        └────── si no hay red ─────────┘
                        │
                        ▼
                UI siempre recibe Flow<T>
```

- Room guarda: líneas, paradas, recorridos, favoritos, historial, últimas posiciones conocidas.
- `sync/` usa WorkManager con `NetworkType.CONNECTED` para resincronizar automáticamente al volver la conexión, con backoff exponencial.
- El seguimiento en vivo de un colectivo (`Seguir`) intenta reconectar el `Flow` de posiciones al recuperar red, sin perder el estado de "siguiendo".

---

## 5. Design system — basado en la referencia visual

**Tema:** oscuro por defecto, glassmorphism, esquinas redondeadas grandes (16–24dp), sombras suaves difusas, tipografía geométrica sans-serif con buen contraste de peso (títulos semibold, cuerpo regular).

### Paleta (`core-ui/theme/Color.kt`)

| Token | Uso | Valor aprox. |
|---|---|---|
| `BackgroundPrimary` | fondo general | `#0A0E12` |
| `SurfaceElevated` | tarjetas, panel inferior | `#141A20` con blur/alpha (glass) |
| `SurfaceGlass` | overlays sobre el mapa | blanco 6–10% + blur |
| `AccentGreenPrimary` | CTA principal, estados positivos, "En horario" | `#22C55E` |
| `AccentBlue` | elementos de mapa, líneas de recorrido | `#3B82F6` |
| `AlertRed` | únicamente alertas/errores | `#EF4444` |
| `TextPrimary` / `TextSecondary` | `#F5F7FA` / `#8B95A1` |
| Colores dinámicos por línea | rojo, azul, verde, amarillo, violeta... | asignados por `LineColorProvider` (determinístico por `routeId`, no hardcodeado por línea) |

### Componentes core reutilizables (`core-ui/components`)

- `GlassCard` — contenedor con blur + borde sutil, base de todas las tarjetas
- `LiveBadge` — pastilla "● Tránsito en vivo" con punto animado
- `ReliabilityGauge` — barra/anillo de confiabilidad con color según %
- `RouteBadge` — chip de número de línea con color dinámico
- `VehicleMapMarker3D` — marcador custom (no pin clásico), ver sección 6
- `BottomNavBar` — con botón central elevado (acceso directo a Mapa en vivo)
- `SearchBar` — búsqueda unificada línea/parada/dirección/empresa con debounce

### Animaciones

- Marcadores de colectivo interpolan posición con `Animatable` (no saltan de punto a punto)
- Transiciones de tarjeta con `AnimatedVisibility` + `spring()` spec, sensación "premium"
- `LiveBadge` con pulso sutil (`infiniteRepeatable`)
- Shared element transition entre marcador tocado → tarjeta de detalle

---

## 6. Mapa: marcadores 3D de colectivos

- `Google Maps SDK` + capa de edificios 3D activada, tilt/rotación habilitados
- Los colectivos **no son `Marker` estándar** con ícono plano: se renderizan como `Composable` sobre `MapEffect`/`AdvancedMarker` con un ícono ilustrado (bus visto en 3/4, coloreado dinámicamente) — capa `feature-map/marker`
- `VehicleClusterManager` propio para evitar saturar el mapa en zonas con muchas líneas superpuestas (Microcentro, por ejemplo)
- Seguimiento (`Seguir colectivo`): la cámara sigue al `VehiclePosition` con `CameraUpdateFactory` animado, desactivable con un gesto manual del usuario (igual que Google Maps navegación)

---

## 7. Mapa de pantallas / flujo de navegación

```
Auth (opcional, se puede usar sin cuenta)
   │
   ▼
Dashboard (Inicio)
 ├─ Búsqueda global → resultados (línea | parada | dirección | empresa)
 ├─ Mapa en vivo (embebido, resumen) → Mapa en vivo (full screen)
 │       └─ Tap en colectivo → BottomSheet Detalle de Vehículo
 │              ├─ Seguir → activa modo seguimiento en Mapa
 │              ├─ Ver recorrido completo → Detalle de Línea
 │              └─ Compartir → intent nativo
 ├─ Paradas cercanas → Detalle de Parada (próximos arribos)
 ├─ Próximos colectivos → Detalle de Línea
 ├─ Favoritos → gestión (Casa/Trabajo/Universidad/+ custom)
 └─ Alertas (badge contador) → Lista de Alertas → Detalle de Alerta

Bottom Nav: Inicio | Mapa en vivo | [Buscar/Reportar •central•] | Líneas | Más
Más → Paradas | Favoritos | Alertas | Historial | Ajustes | Perfil

Ajustes
 ├─ Idioma
 ├─ Tema (oscuro por defecto / claro / sistema)
 ├─ Notificaciones (por tipo: llegada, desvío, demora, suspensión)
 └─ Gestión de caché offline
```

Navegación implementada con **Navigation Compose**, rutas tipadas (`@Serializable` sealed routes), un solo `NavHost` en `app`, cada `feature` expone sus propias rutas/destinos sin conocer las del resto (se registran vía Hilt multibinding de `NavGraphBuilder` extensions).

---

## 8. Modelos de dominio (núcleo, agnóstico de proveedor)

```kotlin
data class RouteId(val value: String)
data class StopId(val value: String)
data class VehicleId(val value: String)

data class VehiclePosition(
    val vehicleId: VehicleId,
    val routeId: RouteId,
    val position: LatLng,
    val bearing: Float,
    val speedKmh: Float,
    val timestamp: Instant,
    val status: VehicleStatus,          // ON_TIME, DELAYED, OUT_OF_SERVICE
    val branchId: String?               // ramal
)

data class RouteDetails(
    val routeId: RouteId,
    val shortName: String,              // "60"
    val company: String,                // "MONSA"
    val branches: List<Branch>,
    val colorSeed: String,              // determina color en LineColorProvider
    val reliability: ReliabilityScore
)

data class Stop(
    val stopId: StopId,
    val name: String,
    val location: LatLng,
    val routesServed: List<RouteId>
)

data class ArrivalEstimate(
    val routeId: RouteId,
    val stopId: StopId,
    val etaMinutes: Int,
    val confidence: ReliabilityScore
)

data class ServiceAlert(
    val id: String,
    val type: AlertType,                // DETOUR, CLOSURE, PROTEST, ACCIDENT, STRIKE
    val affectedRoutes: List<RouteId>,
    val message: String,
    val severity: AlertSeverity,
    val startedAt: Instant
)

@JvmInline value class ReliabilityScore(val percent: Int) // 0..100
```

Estos modelos son el "idioma común" de toda la app — cualquier proveedor nuevo solo necesita un *mapper* que traduzca su formato (GTFS-RT protobuf, JSON del GCBA, etc.) a estos tipos.

---

## 9. Casos de uso (domain, ejemplos representativos)

- `ObserveVehiclesOnRouteUseCase`
- `ObserveVehiclesInVisibleMapAreaUseCase`
- `GetArrivalEstimatesForStopUseCase`
- `FollowVehicleUseCase` (mantiene el Flow activo + reconexión)
- `ManageFavoritesUseCase` (add/remove/reorder)
- `SubscribeToVehicleAlertsUseCase` (integra con notificaciones push)
- `SyncOfflineCacheUseCase` (disparado por WorkManager)

Cada uno con una sola responsabilidad, testeable con `MockTransportProvider` de `core-testing`.

---

## 10. Notificaciones push

`feature-alerts` + `core-common/notifications`:

- Suscripción por (línea + parada) o por (línea + colectivo específico si se está "siguiendo")
- Disparadores: proximidad ETA, cambio de `VehicleStatus`, nuevo `ServiceAlert` que afecte una línea favorita, suspensión de servicio
- Implementado como `WorkManager` periódico liviano + listeners de `Flow` mientras la app está en foreground; para background se evalúa FCM cuando haya backend propio (queda como *pluggable*, mismo principio que `TransportDataProvider`: interfaz `PushNotifier` con implementación intercambiable)

---

## 11. Testing

- `domain-*`: unit tests puros con `MockTransportProvider`
- `data-*`: tests de mappers (fuente externa → modelo de dominio) — el punto más crítico para no romper nada al cambiar de API
- `feature-*`: tests de ViewModel con `Turbine` sobre `StateFlow`
- Compose UI tests con `MockTransportProvider` inyectado, sin red real, para snapshots del diseño

---

## 12. Próximos pasos sugeridos

1. Validar este documento (nombres, límites de módulos, modelos).
2. Armar el proyecto base multi-módulo vacío + `MockTransportProvider` con datos simulados en Buenos Aires.
3. Implementar `core-ui` (tema, `GlassCard`, `RouteBadge`, `LiveBadge`) reproduciendo la estética de la referencia.
4. Construir `feature-dashboard` completo contra el mock (sin mapa aún).
5. Integrar `feature-map` con marcadores 3D animados sobre datos simulados.
6. Recién ahí, conectar una fuente real detrás de `TransportDataProvider` (a definir: GCBA vs GTFS-RT).

---

## 13. Addendum — Escalabilidad del mapa a miles de vehículos

Este addendum documenta el refactor hecho para soportar cientos/miles de
colectivos simultáneos sin degradar el rendimiento, manteniendo intacto el
contrato `TransportDataProvider` (ningún cambio de interfaz de dominio).

**Simulación (data-transport):**
- Un único loop de simulación compartido vía `shareIn(WhileSubscribed)` — no uno por cada pantalla que observa.
- `SpatialGrid`: índice en grilla uniforme para que "qué se ve en el viewport" sea O(celdas visibles), no O(flota completa).
- `SyntheticFleetGenerator`: genera cientos/miles de vehículos sintéticos en decenas de líneas proceduales, para stress-testing real antes de tener datos oficiales. Configurable con dos constantes (`SYNTHETIC_ROUTE_COUNT`, `VEHICLES_PER_ROUTE`).

**Renderizado (feature-map):**
- Se abandonó "un `Marker` nativo por colectivo" (no escala: cada uno es un objeto pesado del SDK).
- `VehicleCanvasOverlay`: un único `Canvas` dibuja toda la flota visible en una sola pasada.
- `VehicleClusterer`: agrupa vehículos según el zoom cuando hay demasiados para distinguirlos, reduciendo aún más los draw calls. Puro Kotlin, testeable sin emulador.
- El único `Marker` real que sobrevive es el del vehículo seleccionado/seguido (0 o 1 a la vez) — ahí sí vale la pena la animación de interpolación completa.

**Infraestructura para fuentes reales (sin conectar todavía):**
- `GtfsRealtimeProvider` + `GtfsRealtimeMappers` — esqueleto listo para `VehiclePositions.pb` / `TripUpdates.pb` / `ServiceAlerts.pb`.
- `GcbaOfficialApiProvider` — alternativa si el GCBA expone API propia en vez de/además de GTFS-RT.
- `CompositeTransportProvider` — combina ambas por `ProviderCapabilities` cuando haga falta (ej: posiciones de una fuente + alertas de otra).

Ninguna feature, ViewModel ni UseCase cambió en este refactor — es la prueba de que separar por `TransportDataProvider` cumplió su propósito.


---

## 14. Addendum — Crowdsourcing de ubicación (anónimo, opt-in)

Capa adicional para corregir/afinar posiciones cuando el GPS oficial del
colectivo no existe o no es confiable, usando la ubicación de los propios
pasajeros — con consentimiento explícito y sin identificar a nadie.

**Por qué necesita backend (no alcanza con el cliente Android solo):**
triangular requiere ver los pings de varios dispositivos distintos al mismo
tiempo, algo que ningún teléfono puede hacer por sí solo. El cliente reporta
pings anónimos; un servidor (todavía no construido) los agrupa y devuelve la
posición ya calculada.

**Diseño del cliente (`domain-crowdsourcing` + `data-crowdsourcing`):**
- `OnBusHeuristic` — puro Kotlin, decide si el patrón de movimiento (velocidad + paradas + persistencia) sugiere "vas en colectivo", para no reportar constantemente ni molestar.
- `TripSessionId` — identificador de SESIÓN DE VIAJE, no de usuario. Se descarta al terminar el viaje; no correlaciona dos viajes de la misma persona entre sí.
- `LocationReporter` — solo escucha GPS si hay opt-in activo (`flatMapLatest` corta el stream apenas se apaga); reporta pings solo cuando el heurístico da `LikelyOnBus`.
- `CrowdSourcingRepository` — mismo patrón que `TransportDataProvider`: contrato en domain, implementación intercambiable. Hoy `LocalCrowdSourcingRepository` (opt-in real, sin agregación real por falta de backend — honestamente documentado como tal).
- `CrowdsourcedTransportProvider` — esqueleto que traduciría estimaciones agregadas del backend al mismo `VehiclePosition` de siempre, combinable vía `CompositeTransportProvider` con cualquier otra fuente.
- `DeviceLocationSource` (core-location) — contrato de ubicación desacoplado de FusedLocationProvider; hoy usa `MockDeviceLocationSource` (simula estar en colectivo) mientras `FusedDeviceLocationSource` no está implementada.

**Pendiente, en orden de prioridad:**
1. Backend que agrupe pings y calcule `VehicleGroupEstimate` (fuera del alcance de este repo Android).
2. `FusedDeviceLocationSource` real (reemplaza al mock).
3. Foreground Service para que `LocationReporter` sobreviva en background (Android no permite ubicación continua sin uno).
4. Persistencia del opt-in en DataStore (hoy en memoria, se resetea al reiniciar la app).
5. Merge por confianza en `CompositeTransportProvider` cuando conviva con una fuente oficial (hoy es "gana el primero de la lista", no "gana el más confiable").

### 14.1 — Confianza visible en el mapa (crowdsourcing como fuente primaria)

Se decidió usar Mapbox + crowdsourcing como estrategia principal de posición
(no una fuente oficial con crowdsourcing como capa secundaria). Esto agrega
dos conceptos al modelo:

- `VehiclePosition.positionConfidence` / `.contributingReports` (nullable —
  null = fuente oficial, no aplica el concepto).
- `VehicleConfidenceStyle` (core-ui): traduce confianza a alpha + anillo
  punteado. Aplicado en `VehicleMapMarker3D`, `VehicleCanvasOverlay` y
  `VehicleDetailScreen`.
- Estado de "arranque en frío": cuando una zona/línea no tiene reportes
  suficientes, se muestra un mensaje honesto (`ColdStartMessage` en
  LiveMapScreen) en vez de un colectivo fantasma o una lista vacía sin
  contexto — con un nudge pasivo (no invasivo) hacia el opt-in de Ajustes.

Pendiente de esta sub-sección: suavizado de interpolación cuando los datos
llegan más espaciados de lo normal (crowdsourcing depende de cuánta gente
hay reportando, a diferencia de un GPS oficial que es constante), e
incentivos pasivos adicionales para sumar reporters.
