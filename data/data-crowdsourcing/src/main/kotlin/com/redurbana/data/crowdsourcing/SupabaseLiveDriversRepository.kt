package com.redurbana.data.crowdsourcing

import com.redurbana.domain.crowdsourcing.LiveDriversRepository
import com.redurbana.domain.crowdsourcing.model.DriverSessionId
import com.redurbana.domain.crowdsourcing.model.LiveDriverPosition
import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.VehicleCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private const val POLL_INTERVAL_MS = 3_000L

/**
 * Implementación real contra Supabase (tabla `live_drivers`, ver
 * supabase/migrations/0003_live_drivers.sql). A diferencia de
 * [SupabaseCrowdSourcingRepository.observeGroupEstimates] (un solo fetch por
 * colección — ver comentario ahí), acá SÍ hace falta sondear de verdad: el
 * pedido explícito es ver a otros moverse en vivo, no una foto fija.
 *
 * Sin Supabase Realtime (WebSocket) todavía — un poll simple cada 3s es
 * consistente con el resto de la app (LocationReporter reporta cada 5s) y
 * evita sumar una dependencia nueva (`realtime-kt`) para una primera
 * versión. Si más adelante hace falta que se sienta más instantáneo,
 * cambiar a Realtime acá adentro no afecta a nadie que use la interfaz.
 */
@Singleton
class SupabaseLiveDriversRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : LiveDriversRepository {

    override suspend fun publishPosition(position: LiveDriverPosition): Result<Unit> = try {
        supabaseClient.postgrest["live_drivers"].upsert(position.toRow()) { onConflict = "session_id" }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun stopSharing(sessionId: DriverSessionId) {
        try {
            supabaseClient.postgrest["live_drivers"].delete { filter { eq("session_id", sessionId.value) } }
        } catch (e: Exception) {
            // Best-effort: si falla (sin red al salir, por ejemplo), la fila
            // igual desaparece sola por el cron de limpieza de filas viejas.
        }
    }

    override fun observeNearbyDrivers(bounds: GeoBounds, excludingSessionId: DriverSessionId): Flow<List<LiveDriverPosition>> = flow {
        while (true) {
            val drivers = try {
                supabaseClient.postgrest["live_drivers"]
                    .select {
                        filter {
                            gte("latitude", bounds.southWest.latitude)
                            lte("latitude", bounds.northEast.latitude)
                            gte("longitude", bounds.southWest.longitude)
                            lte("longitude", bounds.northEast.longitude)
                            neq("session_id", excludingSessionId.value)
                        }
                    }
                    .decodeList<LiveDriverRow>()
                    .map { it.toDomain() }
            } catch (e: Exception) {
                emptyList()
            }
            emit(drivers)
            delay(POLL_INTERVAL_MS)
        }
    }
}

@Serializable
private data class LiveDriverRow(
    @SerialName("session_id") val sessionId: String,
    val latitude: Double,
    val longitude: Double,
    @SerialName("bearing_degrees") val bearingDegrees: Float,
    @SerialName("vehicle_category") val vehicleCategory: String,
    @SerialName("updated_at") val updatedAt: String,
)

private fun LiveDriverPosition.toRow() = LiveDriverRow(
    sessionId = sessionId.value,
    latitude = position.latitude,
    longitude = position.longitude,
    bearingDegrees = bearingDegrees,
    vehicleCategory = vehicleCategory.name,
    updatedAt = updatedAt.toString(), // ISO-8601, compatible con timestamptz de Postgres
)

private fun LiveDriverRow.toDomain() = LiveDriverPosition(
    sessionId = DriverSessionId(sessionId),
    position = GeoPoint(latitude, longitude),
    bearingDegrees = bearingDegrees,
    // Categoría desconocida (ej. app vieja de otro usuario con una versión
    // futura o pasada que agregó/sacó categorías) cae a CAR en vez de
    // explotar: un ícono de auto de más nunca es peor que una excepción que
    // tira abajo el mapa de todos.
    vehicleCategory = runCatching { VehicleCategory.valueOf(vehicleCategory) }.getOrDefault(VehicleCategory.CAR),
    updatedAt = Instant.parse(updatedAt),
)
