package com.redurbana.data.transport.mock

import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehicleId
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.VehicleStatus
import kotlinx.datetime.Clock
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Simula un vehículo moviéndose a velocidad constante a lo largo de una
 * secuencia de waypoints, yendo y viniendo (ping-pong) para no tener que
 * modelar recorridos circulares completos en el mock.
 *
 * No tiene ninguna dependencia de Android: es Kotlin puro y testeable.
 */
class SimulatedVehicle(
    val vehicleId: VehicleId,
    val routeId: RouteId,
    val branchId: String,
    val internalNumber: String,
    private val path: List<GeoPoint>,
    private val speedKmh: Float,
    startFractionAlongPath: Float = Random.nextFloat(),
) {
    // Posición expresada como distancia acumulada [0, totalLength] a lo largo del path.
    private val segmentLengths: List<Double> = path.zipWithNext { a, b -> haversineMeters(a, b) }
    private val totalLength: Double = segmentLengths.sum().coerceAtLeast(1.0)
    private var distanceTraveled: Double = totalLength * startFractionAlongPath
    private var direction: Int = 1 // 1 = avanzando, -1 = retrocediendo (ping-pong)

    var status: VehicleStatus = VehicleStatus.ON_TIME
        private set

    fun tick(elapsedSeconds: Double): VehiclePosition {
        val metersPerSecond = speedKmh * 1000.0 / 3600.0
        distanceTraveled += direction * metersPerSecond * elapsedSeconds

        if (distanceTraveled >= totalLength) {
            distanceTraveled = totalLength
            direction = -1
        } else if (distanceTraveled <= 0.0) {
            distanceTraveled = 0.0
            direction = 1
        }

        // Ocasionalmente (5% de probabilidad por tick) el mock cambia el estado,
        // para poder probar la UI de "Demorado" / "Fuera de servicio" sin backend real.
        if (Random.nextFloat() < 0.02f) {
            status = listOf(VehicleStatus.ON_TIME, VehicleStatus.ON_TIME, VehicleStatus.DELAYED).random()
        }

        val (point, bearing) = pointAndBearingAt(distanceTraveled)
        return VehiclePosition(
            vehicleId = vehicleId,
            routeId = routeId,
            position = point,
            bearingDegrees = bearing,
            speedKmh = speedKmh,
            timestamp = Clock.System.now(),
            status = status,
            branchId = branchId,
            internalNumber = internalNumber,
        )
    }

    private fun pointAndBearingAt(distance: Double): Pair<GeoPoint, Float> {
        var remaining = distance
        for (i in segmentLengths.indices) {
            val segLen = segmentLengths[i]
            if (remaining <= segLen || i == segmentLengths.lastIndex) {
                val fraction = if (segLen > 0) (remaining / segLen).coerceIn(0.0, 1.0) else 0.0
                val a = path[i]
                val b = path[i + 1]
                val lat = a.latitude + (b.latitude - a.latitude) * fraction
                val lng = a.longitude + (b.longitude - a.longitude) * fraction
                val bearing = bearingBetween(a, b) * direction // invierte al retroceder
                return GeoPoint(lat, lng) to bearing
            }
            remaining -= segLen
        }
        return path.last() to 0f
    }

    private fun bearingBetween(a: GeoPoint, b: GeoPoint): Float {
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val y = Math.sin(dLon) * Math.cos(lat2)
        val x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val la1 = Math.toRadians(a.latitude)
        val la2 = Math.toRadians(b.latitude)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}
