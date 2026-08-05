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
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.distanceMeters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Implementación de [TransportDataProvider] sobre datos reales de geometría
 * de líneas (ver [RealRouteData]): 138 líneas / 566 ramales reales del área
 * metropolitana de Buenos Aires, con un vehículo simulado moviéndose sobre
 * cada ramal — la posición del vehículo es simulada, pero el recorrido que
 * sigue es el real.
 *
 * Dos decisiones de diseño para que esto escale de verdad:
 *
 * 1. CARGA ASÍNCRONA EN BACKGROUND: parsear ~7.5MB de JSON y generar ~566
 *    vehículos no puede pasar en el hilo que construye este singleton
 *    (típicamente el hilo principal, vía Hilt, al crear el primer
 *    ViewModel que lo necesita). [loadedFleet] arranca la carga apenas se
 *    construye el provider, en `Dispatchers.IO`, y cada método suspende
 *    hasta que esté lista en vez de bloquear.
 *
 * 2. SIMULACIÓN COMPARTIDA (no una por pantalla): el tick de la flota corre
 *    en un único [flow] compartido vía [shareIn]. Si el Dashboard, el Mapa y
 *    un widget de detalle observan al mismo tiempo, se recalcula UNA vez por
 *    tick, no una vez por cada `collect()` activo. Cada tick arma un
 *    [SpatialGrid] nuevo (no lo muta en el lugar), así los collectors
 *    lentos nunca leen un índice a medio reconstruir.
 */
@Singleton
class MockTransportProvider @Inject constructor(
    private val realRouteData: RealRouteData,
    private val tripPlanner: TripPlanner,
) : TransportDataProvider {

    companion object {
        private const val TICK_INTERVAL_MS = 2_000L // GTFS-RT real suele actualizar cada 10-30s; acá va más rápido a propósito, para poder ver el movimiento en la demo
        private const val GRID_CELL_SIZE_DEGREES = 0.01 // ~1.1km — suficiente para viewports típicos de un celular
        private const val STOP_GRID_CELL_SIZE_DEGREES = 0.002 // ~220m — más fino que el de vehículos, para detectar transbordos a pie
    }

    override val providerId: String = "mock-argentina-real-routes"

    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = true,
        supportsArrivalEstimates = true,
        supportsServiceAlerts = true,
        supportsReliabilityScore = true,
    )

    // Scope propio de este singleton: vive mientras viva el proceso.
    private val providerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val loadedFleet: Deferred<RealRouteData.LoadedFleet> = providerScope.async { realRouteData.load() }

    private val allStops: Deferred<List<Stop>> = providerScope.async {
        loadedFleet.await().routes.values.flatMap { route -> route.branches.flatMap { it.stops } }
    }

    // Índice espacial de paradas, para detectar candidatos de transbordo
    // (paradas de OTRAS líneas a poca distancia a pie) sin recorrer las
    // ~30 mil paradas reales en cada búsqueda de itinerario.
    private val stopIndex: Deferred<SpatialGrid<Stop>> = providerScope.async {
        SpatialGrid<Stop>(locationOf = { it.location }, cellSizeDegrees = STOP_GRID_CELL_SIZE_DEGREES)
            .apply { rebuild(allStops.await()) }
    }

    private data class FleetSnapshot(
        val positions: List<VehiclePosition>,
        val spatialIndex: SpatialGrid<VehiclePosition>,
    )

    /**
     * Único loop de simulación de toda la app. `shareIn` con
     * `WhileSubscribed` hace que el tick se apague solo si nadie lo está
     * mirando (ej: app en background) y se retome cuando alguien vuelve a
     * suscribirse.
     */
    private val fleetSnapshots: Flow<FleetSnapshot> = flow {
        val fleet = loadedFleet.await().vehicles
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
        val details = loadedFleet.await().routes[routeId]
        return if (details != null) Result.success(details)
        else Result.failure(NoSuchElementException("Línea $routeId no encontrada"))
    }

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> {
        val nearby = allStops.await()
            .map { stop -> stop to stop.location.distanceMeters(location) }
            .filter { (_, distance) -> distance <= radiusMeters }
            .sortedBy { (_, distance) -> distance }
            .map { (stop, _) -> stop }
        return Result.success(nearby)
    }

    override suspend fun getTripItineraries(origin: GeoPoint, destination: GeoPoint): Result<List<TripItinerary>> = runCatching {
        val fleet = loadedFleet.await()
        val currentPositions = fleetSnapshots.first().positions
        tripPlanner.plan(
            origin = origin,
            destination = destination,
            routes = fleet.routes,
            stopIndex = stopIndex.await(),
            currentPositions = currentPositions,
        )
    }

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> {
        val routes = loadedFleet.await().routes
        val stop = allStops.await().firstOrNull { it.stopId == stopId }
        val relevantRoutes = stop?.routesServed.orEmpty().mapNotNull { routes[it] }
        val etas = relevantRoutes.mapIndexed { index, route ->
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
        val base = loadedFleet.await().routes[routeId]?.reliability ?: ReliabilityScore(90)
        while (true) {
            emit(base)
            delay(30_000)
        }
    }
}
