package com.redurbana.feature.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.mapbox.geojson.Point
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.core.ui.theme.VehicleConfidenceStyle
import com.redurbana.domain.transport.model.VehiclePosition
import com.redurbana.domain.transport.model.VehicleStatus

/**
 * Representa al colectivo seleccionado/seguido como una ViewAnnotation real
 * de Mapbox: Compose UI genuina montada sobre el mapa (no un ícono
 * rasterizado), con la misma interpolación de posición que teníamos con
 * Google Maps. Nunca hay más de una instancia de esto activa a la vez —
 * ver LiveMapScreen para la explicación de por qué el resto de la flota NO
 * usa este mismo mecanismo.
 */
@Composable
fun VehicleMapMarker3D(
    vehicle: VehiclePosition,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val color = LineColorProvider.colorFor(colorSeed = vehicle.routeId.value)
    val animatedLat = remember { Animatable(vehicle.position.latitude.toFloat()) }
    val animatedLng = remember { Animatable(vehicle.position.longitude.toFloat()) }

    LaunchedEffect(vehicle.position) {
        animatedLat.animateTo(vehicle.position.latitude.toFloat(), animationSpec = tween(durationMillis = 1400))
    }
    LaunchedEffect(vehicle.position) {
        animatedLng.animateTo(vehicle.position.longitude.toFloat(), animationSpec = tween(durationMillis = 1400))
    }

    val point = Point.fromLngLat(animatedLng.value.toDouble(), animatedLat.value.toDouble())

    ViewAnnotation(
        options = viewAnnotationOptions {
            geometry(point)
            allowOverlap(true)
        },
    ) {
        BusPill(
            routeShortName = vehicle.routeId.value,
            color = color,
            bearingDegrees = vehicle.bearingDegrees,
            status = vehicle.status,
            isSelected = isSelected,
            confidenceStyle = VehicleConfidenceStyle.from(vehicle.positionConfidence?.percent),
            onClick = onClick,
        )
    }
}

@Composable
private fun BusPill(
    routeShortName: String,
    color: Color,
    bearingDegrees: Float,
    status: VehicleStatus,
    isSelected: Boolean,
    confidenceStyle: VehicleConfidenceStyle,
    onClick: () -> Unit,
) {
    val borderColor = when (status) {
        VehicleStatus.DELAYED -> RedUrbanaColors.WarningAmber
        VehicleStatus.OUT_OF_SERVICE -> RedUrbanaColors.AlertRed
        else -> Color.White.copy(alpha = 0.6f)
    }
    val textColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    val size = if (isSelected) 44.dp else 36.dp

    Box(
        modifier = Modifier
            .size(size)
            .rotate(bearingDegrees) // orienta la "flecha" del colectivo en su dirección de avance
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = confidenceStyle.alpha))
            .border(
                width = if (isSelected) 3.dp else 2.dp,
                color = borderColor.copy(alpha = confidenceStyle.alpha),
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = routeShortName,
            modifier = Modifier.rotate(-bearingDegrees), // el texto no gira, solo el cuerpo del marcador
            color = textColor.copy(alpha = confidenceStyle.alpha),
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}
