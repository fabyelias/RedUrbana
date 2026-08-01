package com.redurbana.data.transport.spatial

import com.redurbana.domain.transport.GeoBounds
import com.redurbana.domain.transport.model.GeoPoint
import kotlin.math.floor

/**
 * Índice espacial simple en grilla (uniform grid / bucket hashing).
 *
 * Por qué existe: con cientos o miles de vehículos, filtrar "¿cuáles están
 * dentro del viewport?" recorriendo la lista completa en cada tick (O(n) por
 * tick) se vuelve el cuello de botella antes que el renderizado. Este índice
 * divide el mapa en celdas de tamaño fijo ([cellSizeDegrees]) y solo recorre
 * las celdas que caen dentro del bounds consultado — la cantidad de celdas
 * visibles es independiente del tamaño total de la flota.
 *
 * Es intencionalmente simple (grilla uniforme, no quad-tree ni R-tree): para
 * el rango de una ciudad y unos pocos miles de vehículos es más que
 * suficiente, y mucho más fácil de mantener. Si en el futuro hace falta
 * densidad muy despareja a escala país, este es el punto de reemplazo por un
 * quad-tree, sin cambiar a quién lo consume (mismo rebuild()/query()).
 */
class SpatialGrid<T>(
    private val locationOf: (T) -> GeoPoint,
    private val cellSizeDegrees: Double = 0.01, // ~1.1km en latitud, suficiente para un viewport típico
) {
    private val buckets: MutableMap<Long, MutableList<T>> = HashMap()

    private fun cellKey(row: Long, col: Long): Long = (row shl 32) xor (col and 0xFFFFFFFFL)

    /** Reconstruye el índice completo. Se llama una vez por tick de simulación, no por consulta. */
    fun rebuild(items: List<T>) {
        buckets.clear()
        for (item in items) {
            val point = locationOf(item)
            val row = floor(point.latitude / cellSizeDegrees).toLong()
            val col = floor(point.longitude / cellSizeDegrees).toLong()
            buckets.getOrPut(cellKey(row, col)) { mutableListOf() }.add(item)
        }
    }

    /** Devuelve solo los items cuyas celdas intersectan el bounds pedido. */
    fun query(bounds: GeoBounds): List<T> {
        val minRow = floor(bounds.southWest.latitude / cellSizeDegrees).toLong()
        val maxRow = floor(bounds.northEast.latitude / cellSizeDegrees).toLong()
        val minCol = floor(bounds.southWest.longitude / cellSizeDegrees).toLong()
        val maxCol = floor(bounds.northEast.longitude / cellSizeDegrees).toLong()

        val result = mutableListOf<T>()
        for (row in minRow..maxRow) {
            for (col in minCol..maxCol) {
                buckets[cellKey(row, col)]?.let { result.addAll(it) }
            }
        }
        return result
    }
}
