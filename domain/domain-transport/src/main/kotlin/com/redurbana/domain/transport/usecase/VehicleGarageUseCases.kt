package com.redurbana.domain.transport.usecase

import com.redurbana.domain.transport.VehicleGarageRepository
import com.redurbana.domain.transport.model.VehicleCategory
import com.redurbana.domain.transport.model.VehicleProfile
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSavedVehiclesUseCase @Inject constructor(
    private val repository: VehicleGarageRepository,
) {
    operator fun invoke(): Flow<List<VehicleProfile>> = repository.observeSavedVehicles()
}

class ObserveActiveVehicleUseCase @Inject constructor(
    private val repository: VehicleGarageRepository,
) {
    operator fun invoke(): Flow<VehicleProfile> = repository.observeActiveVehicle()
}

/** Llamado al elegir un vehículo en ExploreMapScreen — guarda y activa en el mismo paso. */
class SelectVehicleUseCase @Inject constructor(
    private val repository: VehicleGarageRepository,
) {
    suspend operator fun invoke(profile: VehicleProfile) = repository.selectVehicle(profile)
}

class RemoveVehicleUseCase @Inject constructor(
    private val repository: VehicleGarageRepository,
) {
    suspend operator fun invoke(category: VehicleCategory) = repository.removeVehicle(category)
}
