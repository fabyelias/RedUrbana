package com.redurbana.data.crowdsourcing

import com.redurbana.domain.crowdsourcing.CrowdSourcingRepository
import com.redurbana.domain.crowdsourcing.model.CrowdPing
import com.redurbana.domain.crowdsourcing.model.VehicleGroupEstimate
import com.redurbana.domain.transport.model.RouteId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación local, SIN backend todavía.
 *
 * El opt-in sí es 100% real y persiste en memoria (TODO: mover a DataStore
 * para que sobreviva un reinicio de la app — es la única pieza que falta
 * para que esta parte esté completa).
 *
 * `sendPing` y `observeGroupEstimates` son honestamente limitados: la
 * triangulación necesita ver los pings de OTROS dispositivos al mismo
 * tiempo, algo que ningún cliente puede hacer por sí solo (a diferencia de
 * MockTransportProvider, que sí puede simular una flota completa porque esa
 * simulación no depende de otros usuarios reales). `sendPing` guarda el
 * ping localmente (útil para debug/logs) pero no lo "envía" a ningún lado
 * — no hay ningún lado todavía. `observeGroupEstimates` devuelve lista
 * vacía por la misma razón: no hay con quién agregar.
 *
 * Se reemplaza por una implementación HTTP/WebSocket real el día que exista
 * el backend — mismo @Binds, mismo patrón que TransportModule.
 */
@Singleton
class LocalCrowdSourcingRepository @Inject constructor() : CrowdSourcingRepository {

    private val optInState = MutableStateFlow(false) // apagado por defecto — opt-in explícito
    private val receivedPingsForDebug = mutableListOf<CrowdPing>()

    override fun observeOptIn(): Flow<Boolean> = optInState.asStateFlow()

    override suspend fun setOptIn(enabled: Boolean) {
        optInState.value = enabled
    }

    override suspend fun sendPing(ping: CrowdPing): Result<Unit> {
        receivedPingsForDebug.add(ping)
        if (receivedPingsForDebug.size > 200) receivedPingsForDebug.removeAt(0)
        // TODO: reemplazar por POST/WebSocket al backend cuando exista.
        return Result.success(Unit)
    }

    override fun observeGroupEstimates(routeId: RouteId): Flow<List<VehicleGroupEstimate>> =
        flowOf(emptyList()) // sin backend no hay otros dispositivos con quién agregar — ver doc de la clase
}
