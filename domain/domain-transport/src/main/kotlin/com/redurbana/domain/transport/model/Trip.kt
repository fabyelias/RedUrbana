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
     *
     * [typicalHeadwayMinutes] es distinto: sale de un GTFS oficial de 2019
     * (frecuencia histórica por línea/día/franja horaria), no de la posición
     * simulada — es "cada cuánto pasa normalmente", no una predicción del
     * próximo. Puede coexistir con [nextDepartureMinutes] siendo null (o
     * viceversa) porque salen de fuentes independientes.
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
        val typicalHeadwayMinutes: Double? = null,
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

/** Ruta real en auto entre dos puntos (sigue calles, no línea recta) — ver [com.redurbana.domain.transport.DirectionsProvider]. */
data class DrivingRoute(
    val distanceMeters: Double,
    val durationMinutes: Int,
    val polyline: List<GeoPoint>,
    /** Vacío salvo que se haya pedido con instrucciones de giro (navegación paso a paso) — ver [RouteStep]. */
    val steps: List<RouteStep> = emptyList(),
    /**
     * Restricciones que esta ruta NO pudo evitar pese a haberlas pedido
     * (max_height/width/weight, exclude=tunnel — ver DirectionsClient) porque
     * no había otra forma real de llegar al destino. Mapbox lo reporta él
     * mismo en la respuesta (`notifications`), no es algo que este código
     * infiera — reporte de campo: un túnel real de altura desconocida no fue
     * evitado, y esto es lo que permite avisarlo en vez de que pase
     * silencioso.
     */
    val unavoidableViolations: Set<RouteRestrictionViolation> = emptySet(),
)

enum class RouteRestrictionViolation { TUNNEL, MAX_HEIGHT, MAX_WIDTH, MAX_WEIGHT }

/**
 * Las dos rutas que le importan a un vehículo grande cuando de verdad hay
 * una diferencia entre ellas — ver [com.redurbana.domain.transport.DirectionsProvider.getAlternativeDrivingRoutes].
 * [direct] queda en null cuando evitar túneles/restricciones NO cambió la
 * ruta (la enorme mayoría de los viajes): en ese caso no tiene sentido
 * ofrecer una "alternativa" idéntica a la recomendada.
 */
data class DrivingRouteOptions(
    /** La que respeta max_height/width/weight y exclude=tunnel — la que ya se ofrecía antes de esto. */
    val recommended: DrivingRoute,
    /** La más rápida SIN esas restricciones — puede pasar por un túnel bajo o una calle angosta para este vehículo. Null si coincide con [recommended]. */
    val direct: DrivingRoute? = null,
)

/**
 * Un tramo entre dos maniobras consecutivas de una [DrivingRoute] (ej. "girá
 * a la izquierda en Av. Corrientes"). [voiceAnnouncement] viene pre-armado
 * por Mapbox en español (`language=es`, `voice_instructions=true`); si no
 * vino, el texto escrito ([instruction]) sirve igual de fallback para leer
 * en voz alta.
 */
data class RouteStep(
    val instruction: String,
    val distanceMeters: Double,
    val maneuverLocation: GeoPoint,
    val maneuverType: String,
    val maneuverModifier: String?,
    val voiceAnnouncement: String?,
    val polyline: List<GeoPoint>,
)
