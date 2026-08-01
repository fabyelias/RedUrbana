package com.redurbana.app.more

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.theme.RedUrbanaColors

private data class MoreEntry(val icon: String, val label: String, val subtitle: String, val onClick: () -> Unit)

/**
 * Hub de navegación secundaria. Vive en :app (no en un módulo :feature)
 * porque es puramente un índice hacia otras rutas — no tiene lógica propia
 * ni UseCases, así que no justifica un módulo aparte todavía. Si en el
 * futuro gana contenido propio (ej. resumen de perfil), se puede extraer.
 */
@Composable
fun MoreMenuScreen(
    modifier: Modifier = Modifier,
    onNavigateToStops: () -> Unit,
    onNavigateToFavorites: () -> Unit,
    onNavigateToAlerts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    val entries = listOf(
        MoreEntry("📍", "Paradas", "Buscar paradas cercanas o por nombre", onNavigateToStops),
        MoreEntry("⭐", "Favoritos", "Casa, trabajo, universidad y más", onNavigateToFavorites),
        MoreEntry("🔔", "Alertas", "Desvíos, cortes y demoras activas", onNavigateToAlerts),
        MoreEntry("🕘", "Historial", "Todos los viajes consultados", onNavigateToHistory),
        MoreEntry("⚙️", "Ajustes", "Idioma, tema y notificaciones", onNavigateToSettings),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Más",
            style = MaterialTheme.typography.headlineMedium,
            color = RedUrbanaColors.TextPrimary,
        )
        entries.forEach { entry ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = entry.onClick),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = entry.icon, style = MaterialTheme.typography.titleLarge)
                    Column {
                        Text(text = entry.label, style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
                        Text(text = entry.subtitle, style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextSecondary)
                    }
                }
            }
        }
    }
}
