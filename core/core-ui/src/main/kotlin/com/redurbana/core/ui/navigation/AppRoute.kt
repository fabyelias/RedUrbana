package com.redurbana.core.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Rutas tipadas de Navigation Compose (2.8+). Viven en core-ui —no en :app—
 * para que cada feature pueda declarar hacia dónde navega (ej: LiveMapScreen
 * navega a VehicleDetail) sin depender del módulo :app.
 *
 * El NavHost real que las registra vive en :app (RedUrbanaNavHost), el único
 * módulo que conoce a todas las features a la vez.
 */
sealed interface AppRoute {

    @Serializable
    data object Dashboard : AppRoute

    /**
     * [routeId] null = se entró sin elegir destino (bottom nav directo): el
     * mapa no dibuja ningún vehículo hasta que se elige una línea vía
     * [Lines]. No-null = se navegó desde un itinerario elegido, el mapa
     * muestra solo esa línea.
     *
     * [alightingLat]/[alightingLng]/[alightingStopName] son opcionales: solo
     * vienen completos cuando se llega acá desde "Iniciar viaje" en
     * [TripDetail] — es lo que le permite al mapa avisar "bajate en la
     * próxima parada" cuando el usuario se acerca de verdad.
     */
    @Serializable
    data class LiveMap(
        val routeId: String? = null,
        val alightingLat: Double? = null,
        val alightingLng: Double? = null,
        val alightingStopName: String? = null,
    ) : AppRoute

    /** Route del grafo anidado que agrupa [Lines] y [TripDetail] — nunca se muestra directamente. */
    @Serializable
    data object TripPlanningGraph : AppRoute

    @Serializable
    data object Lines : AppRoute

    /** [index] identifica el itinerario dentro de la lista ya calculada por LinesViewModel (ViewModel compartido a nivel de grafo, no se recalcula nada). */
    @Serializable
    data class TripDetail(val index: Int) : AppRoute

    @Serializable
    data object More : AppRoute

    @Serializable
    data object Stops : AppRoute

    @Serializable
    data object Favorites : AppRoute

    @Serializable
    data object Alerts : AppRoute

    @Serializable
    data object History : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class VehicleDetail(
        val routeId: String,
        val vehicleId: String,
    ) : AppRoute
}
