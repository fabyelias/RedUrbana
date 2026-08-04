package com.redurbana.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.redurbana.core.ui.components.GlassCard
import com.redurbana.core.ui.components.LiveBadge
import com.redurbana.core.ui.theme.RedUrbanaColors
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.feature.map.cluster.VehicleClusterer

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

    // Congreso, CABA — mismo punto de partida que la referencia visual.
    val congreso = Point.fromLngLat(-58.3924, -34.6095) // Mapbox: (lng, lat), OJO con el orden

    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(congreso)
            zoom(16.0)
            pitch(45.0) // tilt para que los edificios 3D se vean con perspectiva
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
                }
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
