package com.redurbana.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
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
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.LiveBadge
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.feature.map.cluster.VehicleClusterer
import kotlinx.coroutines.launch

/**
 * Mapa principal de la app, sobre Mapbox Maps SDK (Compose extension).
 *
 * Por qué Mapbox y no Google Maps acá: el estilo "Standard" con
 * lightPreset = NIGHT da 3D buildings + un mapa oscuro nativo que calza con
 * la identidad visual de la app sin tener que pelear con JSON de estilo
 * custom, como hacía falta con Google Maps.
 *
 * ESTRATEGIA DE RENDERIZADO (sin cambios respecto a la versión con Google
 * Maps — la migración de SDK no tocó esta decisión de diseño):
 *  - El vehículo seleccionado/seguido se dibuja con VehicleMapMarker3D
 *    (ahora implementado como ViewAnnotation de Mapbox: Compose real
 *    montado sobre el mapa, con animación de interpolación).
 *  - El resto de la flota se dibuja en VehicleCanvasOverlay, un único
 *    Canvas — la traducción GeoPoint→pantalla ahora usa
 *    `mapboxMap.pixelForCoordinate(Point)` en vez de la Projection de
 *    Google, pero el principio (una sola superficie de dibujo) es el mismo.
 *
 * NOTA: la API de Compose de Mapbox evoluciona rápido entre versiones
 * menores. Validado contra Maps SDK 11.26.0: el estilo Standard se
 * configura vía `standardStyleState.configurationsState` (no
 * `styleImportConfig`), con `LightPresetValue` (no `StandardLightPreset`)
 * y `BooleanValue` para flags como `show3dObjects`. Si se sube de versión
 * mayor, re-chequear contra la doc vigente de Mapbox.
 */
@Composable
fun LiveMapScreen(
    modifier: Modifier = Modifier,
    viewModel: LiveMapViewModel = hiltViewModel(),
    onVehicleClick: (routeId: String, vehicleId: String) -> Unit = { _, _ -> },
    onSearchDestinationClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRouteId = (uiState as? MapUiState.Success)?.selectedRouteId

    if (uiState is MapUiState.Success && selectedRouteId == null) {
        // Sin línea elegida: ni siquiera montamos el mapa de Mapbox (motor
        // 3D + tiles), no solo el overlay de vehículos — es lo que resuelve
        // el consumo de recursos al abrir la app.
        NoDestinationSelectedContent(modifier = modifier, onSearchDestinationClick = onSearchDestinationClick)
        return
    }

    // Vista panorámica neutral de CABA mientras se resuelve el GPS real —
    // a propósito NO es un punto/zoom específico que pueda confundirse con
    // "acá estás vos": eso pasaba antes con el fallback fijo a Congreso, que
    // se mostraba como si fuera la ubicación real cuando el GPS no
    // respondía a tiempo. En cuanto llega el primer fix real, el efecto de
    // abajo hace zoom ahí una sola vez.
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(Point.fromLngLat(-58.42, -34.62))
            zoom(11.0)
        }
    }

    // Estilo oscuro nativo con edificios 3D — sin JSON de estilo custom.
    val standardStyleState = rememberStandardStyleState {
        configurationsState.apply {
            lightPreset = LightPresetValue.NIGHT
            show3dObjects = BooleanValue(true)
        }
    }

    // Referencia al MapboxMap nativo, para pixelForCoordinate() en el overlay
    // y para leer bounds/zoom actuales — el equivalente a `Projection` de Google.
    var nativeMap by remember { mutableStateOf<MapboxMap?>(null) }
    var currentZoom by remember { mutableStateOf(16f) }

    // Se incrementa en CADA cambio de cámara (pan, zoom o rotación). currentZoom
    // solo no alcanza como disparador de redibujo: si el usuario solo panea
    // (sin cambiar el zoom), currentZoom no cambia y el Canvas de abajo no se
    // vuelve a dibujar — el punto azul y los colectivos quedan pegados en su
    // posición de pantalla vieja mientras el mapa se desliza debajo, dando la
    // sensación de que "el punto se mueve" o "el mapa se congela".
    var cameraTick by remember { mutableIntStateOf(0) }

    val userLocation = (uiState as? MapUiState.Success)?.userLocation
    val recenterScope = rememberCoroutineScope()

    // Centrado único apenas llega el primer fix real de GPS — sin fallback a
    // ningún punto fijo: si el GPS tarda, la cámara simplemente se queda en
    // la vista panorámica inicial hasta que haya una ubicación real.
    var hasCenteredOnUser by remember { mutableStateOf(false) }
    LaunchedEffect(userLocation) {
        if (userLocation != null && !hasCenteredOnUser) {
            hasCenteredOnUser = true
            mapViewportState.flyTo(
                cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                    .center(Point.fromLngLat(userLocation.longitude, userLocation.latitude))
                    .zoom(16.0)
                    .pitch(45.0)
                    .build(),
                animationOptions = MapAnimationOptions.mapAnimationOptions { duration(1000) },
            )
        }
    }

    val cameraTarget = (uiState as? MapUiState.Success)?.cameraTarget
    LaunchedEffect(cameraTarget) {
        val target = cameraTarget ?: return@LaunchedEffect
        mapViewportState.flyTo(
            cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                .center(Point.fromLngLat(target.longitude, target.latitude))
                .build(),
            animationOptions = MapAnimationOptions.mapAnimationOptions { duration(1000) },
        )
    }

    val vehicles = (uiState as? MapUiState.Success)?.vehicles.orEmpty()
    val selectedId = (uiState as? MapUiState.Success)?.selectedVehicleId
    val selectedVehicle = vehicles.firstOrNull { it.vehicleId.value == selectedId }

    val renderItems = remember(vehicles, currentZoom) {
        VehicleClusterer.cluster(vehicles = vehicles, zoomLevel = currentZoom)
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapboxMapComposable(
            modifier = Modifier.fillMaxSize(),
            mapViewportState = mapViewportState,
            style = { MapboxStandardStyle(standardStyleState = standardStyleState) },
        ) {
            MapEffect(Unit) { mapView ->
                nativeMap = mapView.mapboxMap
                mapView.mapboxMap.subscribeCameraChanged {
                    nativeMap = mapView.mapboxMap
                    currentZoom = mapView.mapboxMap.cameraState.zoom.toFloat()
                    cameraTick++
                }
                mapView.mapboxMap.subscribeStyleLoaded { addTrafficLayer(mapView.mapboxMap) }
            }

            // Único ViewAnnotation "pesado": el vehículo seleccionado/seguido.
            selectedVehicle?.let { vehicle ->
                VehicleMapMarker3D(
                    vehicle = vehicle,
                    isSelected = true,
                    onClick = {
                        viewModel.onVehicleSelected(vehicle.vehicleId.value)
                        onVehicleClick(vehicle.routeId.value, vehicle.vehicleId.value)
                    },
                )
            }
        }

        // El resto de la flota (potencialmente miles) se dibuja acá.
        VehicleCanvasOverlay(
            items = renderItems,
            mapboxMap = nativeMap,
            selectedVehicleId = selectedId,
            onVehicleTap = { vehicleId ->
                val vehicle = vehicles.firstOrNull { it.vehicleId.value == vehicleId }
                if (vehicle != null) {
                    viewModel.onVehicleSelected(vehicleId)
                    onVehicleClick(vehicle.routeId.value, vehicleId)
                }
            },
            modifier = Modifier.fillMaxSize(),
            userLocation = userLocation,
            cameraTick = cameraTick,
        )

        LiveBadge(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp),
        )

        TextButton(
            onClick = onSearchDestinationClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
        ) {
            Text("Cambiar destino")
        }

        // Botón "mi ubicación" estilo Google Maps: recentra la cámara a
        // demanda sin pelear con el usuario mientras panea libremente.
        if (userLocation != null) {
            FloatingActionButton(
                onClick = {
                    recenterScope.launch {
                        mapViewportState.flyTo(
                            cameraOptions = com.mapbox.maps.CameraOptions.Builder()
                                .center(Point.fromLngLat(userLocation.longitude, userLocation.latitude))
                                .zoom(16.0)
                                .pitch(45.0)
                                .build(),
                            animationOptions = MapAnimationOptions.mapAnimationOptions { duration(600) },
                        )
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            ) {
                Text("📍")
            }
        }

        when {
            uiState is MapUiState.Loading -> {
                Text(
                    text = "Cargando colectivos…",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            uiState is MapUiState.Success && vehicles.isEmpty() -> {
                ColdStartMessage(modifier = Modifier.align(Alignment.Center))
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

/**
 * Estado inicial de la pantalla: todavía no se eligió destino/línea. A
 * propósito no monta [MapboxMapComposable] — evita motor 3D + descarga de
 * tiles + simulación de flota hasta que hace falta de verdad.
 */
@Composable
private fun NoDestinationSelectedContent(modifier: Modifier = Modifier, onSearchDestinationClick: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        GlassCard(
            modifier = Modifier.padding(24.dp),
            contentPadding = PaddingValues(20.dp),
        ) {
            Text(
                text = "Elegí tu destino",
                style = MaterialTheme.typography.titleMedium,
                color = RedUrbanaColors.TextPrimary,
            )
            Text(
                text = "Buscá a dónde vas y te mostramos solo la línea que te conviene, en vez de toda la flota de una.",
                style = MaterialTheme.typography.bodyMedium,
                color = RedUrbanaColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = onSearchDestinationClick,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Buscar destino")
            }
        }
    }
}

/**
 * Se muestra cuando el mapa ya cargó pero no hay ningún colectivo con
 * evidencia suficiente en esta zona — distinto de "cargando" (que es
 * transitorio) y distinto de "fuera de servicio" (que es un dato que solo
 * una fuente oficial podría afirmar; el crowdsourcing nunca sabe eso, solo
 * sabe que todavía no tiene reportes).
 */
@Composable
private fun ColdStartMessage(modifier: Modifier = Modifier) {
    com.redurbana.core.ui.components.GlassCard(
        modifier = modifier.padding(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(
            text = "Sin reportes suficientes en esta zona todavía",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = com.redurbana.core.ui.theme.RedUrbanaColors.TextPrimary,
        )
        Text(
            text = "Activá \"Colaborar con la comunidad\" en Ajustes para ayudar a que esto se resuelva más rápido — sos de los primeros en esta zona.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = com.redurbana.core.ui.theme.RedUrbanaColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
