package com.redurbana.domain.transport

import com.redurbana.domain.transport.model.VehicleCategory
import com.redurbana.domain.transport.model.VehicleProfile
import kotlinx.coroutines.flow.Flow

/**
 * "Mis vehículos": a diferencia del resto de las preferencias de esta app
 * (todas efímeras, se resetean al reabrir — ver el comentario en
 * SupabaseCrowdSourcingRepository sobre el opt-in), esto SÍ tiene que
 * sobrevivir cerrar la app. Pedido explícito: uno puede manejar una
 * ambulancia hoy y su auto mañana, y no debería tener que volver a cargar
 * las medidas de la ambulancia cada vez — el dispositivo "recuerda" los
 * vehículos ya usados y cuál es el activo ahora mismo.
 *
 * Guardado por categoría (no hay concepto de "dos camiones distintos" en
 * esta versión — elegir Camión de nuevo actualiza las medidas guardadas de
 * Camión en vez de crear una entrada aparte). Sin cuenta/login de por
 * medio: es un dato del dispositivo, no de un usuario en la nube.
 */
interface VehicleGarageRepository {

    /** Vehículos que el usuario ya usó alguna vez en este dispositivo, uno por categoría. */
    fun observeSavedVehicles(): Flow<List<VehicleProfile>>

    /** Con cuál está viajando ahora — se restaura solo la próxima vez que se abra la app. */
    fun observeActiveVehicle(): Flow<VehicleProfile>

    /** Guarda (o actualiza, si ya existía esa categoría) y lo marca como activo en el mismo paso. */
    suspend fun selectVehicle(profile: VehicleProfile)

    /** Si el que se borra era el activo, el activo pasa a ser Auto por defecto. */
    suspend fun removeVehicle(category: VehicleCategory)
}
