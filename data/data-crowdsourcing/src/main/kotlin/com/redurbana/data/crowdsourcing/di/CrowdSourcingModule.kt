package com.redurbana.data.crowdsourcing.di

import android.content.Context
import com.redurbana.core.location.DeviceLocationSource
import com.redurbana.core.location.FusedDeviceLocationSource
import com.redurbana.data.crowdsourcing.LocationReporter
import com.redurbana.data.crowdsourcing.R
import com.redurbana.data.crowdsourcing.SupabaseCrowdSourcingRepository
import com.redurbana.data.crowdsourcing.SupabaseLiveDriversRepository
import com.redurbana.domain.crowdsourcing.CrowdSourcingRepository
import com.redurbana.domain.crowdsourcing.LiveDriversRepository
import com.redurbana.domain.crowdsourcing.TripSessionController
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import javax.inject.Singleton

/**
 * Mismo patrón que TransportModule: acá se decide qué implementación está
 * activa, sin que el resto de la app se entere.
 *
 * - DeviceLocationSource: FusedDeviceLocationSource (GPS real). Para volver
 *   a probar sin moverte, bindear MockDeviceLocationSource acá de nuevo.
 * - CrowdSourcingRepository: SupabaseCrowdSourcingRepository — pings reales
 *   a la tabla crowd_pings. observeGroupEstimates ya lee vehicle_group_estimates
 *   de verdad, pero esa tabla queda vacía hasta que exista el backend de
 *   agregación (fuera de alcance de este repo Android, ver supabase/README.md).
 * - TripSessionController: LocationReporter mismo (implementa las dos
 *   interfaces). Separado de CrowdSourcingRepository a propósito — ver el
 *   comentario en TripSessionController.kt sobre el ciclo de dependencias
 *   que evita.
 * - LiveDriversRepository: SupabaseLiveDriversRepository — tabla live_drivers
 *   (supabase/migrations/0003_live_drivers.sql), modelo de privacidad
 *   distinto al de arriba: acá SÍ se ve la posición individual de cada uno
 *   (ver LiveDriversRepository.kt).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CrowdSourcingModule {

    @Binds
    abstract fun bindDeviceLocationSource(impl: FusedDeviceLocationSource): DeviceLocationSource

    @Binds
    abstract fun bindCrowdSourcingRepository(impl: SupabaseCrowdSourcingRepository): CrowdSourcingRepository

    @Binds
    abstract fun bindTripSessionController(impl: LocationReporter): TripSessionController

    @Binds
    abstract fun bindLiveDriversRepository(impl: SupabaseLiveDriversRepository): LiveDriversRepository

    companion object {
        @Provides
        @Singleton
        fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient =
            createSupabaseClient(
                supabaseUrl = context.getString(R.string.supabase_url),
                supabaseKey = context.getString(R.string.supabase_publishable_key),
            ) {
                install(Postgrest)
            }
    }
}
