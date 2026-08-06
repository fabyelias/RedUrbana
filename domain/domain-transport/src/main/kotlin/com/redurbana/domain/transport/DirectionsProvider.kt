package com.redurbana.domain.transport

import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.VehicleProfile

/**
 * Separado de [TransportDataProvider] a propósito: no es un dato de
 * transporte público intercambiable entre fuentes (GTFS-RT, GCBA,
 * crowdsourcing, mock) — es routing genérico, siempre la misma fuente
 * (Mapbox Directions) sin importar qué proveedor de transporte esté activo.
 */
interface DirectionsProvider {
    suspend fun getDrivingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicleProfile: VehicleProfile = VehicleProfile(),
    ): Result<DrivingRoute>
}
