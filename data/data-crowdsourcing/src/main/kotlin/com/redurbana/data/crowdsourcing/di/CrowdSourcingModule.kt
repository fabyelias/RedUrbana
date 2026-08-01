package com.redurbana.data.crowdsourcing.di

import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.data.crowdsourcing.LocalCrowdSourcingRepository
import com.redurbana.data.crowdsourcing.mock.MockDeviceLocationSource
import com.redurbana.domain.crowdsourcing.CrowdSourcingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Mismo patrón que TransportModule: acá se decide qué implementación está
 * activa, sin que el resto de la app se entere.
 *
 * - DeviceLocationSource: MockDeviceLocationSource hoy (simula estar en
 *   colectivo, útil para probar el heurístico y el reporter sin moverte).
 *   Cambiar a FusedDeviceLocationSource (core-location) cuando esa
 *   implementación esté completa.
 * - CrowdSourcingRepository: LocalCrowdSourcingRepository hoy (opt-in real,
 *   pings guardados localmente, sin agregación real por falta de backend).
 *   Cambiar cuando exista el servidor que triangula posiciones.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CrowdSourcingModule {

    @Binds
    abstract fun bindDeviceLocationSource(impl: MockDeviceLocationSource): DeviceLocationSource

    @Binds
    abstract fun bindCrowdSourcingRepository(impl: LocalCrowdSourcingRepository): CrowdSourcingRepository
}
