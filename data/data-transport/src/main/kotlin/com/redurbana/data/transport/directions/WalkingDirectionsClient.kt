package com.redurbana.data.transport.directions

import android.content.Context
import com.redurbana.domain.transport.model.GeoPoint
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

data class WalkingRoute(
    val distanceMeters: Double,
    val durationMinutes: Int,
    val polyline: List<GeoPoint>,
)

@Serializable
private data class DirectionsResponseDto(val routes: List<DirectionsRouteDto> = emptyList())

@Serializable
private data class DirectionsRouteDto(val distance: Double, val duration: Double, val geometry: DirectionsGeometryDto)

@Serializable
private data class DirectionsGeometryDto(val coordinates: List<List<Double>>)

/**
 * Caminata real (sigue calles) vía la API REST de Mapbox Directions, con
 * una llamada directa por [HttpURLConnection] en vez del SDK nativo de
 * Directions: esta sesión ya perdió horas por desajustes de versión entre
 * componentes nativos de Mapbox (Search SDK vs Maps SDK, ver
 * `mapboxSearch` en gradle/libs.versions.toml) — una llamada REST plana con
 * el mismo token público no tiene ese riesgo ni suma otra dependencia nativa.
 */
@Singleton
class WalkingDirectionsClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun route(from: GeoPoint, to: GeoPoint): Result<WalkingRoute> = withContext(Dispatchers.IO) {
        runCatching {
            val token = context.getString(com.redurbana.core.common.R.string.mapbox_public_token)
            val coordinates = "${from.longitude},${from.latitude};${to.longitude},${to.latitude}"
            val url = URL(
                "https://api.mapbox.com/directions/v5/mapbox/walking/$coordinates" +
                    "?geometries=geojson&overview=full&access_token=$token",
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
                ?: throw NoSuchElementException("Sin ruta de caminata entre esos puntos")
            WalkingRoute(
                distanceMeters = route.distance,
                durationMinutes = (route.duration / 60.0).roundToInt().coerceAtLeast(1),
                polyline = route.geometry.coordinates.map { GeoPoint(latitude = it[1], longitude = it[0]) },
            )
        }
    }
}
