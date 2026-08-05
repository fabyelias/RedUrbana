package com.redurbana.feature.map

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.extension.compose.MapEffect
import com.mapbox.maps.extension.compose.MapboxMap as MapboxMapComposable
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.style.MapboxStandardStyle
import com.mapbox.maps.extension.compose.style.standard.StandardLightPreset
import com.mapbox.maps.extension.compose.style.standard.rememberStandardStyleState
import com.mapbox.maps.plugin.PuckBearing
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.mapbox.maps.plugin.locationcomponent.createDefault2DPuck
import com.mapbox.maps.plugin.locationcomponent.location
import com.mapbox.maps.plugin.viewport.data.FollowPuckViewportStateOptions
import com.redurbana.core.ui.components.LiveBadge
import com.redurbana.domain.transport.GeoBounds
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
 *  - La ubicación del usuario (punto azul) se dibuja con el LocationComponent
 *    nativo de Mapbox (`mapView.location`), no a mano — así queda anclado a
 *    lat/lng por el SDK y no se corre al mover el mapa. La cámara arranca y
 *    se mantiene siguiendo ese puck (`transitionToFollowPuckState`) salvo que
 *    haya un vehículo seguido, en cuyo caso la cámara lo sigue a él en su lugar.
 *
 * NOTA: la API de Compose de Mapbox evoluciona rápido entre versiones
 * menores — validar los nombres exactos (StandardLightPreset,
 * rememberStandardStyleState, etc.) contra la doc vigente de Mapbox al
 * compilar esto por primera vez.
 */
@Composable
fun LiveMapScreen(
    modifier: Modifier = Modifier,
    viewModel: LiveMapViewModel = hiltViewModel(),
    onVehicleClick: (routeId: String, vehicleId: String) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> hasLocationPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // Sin centro inicial fijo: la cámara arranca y se mantiene sobre la
    // ubicación real del usuario vía transitionToFollowPuckState() más abajo.
    val mapViewportState = rememberMapViewportState()

    // Estilo oscuro nativo con edificios 3D — sin JSON de estilo custom.
    val standardStyleState = rememberStandardStyleState().apply {
        styleImportConfig.apply {
            lightPreset = StandardLightPreset.NIGHT
            show3dObjects = true
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

    // Sin vehículo seguido: la cámara sigue al usuario (arranque y estado
    // por defecto). Al dejar de seguir un vehículo, esto retoma el seguimiento.
    LaunchedEffect(hasLocationPermission, cameraTarget) {
        if (hasLocationPermission && cameraTarget == null) {
            mapViewportState.transitionToFollowPuckState(
                followPuckViewportStateOptions = FollowPuckViewportStateOptions.Builder()
                    .zoom(16.0)
                    .pitch(45.0) // tilt para que los edificios 3D se vean con perspectiva
                    .build(),
            )
        }
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
            style = { MapboxStandardStyle(styleState = standardStyleState) },
        ) {
            MapEffect(Unit) { mapView ->
                nativeMap = mapView.mapboxMap
                mapView.location.updateSettings {
                    enabled = true
                    puckBearing = PuckBearing.COURSE
                    puckBearingEnabled = true
                    locationPuck = createDefault2DPuck(withBearing = true)
                    pulsingEnabled = true
                }
                mapView.mapboxMap.subscribeCameraChanged {
                    nativeMap = mapView.mapboxMap
                    currentZoom = mapView.mapboxMap.cameraState.zoom.toFloat()
                    viewModel.updateVisibleBounds(mapView.mapboxMap.toGeoBounds())
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
                .align(Alignment.TopEnd)
                .padding(16.dp),
        )

        if (!hasLocationPermission) {
            LocationPermissionRationale(
                onRetryClick = {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                },
                modifier = Modifier.align(Alignment.Center),
            )
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

/**
 * Se muestra cuando el usuario todavía no otorgó el permiso de ubicación —
 * sin esto el mapa no puede mostrar el punto azul ni seguir al usuario.
 */
@Composable
private fun LocationPermissionRationale(onRetryClick: () -> Unit, modifier: Modifier = Modifier) {
    com.redurbana.core.ui.components.GlassCard(
        modifier = modifier.padding(24.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Text(
            text = "Necesitamos tu ubicación",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            color = com.redurbana.core.ui.theme.RedUrbanaColors.TextPrimary,
        )
        Text(
            text = "Activá el permiso de ubicación para ver dónde estás parado en el mapa.",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = com.redurbana.core.ui.theme.RedUrbanaColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Button(onClick = onRetryClick) {
            Text("Reintentar")
        }
    }
}

/**
 * Traduce el viewport actual de Mapbox a GeoBounds de dominio.
 * Es la ÚNICA función de todo feature-map que sabe que existe
 * CoordinateBounds de Mapbox — el resto de la feature trabaja en
 * GeoPoint/GeoBounds puros, igual que cuando esto usaba Google Maps.
 */
private fun MapboxMap.toGeoBounds(): GeoBounds {
    val bounds = coordinateBoundsForCamera(cameraState.toCameraOptions())
    return GeoBounds(
        northEast = GeoPoint(bounds.northeast.latitude(), bounds.northeast.longitude()),
        southWest = GeoPoint(bounds.southwest.latitude(), bounds.southwest.longitude()),
    )
}

private fun com.mapbox.maps.CameraState.toCameraOptions(): com.mapbox.maps.CameraOptions =
    com.mapbox.maps.CameraOptions.Builder()
        .center(center)
        .zoom(zoom)
        .bearing(bearing)
        .pitch(pitch)
        .build()
