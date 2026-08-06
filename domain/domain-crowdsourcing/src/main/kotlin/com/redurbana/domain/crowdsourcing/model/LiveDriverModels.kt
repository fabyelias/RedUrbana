package com.redurbana.domain.crowdsourcing.model

import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.VehicleCategory
import kotlinx.datetime.Instant

/**
 * Identifica una sesión de "estoy manejando y me dejo ver" — se genera una
 * nueva por cada vez que se entra a la navegación paso a paso en auto (ver
 * CarNavigationViewModel) y se descarta al salir. Deliberadamente NO es un
 * id de cuenta: nadie más que vos sabe que ese ícono en el mapa sos vos.
 *
 * A diferencia de [TripSessionId] (colectivo, pings anónimos que el backend
 * PROMEDIA antes de mostrarlos — ver CrowdsourcingModels.kt), acá cada fila
 * se muestra tal cual, individual, a cualquier otro usuario — es justamente
 * el pedido: "que se vea el vehículo, como Waze". Por eso es una sesión
 * propia y no reusa TripSessionId: el modelo de privacidad es distinto
 * (individual y visible en vivo, no agregado y anónimo por lote).
 */
@JvmInline
value class DriverSessionId(val value: String)

/**
 * Posición en vivo de alguien manejando con "Vehículo" elegido, visible
 * para cualquier otro usuario con el mapa abierto — nunca para quien eligió
 * "Colectivo" (transporte público), que sigue viendo solo su propio punto
 * azul de siempre.
 */
data class LiveDriverPosition(
    val sessionId: DriverSessionId,
    val position: GeoPoint,
    val bearingDegrees: Float,
    val vehicleCategory: VehicleCategory,
    val updatedAt: Instant,
)
