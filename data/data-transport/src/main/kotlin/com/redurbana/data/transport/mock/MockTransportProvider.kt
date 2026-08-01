package com.redurbana.data.transport.mock

import com.redurbana.data.transport.spatial.SpatialGrid
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.TransportDataProvider
import com.redurbana.domain.transport.model.ArrivalEstimate
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ProviderCapabilities
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.ServiceAlert
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.StopId
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Implementación de [TransportDataProvider] que simula una flota completa
 * moviéndose por Buenos Aires, pensada explícitamente para VALIDAR
 * RENDIMIENTO antes de conectar una fuente real: por defecto simula
 * [SYNTHETIC_ROUTE_COUNT] * [VEHICLES_PER_ROUTE] vehículos sintéticos
 * (+ los 4 de la demo de detalle), fácilmente escalable a miles subiendo
 * esas dos constantes.
 *
 * Tres decisiones de diseño para que esto escale de verdad:
 *
 * 1. SIMULACIÓN COMPARTIDA (no una por pantalla): el tick de la flota corre
 *    en un único [flow] compartido vía [shareIn]. Si el Dashboard, el Mapa y
 *    un widget de detalle observan al mismo tiempo, se recalcula UNA vez por
 *    tick, no una vez por cada `collect()` activo.
 *
 * 2. SNAPSHOT INMUTABLE POR TICK: cada tick arma un [SpatialGrid] nuevo (no
 *    lo muta en el lugar), así los collectors lentos nunca leen un índice a
 *    medio reconstruir.
 *
 * 3. CONSULTAS POR BOUNDS SON O(celdas visibles), NO O(flota completa):
 *    `observeVehiclesInBounds` consulta el grid en vez de filtrar la lista
 *    entera — el costo de "qué se ve en pantalla" no crece con el tamaño
 *    total de la flota simulada.
 */
@Singleton
class MockTransportProvider @Inject constructor() : TransportDataProvider {

    companion object {
        /** Subir estas dos constantes es la forma de stress-testear con miles de vehículos. */
        private const val SYNTHETIC_ROUTE_COUNT = 40
        private const val VEHICLES_PER_ROUTE = 10 // 40 * 10 = 400 vehículos sintéticos + 4 de demo

        private const val TICK_INTERVAL_MS = 2_000L // GTFS-RT real suele actualizar cada 10-30s; acá va más rápido a propósito, para poder ver el movimiento en la demo
        private const val GRID_CELL_SIZE_DEGREES = 0.01 // ~1.1km — suficiente para viewports típicos de un celular
    }

    override val providerId: String = "mock-buenos-aires-v2-stress"

    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = true,
        supportsArrivalEstimates = true,
        supportsServiceAlerts = true,
        supportsReliabilityScore = true,
    )

    // Scope propio de este singleton: vive mientras viva el proceso. Si en el
    // futuro se necesita apagar la simulación (ej. app en background por
    // mucho tiempo), este es el punto para cancelarlo.
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val syntheticFleet = SyntheticFleetGenerator.generate(
        routeCount = SYNTHETIC_ROUTE_COUNT,
        vehiclesPerRoute = VEHICLES_PER_ROUTE,
    )

    private val allRoutes: Map<RouteId, RouteDetails> = MockRouteData.routes + syntheticFleet.routes

    private val fleet: List<SimulatedVehicle> = buildList {
        // Las 4 líneas "de demo" con recorridos reales alrededor del Congreso,
        // usadas por las pantallas de detalle/paradas cercanas.
        add(SimulatedVehicle(VehicleId("60-1845"), RouteId("60"), "R1", "1845", MockRouteData.route60Path, speedKmh = 32f, startFractionAlongPath = 0.45f))
        add(SimulatedVehicle(VehicleId("60-1902"), RouteId("60"), "R1", "1902", MockRouteData.route60Path, speedKmh = 28f, startFractionAlongPath = 0.10f))
        add(SimulatedVehicle(VehicleId("152-0311"), RouteId("152"), "R2", "0311", MockRouteData.route152Path, speedKmh = 26f, startFractionAlongPath = 0.60f))
        add(SimulatedVehicle(VehicleId("59-2210"), RouteId("59"), "R1", "2210", MockRouteData.route59Path, speedKmh = 30f, startFractionAlongPath = 0.30f))
        add(SimulatedVehicle(VehicleId("37-0087"), RouteId("37"), "R1", "0087", MockRouteData.route37Path, speedKmh = 24f, startFractionAlongPath = 0.75f))
        // + la flota sintética grande, para stress-testing de mapa/clustering.
        addAll(syntheticFleet.vehicles)
    }

    private data class FleetSnapshot(
        val positions: List<VehiclePosition>,
        val spatialIndex: SpatialGrid<VehiclePosition>,
    )

    /**
     * Único loop de simulación de toda la app. `shareIn` con
     * `WhileSubscribed` hace que el tick se apague solo si nadie lo está
     * mirando (ej: app en background) y se retome cuando alguien vuelve a
     * suscribirse, sin perder el progreso de las posiciones (viven en
     * [fleet], no en el Flow).
     */
    private val fleetSnapshots: Flow<FleetSnapshot> = flow {
        while (true) {
            val elapsedSeconds = TICK_INTERVAL_MS / 1000.0
            val positions = fleet.map { it.tick(elapsedSeconds) }
            val index = SpatialGrid<VehiclePosition>(
                locationOf = { it.position },
                cellSizeDegrees = GRID_CELL_SIZE_DEGREES,
            ).apply { rebuild(positions) }
            emit(FleetSnapshot(positions, index))
            delay(TICK_INTERVAL_MS)
        }
    }.shareIn(
        scope = providerScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        replay = 1,
    )

    override fun observeVehiclesOnRoute(routeId: RouteId): Flow<List<VehiclePosition>> =
        fleetSnapshots.map { snapshot -> snapshot.positions.filter { it.routeId == routeId } }

    override fun observeVehiclesInBounds(bounds: GeoBounds): Flow<List<VehiclePosition>> =
        fleetSnapshots.map { snapshot -> snapshot.spatialIndex.query(bounds) }

    override fun observeVehicle(routeId: RouteId, vehicleId: VehicleId): Flow<VehiclePosition?> =
        fleetSnapshots.map { snapshot -> snapshot.positions.firstOrNull { it.vehicleId == vehicleId } }

    override suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails> {
        val details = allRoutes[routeId]
        return if (details != null) Result.success(details)
        else Result.failure(NoSuchElementException("Línea $routeId no encontrada en el mock"))
    }

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> {
        // Paradas fijas de ejemplo alrededor del Congreso, como en la referencia visual.
        val stops = listOf(
            Stop(StopId("congreso"), "Congreso", GeoPoint(-34.6095, -58.3924), listOf(RouteId("60"), RouteId("152"), RouteId("37"), RouteId("59"))),
            Stop(StopId("plaza-congreso"), "Plaza del Congreso", GeoPoint(-34.6098, -58.3928), listOf(RouteId("70"), RouteId("100"), RouteId("146"))),
            Stop(StopId("avmayo-9dejulio"), "Av. de Mayo y 9 de Julio", GeoPoint(-34.6083, -58.3811), listOf(RouteId("17"), RouteId("45"), RouteId("86"), RouteId("102"))),
        )
        return Result.success(stops)
    }

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> {
        val etas = MockRouteData.routes.values.mapIndexed { index, route ->
            ArrivalEstimate(
                routeId = route.routeId,
                stopId = stopId,
                etaMinutes = 3 + index * 3 + Random.nextInt(0, 3),
                confidence = route.reliability,
            )
        }
        return Result.success(etas)
    }

    override suspend fun getServiceAlerts(routeId: RouteId?): Result<List<ServiceAlert>> {
        // El mock no simula alertas activas por defecto (fleet 100% en horario).
        // Ver :data:data-transport/gtfsrt para el mapper real de Service Alerts.
        val all = emptyList<ServiceAlert>()
        return Result.success(if (routeId == null) all else all.filter { routeId in it.affectedRoutes })
    }

    override fun observeVehicleReliability(routeId: RouteId): Flow<ReliabilityScore> = flow {
        val base = allRoutes[routeId]?.reliability ?: ReliabilityScore(90)
        while (true) {
            emit(base)
            delay(30_000)
        }
    }
}
