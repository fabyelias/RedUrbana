package com.redurbana.data.crowdsourcing

import com.redurbana.domain.crowdsourcing.CrowdSourcingRepository
import com.redurbana.domain.crowdsourcing.model.CrowdPing
import com.redurbana.domain.crowdsourcing.model.VehicleGroupEstimate
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteId
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real contra Supabase (ver supabase/README.md y
 * supabase/migrations/0001_init_schema.sql para el schema y las políticas
 * de RLS). El opt-in sigue siendo un estado local — es una preferencia del
 * dispositivo, no algo que tenga sentido guardar en el backend.
 *
 * `observeGroupEstimates` hace un fetch por cada colección del Flow (no hay
 * suscripción realtime todavía) — razonable mientras `vehicle_group_estimates`
 * esté vacía por falta de backend de agregación; conviene revisar esto
 * (Supabase Realtime o polling) el día que ese backend exista de verdad.
 */
@Singleton
class SupabaseCrowdSourcingRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : CrowdSourcingRepository {

    private val optInState = MutableStateFlow(false) // apagado por defecto — opt-in explícito

    override fun observeOptIn(): Flow<Boolean> = optInState.asStateFlow()

    override suspend fun setOptIn(enabled: Boolean) {
        optInState.value = enabled
    }

    override suspend fun sendPing(ping: CrowdPing): Result<Unit> = try {
        supabaseClient.postgrest["crowd_pings"].insert(ping.toRow())
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override fun observeGroupEstimates(routeId: RouteId): Flow<List<VehicleGroupEstimate>> = flow {
        val estimates = try {
            supabaseClient.postgrest["vehicle_group_estimates"]
                .select { filter { eq("route_id", routeId.value) } }
                .decodeList<VehicleGroupEstimateRow>()
                .map { it.toDomain() }
        } catch (e: Exception) {
            emptyList()
        }
        emit(estimates)
    }
}

@Serializable
private data class CrowdPingRow(
    @SerialName("session_id") val sessionId: String,
    @SerialName("candidate_route_id") val candidateRouteId: String? = null,
    val latitude: Double,
    val longitude: Double,
    @SerialName("speed_kmh") val speedKmh: Float,
    @SerialName("bearing_degrees") val bearingDegrees: Float,
    @SerialName("recorded_at") val recordedAt: String,
)

private fun CrowdPing.toRow() = CrowdPingRow(
    sessionId = sessionId.value,
    candidateRouteId = candidateRouteId?.value,
    latitude = position.latitude,
    longitude = position.longitude,
    speedKmh = speedKmh,
    bearingDegrees = bearingDegrees,
    recordedAt = timestamp.toString(), // ISO-8601, compatible con timestamptz de Postgres
)

@Serializable
private data class VehicleGroupEstimateRow(
    @SerialName("route_id") val routeId: String,
    @SerialName("estimated_latitude") val estimatedLatitude: Double,
    @SerialName("estimated_longitude") val estimatedLongitude: Double,
    @SerialName("estimated_bearing_degrees") val estimatedBearingDegrees: Float,
    @SerialName("estimated_speed_kmh") val estimatedSpeedKmh: Float,
    @SerialName("contributing_ping_count") val contributingPingCount: Int,
    @SerialName("confidence_percent") val confidencePercent: Int,
    @SerialName("last_updated") val lastUpdated: String,
)

private fun VehicleGroupEstimateRow.toDomain() = VehicleGroupEstimate(
    routeId = RouteId(routeId),
    estimatedPosition = GeoPoint(estimatedLatitude, estimatedLongitude),
    estimatedBearingDegrees = estimatedBearingDegrees,
    estimatedSpeedKmh = estimatedSpeedKmh,
    contributingPingCount = contributingPingCount,
    confidence = ReliabilityScore(confidencePercent),
    lastUpdated = Instant.parse(lastUpdated),
)
