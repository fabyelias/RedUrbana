package com.redurbana.data.transport.mock

import com.redurbana.data.transport.directions.WalkingDirectionsClient
import com.redurbana.data.transport.spatial.SpatialGrid
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.model.Branch
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.TripLeg
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.distanceMeters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Busca alternativas puerta a puerta entre [origin] y [destination]: directas
 * (una línea) y con 1 transbordo (dos líneas), acotado a propósito — un
 * motor de N transbordos arbitrarios necesitaría una búsqueda tipo RAPTOR
 * multi-nivel, mucho más trabajo para un beneficio marginal sobre el caso
 * típico de 0-1 transbordos.
 *
 * Todas las estimaciones salen de datos que la app realmente tiene: la
 * caminata usa [WalkingDirectionsClient] (real, sigue calles); el tiempo en
 * el colectivo sale de la distancia real a lo largo del ramal sobre la
 * velocidad del vehículo simulado de ESE ramal; "sale en X min" sale de la
 * distancia real entre la posición simulada actual del vehículo y la parada
 * de subida. Nada de esto es un horario inventado.
 */
@Singleton
class TripPlanner @Inject constructor(
    private val walkingDirectionsClient: WalkingDirectionsClient,
    private val expectedFrequencyData: ExpectedFrequencyData,
) {
    private companion object {
        const val MAX_WALK_TO_STOP_METERS = 2_000.0
        const val TRANSFER_RADIUS_METERS = 150.0
        const val MAX_DIRECT_RESULTS = 5
        const val MAX_TRANSFER_SEED_ROUTES = 5
        const val MAX_TRANSFER_POINTS_PER_SEED = 20
        const val MAX_TRANSFER_RESULTS = 5
        const val DEFAULT_SPEED_KMH = 18f
    }

    private data class DirectCandidate(val route: RouteDetails, val branch: Branch, val boardIndex: Int, val alightIndex: Int)

    private data class TransferCandidate(
        val routeA: RouteDetails,
        val branchA: Branch,
        val boardIndexA: Int,
        val transferIndexA: Int,
        val routeB: RouteDetails,
        val branchB: Branch,
        val transferIndexB: Int,
        val alightIndexB: Int,
    )

    suspend fun plan(
        origin: GeoPoint,
        destination: GeoPoint,
        routes: Map<RouteId, RouteDetails>,
        stopIndex: SpatialGrid<Stop>,
        currentPositions: List<VehiclePosition>,
    ): List<TripItinerary> = coroutineScope {
        val directCandidates = findDirectCandidates(routes, origin, destination)
            .sortedBy { it.branch.stops[it.boardIndex].location.distanceMeters(origin) }
            // Sin esto, una sola línea con muchos ramales casi idénticos (común
            // en el dataset real: p. ej. la línea 015 tiene 12 ramales que
            // arrancan en el mismo punto) podía copar las top 5 alternativas
            // con variantes de sí misma, en vez de mostrar líneas distintas.
            .distinctBy { it.route.routeId }

        val transferCandidates = directCandidates
            .take(MAX_TRANSFER_SEED_ROUTES)
            .flatMap { findTransfersFrom(it, routes, stopIndex, destination) }
            .take(MAX_TRANSFER_RESULTS)

        val directItineraries = directCandidates
            .take(MAX_DIRECT_RESULTS)
            .map { async { buildDirectItinerary(it, origin, destination, currentPositions) } }
            .awaitAll()
            .filterNotNull()

        val transferItineraries = transferCandidates
            .map { async { buildTransferItinerary(it, origin, destination, currentPositions) } }
            .awaitAll()
            .filterNotNull()

        (directItineraries + transferItineraries).sortedBy { it.totalMinutes }
    }

    private fun findDirectCandidates(
        routes: Map<RouteId, RouteDetails>,
        origin: GeoPoint,
        destination: GeoPoint,
    ): List<DirectCandidate> = routes.values.flatMap { route ->
        route.branches.mapNotNull { branch -> directCandidateFor(route, branch, origin, destination) }
    }

    /**
     * Mejor combinación (parada de subida, parada de bajada) de este ramal,
     * con el destino REALMENTE más adelante en la dirección de viaje (no
     * `abs()` — un colectivo no puede "ir para atrás" para dejarte más
     * cerca).
     *
     * Evalúa TODAS las paradas caminables desde el origen como posible
     * subida, no solo la más cercana: si esa única parada más cercana queda
     * más adelante en el recorrido que la parada más cercana al destino
     * (recorridos que curvan o vuelven cerca de sí mismos), antes no
     * quedaba ningún índice de bajada válido "hacia adelante" y el ramal
     * entero se descartaba — aunque otra parada de subida un poco más lejos
     * sí hubiera servido. Con recorridos largos esto hacía que la búsqueda
     * casi nunca encontrara nada.
     */
    private fun directCandidateFor(route: RouteDetails, branch: Branch, origin: GeoPoint, destination: GeoPoint): DirectCandidate? {
        val stops = branch.stops
        if (stops.isEmpty()) return null

        return stops.indices
            .asSequence()
            .filter { stops[it].location.distanceMeters(origin) <= MAX_WALK_TO_STOP_METERS }
            .mapNotNull { boardIndex ->
                val alightIndex = (boardIndex + 1 until stops.size)
                    .minByOrNull { stops[it].location.distanceMeters(destination) } ?: return@mapNotNull null
                if (stops[alightIndex].location.distanceMeters(destination) > MAX_WALK_TO_STOP_METERS) return@mapNotNull null
                DirectCandidate(route, branch, boardIndex, alightIndex)
            }
            .minByOrNull { candidate ->
                stops[candidate.boardIndex].location.distanceMeters(origin) +
                    stops[candidate.alightIndex].location.distanceMeters(destination)
            }
    }

    private fun findTransfersFrom(
        seed: DirectCandidate,
        routes: Map<RouteId, RouteDetails>,
        stopIndex: SpatialGrid<Stop>,
        destination: GeoPoint,
    ): List<TransferCandidate> {
        val stops = seed.branch.stops
        val transferPoints = (seed.boardIndex + 1 until stops.size).take(MAX_TRANSFER_POINTS_PER_SEED)

        return transferPoints.flatMap { transferIndexA ->
            val transferStopA = stops[transferIndexA]
            val nearbyOtherStops = stopIndex.query(boundsAround(transferStopA.location, TRANSFER_RADIUS_METERS))
                .filter { it.location.distanceMeters(transferStopA.location) <= TRANSFER_RADIUS_METERS }
                .filter { it.routesServed.firstOrNull() != seed.route.routeId }
                .sortedBy { it.location.distanceMeters(transferStopA.location) }
                .take(3)

            nearbyOtherStops.mapNotNull { transferStopB ->
                val routeIdB = transferStopB.routesServed.firstOrNull() ?: return@mapNotNull null
                val routeB = routes[routeIdB] ?: return@mapNotNull null
                val branchB = routeB.branches.firstOrNull { transferStopB in it.stops } ?: return@mapNotNull null
                val transferIndexB = branchB.stops.indexOf(transferStopB)
                if (transferIndexB < 0) return@mapNotNull null

                val alightIndexB = (transferIndexB + 1 until branchB.stops.size)
                    .minByOrNull { branchB.stops[it].location.distanceMeters(destination) } ?: return@mapNotNull null
                if (branchB.stops[alightIndexB].location.distanceMeters(destination) > MAX_WALK_TO_STOP_METERS) return@mapNotNull null

                TransferCandidate(
                    routeA = seed.route, branchA = seed.branch, boardIndexA = seed.boardIndex, transferIndexA = transferIndexA,
                    routeB = routeB, branchB = branchB, transferIndexB = transferIndexB, alightIndexB = alightIndexB,
                )
            }
        }
    }

    private suspend fun buildDirectItinerary(
        candidate: DirectCandidate,
        origin: GeoPoint,
        destination: GeoPoint,
        positions: List<VehiclePosition>,
    ): TripItinerary? = coroutineScope {
        val boardStop = candidate.branch.stops[candidate.boardIndex]
        val alightStop = candidate.branch.stops[candidate.alightIndex]
        val walkToBoardDeferred = async { walkingDirectionsClient.route(origin, boardStop.location) }
        val walkToDestDeferred = async { walkingDirectionsClient.route(alightStop.location, destination) }
        val walkToBoard = walkToBoardDeferred.await().getOrNull() ?: return@coroutineScope null
        val walkToDest = walkToDestDeferred.await().getOrNull() ?: return@coroutineScope null

        val transit = transitLeg(candidate.route, candidate.branch, candidate.boardIndex, candidate.alightIndex, boardStop, alightStop, positions)
        val legs = listOf(
            TripLeg.Walk(origin, boardStop.location, walkToBoard.distanceMeters, walkToBoard.durationMinutes, walkToBoard.polyline),
            transit,
            TripLeg.Walk(alightStop.location, destination, walkToDest.distanceMeters, walkToDest.durationMinutes, walkToDest.polyline),
        )
        TripItinerary(
            legs = legs,
            // + espera hasta que pasa el colectivo (transit.nextDepartureMinutes):
            // sin esto, dos alternativas con el mismo caminar+viaje quedaban
            // empatadas aunque una tuviera el bondi a 1 min y la otra a 20 —
            // así es como Google Maps ordena "salís ahora, llegás a las X".
            // Si no hay vehículo simulado con posición conocida para esa
            // línea/ramal, nextDepartureMinutes es null y NO se inventa un
            // valor (ver doc de la clase: nada de horarios inventados) — se
            // suma 0, sin penalizar lo que no se sabe.
            totalMinutes = walkToBoard.durationMinutes + (transit.nextDepartureMinutes ?: 0) +
                transit.estimatedMinutes + walkToDest.durationMinutes,
            transferCount = 0,
        )
    }

    private suspend fun buildTransferItinerary(
        candidate: TransferCandidate,
        origin: GeoPoint,
        destination: GeoPoint,
        positions: List<VehiclePosition>,
    ): TripItinerary? = coroutineScope {
        val boardStopA = candidate.branchA.stops[candidate.boardIndexA]
        val transferStopA = candidate.branchA.stops[candidate.transferIndexA]
        val transferStopB = candidate.branchB.stops[candidate.transferIndexB]
        val alightStopB = candidate.branchB.stops[candidate.alightIndexB]

        val walkToBoardDeferred = async { walkingDirectionsClient.route(origin, boardStopA.location) }
        val walkTransferDeferred = async { walkingDirectionsClient.route(transferStopA.location, transferStopB.location) }
        val walkToDestDeferred = async { walkingDirectionsClient.route(alightStopB.location, destination) }
        val walkToBoard = walkToBoardDeferred.await().getOrNull() ?: return@coroutineScope null
        val walkTransfer = walkTransferDeferred.await().getOrNull() ?: return@coroutineScope null
        val walkToDest = walkToDestDeferred.await().getOrNull() ?: return@coroutineScope null

        val transitA = transitLeg(candidate.routeA, candidate.branchA, candidate.boardIndexA, candidate.transferIndexA, boardStopA, transferStopA, positions)
        val transitB = transitLeg(candidate.routeB, candidate.branchB, candidate.transferIndexB, candidate.alightIndexB, transferStopB, alightStopB, positions)

        val legs = listOf(
            TripLeg.Walk(origin, boardStopA.location, walkToBoard.distanceMeters, walkToBoard.durationMinutes, walkToBoard.polyline),
            transitA,
            TripLeg.Walk(transferStopA.location, transferStopB.location, walkTransfer.distanceMeters, walkTransfer.durationMinutes, walkTransfer.polyline),
            transitB,
            TripLeg.Walk(alightStopB.location, destination, walkToDest.distanceMeters, walkToDest.durationMinutes, walkToDest.polyline),
        )
        TripItinerary(
            legs = legs,
            // Solo se suma la espera del PRIMER colectivo (transitA), igual que
            // en buildDirectItinerary. La del segundo (transitB) NO se suma:
            // nextDepartureMinutes sale de la posición ACTUAL del vehículo
            // simulado, "si salieras ya" — para el segundo tramo el usuario en
            // realidad llega al punto de transbordo más tarde (después de
            // caminar + viajar en el primero), y no hay forma de predecir
            // dónde va a estar ESE vehículo en ese momento futuro con los
            // datos que tiene la app hoy. Sumarlo igual sería inventar un
            // número, algo que este archivo evita a propósito (ver doc de la
            // clase).
            totalMinutes = walkToBoard.durationMinutes + (transitA.nextDepartureMinutes ?: 0) + transitA.estimatedMinutes +
                walkTransfer.durationMinutes + transitB.estimatedMinutes + walkToDest.durationMinutes,
            transferCount = 1,
        )
    }

    private suspend fun transitLeg(
        route: RouteDetails,
        branch: Branch,
        boardIndex: Int,
        alightIndex: Int,
        boardStop: Stop,
        alightStop: Stop,
        positions: List<VehiclePosition>,
    ): TripLeg.Transit {
        val vehicle = positions.firstOrNull { it.routeId == route.routeId && it.branchId == branch.id }
        val speed = vehicle?.speedKmh?.takeIf { it > 0f } ?: DEFAULT_SPEED_KMH
        val rideMeters = (boardIndex until alightIndex).sumOf { branch.stops[it].location.distanceMeters(branch.stops[it + 1].location) }
        val rideMinutes = ((rideMeters / 1000.0 / speed) * 60).roundToInt().coerceAtLeast(1)
        val nextDeparture = vehicle?.let {
            val distance = it.position.distanceMeters(boardStop.location)
            ((distance / 1000.0 / speed) * 60).roundToInt().coerceAtLeast(0)
        }

        return TripLeg.Transit(
            routeId = route.routeId,
            shortName = route.shortName,
            colorSeed = route.colorSeed,
            boardingStop = boardStop,
            alightingStop = alightStop,
            stopsCount = alightIndex - boardIndex,
            estimatedMinutes = rideMinutes,
            nextDepartureMinutes = nextDeparture,
            typicalHeadwayMinutes = expectedFrequencyData.typicalHeadwayMinutes(route.routeId.value),
            path = (boardIndex..alightIndex).map { branch.stops[it].location },
        )
    }

    private fun boundsAround(point: GeoPoint, radiusMeters: Double): GeoBounds {
        val latDelta = radiusMeters / 111_000.0
        val lngDelta = radiusMeters / (111_000.0 * cos(Math.toRadians(point.latitude)).coerceAtLeast(0.2))
        return GeoBounds(
            northEast = GeoPoint(point.latitude + latDelta, point.longitude + lngDelta),
            southWest = GeoPoint(point.latitude - latDelta, point.longitude - lngDelta),
        )
    }
}
