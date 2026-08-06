package com.redurbana.data.transport.directions

import android.content.Context
import com.redurbana.domain.transport.DirectionsProvider
import com.redurbana.domain.transport.model.DrivingRoute
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteStep
import com.redurbana.domain.transport.model.VehicleDimensions
import com.redurbana.domain.transport.model.VehicleProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

enum class DirectionsProfile(val pathSegment: String) {
    WALKING("walking"),
    // "driving-traffic", no "driving": usa congestión en vivo para elegir
    // calles, no solo la ruta más corta en el mapa base — reporte de campo:
    // el perfil simple mandaba por una avenida (Santa Fe) en vez de calles
    // internas más rápidas para ese viaje puntual. Mismo perfil que usan
    // Waze/Google Maps para navegación real.
    DRIVING("driving-traffic"),
}

data class MapboxRoute(
    val distanceMeters: Double,
    val durationMinutes: Int,
    val polyline: List<GeoPoint>,
    val steps: List<RouteStep> = emptyList(),
)

@Serializable
private data class DirectionsResponseDto(val routes: List<DirectionsRouteDto> = emptyList())

@Serializable
private data class DirectionsRouteDto(
    val distance: Double,
    val duration: Double,
    val geometry: DirectionsGeometryDto,
    val legs: List<DirectionsLegDto> = emptyList(),
)

@Serializable
private data class DirectionsGeometryDto(val coordinates: List<List<Double>>)

@Serializable
private data class DirectionsLegDto(val steps: List<DirectionsStepDto> = emptyList())

@Serializable
private data class DirectionsStepDto(
    val distance: Double,
    val geometry: DirectionsGeometryDto,
    val maneuver: DirectionsManeuverDto,
    // Solo el primer anuncio de cada paso: es el que corresponde al inicio
    // del tramo (los siguientes, si hay, son repeticiones a distancias
    // menores dentro del mismo giro — no hacen falta para esta versión).
    val voiceInstructions: List<VoiceInstructionDto> = emptyList(),
)

@Serializable
private data class DirectionsManeuverDto(
    val instruction: String,
    val type: String,
    val modifier: String? = null,
    val location: List<Double>,
)

@Serializable
private data class VoiceInstructionDto(val announcement: String)

/**
 * Ruta real (sigue calles, no línea recta) vía la API REST de Mapbox
 * Directions, con una llamada directa por [HttpURLConnection] en vez del SDK
 * nativo de Directions: esta sesión ya perdió horas por desajustes de
 * versión entre componentes nativos de Mapbox (Search SDK vs Maps SDK, ver
 * `mapboxSearch` en gradle/libs.versions.toml) — una llamada REST plana con
 * el mismo token público no tiene ese riesgo ni suma otra dependencia nativa.
 *
 * Implementa [DirectionsProvider] (perfil auto, para el modo "Auto" de
 * ExploreMapScreen) Y sigue siendo el cliente de bajo nivel que TripPlanner
 * usa directo para caminata — TripPlanner vive en data-transport, no cruza
 * la frontera de dominio que sí importa para una feature como feature-lines.
 */
@Singleton
class DirectionsClient @Inject constructor(
    @ApplicationContext private val context: Context,
) : DirectionsProvider {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun route(
        from: GeoPoint,
        to: GeoPoint,
        profile: DirectionsProfile = DirectionsProfile.WALKING,
        // Solo hace falta para el modo Auto (instrucciones de giro + voz para
        // la navegación paso a paso) — TripPlanner llama acá varias veces por
        // búsqueda (una por cada pata de caminata de cada alternativa
        // candidata), así que no vale la pena pedir/parsear ese payload extra
        // en ese caso.
        withSteps: Boolean = false,
        // Solo se manda si el vehículo elegido lo necesita (camión, colectivo,
        // ambulancia, bomberos — ver VehicleCategory.requiresDimensions): son
        // parámetros reales de la Directions API, "mejor esfuerzo" del lado de
        // Mapbox — max_height/width/weight evitan calles con un límite CARGADO
        // en el mapa de Mapbox por debajo de la medida del vehículo, pero si
        // esa calle puntual no tiene el dato cargado (reporte de campo: un
        // túnel real de 2m no tenía la restricción de altura en los datos de
        // Mapbox para esa zona, así que max_height=4 no lo evitó), no hace
        // nada — silenciosamente no hay restricción que respetar. Por eso
        // también se manda exclude=tunnel para vehículos grandes: no depende
        // de que el túnel tenga la altura cargada, evita CUALQUIER túnel de
        // entrada (con la misma salvedad "mejor esfuerzo": si no hay otra
        // forma de llegar, Mapbox igual lo cruza en vez de fallar la ruta).
        dimensions: VehicleDimensions? = null,
    ): Result<MapboxRoute> =
        withContext(Dispatchers.IO) {
            runCatching {
                val token = context.getString(com.redurbana.core.common.R.string.mapbox_public_token)
                val coordinates = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
                val extraParams = if (withSteps) "&steps=true&voice_instructions=true&voice_units=metric&language=es" else ""
                val dimensionParams = if (dimensions != null) {
                    "&max_height=${dimensions.heightMeters}&max_width=${dimensions.widthMeters}&max_weight=${dimensions.weightTons}&exclude=tunnel"
                } else {
                    ""
                }
                val url = URL(
                    "https://api.mapbox.com/directions/v5/mapbox/${profile.pathSegment}/$coordinates" +
                        "?geometries=geojson&overview=full$extraParams$dimensionParams&access_token=$token",
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                val body = try {
                    if (connection.responseCode !in 200..299) {
                        error("Directions API respondió ${connection.responseCode}")
                    }
                    connection.inputStream.bufferedReader().use { it.readText() }
                } finally {
                    connection.disconnect()
                }

                val route = json.decodeFromString<DirectionsResponseDto>(body).routes.firstOrNull()
                    ?: throw NoSuchElementException("Sin ruta entre esos puntos (perfil ${profile.pathSegment})")
                MapboxRoute(
                    distanceMeters = route.distance,
                    durationMinutes = (route.duration / 60.0).roundToInt().coerceAtLeast(1),
                    polyline = route.geometry.coordinates.map { GeoPoint(latitude = it[1], longitude = it[0]) },
                    steps = route.legs.flatMap { leg -> leg.steps.map { it.toRouteStep() } },
                )
            }
        }

    private fun DirectionsStepDto.toRouteStep() = RouteStep(
        instruction = maneuver.instruction,
        distanceMeters = distance,
        maneuverLocation = GeoPoint(latitude = maneuver.location[1], longitude = maneuver.location[0]),
        maneuverType = maneuver.type,
        maneuverModifier = maneuver.modifier,
        voiceAnnouncement = voiceInstructions.firstOrNull()?.announcement,
        polyline = geometry.coordinates.map { GeoPoint(latitude = it[1], longitude = it[0]) },
    )

    override suspend fun getDrivingRoute(
        origin: GeoPoint,
        destination: GeoPoint,
        vehicleProfile: VehicleProfile,
    ): Result<DrivingRoute> =
        route(origin, destination, DirectionsProfile.DRIVING, withSteps = true, dimensions = vehicleProfile.dimensions).map {
            DrivingRoute(distanceMeters = it.distanceMeters, durationMinutes = it.durationMinutes, polyline = it.polyline, steps = it.steps)
        }
}
