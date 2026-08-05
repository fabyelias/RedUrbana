package com.redurbana.feature.lines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.EdgeInsets
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap as MapboxMapComposable
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.BooleanValue
import com.mapbox.maps.extension.compose.style.standard.LightPresetValue
import com.mapbox.maps.extension.compose.style.standard.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.extension.style.expressions.generated.Expression
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.generated.lineLayer
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.vectorSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.TripLeg
import kotlin.math.roundToInt

/**
 * Pantalla de inicio de toda la app: un mapa de Mapbox SIEMPRE montado (a
 * diferencia de LiveMapScreen, que evita montarlo hasta elegir línea — acá
 * no se puede evitar, es justamente donde se elige el destino tocando el
 * mapa) con un panel deslizable abajo (`BottomSheetScaffold`) cuyo contenido
 * cambia según [ExploreUiState].
 *
 * El toque en el mapa usa el listener NATIVO de gestos de Mapbox
 * (`addOnMapClickListener`), no un `Canvas` de Compose con `pointerInput`:
 * así nunca compite con el pan/zoom del mapa (ver el bug que se arregló en
 * VehicleCanvasOverlay — un overlay de Compose con `pointerInput` encima
 * del mapa puede robarle los gestos; el listener nativo es parte del mismo
 * sistema de gestos del mapa, no un overlay aparte).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreMapScreen(
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
    onStartTrip: (routeId: String, alightingStop: Stop) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    var nativeMap by remember { mutableStateOf<MapboxMap?>(null) }

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(-58.3924, -34.6095)) // Congreso, se corrige apenas resuelve el GPS real
            zoom(14.0)
        }
    }

    val standardStyleState = rememberStandardStyleState {
        configurationsState.apply {
            lightPreset = LightPresetValue.NIGHT
            show3dObjects = BooleanValue(true)
        }
    }

    LaunchedEffect(Unit) {
        val origin = viewModel.initialCameraTarget()
        mapViewportState.flyTo(
            cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(origin.longitude, origin.latitude))
                .zoom(15.0)
                .build(),
            animationOptions = MapAnimationOptions.mapAnimationOptions { duration(600) },
        )
    }

    // Al mostrar el detalle de un itinerario, ajustar la cámara a los bounds del recorrido.
    LaunchedEffect(uiState, nativeMap) {
        val state = uiState
        val map = nativeMap
        if (state is ExploreUiState.ItineraryDetail && map != null) {
            val points = routePoints(state.itinerary).map { Point.fromLngLat(it.longitude, it.latitude) }
            if (points.size >= 2) {
                val camera = map.cameraForCoordinates(points, EdgeInsets(80.0, 60.0, 380.0, 60.0), null, null)
                mapViewportState.flyTo(
                    cameraOptions = camera,
                    animationOptions = MapAnimationOptions.mapAnimationOptions { duration(600) },
                )
            }
        }
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = 110.dp,
        sheetContent = {
            ExploreSheetContent(
                state = uiState,
                onConfirm = viewModel::onDestinationConfirmed,
                onCancel = viewModel::onCancelDestination,
                onItinerarySelected = viewModel::onItinerarySelected,
                onBack = viewModel::onBackToAlternatives,
                onStartTrip = { itinerary ->
                    val lastTransit = itinerary.legs.filterIsInstance<TripLeg.Transit>().lastOrNull()
                    lastTransit?.let { onStartTrip(it.routeId.value, it.alightingStop) }
                },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            MapboxMapComposable(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                style = { MapboxStandardStyle(standardStyleState = standardStyleState) },
            ) {
                MapEffect(Unit) { mapView ->
                    nativeMap = mapView.mapboxMap
                    mapView.mapboxMap.addOnMapClickListener { point ->
                        viewModel.onMapTapped(GeoPoint(point.latitude(), point.longitude()))
                        true
                    }
                    mapView.mapboxMap.subscribeStyleLoaded { addTrafficLayer(mapView.mapboxMap) }
                }
            }

            ExploreOverlay(uiState = uiState, liveLocation = liveLocation, mapboxMap = nativeMap, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val TRAFFIC_SOURCE_ID = "redurbana-traffic"
private const val TRAFFIC_LAYER_ID = "redurbana-traffic-layer"

/**
 * Capa de tráfico REAL de Mapbox (congestión en vivo, no inventada) — mismo
 * tileset que usa Google Maps/Waze por debajo (`mapbox.mapbox-traffic-v1`).
 * Se agrega vía la API de estilos en vez de cambiar a un style "traffic-*"
 * completo, para no perder los edificios 3D / preset nocturno del Standard
 * style ya elegido. `slot("middle")` la ubica en la posición correcta
 * respecto a las calles del style Standard (API de slots de v11).
 */
private fun addTrafficLayer(map: MapboxMap) {
    if (map.styleSourceExists(TRAFFIC_SOURCE_ID)) return
    map.addSource(vectorSource(TRAFFIC_SOURCE_ID) { url("mapbox://mapbox.mapbox-traffic-v1") })
    map.addLayer(
        lineLayer(TRAFFIC_LAYER_ID, TRAFFIC_SOURCE_ID) {
            sourceLayer("traffic")
            lineWidth(2.5)
            lineColor(
                Expression.match(
                    Expression.get("congestion"),
                    Expression.literal("low"), Expression.color(android.graphics.Color.parseColor("#4CAF50")),
                    Expression.literal("moderate"), Expression.color(android.graphics.Color.parseColor("#FFC107")),
                    Expression.literal("heavy"), Expression.color(android.graphics.Color.parseColor("#FF5722")),
                    Expression.literal("severe"), Expression.color(android.graphics.Color.parseColor("#B71C1C")),
                    Expression.color(android.graphics.Color.TRANSPARENT),
                ),
            )
            slot("middle")
        },
    )
}

private fun routePoints(itinerary: TripItinerary): List<GeoPoint> = itinerary.legs.flatMap { leg ->
    when (leg) {
        is TripLeg.Walk -> leg.polyline
        is TripLeg.Transit -> leg.path
    }
}

/**
 * Dibuja el punto "estás acá" (siempre, si hay GPS) + el pin de destino o el
 * recorrido elegido según el estado — solo dibuja, nunca intercepta gestos.
 */
@Composable
private fun ExploreOverlay(uiState: ExploreUiState, liveLocation: GeoPoint?, mapboxMap: MapboxMap?, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val map = mapboxMap ?: return@Canvas
        liveLocation?.let { drawUserLocationDot(map, it) }
        when (uiState) {
            is ExploreUiState.ConfirmingDestination -> drawPin(map, uiState.point)
            is ExploreUiState.Recommending -> drawPin(map, uiState.destination)
            is ExploreUiState.ItineraryDetail -> {
                for (leg in uiState.itinerary.legs) {
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
            else -> Unit
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPin(map: MapboxMap, point: GeoPoint) {
    val screen = map.pixelForCoordinate(Point.fromLngLat(point.longitude, point.latitude))
    val center = Offset(screen.x.toFloat(), screen.y.toFloat())
    drawCircle(color = RedUrbanaColors.AccentGreenPrimary, radius = 16f, center = center)
    drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 16f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
}

/** Punto azul estilo Google Maps: distinto del pin de destino (verde) para no confundirlos. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUserLocationDot(map: MapboxMap, point: GeoPoint) {
    val screen = map.pixelForCoordinate(Point.fromLngLat(point.longitude, point.latitude))
    val center = Offset(screen.x.toFloat(), screen.y.toFloat())
    drawCircle(color = RedUrbanaColors.AccentBlue.copy(alpha = 0.18f), radius = 26f, center = center)
    drawCircle(color = RedUrbanaColors.AccentBlue, radius = 11f, center = center)
    drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 11f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
}

@Composable
private fun ExploreSheetContent(
    state: ExploreUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onItinerarySelected: (TripItinerary) -> Unit,
    onBack: () -> Unit,
    onStartTrip: (TripItinerary) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is ExploreUiState.Idle -> {
                Text(
                    text = "Tocá el mapa para elegir tu destino",
                    style = MaterialTheme.typography.titleMedium,
                    color = RedUrbanaColors.TextPrimary,
                )
                Text(
                    text = "Te mostramos las líneas de colectivo que te sirven para llegar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RedUrbanaColors.TextSecondary,
                )
            }

            is ExploreUiState.ConfirmingDestination -> {
                Text(text = "¿Ir hasta acá?", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextSecondary)
                Text(text = state.placeName, style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onConfirm) { Text("Ir hasta acá") }
                    TextButton(onClick = onCancel) { Text("Cancelar") }
                }
            }

            is ExploreUiState.Recommending -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = "Alternativas para llegar a", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextSecondary)
                        Text(text = state.destinationName, style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
                    }
                    TextButton(onClick = onCancel) { Text("Cambiar destino") }
                }
                when {
                    state.isLoading -> CircularProgressIndicator(color = RedUrbanaColors.AccentGreenPrimary)
                    state.error != null -> Text(text = state.error, color = RedUrbanaColors.AlertRed)
                    state.itineraries.isEmpty() -> Text(
                        text = "No encontramos alternativas cerca tuyo para ese destino.",
                        color = RedUrbanaColors.TextSecondary,
                    )
                    else -> LazyColumn(
                        modifier = Modifier.heightIn(max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.itineraries) { itinerary ->
                            TripItineraryCard(itinerary = itinerary, onClick = { onItinerarySelected(itinerary) })
                        }
                    }
                }
            }

            is ExploreUiState.ItineraryDetail -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onBack) { Text("‹ Alternativas") }
                    Text(
                        text = "${state.itinerary.totalMinutes} min · ${if (state.itinerary.transferCount == 0) "Directo" else "${state.itinerary.transferCount} transbordo"}",
                        style = MaterialTheme.typography.titleMedium,
                        color = RedUrbanaColors.TextPrimary,
                    )
                }
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.itinerary.legs.withIndex().toList()) { (index, leg) ->
                        TripStepCard(leg = leg, nextLeg = state.itinerary.legs.getOrNull(index + 1))
                    }
                }
                Button(
                    onClick = { onStartTrip(state.itinerary) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Iniciar viaje")
                }
            }
        }
    }
}

@Composable
private fun TripItineraryCard(itinerary: TripItinerary, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "${itinerary.totalMinutes} min", style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
            Text(
                text = if (itinerary.transferCount == 0) "Directo" else "${itinerary.transferCount} transbordo",
                style = MaterialTheme.typography.labelSmall,
                color = RedUrbanaColors.TextSecondary,
            )
        }

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itinerary.legs.forEachIndexed { index, leg ->
                when (leg) {
                    is TripLeg.Walk -> WalkChip(distanceMeters = leg.distanceMeters)
                    is TripLeg.Transit -> RouteBadge(
                        routeShortName = leg.shortName,
                        color = LineColorProvider.colorFor(colorSeed = leg.colorSeed),
                    )
                }
                if (index != itinerary.legs.lastIndex) {
                    Text(text = "›", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextTertiary)
                }
            }
        }

        val nextDeparture = itinerary.legs.filterIsInstance<TripLeg.Transit>().firstOrNull()?.nextDepartureMinutes
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = when {
                nextDeparture == null -> "Sin dato de salida en vivo todavía"
                nextDeparture <= 0 -> "Está llegando"
                else -> "Sale en $nextDeparture min"
            },
            style = MaterialTheme.typography.bodySmall,
            color = RedUrbanaColors.TextSecondary,
        )
    }
}

@Composable
private fun TripStepCard(leg: TripLeg, nextLeg: TripLeg?) {
    GlassCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) {
        when (leg) {
            is TripLeg.Walk -> {
                val destinationName = (nextLeg as? TripLeg.Transit)?.boardingStop?.name
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WalkChip(distanceMeters = leg.distanceMeters)
                    Text(
                        text = "Caminá ${leg.durationMinutes} min" + (destinationName?.let { " hasta $it" } ?: " hasta destino"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RedUrbanaColors.TextPrimary,
                    )
                }
            }
            is TripLeg.Transit -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RouteBadge(routeShortName = leg.shortName, color = LineColorProvider.colorFor(colorSeed = leg.colorSeed))
                    Column {
                        Text(
                            text = "Tomá la línea ${leg.shortName} — ${leg.stopsCount} paradas",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RedUrbanaColors.TextPrimary,
                        )
                        Text(
                            text = "Bajate en ${leg.alightingStop.name} · ${leg.estimatedMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                            color = RedUrbanaColors.TextSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalkChip(distanceMeters: Double) {
    Text(
        text = formatDistance(distanceMeters),
        style = MaterialTheme.typography.labelSmall,
        color = RedUrbanaColors.TextSecondary,
    )
}

private fun formatDistance(meters: Double): String =
    if (meters >= 1000) "${(meters / 100).roundToInt() / 10.0} km" else "${meters.roundToInt()} m"
