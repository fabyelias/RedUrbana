package com.redurbana.data.transport.gtfsrt

import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.VehicleStatus
import kotlinx.datetime.Instant

/**
 * Traduce las entidades del protobuf de GTFS-Realtime (`FeedEntity`,
 * `VehiclePosition` del proto oficial) al "idioma común" de dominio.
 *
 * Es el ÚNICO lugar de todo el proyecto que debería conocer el formato
 * específico de GTFS-RT — ninguna feature ni ViewModel llega a ver un
 * `FeedEntity` en ningún momento.
 *
 * Las firmas reciben tipos livianos definidos acá mismo ([RawGtfsVehicle])
 * en vez de las clases generadas por el protobuf oficial, para poder
 * compilar y testear los mappers sin agregar la dependencia de
 * `gtfs-realtime-bindings` hasta el momento de conectar la fuente real.
 * Cuando se agregue esa dependencia, [RawGtfsVehicle] se reemplaza por
 * `com.google.transit.realtime.GtfsRealtime.VehiclePosition` sin que el
 * resto de la app note la diferencia (el resultado sigue siendo VehiclePosition
 * de dominio).
 */
data class RawGtfsVehicle(
    val vehicleId: String,
    val tripRouteId: String,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float?,
    val speedMetersPerSecond: Float?,
    val timestampEpochSeconds: Long,
    val currentStatus: String?, // "IN_TRANSIT_TO", "STOPPED_AT", etc. (enum oficial del proto)
    val congestionLevel: String?, // señal opcional del proto para inferir demoras
)

object GtfsRealtimeMappers {

    fun toVehiclePosition(raw: RawGtfsVehicle): VehiclePosition = VehiclePosition(
        vehicleId = VehicleId(raw.vehicleId),
        routeId = RouteId(raw.tripRouteId),
        position = GeoPoint(raw.latitude, raw.longitude),
        bearingDegrees = raw.bearing ?: 0f,
        speedKmh = (raw.speedMetersPerSecond ?: 0f) * 3.6f,
        timestamp = Instant.fromEpochSeconds(raw.timestampEpochSeconds),
        status = mapStatus(raw),
        branchId = null, // GTFS-RT no tiene "ramal" nativo; se derivaría del trip_id + shapes.txt del feed estático
        internalNumber = raw.vehicleId, // por defecto se usa el mismo id; algunos operadores exponen un "label" separado
    )

    private fun mapStatus(raw: RawGtfsVehicle): VehicleStatus {
        // TODO: heurística real una vez que se vea el feed en producción —
        // por ejemplo comparar contra TripUpdates para detectar demoras,
        // o usar congestion_level si el operador lo publica.
        return when (raw.congestionLevel) {
            "CONGESTION", "SEVERE_CONGESTION" -> VehicleStatus.DELAYED
            else -> VehicleStatus.ON_TIME
        }
    }
}
