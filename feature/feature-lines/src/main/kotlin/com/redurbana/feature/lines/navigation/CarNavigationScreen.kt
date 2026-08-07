package com.redurbana.feature.lines.navigation

import android.graphics.Bitmap
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.maps.ImageHolder
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap as MapboxMapComposable
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.plugin.LocationPuck2D
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateBearing
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteRestrictionViolation
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

    // Reporte de campo: "la pantalla se apaga cada tantos segundos mientras
    // está funcionando la aplicación no se debe apagar" — navegando activo
    // no debe entrar en reposo, mismo criterio que Google Maps/Waze. Se
    // restaura el comportamiento normal al salir de esta pantalla, no queda
    // prendida para siempre.
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

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

    var nativeMap by remember { mutableStateOf<MapboxMap?>(null) }

    // La ruta dibujada en el mapa: antes faltaba por completo acá (solo se
    // veía el punto siguiéndote, sin ninguna línea) — capa NATIVA de Mapbox,
    // no Canvas, por la misma razón que en ExploreMapScreen: se reproyecta
    // sola en cada frame, nunca se desincroniza del mapa. Se re-dibuja cada
    // vez que cambia la ruta, incluido cuando se recalcula por desvío.
    LaunchedEffect((uiState as? CarNavigationUiState.Active)?.route, nativeMap) {
        val map = nativeMap ?: return@LaunchedEffect
        val polyline = (uiState as? CarNavigationUiState.Active)?.route?.polyline
        updateDrivingRouteLayer(map, polyline)
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMapComposable(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapboxStandardStyle(standardStyleState = standardStyleState) },
        ) {
            MapEffect(Unit) { mapView ->
                nativeMap = mapView.mapboxMap
                // Puck nativo de Mapbox (no el Canvas a mano que usan las
                // otras pantallas): transitionToFollowPuckState necesita
                // este puck real para seguirlo y rotarlo con el rumbo. El
                // ícono es el emoji del vehículo elegido en ExploreMapScreen
                // (viaja por AppRoute.CarNavigation), no el puntito
                // genérico de siempre — pedido explícito: "al elegir el
                // vehículo, el punto azul se transforma en el vehículo elegido".
                mapView.location.updateSettings {
                    enabled = true
                    // COURSE (rumbo real del GPS, hacia dónde te estás
                    // moviendo), no HEADING (brújula/magnetómetro, hacia
                    // dónde apunta el dispositivo) — reporte de campo: "el
                    // mapa se queda quieto, no gira". HEADING depende de la
                    // orientación física de la tablet (montada/sostenida en
                    // cualquier ángulo dentro del auto), que no tiene
                    // relación con la dirección de viaje; COURSE sí.
                    puckBearing = PuckBearing.COURSE
                    puckBearingEnabled = true
                    locationPuck = LocationPuck2D(topImage = ImageHolder.from(vehicleEmojiBitmap(viewModel.vehicleProfile.category.emoji)))
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
            // Se borra la fila de "estoy manejando" ANTES de salir, no se
            // espera a que expire sola (ver el comentario en
            // CarNavigationViewModel.stopSharing sobre por qué esto no puede
            // ir en onCleared()) — así el ícono desaparece del mapa de los
            // demás apenas uno sale, no hasta 90s después.
            onClick = {
                recenterScope.launch {
                    viewModel.stopSharing()
                    onExit()
                }
            },
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
            // Mismo aviso que ExploreMapScreen muestra antes de arrancar —
            // se repite acá por si el viaje es largo y conviene recordarlo,
            // no solo verlo una vez al elegir destino.
            if (state.route.unavoidableViolations.isNotEmpty()) {
                Text(
                    text = "⚠️ Esta ruta cruza ${state.route.unavoidableViolations.joinToString(" y ") { it.warningLabel() }} que no pudimos evitar",
                    style = MaterialTheme.typography.labelSmall,
                    color = RedUrbanaColors.AlertRed,
                )
            }
        }
    }
}

private fun RouteRestrictionViolation.warningLabel(): String = when (this) {
    RouteRestrictionViolation.TUNNEL -> "un túnel"
    RouteRestrictionViolation.MAX_HEIGHT -> "una calle con restricción de altura"
    RouteRestrictionViolation.MAX_WIDTH -> "una calle con restricción de ancho"
    RouteRestrictionViolation.MAX_WEIGHT -> "una calle con restricción de peso"
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

private const val NAV_ROUTE_SOURCE_ID = "redurbana-nav-route"
private const val NAV_ROUTE_LAYER_ID = "redurbana-nav-route-layer"

/** Mismo mecanismo que ExploreMapScreen.updateDrivingRouteLayer — ver el comentario ahí para el porqué de la capa nativa en vez de Canvas. */
private fun updateDrivingRouteLayer(map: MapboxMap, polyline: List<GeoPoint>?) {
    if (map.styleLayerExists(NAV_ROUTE_LAYER_ID)) map.removeStyleLayer(NAV_ROUTE_LAYER_ID)
    if (map.styleSourceExists(NAV_ROUTE_SOURCE_ID)) map.removeStyleSource(NAV_ROUTE_SOURCE_ID)

    if (polyline == null || polyline.size < 2) return

    val lineString = LineString.fromLngLats(polyline.map { Point.fromLngLat(it.longitude, it.latitude) })
    map.addSource(geoJsonSource(NAV_ROUTE_SOURCE_ID) { geometry(lineString) })
    map.addLayer(
        lineLayer(NAV_ROUTE_LAYER_ID, NAV_ROUTE_SOURCE_ID) {
            lineWidth(6.0)
            lineColor(android.graphics.Color.parseColor("#3B82F6")) // RedUrbanaColors.AccentBlue
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
            slot("middle")
        },
    )
}

/**
 * Dibuja el emoji del vehículo elegido a un bitmap chico para usarlo como
 * ícono del puck nativo — no hay forma de pasarle texto/emoji directo al
 * puck de Mapbox, solo imágenes (`LocationPuck2D.topImage: ImageHolder`,
 * que sí acepta un [Bitmap] — ver `ImageHolder.from(bitmap)`).
 */
/**
 * Reporte de campo: "voy hacia adelante y el dibujo mira hacia atrás". El
 * puck nativo rota la imagen asumiendo que a rumbo 0° (norte) el FRENTE del
 * dibujo ya apunta hacia arriba — pero el emoji de vehículo (🚑🚗🚌 etc, en
 * la tipografía de emoji de Android/Noto) viene dibujado mirando hacia la
 * IZQUIERDA por diseño, no hacia arriba. Sin corrección, con rumbo 0° se ve
 * apuntando al oeste (90° de error), y para cualquier otro rumbo el error
 * se arrastra igual — que a ojo puede leerse como "mirando para cualquier
 * lado menos el que corresponde", incluido "hacia atrás" según el ángulo.
 * Se rota el bitmap 90° en sentido horario ANTES de que Mapbox aplique su
 * propia rotación por rumbo, así el frente del dibujo queda apuntando hacia
 * arriba a rumbo 0°, que es lo que el puck nativo asume.
 */
private fun vehicleEmojiBitmap(emoji: String, sizePx: Int = 140): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val center = sizePx / 2f
    canvas.rotate(90f, center, center)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = sizePx * 0.75f
        textAlign = android.graphics.Paint.Align.CENTER
    }
    val metrics = paint.fontMetrics
    val y = center - (metrics.ascent + metrics.descent) / 2f
    canvas.drawText(emoji, center, y, paint)
    return bitmap
}
