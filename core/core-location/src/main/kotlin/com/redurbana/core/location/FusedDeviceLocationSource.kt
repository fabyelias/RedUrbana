package com.redurbana.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.datetime.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real con `FusedLocationProviderClient` (Google Play Services).
 *
 * El chequeo de permiso vive acá, no en la UI: es la última línea de defensa
 * antes de tocar la API de ubicación. Si se llama a esto sin permiso
 * concedido, devuelve un Flow vacío en vez de crashear con
 * `SecurityException` — el pedido real del permiso (una sola vez, al abrir
 * la app) vive en `MainActivity`.
 *
 * PRIORITY_BALANCED_POWER_ACCURACY por defecto, no PRIORITY_HIGH_ACCURACY:
 * la de alta precisión depende casi excluyentemente del chip GPS — en una
 * tablet solo-WiFi (sin GPS ni datos móviles), FusedLocationProviderClient
 * nunca llega a resolver un fix con esa prioridad y el Flow se queda mudo
 * para siempre. La prioridad balanceada también acepta ubicación por red
 * (WiFi), que sí está disponible en ese escenario.
 *
 * [DeviceLocationSource.observeLocation]'s `highAccuracy = true` (usado por
 * la navegación paso a paso manejando) pide PRIORITY_HIGH_ACCURACY igual:
 * ahí el dispositivo real ya está en un vehículo en movimiento (GPS
 * disponible en la enorme mayoría de los casos) y la precisión de carril +
 * velocidad/rumbo reales importan mucho más que agotar batería.
 */
@Singleton
class FusedDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationSource {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission") // chequeado a mano antes de llamar a la API de ubicación
    override fun observeLocation(intervalMs: Long, highAccuracy: Boolean): Flow<RawLocationSample> = callbackFlow {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            close()
            return@callbackFlow
        }

        // Último fix cacheado (si existe): suele llegar casi al instante,
        // mucho antes que el primer resultado de requestLocationUpdates —
        // evita esperar de más para el primer centrado de cámara.
        client.lastLocation.addOnSuccessListener { location ->
            if (location != null) trySend(location.toRawSample())
        }

        val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val request = LocationRequest.Builder(intervalMs)
            .setPriority(priority)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(location.toRawSample())
            }
        }

        client.requestLocationUpdates(request, callback, null)
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun android.location.Location.toRawSample() = RawLocationSample(
        latitude = latitude,
        longitude = longitude,
        speedMetersPerSecond = speed,
        bearingDegrees = bearing,
        timestamp = Instant.fromEpochMilliseconds(time),
        accuracyMeters = accuracy,
    )
}
