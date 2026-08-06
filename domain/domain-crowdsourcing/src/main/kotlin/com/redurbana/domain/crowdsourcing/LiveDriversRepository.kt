package com.redurbana.domain.crowdsourcing

import com.redurbana.domain.crowdsourcing.model.DriverSessionId
import com.redurbana.domain.crowdsourcing.model.LiveDriverPosition
import com.redurbana.domain.transport.GeoBounds
import kotlinx.coroutines.flow.Flow

/**
 * Contrato para "verse manejando" estilo Waze — separado de
 * [CrowdSourcingRepository] a propósito, aunque las dos hablan con la misma
 * tabla de Supabase por debajo: el modelo de privacidad es distinto
 * (ver [com.redurbana.domain.crowdsourcing.model.DriverSessionId]) y mezclar
 * las dos interfaces haría que una sola clase tuviera que entender ambos.
 *
 * Sin opt-in propio: se activa solo con el hecho de elegir "Vehículo" y
 * tocar "Comenzar viaje" (pantalla de navegación paso a paso), nunca en
 * segundo plano ni por sorpresa — a diferencia del crowdsourcing de
 * colectivo (silencioso, por eso SÍ necesita un opt-in explícito en
 * Ajustes). Si más adelante hace falta poder desactivarlo aparte, agregar
 * acá el mismo patrón observeOptIn/setOptIn que ya tiene CrowdSourcingRepository.
 */
interface LiveDriversRepository {

    /** Publica/actualiza la posición propia — upsert por sessionId, no un historial. */
    suspend fun publishPosition(position: LiveDriverPosition): Result<Unit>

    /** Se llama al salir de la navegación: borra la fila propia en vez de esperar a que expire sola. */
    suspend fun stopSharing(sessionId: DriverSessionId)

    /**
     * Otros vehículos visibles cerca de [bounds], sin incluir [excludingSessionId]
     * (la propia sesión, si está compartiendo). Vuelve a pedir sola cada
     * pocos segundos mientras haya un colector activo — no hay push en
     * tiempo real todavía (ver comentario en la implementación real).
     */
    fun observeNearbyDrivers(bounds: GeoBounds, excludingSessionId: DriverSessionId): Flow<List<LiveDriverPosition>>
}
