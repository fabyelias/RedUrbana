package com.redurbana.feature.lines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap as MapboxMapComposable
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.TripLeg

/**
 * Mini-mapa ESTÁTICO del detalle de un itinerario: cámara fija ajustada a
 * los bounds del recorrido, sin flota simulada ni overlays pesados — no es
 * [com.redurbana.feature.map.LiveMapScreen] ni tiene su costo. Dibuja los
 * tramos con el mismo patrón ya probado en VehicleCanvasOverlay
 * (`Canvas` + `MapboxMap.pixelForCoordinate`), en vez del plugin de
 * anotaciones nativo — evita sumar otra superficie de la API de Mapbox
 * cuyo comportamiento exacto en la versión pineada (11.26.0) no está
 * verificado.
 */
@Composable
fun TripPreviewMap(legs: List<TripLeg>, modifier: Modifier = Modifier) {
    val allPoints = legs.flatMap { leg ->
        when (leg) {
            is TripLeg.Walk -> leg.polyline
            is TripLeg.Transit -> leg.path
        }
    }
    if (allPoints.size < 2) return

    var nativeMap by remember { mutableStateOf<MapboxMap?>(null) }
    val firstPoint = Point.fromLngLat(allPoints.first().longitude, allPoints.first().latitude)
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(firstPoint)
            zoom(13.0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        MapboxMapComposable(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapboxStandardStyle() },
        ) {
            MapEffect(allPoints) { mapView ->
                nativeMap = mapView.mapboxMap
                val points = allPoints.map { Point.fromLngLat(it.longitude, it.latitude) }
                val cameraOptions = mapView.mapboxMap.cameraForCoordinates(
                    points,
                    EdgeInsets(32.0, 32.0, 32.0, 32.0),
                    null,
                    null,
                )
                mapView.mapboxMap.setCamera(cameraOptions)
            }
        }

        RouteOverlay(legs = legs, mapboxMap = nativeMap, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun RouteOverlay(legs: List<TripLeg>, mapboxMap: MapboxMap?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val map = mapboxMap ?: return@Canvas
        for (leg in legs) {
            val (points, color) = when (leg) {
                is TripLeg.Walk -> leg.polyline to RedUrbanaColors.TextSecondary
                is TripLeg.Transit -> leg.path to LineColorProvider.colorFor(colorSeed = leg.colorSeed)
            }
            if (points.size < 2) continue
            val screenPoints = points.map { geoPoint ->
                val screen = map.pixelForCoordinate(Point.fromLngLat(geoPoint.longitude, geoPoint.latitude))
                Offset(screen.x.toFloat(), screen.y.toFloat())
            }
            for (i in 0 until screenPoints.lastIndex) {
                drawLine(
                    color = color,
                    start = screenPoints[i],
                    end = screenPoints[i + 1],
                    strokeWidth = if (leg is TripLeg.Transit) 6f else 4f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    pathEffect = if (leg is TripLeg.Walk) {
                        androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    } else {
                        null
                    },
                )
            }
        }
    }
}
