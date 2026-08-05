package com.redurbana.data.transport.mock

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class FrequencyWindowDto(val startTime: String, val endTime: String, val headwayMinutes: Double)

@Serializable
private data class DayScheduleDto(val dayType: String, val windows: List<FrequencyWindowDto>)

@Serializable
private data class RouteFrequencyDto(val routeId: String, val schedule: List<DayScheduleDto>)

/**
 * Frecuencia esperada por línea (cada cuánto pasa normalmente), sacada de un
 * GTFS oficial de la Ciudad (frequencies.txt, jurisdicción CNRT) de 2019 —
 * es lo más nuevo públicamente disponible: la API en tiempo real de la
 * Ciudad está suspendida desde julio 2026. Cubre 273 de las 275 líneas de
 * real_routes.json (match por número de línea, no por routeId interno del
 * GTFS — cada ramal del GTFS original es una "línea" separada ahí). Mismo
 * GTFS que se usó para agregar las 137 líneas que le faltaban a
 * `RealRouteData` (ver ese archivo).
 *
 * A propósito NO se usa para calcular "a horario / demorado": eso implicaría
 * comparar contra la posición de un vehículo SIMULADO, no real, lo que daría
 * un resultado inventado. Se muestra tal cual, como referencia honesta de
 * "normalmente cada X min" — el día que haya posiciones reales (crowdsourcing
 * agregado, o vuelve la API oficial), esta misma base sirve para calcular
 * demoras de verdad.
 */
@Singleton
class ExpectedFrequencyData @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var cache: Map<String, RouteFrequencyDto>? = null

    private suspend fun routes(): Map<String, RouteFrequencyDto> {
        cache?.let { return it }
        return withContext(Dispatchers.IO) {
            val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
            json.decodeFromString<List<RouteFrequencyDto>>(text)
                .associateBy { it.routeId }
                .also { cache = it }
        }
    }

    /** Cada cuántos minutos pasa habitualmente esta línea ahora mismo, o null si no hay dato para esa línea/franja horaria. */
    suspend fun typicalHeadwayMinutes(routeId: String, now: LocalDateTime = LocalDateTime.now()): Double? {
        val schedule = routes()[routeId]?.schedule ?: return null
        val dayType = when (now.dayOfWeek) {
            DayOfWeek.SATURDAY -> "SATURDAY"
            DayOfWeek.SUNDAY -> "SUNDAY"
            else -> "WEEKDAY"
        }
        val windows = schedule.firstOrNull { it.dayType == dayType }?.windows ?: return null
        val minuteOfDay = now.hour * 60 + now.minute
        return windows.firstOrNull { w ->
            val start = parseMinutes(w.startTime)
            val end = parseMinutes(w.endTime)
            if (end > start) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end
        }?.headwayMinutes
    }

    private fun parseMinutes(hhmm: String): Int {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        return h * 60 + m
    }

    private companion object {
        const val ASSET_NAME = "expected_frequency.json"
    }
}
