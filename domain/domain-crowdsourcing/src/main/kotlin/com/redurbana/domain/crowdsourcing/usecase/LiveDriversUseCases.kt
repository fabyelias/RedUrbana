package com.redurbana.domain.crowdsourcing.usecase

import com.redurbana.domain.crowdsourcing.LiveDriversRepository
import com.redurbana.domain.crowdsourcing.model.DriverSessionId
import com.redurbana.domain.crowdsourcing.model.LiveDriverPosition
import com.redurbana.domain.transport.GeoBounds
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Llamado por CarNavigationViewModel mientras la navegación en auto está activa. */
class PublishLiveDriverPositionUseCase @Inject constructor(
    private val repository: LiveDriversRepository,
) {
    suspend operator fun invoke(position: LiveDriverPosition): Result<Unit> = repository.publishPosition(position)
}

/** Llamado al salir de la navegación en auto (botón salir), para no depender solo de que la fila expire sola. */
class StopSharingLiveDriverUseCase @Inject constructor(
    private val repository: LiveDriversRepository,
) {
    suspend operator fun invoke(sessionId: DriverSessionId) = repository.stopSharing(sessionId)
}

/** Llamado por ExploreViewModel para mostrar otros vehículos en vivo en el mapa principal. */
class ObserveNearbyLiveDriversUseCase @Inject constructor(
    private val repository: LiveDriversRepository,
) {
    operator fun invoke(bounds: GeoBounds, excludingSessionId: DriverSessionId): Flow<List<LiveDriverPosition>> =
        repository.observeNearbyDrivers(bounds, excludingSessionId)
}
