package com.redurbana.data.transport.vehicle

import android.content.Context
import com.redurbana.domain.transport.VehicleGarageRepository
import com.redurbana.domain.transport.model.VehicleCategory
import com.redurbana.domain.transport.model.VehicleDimensions
import com.redurbana.domain.transport.model.VehicleProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "vehicle_garage"
private const val KEY_SAVED_VEHICLES = "saved_vehicles"
private const val KEY_ACTIVE_CATEGORY = "active_category"

/**
 * SharedPreferences, no DataStore: es un puñado de filas chiquitas (como
 * mucho 8, una por VehicleCategory) que se leen/escriben poco seguido
 * (abrir la app, elegir vehículo) — no vale la pena sumar una dependencia
 * nueva (androidx.datastore) para esto. Mismo criterio que ya usa
 * MainActivity para recordar si ya se pidió el permiso de ubicación.
 *
 * El cache en memoria (_savedVehicles/_activeVehicle) es la fuente de
 * verdad para los Flow — se lee de SharedPreferences una sola vez al
 * construirse (Singleton, vive todo el proceso) y cada escritura actualiza
 * las dos cosas a la vez, así no hace falta un OnSharedPreferenceChangeListener.
 */
@Singleton
class SharedPrefsVehicleGarageRepository @Inject constructor(
    @ApplicationContext context: Context,
) : VehicleGarageRepository {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _savedVehicles = MutableStateFlow(loadSavedVehicles())
    private val _activeVehicle = MutableStateFlow(loadActiveVehicle(_savedVehicles.value))

    override fun observeSavedVehicles(): Flow<List<VehicleProfile>> = _savedVehicles.asStateFlow()

    override fun observeActiveVehicle(): Flow<VehicleProfile> = _activeVehicle.asStateFlow()

    override suspend fun selectVehicle(profile: VehicleProfile) {
        val updated = _savedVehicles.value.filterNot { it.category == profile.category } + profile
        _savedVehicles.value = updated
        _activeVehicle.value = profile
        persist(updated, profile.category)
    }

    override suspend fun removeVehicle(category: VehicleCategory) {
        val updated = _savedVehicles.value.filterNot { it.category == category }
        _savedVehicles.value = updated
        val activeAfterRemoval = if (_activeVehicle.value.category == category) {
            updated.firstOrNull() ?: VehicleProfile()
        } else {
            _activeVehicle.value
        }
        _activeVehicle.value = activeAfterRemoval
        persist(updated, activeAfterRemoval.category)
    }

    private fun persist(vehicles: List<VehicleProfile>, activeCategory: VehicleCategory) {
        val dtos: List<SavedVehicleDto> = vehicles.map { it.toDto() }
        prefs.edit()
            .putString(KEY_SAVED_VEHICLES, json.encodeToString(ListSerializer(SavedVehicleDto.serializer()), dtos))
            .putString(KEY_ACTIVE_CATEGORY, activeCategory.name)
            .apply()
    }

    private fun loadSavedVehicles(): List<VehicleProfile> {
        val raw = prefs.getString(KEY_SAVED_VEHICLES, null) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(SavedVehicleDto.serializer()), raw) }
            .getOrDefault(emptyList())
            .mapNotNull { it.toDomainOrNull() }
    }

    private fun loadActiveVehicle(savedVehicles: List<VehicleProfile>): VehicleProfile {
        val activeCategory = prefs.getString(KEY_ACTIVE_CATEGORY, null)
            ?.let { name -> runCatching { VehicleCategory.valueOf(name) }.getOrNull() }
        return savedVehicles.firstOrNull { it.category == activeCategory } ?: VehicleProfile()
    }
}

@Serializable
private data class SavedVehicleDto(
    val category: String,
    val heightMeters: Double? = null,
    val widthMeters: Double? = null,
    val weightTons: Double? = null,
)

private fun VehicleProfile.toDto() = SavedVehicleDto(
    category = category.name,
    heightMeters = dimensions?.heightMeters,
    widthMeters = dimensions?.widthMeters,
    weightTons = dimensions?.weightTons,
)

/** null si la categoría guardada ya no existe (ej. versión vieja de la app con categorías que se sacaron) — se descarta en vez de romper el resto del garage. */
private fun SavedVehicleDto.toDomainOrNull(): VehicleProfile? {
    val vehicleCategory = runCatching { VehicleCategory.valueOf(category) }.getOrNull() ?: return null
    val dimensions = if (heightMeters != null && widthMeters != null && weightTons != null) {
        VehicleDimensions(heightMeters = heightMeters, widthMeters = widthMeters, weightTons = weightTons)
    } else {
        null
    }
    return VehicleProfile(category = vehicleCategory, dimensions = dimensions)
}
