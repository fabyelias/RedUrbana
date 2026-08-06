package com.redurbana.domain.transport

import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.DrivingRouteOptions
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.VehicleProfile

/**
 * Separado de [TransportDataProvider] a propósito: no es un dato de
 * transporte público intercambiable entre fuentes (GTFS-RT, GCBA,
 * crowdsourcing, mock) — es routing genérico, siempre la misma fuente
 * (Mapbox Directions) sin importar qué proveedor de transporte esté activo.
 */
interface DirectionsProvider {
    /**
     * Una sola ruta, ya respetando las restricciones del vehículo si las
     * tiene. La usa CarNavigationViewModel (arranque y cada recálculo por
     * desvío): ahí no tiene sentido re-ofrecer la elección directa/segura en
     * cada recálculo, solo mantener la que el conductor ya eligió.
     */
    suspend fun getDrivingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicleProfile: VehicleProfile = VehicleProfile(),
    ): Result<DrivingRoute>

    /**
     * Para el momento de elegir destino (ExploreViewModel): si el vehículo
     * tiene medidas configuradas, pide TAMBIÉN la ruta sin restricciones y
     * las compara — si evitar túneles/calles angostas cambió algo de verdad,
     * [DrivingRouteOptions.direct] viene no-null para que el conductor
     * elija. Para vehículos sin medidas (auto/moto/camioneta/patrulla) es
     * exactamente [getDrivingRoute] envuelto, sin pedir nada de más.
     */
    suspend fun getAlternativeDrivingRoutes(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicleProfile: VehicleProfile = VehicleProfile(),
    ): Result<DrivingRouteOptions>
}
