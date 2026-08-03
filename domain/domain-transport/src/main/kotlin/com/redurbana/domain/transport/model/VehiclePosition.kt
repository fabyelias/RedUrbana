package com.redurbana.domain.transport.model

import kotlinx.datetime.Instant
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Coordenada geográfica agnóstica del SDK de mapas usado.
 * (No es com.google.android.gms.maps.model.LatLng a propósito:
 * el dominio no depende de Google Maps ni de ningún proveedor).
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Distancia en línea recta (haversine), no distancia de recorrido real. */
fun GeoPoint.distanceMeters(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val deltaLat = Math.toRadians(other.latitude - latitude)
    val deltaLon = Math.toRadians(other.longitude - longitude)
    val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
        cos(lat1) * cos(lat2) * sin(deltaLon / 2) * sin(deltaLon / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
}

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
