package com.redurbana.feature.lines.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap as MapboxMapComposable
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.theme.RedUrbanaColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Navegación paso a paso en auto: cámara siguiendo y rotando según el rumbo
 * (como Google Maps/Waze manejando), velocidad en vivo, instrucción de giro
 * + distancia, y voz. Pantalla propia (no un estado más de ExploreMapScreen)
 * — mismo criterio que ya usa el modo Colectivo con LiveMapScreen: el chrome
 * de "estoy viajando activamente" es demasiado distinto del de "elegir
 * destino" para convivir en el mismo sealed interface.
 *
 * RIESGO A VERIFICAR EN LA PRIMERA COMPILACIÓN: no hay SDK de Mapbox
 * instalado en el entorno donde se escribió esto para confirmar los nombres
 * exactos de `FollowPuckViewportStateBearing`/`FollowPuckViewportStateOptions`
 * contra la versión 11.26.0 (`gradle/libs.versions.toml`). Si el nombre no
 * coincide, ajustar acá antes de seguir — no afecta a ninguna otra pantalla.
 */
@Composable
fun CarNavigationScreen(
    modifier: Modifier = Modifier,
    viewModel: CarNavigationViewModel = hiltViewModel(),
    onExit: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val position by viewModel.position.collectAsState()
    val tts = rememberNavTts()
    val recenterScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.speak.collect { text -> tts.speak(text) }
    }

    val mapViewportState = rememberMapViewportState()

    val standardStyleState = rememberStandardStyleState {
        configurationsState.apply {
            lightPreset = LightPresetValue.NIGHT
            show3dObjects = BooleanValue(true)
        }
    }

    fun followPuck() {
        recenterScope.launch {
            mapViewportState.transitionToFollowPuckState(
                followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                    .pitch(60.0) // más inclinado que el 45 de LiveMapScreen: se busca sensación de manejar, no de mirar el mapa de arriba
                    .zoom(17.5)
                    .bearing(FollowPuckViewportStateBearing.SyncWithLocationPuck) // rota la cámara con el rumbo real del GPS
                    .build(),
            )
        }
    }

    // Empieza a seguir apenas hay una primera posición real — no antes,
    // para no arrancar mirando a ningún lado en particular.
    var hasStartedFollowing by remember { mutableStateOf(false) }
    LaunchedEffect(position) {
        if (position != null && !hasStartedFollowing) {
            hasStartedFollowing = true
            followPuck()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMapComposable(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapboxStandardStyle(standardStyleState = standardStyleState) },
        ) {
            MapEffect(Unit) { mapView ->
                // Puck nativo de Mapbox (no el Canvas a mano que usan las
                // otras pantallas): transitionToFollowPuckState necesita
                // este puck real para seguirlo y rotarlo con el rumbo.
                mapView.location.updateSettings {
                    enabled = true
                    puckBearing = PuckBearing.HEADING
                    puckBearingEnabled = true
                    locationPuck = createDefault2DPuck(withBearing = true)
                }
            }
        }

        when (val state = uiState) {
            is CarNavigationUiState.Loading -> {
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    contentPadding = PaddingValues(20.dp),
                ) {
                    Column {
                        Text(
                            text = "Calculando ruta…",
                            style = MaterialTheme.typography.titleMedium,
                            color = RedUrbanaColors.TextPrimary,
                        )
                        CircularProgressIndicator(
                            modifier = Modifier.padding(top = 12.dp),
                            color = RedUrbanaColors.AccentGreenPrimary,
                        )
                    }
                }
            }

            is CarNavigationUiState.Error -> {
                GlassCard(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    contentPadding = PaddingValues(20.dp),
                ) {
                    Text(text = state.message, style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
                }
            }

            is CarNavigationUiState.Active -> {
                NavInstructionBanner(state = state, modifier = Modifier.align(Alignment.TopCenter))
                NavBottomBar(state = state, speedKmh = position?.speedKmh, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }

        FloatingActionButton(
            onClick = { tts.muted = !tts.muted },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text(if (tts.muted) "🔇" else "🔊")
        }
        FloatingActionButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        ) {
            Text("✕")
        }

        // Recentrado manual, mismo ícono que en las otras pantallas de mapa.
        FloatingActionButton(
            onClick = { followPuck() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 170.dp, end = 16.dp), // arriba de NavBottomBar
        ) {
            Text("📍")
        }
    }
}

@Composable
private fun NavInstructionBanner(state: CarNavigationUiState.Active, modifier: Modifier = Modifier) {
    val step = state.route.steps.getOrNull(state.currentStepIndex)
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        if (state.isRecalculating) {
            Text(text = "Recalculando…", style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
        } else {
            Text(
                text = formatDistance(state.distanceToManeuverMeters),
                style = MaterialTheme.typography.headlineSmall,
                color = RedUrbanaColors.AccentGreenPrimary,
            )
            Text(
                text = step?.instruction ?: "Seguí derecho",
                style = MaterialTheme.typography.titleMedium,
                color = RedUrbanaColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun NavBottomBar(state: CarNavigationUiState.Active, speedKmh: Int?, modifier: Modifier = Modifier) {
    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(
                    text = "${state.minutesRemaining} min",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RedUrbanaColors.TextPrimary,
                )
                Text(
                    text = formatDistance(state.distanceRemainingMeters),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.TextSecondary,
                )
            }
            Column {
                Text(
                    text = "${speedKmh ?: 0}",
                    style = MaterialTheme.typography.headlineSmall,
                    color = RedUrbanaColors.TextPrimary,
                )
                Text(text = "km/h", style = MaterialTheme.typography.bodySmall, color = RedUrbanaColors.TextSecondary)
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "${(meters / 100).roundToInt() / 10.0} km" else "${meters.roundToInt()} m"
