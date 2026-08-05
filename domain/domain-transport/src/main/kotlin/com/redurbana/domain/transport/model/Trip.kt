package com.redurbana.domain.transport.model

/**
 * Un tramo de un [TripItinerary]: a pie o en una línea concreta. [Walk]
 * incluye la polilínea real (Mapbox Directions), no una línea recta — así
 * el mini-mapa de detalle puede dibujar el camino de verdad.
 */
sealed interface TripLeg {
    data class Walk(
        val from: GeoPoint,
        val to: GeoPoint,
        val distanceMeters: Double,
        val durationMinutes: Int,
        val polyline: List<GeoPoint>,
    ) : TripLeg

    /**
     * [nextDepartureMinutes] es una estimación derivada de la posición
     * simulada en vivo (distancia real al [boardingStop] sobre la velocidad
     * del vehículo), no un horario programado — null si no hay ningún
     * vehículo visible todavía para esa línea.
     */
    data class Transit(
        val routeId: RouteId,
        val shortName: String,
        val colorSeed: String,
        val boardingStop: Stop,
        val alightingStop: Stop,
        val stopsCount: Int,
        val estimatedMinutes: Int,
        val nextDepartureMinutes: Int?,
        /** Ubicación real de las paradas entre subida y bajada (~cada 500m), para dibujar el mini-mapa. */
        val path: List<GeoPoint>,
    ) : TripLeg
}

/**
 * Una alternativa completa puerta a puerta: caminata + 1 o 2 tramos de
 * transporte (máximo 1 transbordo) + caminata final.
 */
data class TripItinerary(
    val legs: List<TripLeg>,
    val totalMinutes: Int,
    val transferCount: Int,
)
