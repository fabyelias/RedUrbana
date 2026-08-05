package com.redurbana.domain.transport.model

import kotlin.math.cos

private const val METERS_PER_DEGREE_LAT = 111_000.0

/**
 * Proyección de un punto sobre un segmento AB: qué tan lejos cae (metros,
 * perpendicular) y en qué parte del segmento ([fraction], 0.0 = A, 1.0 = B).
 * Aproximación planar local (mismo criterio que `TripPlanner.boundsAround`):
 * suficientemente precisa a escala de ciudad, mucho más simple que proyectar
 * sobre la esfera — la distancia final sí usa [distanceMeters] real
 * (haversine), no la aproximación planar, para no perder precisión ahí.
 */
data class SegmentProjection(
    val distanceToSegmentMeters: Double,
    val fraction: Double,
    val closestPoint: GeoPoint,
)

fun projectOntoSegment(point: GeoPoint, segmentStart: GeoPoint, segmentEnd: GeoPoint): SegmentProjection {
    val lngScale = cos(Math.toRadians(point.latitude)).coerceAtLeast(0.2)
    fun localXY(p: GeoPoint) = (p.longitude - segmentStart.longitude) * METERS_PER_DEGREE_LAT * lngScale to
        (p.latitude - segmentStart.latitude) * METERS_PER_DEGREE_LAT

    val (bx, by) = localXY(segmentEnd)
    val (px, py) = localXY(point)

    val abLengthSquared = bx * bx + by * by
    val fraction = if (abLengthSquared == 0.0) {
        0.0
    } else {
        (((px * bx) + (py * by)) / abLengthSquared).coerceIn(0.0, 1.0)
    }
    val closestPoint = GeoPoint(
        latitude = segmentStart.latitude + fraction * (segmentEnd.latitude - segmentStart.latitude),
        longitude = segmentStart.longitude + fraction * (segmentEnd.longitude - segmentStart.longitude),
    )
    return SegmentProjection(
        distanceToSegmentMeters = point.distanceMeters(closestPoint),
        fraction = fraction,
        closestPoint = closestPoint,
    )
}

/**
 * Proyecta [point] sobre TODA la polilínea (no un solo segmento): busca el
 * segmento más cercano y devuelve, además, cuánta distancia ya se recorrió
 * a lo largo de la polilínea hasta ese punto — hace falta tanto para saber
 * si el usuario se salió de la ruta (distanceToRouteMeters) como qué tanto
 * de ella ya hizo (distanceAlongRouteMeters, para ubicar el tramo actual de
 * la navegación paso a paso). Null si la polilínea no tiene al menos 2
 * puntos.
 */
data class PolylineProjection(
    val distanceToRouteMeters: Double,
    val distanceAlongRouteMeters: Double,
    val closestPoint: GeoPoint,
)

fun projectOntoPolyline(point: GeoPoint, polyline: List<GeoPoint>): PolylineProjection? {
    if (polyline.size < 2) return null

    var cumulativeDistance = 0.0
    var best: PolylineProjection? = null

    for (i in 0 until polyline.lastIndex) {
        val segmentStart = polyline[i]
        val segmentEnd = polyline[i + 1]
        val segmentLength = segmentStart.distanceMeters(segmentEnd)
        val projection = projectOntoSegment(point, segmentStart, segmentEnd)

        if (best == null || projection.distanceToSegmentMeters < best.distanceToRouteMeters) {
            best = PolylineProjection(
                distanceToRouteMeters = projection.distanceToSegmentMeters,
                distanceAlongRouteMeters = cumulativeDistance + projection.fraction * segmentLength,
                closestPoint = projection.closestPoint,
            )
        }
        cumulativeDistance += segmentLength
    }
    return best
}
