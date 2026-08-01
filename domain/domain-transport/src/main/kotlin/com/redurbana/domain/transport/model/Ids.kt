package com.redurbana.domain.transport.model

/**
 * Identificadores tipados (value classes) para evitar el error clásico de
 * confundir un String de línea con un String de parada en toda la app.
 */

@JvmInline
value class RouteId(val value: String)

@JvmInline
value class StopId(val value: String)

@JvmInline
value class VehicleId(val value: String)
