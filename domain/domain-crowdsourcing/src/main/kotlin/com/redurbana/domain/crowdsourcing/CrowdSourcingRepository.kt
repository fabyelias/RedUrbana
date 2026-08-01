package com.redurbana.domain.crowdsourcing

import com.redurbana.domain.crowdsourcing.model.CrowdPing
import com.redurbana.domain.crowdsourcing.model.VehicleGroupEstimate
import com.redurbana.domain.transport.model.RouteId
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para todo lo relacionado a la colaboración anónima de ubicación.
 * Mismo principio que TransportDataProvider: la UI y el resto del dominio
 * dependen de esta interfaz, nunca de si el backend detrás es HTTP, un
 * WebSocket, Firebase, o lo que sea que se termine usando.
 */
interface CrowdSourcingRepository {

    /** Si el usuario activó "Colaborar con la comunidad" en Ajustes. Apagado por defecto. */
    fun observeOptIn(): Flow<Boolean>

    suspend fun setOptIn(enabled: Boolean)

    /**
     * Envía un ping anónimo. Se llama solo cuando [OnBusHeuristic] devuelve
     * [OnBusSignal.LikelyOnBus] Y el usuario tiene el opt-in activo — la
     * implementación no debería tener que volver a chequear el opt-in, pero
     * lo hace de todas formas como defensa en profundidad.
     */
    suspend fun sendPing(ping: CrowdPing): Result<Unit>

    /**
     * Estimaciones ya calculadas por el backend a partir de los pings de
     * TODOS los usuarios (no solo el propio dispositivo). Esto es lo que
     * después se combina con TransportDataProvider para refinar posiciones.
     */
    fun observeGroupEstimates(routeId: RouteId): Flow<List<VehicleGroupEstimate>>
}
