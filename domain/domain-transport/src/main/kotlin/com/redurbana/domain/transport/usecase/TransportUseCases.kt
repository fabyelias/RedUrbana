package com.redurbana.domain.transport.usecase

import com.redurbana.domain.transport.DirectionsProvider
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.TransportDataProvider
import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.DrivingRouteOptions
import com.redurbana.domain.transport.model.ArrivalEstimate
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.StopId
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.VehicleProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Todas las features dependen de estos UseCases, NUNCA de TransportDataProvider
 * directamente. Esto deja un único punto de inyección para cambiar de fuente
 * de datos (ver módulo :data:data-transport/di).
 */

class ObserveVehiclesInBoundsUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    operator fun invoke(bounds: GeoBounds): Flow<List<VehiclePosition>> =
        provider.observeVehiclesInBounds(bounds)
}

class ObserveVehiclesOnRouteUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    operator fun invoke(routeId: RouteId): Flow<List<VehiclePosition>> =
        provider.observeVehiclesOnRoute(routeId)
}

/**
 * Sostiene el seguimiento de un colectivo específico ("Seguir colectivo").
 * Si el Flow se corta por falta de red, quien lo colecte (ViewModel) puede
 * reintentar sin perder el vehicleId que se estaba siguiendo.
 */
class FollowVehicleUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    operator fun invoke(routeId: RouteId, vehicleId: VehicleId): Flow<VehiclePosition?> =
        provider.observeVehicle(routeId, vehicleId)
}

class GetArrivalEstimatesForStopUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    suspend operator fun invoke(stopId: StopId): Result<List<ArrivalEstimate>> =
        provider.getArrivalEstimates(stopId)
}

class GetStopsNearbyUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    suspend operator fun invoke(location: GeoPoint, radiusMeters: Int = 500): Result<List<com.redurbana.domain.transport.model.Stop>> =
        provider.getStopsNearby(location, radiusMeters)
}

class GetTripItinerariesUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    suspend operator fun invoke(origin: GeoPoint, destination: GeoPoint): Result<List<com.redurbana.domain.transport.model.TripItinerary>> =
        provider.getTripItineraries(origin, destination)
}

class GetServiceAlertsUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    suspend operator fun invoke(routeId: RouteId? = null): Result<List<com.redurbana.domain.transport.model.ServiceAlert>> =
        provider.getServiceAlerts(routeId)
}

class GetRouteDetailsUseCase @Inject constructor(
    private val provider: TransportDataProvider,
) {
    suspend operator fun invoke(routeId: RouteId): Result<com.redurbana.domain.transport.model.RouteDetails> =
        provider.getRouteDetails(routeId)
}

/** Modo "Auto" de ExploreMapScreen: ruta real manejando, no transporte público. Usado por CarNavigationViewModel (arranque + recálculos). */
class GetDrivingRouteUseCase @Inject constructor(
    private val provider: DirectionsProvider,
) {
    suspend operator fun invoke(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicleProfile: VehicleProfile = VehicleProfile(),
    ): Result<DrivingRoute> = provider.getDrivingRoute(origin, destination, vehicleProfile)
}

/** Momento de elegir destino (ExploreViewModel): puede devolver dos rutas para elegir — ver DrivingRouteOptions. */
class GetAlternativeDrivingRoutesUseCase @Inject constructor(
    private val provider: DirectionsProvider,
) {
    suspend operator fun invoke(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicleProfile: VehicleProfile = VehicleProfile(),
    ): Result<DrivingRouteOptions> = provider.getAlternativeDrivingRoutes(origin, destination, vehicleProfile)
}
