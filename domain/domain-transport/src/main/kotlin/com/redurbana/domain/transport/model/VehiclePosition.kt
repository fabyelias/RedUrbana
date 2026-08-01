package com.redurbana.domain.transport.model

import kotlinx.datetime.Instant

/**
 * Coordenada geográfica agnóstica del SDK de mapas usado.
 * (No es com.google.android.gms.maps.model.LatLng a propósito:
 * el dominio no depende de Google Maps ni de ningún proveedor).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

enum class VehicleStatus {
    ON_TIME,
    DELAYED,
    OUT_OF_SERVICE,
    UNKNOWN,
}

/**
 * Posición en tiempo real de un colectivo, en el "idioma común" de la app.
 * Cualquier proveedor (GTFS-RT, API del GCBA, crowdsourcing, mock) debe
 * mapear sus datos a esto.
 *
 * [positionConfidence] y [contributingReports] son nullable A PROPÓSITO:
 * una fuente oficial (GPS del propio colectivo) no tiene "cantidad de
 * reportes" — el dato ya es la posición real, punto. Solo las fuentes
 * agregadas (hoy, [com.redurbana.domain.transport.model.RouteDetails] vía
 * CrowdsourcedTransportProvider) completan estos campos, y son los únicos
 * que la UI debe usar para decidir si mostrar un colectivo "sólido" o
 * "estimado" — null significa "no aplica ese concepto acá", no "confianza cero".
 */
data class VehiclePosition(
    val vehicleId: VehicleId,
    val routeId: RouteId,
    val position: GeoPoint,
    val bearingDegrees: Float,
    val speedKmh: Float,
    val timestamp: Instant,
    val status: VehicleStatus,
    val branchId: String?,
    val internalNumber: String?,
    val positionConfidence: ReliabilityScore? = null,
    val contributingReports: Int? = null,
)
