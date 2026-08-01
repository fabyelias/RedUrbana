package com.redurbana.core.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.redurbana.core.ui.theme.RedUrbanaColors

/** Pastilla "● Tránsito en vivo" con punto pulsante — usada en el header del mapa. */
@Composable
fun LiveBadge(
    modifier: Modifier = Modifier,
    label: String = "Tránsito en vivo",
) {
    val transition = rememberInfiniteTransition(label = "live-pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-pulse-alpha",
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(RedUrbanaColors.AccentGreenSoft.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(RedUrbanaColors.AccentGreenPrimary.copy(alpha = alpha)),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = RedUrbanaColors.AccentGreenPrimary,
        )
    }
}

/** Chip de número de línea con color dinámico (ver LineColorProvider). */
@Composable
fun RouteBadge(
    routeShortName: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val textColor = if (color.luminance() > 0.5f) Color.Black else Color.White
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = routeShortName,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
    }
}
