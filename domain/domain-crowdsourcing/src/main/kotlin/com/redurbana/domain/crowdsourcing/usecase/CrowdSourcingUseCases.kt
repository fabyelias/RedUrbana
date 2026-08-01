package com.redurbana.domain.crowdsourcing.usecase

import com.redurbana.domain.crowdsourcing.CrowdSourcingRepository
import com.redurbana.domain.crowdsourcing.model.CrowdPing
import com.redurbana.domain.crowdsourcing.model.VehicleGroupEstimate
import com.redurbana.domain.transport.model.RouteId
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveCrowdSourcingOptInUseCase @Inject constructor(
    private val repository: CrowdSourcingRepository,
) {
    operator fun invoke(): Flow<Boolean> = repository.observeOptIn()
}

class SetCrowdSourcingOptInUseCase @Inject constructor(
    private val repository: CrowdSourcingRepository,
) {
    suspend operator fun invoke(enabled: Boolean) = repository.setOptIn(enabled)
}

/** Llamado únicamente por LocationReporter (data-crowdsourcing), cuando el heurístico da LikelyOnBus. */
class ReportCrowdPingUseCase @Inject constructor(
    private val repository: CrowdSourcingRepository,
) {
    suspend operator fun invoke(ping: CrowdPing): Result<Unit> = repository.sendPing(ping)
}

class ObserveVehicleGroupEstimatesUseCase @Inject constructor(
    private val repository: CrowdSourcingRepository,
) {
    operator fun invoke(routeId: RouteId): Flow<List<VehicleGroupEstimate>> = repository.observeGroupEstimates(routeId)
}
