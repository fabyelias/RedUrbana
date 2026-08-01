# RedUrbana

App de transporte público en tiempo real para Buenos Aires. Android nativo,
Kotlin + Jetpack Compose, Clean Architecture multi-módulo.

## Estado actual (ver `docs/ARQUITECTURA.md` para el detalle completo)

**Funcional de punta a punta, sobre datos simulados (`MockTransportProvider`):**
- Dashboard, Mapa en vivo (Mapbox, edificios 3D, modo oscuro nativo)
- Detalle de vehículo + "Seguir colectivo" (cámara sigue al vehículo)
- Alertas, Paradas cercanas
- Navegación completa (`BottomNavBar` + `NavHost`, rutas tipadas)
- Renderizado del mapa preparado para **cientos/miles de vehículos**
  simultáneos (Canvas overlay + clustering + índice espacial), no solo la
  demo de 4 líneas
- Ajustes → toggle real de "Colaborar con la comunidad" (crowdsourcing de
  ubicación anónimo — ver `docs/ARQUITECTURA.md` sección 14)

**Pendiente:**
- Favoritos, Historial (placeholders navegables, sin lógica)
- Ajustes: falta idioma/tema/notificaciones (el toggle de crowdsourcing ya está)
- `feature-lines` (detalle completo de una línea)
- `core-location`: falta `FusedDeviceLocationSource` real (hoy usa un mock que simula estar en colectivo)
- Backend de crowdsourcing (triangulación real de posiciones — el cliente
  Android ya está listo para consumirlo, ver `docs/ARQUITECTURA.md` sección 14)
- Foreground Service para que `LocationReporter` sobreviva en background
- Offline/sync con Room + WorkManager (diseñado, no implementado)
- Conectar una fuente de datos real (GTFS-RT / API del GCBA) — la
  infraestructura ya está lista en `data/data-transport/gtfsrt` y `gcba/`,
  solo falta el endpoint definitivo

## Antes de compilar: credenciales

**Leer `docs/CREDENTIALS_SETUP.md` primero.** Resumen:
- El token público de Mapbox (`pk...`) NO está en el repo (GitHub push
  protection lo bloquea igual que uno secreto) — copiar
  `app/src/main/res/values/mapbox.xml.example` a `mapbox.xml` y completarlo.
- Falta que vos agregues tu token secreto de Mapbox (`sk...`, para que Gradle
  pueda descargar el SDK) en `~/.gradle/gradle.properties` de tu máquina —
  **nunca en este repositorio**.

Sin ese segundo token, el proyecto no compila (Gradle no puede resolver las
dependencias de Mapbox).

## Estructura

```
app/                    → ensamblado final, NavHost, DI graph
core/
  core-ui/               → design system, tema, componentes Compose, AppRoute
  core-common/           → utilidades compartidas (Result, dispatchers)
  core-database/         → Room (esqueleto, sin implementar)
  core-network/          → base Retrofit (esqueleto)
  core-location/         → wrapper de ubicación (esqueleto, sin implementar)
  core-testing/          → fakes para tests
domain/
  domain-transport/      → EL CONTRATO: TransportDataProvider, modelos, UseCases
  domain-crowdsourcing/  → opt-in, OnBusHeuristic, CrowdSourcingRepository (contrato)
  domain-favorites/      → (esqueleto)
  domain-alerts/         → (esqueleto)
  domain-user/           → (esqueleto)
data/
  data-transport/        → MockTransportProvider, SpatialGrid, GTFS-RT/GCBA (esqueletos), CompositeTransportProvider
  data-crowdsourcing/    → LocationReporter, LocalCrowdSourcingRepository, CrowdsourcedTransportProvider (esqueleto)
  data-favorites/ data-alerts/ data-user/ → (esqueletos)
feature/
  feature-dashboard/     → Inicio
  feature-map/           → Mapa en vivo (Mapbox)
  feature-vehicle-detail/→ Detalle de colectivo + seguimiento
  feature-alerts/        → Alertas
  feature-stops/         → Paradas cercanas
  feature-settings/      → Ajustes (toggle de colaboración anónima)
  feature-lines/ feature-favorites/ feature-history/ feature-auth/ → (esqueletos, sin pantalla)
sync/                    → WorkManager (esqueleto)
docs/
  ARQUITECTURA.md         → documento completo de arquitectura + addendum de escalabilidad
  CREDENTIALS_SETUP.md    → cómo configurar los tokens de Mapbox
  preview.html            → preview visual navegable del estado de la UI (sin compilar nada)
```

## Cómo seguir

- **Para editar código/revisar estructura ahora (VS Code):** el proyecto es
  Kotlin puro + Gradle, se navega y edita perfectamente sin Android Studio.
  Lo único que no vas a poder hacer sin Android Studio (o sin `./gradlew`
  con un Android SDK instalado) es compilar y correr la app.
- **Para compilar/correr (Android Studio):** abrir la carpeta raíz como
  proyecto Gradle, configurar el token secreto de Mapbox (ver arriba),
  sync de Gradle, y correr `app` en un emulador o dispositivo.
- **`docs/preview.html`:** abrí ese archivo en cualquier navegador para ver
  el estado visual actual de la app (Dashboard, Mapa, Alertas, Paradas, y
  una demo de rendimiento con hasta 3000 vehículos simulados) sin necesidad
  de compilar nada — útil para seguir iterando el diseño desde el celular.

## Reglas de arquitectura a respetar si se sigue editando a mano

1. Ninguna `feature` importa otra `feature` directamente — solo `domain` y `core`.
2. Ningún ViewModel inyecta `TransportDataProvider` directo — siempre a través de un `UseCase` de `domain-transport`.
3. El único lugar que decide qué implementación de `TransportDataProvider` está activa es `data/data-transport/di/TransportModule.kt`.
4. El único archivo que conoce a todas las features a la vez es `app/navigation/RedUrbanaNavHost.kt`.
