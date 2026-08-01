package com.redurbana.domain.transport

import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehicleId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class FollowedVehicle(val routeId: RouteId, val vehicleId: VehicleId)

/**
 * Punto de coordinación entre "Seguir colectivo" (feature-vehicle-detail) y
 * la cámara del mapa (feature-map), sin que ninguna de las dos features
 * dependa de la otra — ambas solo conocen este controlador de :domain.
 *
 * Es deliberadamente liviano: un StateFlow con el vehículo seguido (o null).
 * Al ser un StateFlow, si llegan varias posiciones nuevas antes de que la UI
 * llegue a procesarlas, solo se conserva/anima la última — no hay cola de
 * animaciones de cámara acumulándose ni trabajo extra por tick.
 */
@Singleton
class FollowedVehicleController @Inject constructor() {

    private val _followed = MutableStateFlow<FollowedVehicle?>(null)
    val followed: StateFlow<FollowedVehicle?> = _followed.asStateFlow()

    fun startFollowing(routeId: RouteId, vehicleId: VehicleId) {
        _followed.value = FollowedVehicle(routeId, vehicleId)
    }

    fun stopFollowing() {
        _followed.value = null
    }

    fun toggleFollowing(routeId: RouteId, vehicleId: VehicleId) {
        val current = _followed.value
        if (current != null && current.routeId == routeId && current.vehicleId == vehicleId) {
            stopFollowing()
        } else {
            startFollowing(routeId, vehicleId)
        }
    }

    fun isFollowing(routeId: RouteId, vehicleId: VehicleId): Boolean =
        _followed.value?.let { it.routeId == routeId && it.vehicleId == vehicleId } ?: false
}
