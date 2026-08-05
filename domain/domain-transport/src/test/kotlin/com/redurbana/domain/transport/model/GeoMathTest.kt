package com.redurbana.domain.transport.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Casos de la proyección punto-sobre-ruta que usa la navegación paso a paso
 * (CarNavigationViewModel: detección de desvío + tramo actual). Se verificó
 * primero con una traducción a Python contra los mismos casos (no había
 * forma de compilar Kotlin real en la sesión donde se escribió esto hasta
 * instalar un JDK a mano) — este archivo es la versión real, ejecutable.
 */
class GeoMathTest {

    private val pointA = GeoPoint(-34.60, -58.40)
    private val pointB = GeoPoint(-34.61, -58.40) // ~1.1km al sur de A

    @Test
    fun `punto en el medio del segmento cae casi encima, fraction 0-5`() {
        val mid = GeoPoint((pointA.latitude + pointB.latitude) / 2, pointA.longitude)
        val result = projectOntoSegment(mid, pointA, pointB)
        assertTrue("distancia debería ser ~0, fue ${result.distanceToSegmentMeters}", result.distanceToSegmentMeters < 1.0)
        assertEquals(0.5, result.fraction, 0.01)
    }

    @Test
    fun `punto a 50m del segmento da esa distancia, sin mover la fraction`() {
        val mid = GeoPoint((pointA.latitude + pointB.latitude) / 2, pointA.longitude)
        val lngOffsetFor50m = 50.0 / (111_000.0 * Math.cos(Math.toRadians(mid.latitude)))
        val sidePoint = mid.copy(longitude = mid.longitude + lngOffsetFor50m)
        val result = projectOntoSegment(sidePoint, pointA, pointB)
        assertEquals(50.0, result.distanceToSegmentMeters, 2.0)
        assertEquals(0.5, result.fraction, 0.02)
    }

    @Test
    fun `punto mas alla del extremo B clampea a fraction 1, no proyecta sobre la recta infinita`() {
        val beyondB = pointB.copy(latitude = pointB.latitude - 0.01)
        val result = projectOntoSegment(beyondB, pointA, pointB)
        assertEquals(1.0, result.fraction, 0.0)
        assertTrue(result.closestPoint.distanceMeters(pointB) < 0.1)
    }

    @Test
    fun `punto antes del extremo A clampea a fraction 0`() {
        val beforeA = pointA.copy(latitude = pointA.latitude + 0.01)
        val result = projectOntoSegment(beforeA, pointA, pointB)
        assertEquals(0.0, result.fraction, 0.0)
    }

    @Test
    fun `polilinea de dos tramos elige el segmento correcto y acumula distancia recorrida`() {
        val p0 = GeoPoint(-34.60, -58.40)
        val p1 = GeoPoint(-34.61, -58.40)
        val p2 = GeoPoint(-34.61, -58.39)
        val polyline = listOf(p0, p1, p2)
        val seg1Length = p0.distanceMeters(p1)
        val seg2Length = p1.distanceMeters(p2)

        val mid2 = GeoPoint((p1.latitude + p2.latitude) / 2, (p1.longitude + p2.longitude) / 2)
        val projection = projectOntoPolyline(mid2, polyline)

        requireNotNull(projection)
        val expectedDistanceAlong = seg1Length + seg2Length * 0.5
        assertEquals(expectedDistanceAlong, projection.distanceAlongRouteMeters, 5.0)
        assertTrue(projection.distanceToRouteMeters < 1.0)
    }

    @Test
    fun `punto lejos de toda la ruta supera el umbral de fuera-de-ruta`() {
        val polyline = listOf(GeoPoint(-34.60, -58.40), GeoPoint(-34.61, -58.40), GeoPoint(-34.61, -58.39))
        val farPoint = GeoPoint(-34.65, -58.35)
        val projection = requireNotNull(projectOntoPolyline(farPoint, polyline))
        assertTrue("distancia debería superar 30m, fue ${projection.distanceToRouteMeters}", projection.distanceToRouteMeters > 30.0)
    }

    @Test
    fun `polilinea con menos de 2 puntos devuelve null en vez de explotar`() {
        assertNull(projectOntoPolyline(pointA, listOf(pointA)))
        assertNull(projectOntoPolyline(pointA, emptyList()))
    }
}
