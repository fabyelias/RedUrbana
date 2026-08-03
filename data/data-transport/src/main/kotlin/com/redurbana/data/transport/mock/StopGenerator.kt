package com.redurbana.data.transport.mock

import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.StopId
import com.redurbana.domain.transport.model.distanceMeters

/**
 * Genera paradas sintéticas ordenadas a lo largo de un recorrido, muestreando
 * cada [stepMeters] por interpolación lineal entre waypoints. Ninguna fuente
 * mock tenía hasta ahora el concepto de "paradas en orden a lo largo de una
 * línea" (solo [Branch.path], la polilínea) — esto es lo que le permite a
 * getRouteRecommendations calcular una distancia en paradas hasta destino.
 */
internal object StopGenerator {

    fun generateStops(
        routeId: RouteId,
        branchName: String,
        path: List<GeoPoint>,
        stepMeters: Double = 500.0,
    ): List<Stop> {
        if (path.size < 2) return emptyList()

        val stops = mutableListOf<Stop>()
        fun addStop(point: GeoPoint) {
            stops += Stop(
                stopId = StopId("${routeId.value}-stop-${stops.size}"),
                name = "Parada ${stops.size + 1} · $branchName",
                location = point,
                routesServed = listOf(routeId),
            )
        }

        addStop(path.first())
        var distanceSinceLastStop = 0.0
        for (i in 0 until path.size - 1) {
            val segmentStart = path[i]
            val segmentEnd = path[i + 1]
            val segmentLength = segmentStart.distanceMeters(segmentEnd)
            if (segmentLength <= 0.0) continue

            var traveledInSegment = 0.0
            while (distanceSinceLastStop + (segmentLength - traveledInSegment) >= stepMeters) {
                traveledInSegment += stepMeters - distanceSinceLastStop
                val fraction = traveledInSegment / segmentLength
                addStop(
                    GeoPoint(
                        latitude = segmentStart.latitude + (segmentEnd.latitude - segmentStart.latitude) * fraction,
                        longitude = segmentStart.longitude + (segmentEnd.longitude - segmentStart.longitude) * fraction,
                    ),
                )
                distanceSinceLastStop = 0.0
            }
            distanceSinceLastStop += segmentLength - traveledInSegment
        }
        return stops
    }
}
