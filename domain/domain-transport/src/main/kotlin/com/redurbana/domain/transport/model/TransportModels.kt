package com.redurbana.domain.transport.model

import kotlinx.datetime.Instant

/** Puntuación de confiabilidad normalizada 0..100, usada en toda la UI. */
@JvmInline
value class ReliabilityScore(val percent: Int) {
    init {
        require(percent in 0..100) { "ReliabilityScore debe estar entre 0 y 100" }
    }
}

data class Branch(
    val id: String,
    val name: String,
    val path: List<GeoPoint>,
)

data class RouteDetails(
    val routeId: RouteId,
    val shortName: String,
    val company: String,
    val branches: List<Branch>,
    val colorSeed: String,
    val reliability: ReliabilityScore,
)

data class Stop(
    val stopId: StopId,
    val name: String,
    val location: GeoPoint,
    val routesServed: List<RouteId>,
)

data class ArrivalEstimate(
    val routeId: RouteId,
    val stopId: StopId,
    val etaMinutes: Int,
    val confidence: ReliabilityScore,
)

enum class AlertType { DETOUR, CLOSURE, PROTEST, ACCIDENT, STRIKE }
enum class AlertSeverity { INFO, WARNING, SEVERE }

data class ServiceAlert(
    val id: String,
    val type: AlertType,
    val affectedRoutes: List<RouteId>,
    val message: String,
    val severity: AlertSeverity,
    val startedAt: Instant,
)

/**
 * Describe qué puede ofrecer cada fuente de datos, para que las capas superiores
 * puedan degradar funcionalidad con elegancia (ej: un provider sin alertas
 * simplemente oculta esa sección en vez de romper).
 */
data class ProviderCapabilities(
    val supportsRealtimePositions: Boolean,
    val supportsArrivalEstimates: Boolean,
    val supportsServiceAlerts: Boolean,
    val supportsReliabilityScore: Boolean,
)
