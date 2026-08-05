package com.redurbana.feature.map

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.core.ui.theme.VehicleConfidenceStyle
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.feature.map.cluster.MapRenderItem
import kotlin.math.hypot

/**
 * Dibuja TODOS los vehículos visibles (salvo el seleccionado/seguido, que
 * usa VehicleMapMarker3D) en un único Canvas, con un solo paso de dibujo
 * por frame.
 *
 * La proyección (GeoPoint → coordenadas de pantalla) ahora usa
 * `MapboxMap.pixelForCoordinate(Point)`, el equivalente Mapbox a
 * `Projection.toScreenLocation()` de Google Maps — el resto de la lógica
 * (por qué un Canvas y no un objeto por vehículo) no cambió con la
 * migración de SDK.
 *
 * Este Canvas cubre toda la pantalla, encima del mapa nativo de Mapbox — así
 * que solo puede consumir el gesto cuando el toque inicial cae realmente
 * sobre un vehículo. Si no hay hit, el evento queda sin consumir para que
 * el mapa nativo (debajo) reciba el stream completo y pueda desplazarse o
 * hacer zoom con normalidad. Con `detectTapGestures` (que solo decide "esto era
 * un tap" después de ver el gesto completo) el Canvas terminaba
 * quedándose con TODO el input, y el mapa dejaba de responder a los dedos.
 */
@Composable
fun VehicleCanvasOverlay(
    items: List<MapRenderItem>,
    mapboxMap: MapboxMap?,
    selectedVehicleId: String?,
    onVehicleTap: (vehicleId: String) -> Unit,
    modifier: Modifier = Modifier,
    userLocation: GeoPoint? = null,
    // Sin uso adentro: cambia en cada movimiento de cámara (incluido paneo
    // puro, sin zoom) para forzar el redibujo del Canvas — sin esto, panear
    // sin hacer zoom no invalida nada leído acá adentro y los puntos quedan
    // pegados en su posición de pantalla vieja mientras el mapa se desliza.
    cameraTick: Int = 0,
) {
    val textPaint = remember {
        Paint().apply {
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    Canvas(
        modifier = modifier.pointerInput(items, mapboxMap) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val map = mapboxMap
                val hit = map?.let { findNearestSingleWithinRadius(items, it, down.position, radiusPx = 40f) }
                if (hit != null) {
                    down.consume()
                    val up = waitForUpOrCancellation()
                    if (up != null) {
                        up.consume()
                        onVehicleTap(hit)
                    }
                }
                // Si no hay hit, no se consume nada: el gesto completo
                // (incluido pan/pinch) sigue de largo hacia el mapa nativo.
            }
        },
    ) {
        val map = mapboxMap ?: return@Canvas

        userLocation?.let { drawUserLocationDot(map, it) }

        for (item in items) {
            when (item) {
                is MapRenderItem.Single -> {
                    if (item.vehicle.vehicleId.value == selectedVehicleId) continue // ese lo dibuja VehicleMapMarker3D
                    drawVehicleDot(map, item, textPaint)
                }
                is MapRenderItem.Cluster -> drawClusterBadge(map, item, textPaint)
            }
        }
    }
}

/** Punto azul estilo Google Maps — "estás acá", en vivo. Mismo estilo que ExploreMapScreen para que se reconozca. */
private fun DrawScope.drawUserLocationDot(map: MapboxMap, point: GeoPoint) {
    val screen = map.pixelForCoordinate(Point.fromLngLat(point.longitude, point.latitude))
    val center = Offset(screen.x.toFloat(), screen.y.toFloat())
    drawCircle(color = RedUrbanaColors.AccentBlue.copy(alpha = 0.18f), radius = 26f, center = center)
    drawCircle(color = RedUrbanaColors.AccentBlue, radius = 11f, center = center)
    drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 11f, center = center, style = Stroke(width = 3f))
}

private fun DrawScope.drawVehicleDot(
    map: MapboxMap,
    item: MapRenderItem.Single,
    textPaint: Paint,
) {
    val screenPoint = map.pixelForCoordinate(
        Point.fromLngLat(item.vehicle.position.longitude, item.vehicle.position.latitude),
    )
    val color = LineColorProvider.colorFor(colorSeed = item.vehicle.routeId.value)
    val center = Offset(screenPoint.x.toFloat(), screenPoint.y.toFloat())
    val confidenceStyle = VehicleConfidenceStyle.from(item.vehicle.positionConfidence?.percent)

    drawCircle(color = color.copy(alpha = confidenceStyle.alpha), radius = 14f, center = center)
    if (confidenceStyle.showsDashedRing) {
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
            radius = 14f,
            center = center,
            style = Stroke(
                width = 2f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            ),
        )
    } else {
        drawCircle(color = androidx.compose.ui.graphics.Color.White.copy(alpha = confidenceStyle.alpha), radius = 14f, center = center, style = Stroke(width = 2f))
    }

    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    textPaint.textSize = 18f
    textPaint.alpha = (confidenceStyle.alpha * 255).toInt()
    textPaint.color = if (luminance > 0.5f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    drawContext.canvas.nativeCanvas.drawText(
        item.vehicle.routeId.value.take(3),
        center.x,
        center.y + 6f,
        textPaint,
    )
}

private fun DrawScope.drawClusterBadge(
    map: MapboxMap,
    cluster: MapRenderItem.Cluster,
    textPaint: Paint,
) {
    val screenPoint = map.pixelForCoordinate(Point.fromLngLat(cluster.center.longitude, cluster.center.latitude))
    val center = Offset(screenPoint.x.toFloat(), screenPoint.y.toFloat())
    val radius = (18 + (cluster.count.coerceAtMost(50) / 2)).toFloat()

    drawCircle(color = RedUrbanaColors.AccentGreenSoft, radius = radius, center = center)
    drawCircle(
        color = RedUrbanaColors.AccentGreenPrimary,
        radius = radius,
        center = center,
        style = Stroke(width = 3f),
    )
    textPaint.textSize = 26f
    textPaint.color = android.graphics.Color.WHITE
    drawContext.canvas.nativeCanvas.drawText(
        cluster.count.toString(),
        center.x,
        center.y + 9f,
        textPaint,
    )
}

private fun findNearestSingleWithinRadius(
    items: List<MapRenderItem>,
    map: MapboxMap,
    tapOffset: Offset,
    radiusPx: Float,
): String? {
    var closestId: String? = null
    var closestDistance = radiusPx
    for (item in items) {
        if (item !is MapRenderItem.Single) continue
        val screenPoint = map.pixelForCoordinate(
            Point.fromLngLat(item.vehicle.position.longitude, item.vehicle.position.latitude),
        )
        val distance = hypot(
            (screenPoint.x - tapOffset.x),
            (screenPoint.y - tapOffset.y),
        ).toFloat()
        if (distance < closestDistance) {
            closestDistance = distance
            closestId = item.vehicle.vehicleId.value
        }
    }
    return closestId
}
