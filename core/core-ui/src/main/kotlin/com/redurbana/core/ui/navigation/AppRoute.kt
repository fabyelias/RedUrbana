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

    /**
     * Pantalla de inicio de toda la app: mapa persistente donde se elige el
     * destino tocando el mapa (reemplaza a la vieja Dashboard y a la
     * búsqueda por texto). Ver `feature-lines/ExploreMapScreen`.
     */
    @Serializable
    data object Explore : AppRoute

    /**
     * [routeId] null = se entró sin elegir destino (bottom nav directo): el
     * mapa no dibuja ningún vehículo hasta que se elige una línea desde
     * [Explore]. No-null = se navegó desde un itinerario elegido, el mapa
     * muestra solo esa línea.
     *
     * [alightingLat]/[alightingLng]/[alightingStopName] son opcionales: solo
     * vienen completos cuando se llega acá desde "Iniciar viaje" — es lo que
     * le permite al mapa avisar "bajate en la próxima parada" cuando el
     * usuario se acerca de verdad.
     *
     * [alightingLat]/[alightingLng] son String (no Double): Navigation
     * Compose serializa rutas tipadas solo con String/Int/Float/Long/Boolean
     * (y sus versiones nullable) — Double no tiene un NavType propio y
     * crashea en runtime con "Cannot cast ... to a NavType". Se parsean con
     * toDoubleOrNull() del lado de quien los lee (LiveMapViewModel).
     */
    @Serializable
    data class LiveMap(
        val routeId: String? = null,
        val alightingLat: String? = null,
        val alightingLng: String? = null,
        val alightingStopName: String? = null,
    ) : AppRoute

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

    /**
     * Navegación paso a paso en auto (cámara siguiendo, instrucciones de
     * giro, voz, recálculo si te desviás) — ver `feature-lines/navigation/CarNavigationScreen`.
     * El origen NO viaja acá: lo toma la pantalla del GPS en vivo. Coordenadas
     * como String por el mismo motivo que [LiveMap.alightingLat]/[LiveMap.alightingLng].
     */
    @Serializable
    data class CarNavigation(
        val destinationLat: String,
        val destinationLng: String,
        val destinationName: String,
    ) : AppRoute
}
