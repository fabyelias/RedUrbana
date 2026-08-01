package com.redurbana.core.location

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación real con `com.google.android.gms.location.FusedLocationProviderClient`.
 *
 * Esqueleto: la firma y el punto de inyección ya están listos, falta la
 * implementación con el cliente de Google Play Services (requiere manejar
 * permisos runtime — eso vive en la capa de UI, no acá — y convertir los
 * callbacks de `LocationCallback` a `Flow` con `callbackFlow`).
 *
 * TODO:
 *  1. Inyectar `FusedLocationProviderClient` (vía un @Provides en un Hilt module).
 *  2. `callbackFlow { ... }` que registre un `LocationCallback`, mapee cada
 *     `Location` a [RawLocationSample], y haga `awaitClose { removeUpdates(...) }`.
 *  3. Manejar `SecurityException` si el permiso se revocó a mitad de stream.
 */
@Singleton
class FusedDeviceLocationSource @Inject constructor() : DeviceLocationSource {

    override fun observeLocation(intervalMs: Long): Flow<RawLocationSample> = flow {
        throw NotImplementedError(
            "FusedDeviceLocationSource: pendiente de integrar FusedLocationProviderClient " +
                "(ver TODO en el archivo). Mientras tanto, usar MockDeviceLocationSource " +
                "para desarrollo/demo.",
        )
    }
}
