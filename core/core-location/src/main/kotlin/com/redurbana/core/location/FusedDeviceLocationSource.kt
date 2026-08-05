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
 */
@Singleton
class FusedDeviceLocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocationSource {

    private val client: FusedLocationProviderClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    @SuppressLint("MissingPermission") // chequeado a mano antes de llamar a requestLocationUpdates
    override fun observeLocation(intervalMs: Long): Flow<RawLocationSample> = callbackFlow {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            close()
            return@callbackFlow
        }

        val request = LocationRequest.Builder(intervalMs)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                trySend(
                    RawLocationSample(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speedMetersPerSecond = location.speed,
                        bearingDegrees = location.bearing,
                        timestamp = Instant.fromEpochMilliseconds(location.time),
                        accuracyMeters = location.accuracy,
                    ),
                )
            }
        }

        client.requestLocationUpdates(request, callback, null)
        awaitClose { client.removeLocationUpdates(callback) }
    }
}
