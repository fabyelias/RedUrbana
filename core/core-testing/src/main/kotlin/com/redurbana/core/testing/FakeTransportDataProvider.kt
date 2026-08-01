package com.redurbana.core.testing

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Implementación 100% controlable por el test: se le "empuja" el estado que
 * se necesite (emitVehicles, etc.) y se verifica cómo reacciona el ViewModel
 * bajo prueba. Distinto de MockTransportProvider (data-transport), que simula
 * movimiento real para demos — este es determinístico y sin delays.
 */
class FakeTransportDataProvider : TransportDataProvider {

    override val providerId: String = "fake-test-provider"
    override val capabilities = ProviderCapabilities(
        supportsRealtimePositions = true,
        supportsArrivalEstimates = true,
        supportsServiceAlerts = true,
        supportsReliabilityScore = true,
    )

    private val vehiclesState = MutableStateFlow<List<VehiclePosition>>(emptyList())
    private val routeDetailsMap = mutableMapOf<RouteId, RouteDetails>()
    private val alertsState = MutableStateFlow<List<ServiceAlert>>(emptyList())

    fun emitVehicles(vehicles: List<VehiclePosition>) {
        vehiclesState.value = vehicles
    }

    fun putRouteDetails(details: RouteDetails) {
        routeDetailsMap[details.routeId] = details
    }

    fun emitAlerts(alerts: List<ServiceAlert>) {
        alertsState.value = alerts
    }

    override fun observeVehiclesOnRoute(routeId: RouteId) = vehiclesState.asStateFlow()

    override fun observeVehiclesInBounds(bounds: GeoBounds) = vehiclesState.asStateFlow()

    override fun observeVehicle(routeId: RouteId, vehicleId: VehicleId) =
        kotlinx.coroutines.flow.MutableStateFlow(
            vehiclesState.value.firstOrNull { it.vehicleId == vehicleId },
        ).asStateFlow()

    override suspend fun getRouteDetails(routeId: RouteId): Result<RouteDetails> {
        val details = routeDetailsMap[routeId]
        return if (details != null) Result.success(details)
        else Result.failure(NoSuchElementException("No configurado en el fake: $routeId"))
    }

    override suspend fun getStopsNearby(location: GeoPoint, radiusMeters: Int): Result<List<Stop>> =
        Result.success(emptyList())

    override suspend fun getArrivalEstimates(stopId: StopId): Result<List<ArrivalEstimate>> =
        Result.success(emptyList())

    override suspend fun getServiceAlerts(routeId: RouteId?): Result<List<ServiceAlert>> =
        Result.success(alertsState.value)

    override fun observeVehicleReliability(routeId: RouteId) =
        kotlinx.coroutines.flow.MutableStateFlow(ReliabilityScore(90)).asStateFlow()
}
