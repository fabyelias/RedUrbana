package com.redurbana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.redurbana.core.ui.theme.RedUrbanaColors

/**
 * Contenedor base de todas las tarjetas de la app (paradas cercanas, próximos
 * colectivos, favoritos, detalle de vehículo). Esquinas grandes, borde sutil,
 * leve gradiente para simular profundidad de vidrio esmerilado.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 20.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        RedUrbanaColors.SurfaceCard,
                        RedUrbanaColors.SurfaceElevated,
                    ),
                ),
            )
            .border(width = 1.dp, color = RedUrbanaColors.Divider, shape = shape)
            .padding(contentPadding),
        content = content,
    )
}
