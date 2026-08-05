package com.redurbana.data.transport.di

import com.redurbana.data.transport.directions.DirectionsClient
import com.redurbana.data.transport.mock.MockTransportProvider
import com.redurbana.domain.transport.DirectionsProvider
import com.redurbana.domain.transport.TransportDataProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * ÚNICO lugar de todo el proyecto donde se decide qué implementación de
 * TransportDataProvider está activa.
 *
 * Hoy: MockTransportProvider — flota simulada (~400+ vehículos por defecto,
 * configurable) para validar rendimiento, animaciones y UX antes de tener
 * una fuente oficial.
 *
 * Cuando exista la fuente definitiva, las opciones ya están armadas en este
 * mismo módulo (:data:data-transport):
 *  - GtfsRealtimeProvider   (gtfsrt/)  — feed estándar VehiclePositions/TripUpdates/ServiceAlerts.pb
 *  - GcbaOfficialApiProvider (gcba/)   — si el GCBA expone API propia en vez de/además de GTFS-RT
 *  - CompositeTransportProvider (composite/) — para combinar ambas por capability
 *    (ej: posiciones de GTFS-RT + alertas de la API del GCBA)
 *
 * Cambiar de fuente es reemplazar el tipo del @Binds de abajo. Ninguna
 * feature, ViewModel ni UseCase se modifica en ese proceso.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class TransportModule {

    @Binds
    abstract fun bindTransportDataProvider(
        impl: MockTransportProvider,
    ): TransportDataProvider

    @Binds
    abstract fun bindDirectionsProvider(
        impl: DirectionsClient,
    ): DirectionsProvider
}
