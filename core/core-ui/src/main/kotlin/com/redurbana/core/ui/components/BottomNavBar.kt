package com.redurbana.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.redurbana.core.ui.theme.RedUrbanaColors

enum class BottomNavItem(val label: String, val icon: String) {
    HOME("Inicio", "🏠"),
    MAP("Mapa en vivo", "🗺️"),
    LINES("Líneas", "🚍"),
    MORE("Más", "⋯"),
}

/**
 * Barra inferior con botón central elevado (acceso directo a Mapa en vivo /
 * búsqueda rápida), tal como en la referencia. El ícono central no forma
 * parte de BottomNavItem: se maneja con su propio callback porque su acción
 * (abrir búsqueda o centrar el mapa) puede variar según la pantalla activa.
 */
@Composable
fun BottomNavBar(
    selected: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit,
    onCenterButtonClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .background(RedUrbanaColors.SurfaceElevated),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavSlot(BottomNavItem.HOME, selected, onItemSelected, Modifier)
            NavSlot(BottomNavItem.MAP, selected, onItemSelected, Modifier)
            Box(modifier = Modifier.size(56.dp)) // espacio reservado para el botón central
            NavSlot(BottomNavItem.LINES, selected, onItemSelected, Modifier)
            NavSlot(BottomNavItem.MORE, selected, onItemSelected, Modifier)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(RedUrbanaColors.AccentGreenPrimary)
                .clickable(onClick = onCenterButtonClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🚍", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun NavSlot(
    item: BottomNavItem,
    selected: BottomNavItem,
    onItemSelected: (BottomNavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = item == selected
    val tint = if (isSelected) RedUrbanaColors.AccentGreenPrimary else RedUrbanaColors.TextSecondary
    Column(
        modifier = modifier
            .clickable { onItemSelected(item) }
            .padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = item.icon)
        Text(text = item.label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}
