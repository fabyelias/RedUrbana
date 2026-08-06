package com.redurbana.domain.transport.model

/**
 * Qué vehículo maneja el usuario en el modo "Vehículo" de ExploreMapScreen —
 * más allá de auto, porque un camión o un colectivo no caben por cualquier
 * calle. [requiresDimensions] marca los que SÍ necesitan que el usuario
 * cargue medidas antes de calcular ruta (ver [VehicleDimensions]); autos,
 * motos, camionetas y patrulleros usan los límites por defecto de Mapbox
 * (pensados para un auto común) sin pedir nada.
 */
enum class VehicleCategory(val emoji: String, val requiresDimensions: Boolean, val isEmergency: Boolean = false) {
    CAR("🚗", requiresDimensions = false),
    MOTORCYCLE("🏍️", requiresDimensions = false),
    PICKUP("🛻", requiresDimensions = false),
    TRUCK("🚚", requiresDimensions = true),
    BUS("🚌", requiresDimensions = true),
    AMBULANCE("🚑", requiresDimensions = true, isEmergency = true),
    FIRE_TRUCK("🚒", requiresDimensions = true, isEmergency = true),
    POLICE("🚓", requiresDimensions = false, isEmergency = true),
}

/**
 * Lo que Mapbox Directions realmente sabe usar para evitar calles que un
 * vehículo grande no puede pasar (`max_height`/`max_width`/`max_weight` en
 * la API real, ver DirectionsClient) — no hay parámetro de largo. Es
 * "mejor esfuerzo" del lado de Mapbox: solo evita lo que su propio mapa de
 * calles tiene cargado como restringido (por ejemplo, altura de un túnel),
 * no toda restricción real que pueda existir en la calle.
 */
data class VehicleDimensions(
    val heightMeters: Double,
    val widthMeters: Double,
    val weightTons: Double,
)

data class VehicleProfile(
    val category: VehicleCategory = VehicleCategory.CAR,
    val dimensions: VehicleDimensions? = null,
)
