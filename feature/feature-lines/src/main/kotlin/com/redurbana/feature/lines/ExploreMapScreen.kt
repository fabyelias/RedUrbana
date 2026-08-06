package com.redurbana.feature.lines

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.LineString
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
import com.mapbox.maps.extension.style.layers.properties.generated.LineCap
import com.mapbox.maps.extension.style.layers.properties.generated.LineJoin
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.geoJsonSource
import com.mapbox.maps.extension.style.sources.generated.vectorSource
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.gestures.addOnMapClickListener
import com.mapbox.search.result.SearchSuggestion
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.RouteBadge
import com.redurbana.core.ui.theme.LineColorProvider
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.Stop
import com.redurbana.domain.transport.model.TripItinerary
import com.redurbana.domain.transport.model.TripLeg
import com.redurbana.domain.transport.model.VehicleCategory
import com.redurbana.domain.transport.model.VehicleDimensions
import com.redurbana.domain.transport.model.VehicleProfile
import kotlinx.coroutines.launch
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
    onStartDriving: (destination: GeoPoint, destinationName: String, vehicleProfile: VehicleProfile) -> Unit = { _, _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()
    val travelMode by viewModel.travelMode.collectAsState()
    val vehicleProfile by viewModel.vehicleProfile.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchSuggestions by viewModel.searchSuggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val scaffoldState = rememberBottomSheetScaffoldState()

    var nativeMap by remember { mutableStateOf<MapboxMap?>(null) }

    // Sin uso adentro salvo forzar recomposición: se incrementa en CADA
    // cambio de cámara (pan, zoom o rotación). Antes solo currentZoom
    // disparaba el redibujo del overlay, así que panear sin hacer zoom
    // dejaba el punto azul / pin / recorrido pegados en su posición de
    // pantalla vieja mientras el mapa se deslizaba debajo.
    var cameraTick by remember { mutableIntStateOf(0) }
    val recenterScope = rememberCoroutineScope()

    // Vista panorámica neutral de CABA mientras se resuelve el GPS real — a
    // propósito no es un punto/zoom específico que parezca "acá estás vos"
    // (antes era Congreso a zoom de calle, que se mostraba como si fuera la
    // ubicación real cuando el GPS no respondía a tiempo).
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(-58.42, -34.62))
            zoom(11.0)
        }
    }

    val standardStyleState = rememberStandardStyleState {
        configurationsState.apply {
            lightPreset = LightPresetValue.NIGHT
            show3dObjects = BooleanValue(true)
        }
    }

    // Centrado único apenas resuelve el primer fix real — sin timeout ni
    // fallback: si tarda, la cámara se queda en la vista panorámica de arriba.
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

    // Un resultado de búsqueda por texto puede estar en cualquier lado del
    // mapa (a diferencia de tocar el mapa, donde el punto ya está a la
    // vista) — centrar la cámara ahí para que el pin aparezca en pantalla.
    LaunchedEffect((uiState as? ExploreUiState.ConfirmingDestination)?.point) {
        val point = (uiState as? ExploreUiState.ConfirmingDestination)?.point ?: return@LaunchedEffect
        mapViewportState.flyTo(
            cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(point.longitude, point.latitude))
                .zoom(15.5)
                .build(),
            animationOptions = MapAnimationOptions.mapAnimationOptions { duration(600) },
        )
    }

    // Al mostrar el detalle de un itinerario (colectivo) o una ruta en auto,
    // ajustar la cámara a los bounds del recorrido.
    LaunchedEffect(uiState, nativeMap) {
        val state = uiState
        val map = nativeMap ?: return@LaunchedEffect
        val points = when (state) {
            is ExploreUiState.ItineraryDetail -> routePoints(state.itinerary).map { Point.fromLngLat(it.longitude, it.latitude) }
            is ExploreUiState.DrivingResult -> state.route?.polyline?.map { Point.fromLngLat(it.longitude, it.latitude) } ?: emptyList()
            else -> emptyList()
        }
        if (points.size >= 2) {
            val camera = map.cameraForCoordinates(points, EdgeInsets(80.0, 60.0, 380.0, 60.0), null, null)
            mapViewportState.flyTo(
                cameraOptions = camera,
                animationOptions = MapAnimationOptions.mapAnimationOptions { duration(600) },
            )
        }
    }

    // La ruta en auto se dibuja con una capa NATIVA de Mapbox (igual que el
    // tráfico más abajo), no con un Canvas a mano: una capa nativa se
    // reproyecta sola en cada frame, así que nunca se puede "despegar" del
    // mapa al panear — que es justo lo que le pasaba a la versión anterior
    // dibujada en Canvas.
    LaunchedEffect((uiState as? ExploreUiState.DrivingResult)?.route, nativeMap) {
        val map = nativeMap ?: return@LaunchedEffect
        val polyline = (uiState as? ExploreUiState.DrivingResult)?.route?.polyline
        updateDrivingRouteLayer(map, polyline)
    }

    // Camión/colectivo/ambulancia/bomberos necesitan medidas antes de poder
    // pedir ruta (Mapbox recién puede evitar calles con restricción de
    // altura/ancho/peso si se las mandamos) — el diálogo aparece apenas se
    // elige uno de esos vehículos y todavía no las cargó, en cualquier
    // pantalla del flujo (no solo antes de confirmar destino).
    if (vehicleProfile.category.requiresDimensions && vehicleProfile.dimensions == null) {
        VehicleDimensionsDialog(
            category = vehicleProfile.category,
            onConfirm = viewModel::onVehicleDimensionsConfirmed,
            onDismiss = viewModel::onVehicleDimensionsCancelled,
        )
    }

    BottomSheetScaffold(
        modifier = modifier,
        scaffoldState = scaffoldState,
        sheetPeekHeight = 110.dp,
        sheetContent = {
            ExploreSheetContent(
                state = uiState,
                travelMode = travelMode,
                onTravelModeSelected = viewModel::onTravelModeSelected,
                vehicleProfile = vehicleProfile,
                onVehicleCategorySelected = viewModel::onVehicleCategorySelected,
                onConfirm = viewModel::onDestinationConfirmed,
                onCancel = viewModel::onCancelDestination,
                onItinerarySelected = viewModel::onItinerarySelected,
                onBack = viewModel::onBackToAlternatives,
                onStartTrip = { itinerary ->
                    val lastTransit = itinerary.legs.filterIsInstance<TripLeg.Transit>().lastOrNull()
                    lastTransit?.let { onStartTrip(it.routeId.value, it.alightingStop) }
                },
                onStartDriving = onStartDriving,
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
                    mapView.mapboxMap.subscribeCameraChanged { cameraTick++ }
                    mapView.mapboxMap.subscribeStyleLoaded { addTrafficLayer(mapView.mapboxMap) }
                }
            }

            ExploreOverlay(
                uiState = uiState,
                liveLocation = liveLocation,
                mapboxMap = nativeMap,
                cameraTick = cameraTick,
                // Solo en modo Vehículo: en Colectivo el punto es "vos
                // esperando/caminando", no tiene sentido mostrarlo como auto/moto/etc.
                userVehicleEmoji = if (travelMode == TravelMode.CAR) vehicleProfile.category.emoji else null,
                modifier = Modifier.fillMaxSize(),
            )

            // Barra de búsqueda por dirección, estilo Google Maps — solo
            // antes de elegir destino: una vez confirmado, el panel de abajo
            // ya muestra el resultado y la barra estorbaría.
            if (uiState is ExploreUiState.Idle) {
                DestinationSearchBar(
                    query = searchQuery,
                    suggestions = searchSuggestions,
                    isSearching = isSearching,
                    onQueryChanged = viewModel::onSearchQueryChanged,
                    onSuggestionSelected = viewModel::onSearchSuggestionSelected,
                    onClear = viewModel::onSearchCleared,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp, start = 16.dp, end = 16.dp),
                )
            }

            // Botón "mi ubicación" estilo Google Maps: recentra sin pelear
            // con el usuario mientras panea buscando dónde ir.
            val userLocation = liveLocation
            if (userLocation != null) {
                FloatingActionButton(
                    onClick = {
                        recenterScope.launch {
                            mapViewportState.flyTo(
                                cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                                    .center(Point.fromLngLat(userLocation.longitude, userLocation.latitude))
                                    .zoom(15.0)
                                    .build(),
                                animationOptions = MapAnimationOptions.mapAnimationOptions { duration(600) },
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 130.dp, end = 16.dp), // arriba del sheet, que arranca en 110.dp
                ) {
                    Text("📍")
                }
            }
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

private const val DRIVING_ROUTE_SOURCE_ID = "redurbana-driving-route"
private const val DRIVING_ROUTE_LAYER_ID = "redurbana-driving-route-layer"

/**
 * Ruta del modo Auto, como capa NATIVA de Mapbox (mismo mecanismo que
 * [addTrafficLayer]) — a diferencia de dibujarla a mano en un Canvas de
 * Compose, una capa nativa se reproyecta sola en cada frame que renderiza
 * el motor del mapa, así que nunca puede quedar "pegada" en su posición de
 * pantalla vieja al panear (eso era justo el bug: la versión en Canvas
 * dependía de que Compose se enterara del paneo para volver a dibujar).
 *
 * Se borra y se vuelve a crear en cada llamada en vez de actualizar los
 * datos de la fuente existente: es un poco menos eficiente, pero evita
 * depender de la API exacta para actualizar un GeoJsonSource ya creado,
 * que no se pudo verificar contra el SDK real en esta sesión.
 */
private fun updateDrivingRouteLayer(map: MapboxMap, polyline: List<GeoPoint>?) {
    if (map.styleLayerExists(DRIVING_ROUTE_LAYER_ID)) map.removeStyleLayer(DRIVING_ROUTE_LAYER_ID)
    if (map.styleSourceExists(DRIVING_ROUTE_SOURCE_ID)) map.removeStyleSource(DRIVING_ROUTE_SOURCE_ID)

    if (polyline == null || polyline.size < 2) return

    val lineString = LineString.fromLngLats(polyline.map { Point.fromLngLat(it.longitude, it.latitude) })
    map.addSource(geoJsonSource(DRIVING_ROUTE_SOURCE_ID) { geometry(lineString) })
    map.addLayer(
        lineLayer(DRIVING_ROUTE_LAYER_ID, DRIVING_ROUTE_SOURCE_ID) {
            lineWidth(6.0)
            lineColor(android.graphics.Color.parseColor("#3B82F6")) // RedUrbanaColors.AccentBlue
            lineCap(LineCap.ROUND)
            lineJoin(LineJoin.ROUND)
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
private fun ExploreOverlay(
    uiState: ExploreUiState,
    liveLocation: GeoPoint?,
    mapboxMap: MapboxMap?,
    modifier: Modifier = Modifier,
    // Sin uso adentro: solo para forzar redibujo en cada cambio de cámara,
    // incluido paneo puro sin zoom (ver comentario en ExploreMapScreen).
    cameraTick: Int = 0,
    // No-null en modo Vehículo: el punto "estás acá" se dibuja como el
    // emoji del vehículo elegido en vez del punto azul genérico.
    userVehicleEmoji: String? = null,
) {
    Canvas(modifier = modifier) {
        val map = mapboxMap ?: return@Canvas
        liveLocation?.let { location ->
            if (userVehicleEmoji != null) drawVehicleDot(map, location, userVehicleEmoji) else drawUserLocationDot(map, location)
        }
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
            // La línea de la ruta ya no se dibuja acá: es una capa nativa de
            // Mapbox (ver updateDrivingRouteLayer), no un Canvas — así no se
            // desincroniza del mapa al panear. Acá solo queda el pin.
            is ExploreUiState.DrivingResult -> drawPin(map, uiState.destination)
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

/** Mismo círculo blanco de fondo que el punto azul, pero con el emoji del vehículo elegido adentro en vez de un punto liso. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVehicleDot(map: MapboxMap, point: GeoPoint, emoji: String) {
    val screen = map.pixelForCoordinate(Point.fromLngLat(point.longitude, point.latitude))
    val center = Offset(screen.x.toFloat(), screen.y.toFloat())
    drawCircle(color = RedUrbanaColors.AccentBlue.copy(alpha = 0.18f), radius = 30f, center = center)
    drawCircle(color = androidx.compose.ui.graphics.Color.White, radius = 24f, center = center)
    drawCircle(color = RedUrbanaColors.AccentBlue, radius = 24f, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 30f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val metrics = paint.fontMetrics
        drawText(emoji, center.x, center.y - (metrics.ascent + metrics.descent) / 2f, paint)
    }
}

@Composable
private fun ExploreSheetContent(
    state: ExploreUiState,
    travelMode: TravelMode,
    onTravelModeSelected: (TravelMode) -> Unit,
    vehicleProfile: VehicleProfile,
    onVehicleCategorySelected: (VehicleCategory) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onItinerarySelected: (TripItinerary) -> Unit,
    onBack: () -> Unit,
    onStartTrip: (TripItinerary) -> Unit,
    onStartDriving: (destination: GeoPoint, destinationName: String, vehicleProfile: VehicleProfile) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Selector de modo: solo antes de confirmar destino — una vez que ya
        // se está mostrando un resultado (colectivo o auto), cambiar de modo
        // ahí sin cancelar primero sería ambiguo (¿recalcula el mismo destino
        // en el otro modo, o vuelve a Idle?).
        if (state is ExploreUiState.Idle || state is ExploreUiState.ConfirmingDestination) {
            TravelModeSelector(selected = travelMode, onSelected = onTravelModeSelected)
            if (travelMode == TravelMode.CAR) {
                VehiclePicker(selected = vehicleProfile.category, onSelected = onVehicleCategorySelected)
            }
        }

        when (state) {
            is ExploreUiState.Idle -> {
                Text(
                    text = "Tocá el mapa para elegir tu destino",
                    style = MaterialTheme.typography.titleMedium,
                    color = RedUrbanaColors.TextPrimary,
                )
                Text(
                    text = if (travelMode == TravelMode.CAR) {
                        "Te mostramos la ruta manejando hasta ahí."
                    } else {
                        "Te mostramos las líneas de colectivo que te sirven para llegar."
                    },
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

            is ExploreUiState.DrivingResult -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(text = "Manejando hasta", style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextSecondary)
                        Text(text = state.destinationName, style = MaterialTheme.typography.titleMedium, color = RedUrbanaColors.TextPrimary)
                    }
                    TextButton(onClick = onCancel) { Text("Cambiar destino") }
                }
                when {
                    state.isLoading -> CircularProgressIndicator(color = RedUrbanaColors.AccentGreenPrimary)
                    state.error != null -> Text(text = state.error, color = RedUrbanaColors.AlertRed)
                    state.route != null -> {
                        Text(
                            text = "${state.route.durationMinutes} min · ${formatDistance(state.route.distanceMeters)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = RedUrbanaColors.TextPrimary,
                        )
                        Button(
                            onClick = { onStartDriving(state.destination, state.destinationName, vehicleProfile) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Comenzar viaje")
                        }
                    }
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

/**
 * A pie / Bici quedan para más adelante — ver LiveMapScreen para el
 * equivalente en el mapa de seguimiento. "Colectivo" acá es viajar COMO
 * PASAJERO de transporte público (TravelMode.TRANSIT, usa TripPlanner) — no
 * confundir con "Colectivo" como categoría dentro de [VehiclePicker], que es
 * manejar vos mismo un colectivo (por eso el chip dice "Vehículo", no
 * "Auto": ahora cubre auto/moto/camioneta/camión/colectivo/emergencia).
 */
@Composable
private fun TravelModeSelector(selected: TravelMode, onSelected: (TravelMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == TravelMode.TRANSIT,
            onClick = { onSelected(TravelMode.TRANSIT) },
            label = { Text("Colectivo") },
        )
        FilterChip(
            selected = selected == TravelMode.CAR,
            onClick = { onSelected(TravelMode.CAR) },
            label = { Text("Vehículo") },
        )
    }
}

/**
 * Qué vehículo maneja el usuario — el punto "estás acá" del mapa pasa a
 * dibujarse con el emoji de la categoría elegida (ver drawVehicleDot) y, si
 * hace falta (camión/colectivo/ambulancia/bomberos), dispara el diálogo de
 * medidas para que la ruta evite calles que no puede pasar. Los de
 * emergencia van aparte y con borde propio para "destacarlos", como pidió
 * el usuario — es solo agrupación visual, no cambia cómo se calcula la ruta.
 */
@Composable
private fun VehiclePicker(selected: VehicleCategory, onSelected: (VehicleCategory) -> Unit) {
    val regular = listOf(VehicleCategory.CAR, VehicleCategory.MOTORCYCLE, VehicleCategory.PICKUP, VehicleCategory.TRUCK, VehicleCategory.BUS)
    val emergency = listOf(VehicleCategory.AMBULANCE, VehicleCategory.FIRE_TRUCK, VehicleCategory.POLICE)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(regular) { category ->
            VehicleChip(category = category, selected = category == selected, onClick = { onSelected(category) })
        }
        // Separador simple con alto fijo — nada de VerticalDivider (usa
        // fillMaxHeight, que dentro de un LazyRow sin alto explícito puede
        // pedir una altura infinita y romper el layout).
        item {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .height(32.dp)
                    .width(1.dp)
                    .background(RedUrbanaColors.TextTertiary.copy(alpha = 0.3f)),
            )
        }
        items(emergency) { category ->
            VehicleChip(category = category, selected = category == selected, onClick = { onSelected(category) })
        }
    }
}

@Composable
private fun VehicleChip(category: VehicleCategory, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text("${category.emoji} ${category.label()}") },
        colors = if (category.isEmergency) {
            androidx.compose.material3.FilterChipDefaults.filterChipColors(
                selectedContainerColor = RedUrbanaColors.AlertRed.copy(alpha = 0.25f),
            )
        } else {
            androidx.compose.material3.FilterChipDefaults.filterChipColors()
        },
        border = if (category.isEmergency) {
            androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = selected,
                borderColor = RedUrbanaColors.AlertRed,
                selectedBorderColor = RedUrbanaColors.AlertRed,
            )
        } else {
            androidx.compose.material3.FilterChipDefaults.filterChipBorder(enabled = true, selected = selected)
        },
    )
}

private fun VehicleCategory.label(): String = when (this) {
    VehicleCategory.CAR -> "Auto"
    VehicleCategory.MOTORCYCLE -> "Moto"
    VehicleCategory.PICKUP -> "Camioneta"
    VehicleCategory.TRUCK -> "Camión"
    VehicleCategory.BUS -> "Colectivo"
    VehicleCategory.AMBULANCE -> "Ambulancia"
    VehicleCategory.FIRE_TRUCK -> "Bomberos"
    VehicleCategory.POLICE -> "Patrulla"
}

/**
 * Medidas del vehículo — solo para las categorías que las necesitan (ver
 * [VehicleCategory.requiresDimensions]). Se mandan tal cual a Mapbox
 * (max_height/max_width/max_weight, ver DirectionsClient), que las usa
 * "mejor esfuerzo" para evitar calles con una restricción cargada por
 * debajo de la medida — no es una garantía de cubrir cada restricción real.
 * Los valores iniciales son solo un punto de partida típico, editable.
 */
@Composable
private fun VehicleDimensionsDialog(
    category: VehicleCategory,
    onConfirm: (VehicleDimensions) -> Unit,
    onDismiss: () -> Unit,
) {
    val defaults = defaultDimensionsFor(category)
    var height by remember(category) { mutableStateOf(defaults.heightMeters.toString()) }
    var width by remember(category) { mutableStateOf(defaults.widthMeters.toString()) }
    var weight by remember(category) { mutableStateOf(defaults.weightTons.toString()) }

    val heightValue = height.toDoubleOrNull()
    val widthValue = width.toDoubleOrNull()
    val weightValue = weight.toDoubleOrNull()
    val isValid = heightValue != null && heightValue > 0 && widthValue != null && widthValue > 0 && weightValue != null && weightValue > 0

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Medidas de ${category.emoji} ${category.label()}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Así la ruta evita túneles, puentes o calles con un límite menor a esto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RedUrbanaColors.TextSecondary,
                )
                DimensionField(label = "Altura (metros)", value = height, onValueChange = { height = it })
                DimensionField(label = "Ancho (metros)", value = width, onValueChange = { width = it })
                DimensionField(label = "Peso (toneladas)", value = weight, onValueChange = { weight = it })
            }
        },
        confirmButton = {
            Button(
                enabled = isValid,
                onClick = {
                    if (heightValue != null && widthValue != null && weightValue != null) {
                        onConfirm(VehicleDimensions(heightMeters = heightValue, widthMeters = widthValue, weightTons = weightValue))
                    }
                },
            ) { Text("Confirmar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun DimensionField(label: String, value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun defaultDimensionsFor(category: VehicleCategory): VehicleDimensions = when (category) {
    VehicleCategory.TRUCK -> VehicleDimensions(heightMeters = 4.0, widthMeters = 2.5, weightTons = 12.0)
    VehicleCategory.BUS -> VehicleDimensions(heightMeters = 3.5, widthMeters = 2.5, weightTons = 16.0)
    VehicleCategory.AMBULANCE -> VehicleDimensions(heightMeters = 2.8, widthMeters = 2.2, weightTons = 4.5)
    VehicleCategory.FIRE_TRUCK -> VehicleDimensions(heightMeters = 3.8, widthMeters = 2.5, weightTons = 15.0)
    else -> VehicleDimensions(heightMeters = 1.6, widthMeters = 1.9, weightTons = 2.5)
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

        val firstTransit = itinerary.legs.filterIsInstance<TripLeg.Transit>().firstOrNull()
        val nextDeparture = firstTransit?.nextDepartureMinutes
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
        // Frecuencia histórica (GTFS 2019), independiente del dato en vivo de
        // arriba — "cada cuánto pasa normalmente", no una predicción.
        firstTransit?.typicalHeadwayMinutes?.let { headway ->
            Text(
                text = "Normalmente cada ${headway.roundToInt()} min",
                style = MaterialTheme.typography.labelSmall,
                color = RedUrbanaColors.TextTertiary,
            )
        }
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
                        leg.typicalHeadwayMinutes?.let { headway ->
                            Text(
                                text = "Normalmente cada ${headway.roundToInt()} min",
                                style = MaterialTheme.typography.labelSmall,
                                color = RedUrbanaColors.TextTertiary,
                            )
                        }
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

/**
 * Lupa + campo de texto para buscar una dirección (antes solo se podía
 * elegir destino tocando el mapa). Dos pasos, como el SDK de Search de
 * Mapbox: mientras se escribe se piden sugerencias sin coordenadas
 * (`onQueryChanged`, con debounce en el ViewModel); tocar una sugerencia
 * recién ahí la resuelve a un punto real (`onSuggestionSelected`).
 */
@Composable
private fun DestinationSearchBar(
    query: String,
    suggestions: List<SearchSuggestion>,
    isSearching: Boolean,
    onQueryChanged: (String) -> Unit,
    onSuggestionSelected: (SearchSuggestion) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = "🔍", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = query,
                    onValueChange = onQueryChanged,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar una dirección…", color = RedUrbanaColors.TextTertiary) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
                if (query.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("✕") }
                }
            }
        }

        if (isSearching || suggestions.isNotEmpty()) {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                if (isSearching && suggestions.isEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = RedUrbanaColors.AccentGreenPrimary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(suggestions) { suggestion ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSuggestionSelected(suggestion) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(text = suggestion.name, style = MaterialTheme.typography.bodyMedium, color = RedUrbanaColors.TextPrimary)
                                val subtitle = suggestion.fullAddress ?: suggestion.descriptionText
                                if (subtitle != null) {
                                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = RedUrbanaColors.TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
