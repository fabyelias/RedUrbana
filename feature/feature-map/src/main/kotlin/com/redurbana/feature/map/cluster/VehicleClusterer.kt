package com.redurbana.feature.map.cluster

import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.RouteId
import com.redurbana.domain.transport.model.VehiclePosition
import kotlin.math.floor
import kotlin.math.pow

/** Un colectivo individual a dibujar, o un grupo de varios que se ven demasiado juntos para distinguirlos. */
sealed interface MapRenderItem {
    data class Single(val vehicle: VehiclePosition) : MapRenderItem
    data class Cluster(
        val center: GeoPoint,
        val count: Int,
        val representativeRouteId: RouteId,
        val vehicleIds: List<String>,
    ) : MapRenderItem
}

/**
 * Agrupa vehículos por celda de grilla, donde el tamaño de celda se achica a
 * medida que se hace zoom (más zoom = celdas más chicas = menos agrupamiento,
 * hasta llegar a mostrar cada colectivo individual).
 *
 * Deliberadamente puro/sin dependencias de Android ni de Compose: es Kotlin
 * plano, testeable con JUnit sin emulador. La conversión a píxeles de
 * pantalla ocurre después, en VehicleCanvasOverlay — este archivo solo
 * decide QUÉ se agrupa, no CÓMO se dibuja.
 *
 * Nota de diseño: agrupar en coordenadas geográficas (en vez de en píxeles
 * de pantalla ya proyectados) evita tener que re-clusterizar en cada pequeño
 * movimiento de cámara — solo hace falta recalcular cuando cambia el zoom
 * de forma perceptible, no en cada frame de paneo.
 */
object VehicleClusterer {

    /**
     * @param maxUnclusteredCount si hay menos vehículos visibles que esto,
     *   ni siquiera se intenta agrupar (a esa escala, mostrarlos todos es
     *   más barato que el propio cálculo de clustering).
     */
    fun cluster(
        vehicles: List<VehiclePosition>,
        zoomLevel: Float,
        maxUnclusteredCount: Int = 120,
    ): List<MapRenderItem> {
        if (vehicles.size <= maxUnclusteredCount) {
            return vehicles.map { MapRenderItem.Single(it) }
        }

        val cellSizeDegrees = cellSizeForZoom(zoomLevel)
        val buckets = LinkedHashMap<Long, MutableList<VehiclePosition>>()

        for (vehicle in vehicles) {
            val row = floor(vehicle.position.latitude / cellSizeDegrees).toLong()
            val col = floor(vehicle.position.longitude / cellSizeDegrees).toLong()
            val key = (row shl 32) xor (col and 0xFFFFFFFFL)
            buckets.getOrPut(key) { mutableListOf() }.add(vehicle)
        }

        return buckets.values.map { group ->
            if (group.size == 1) {
                MapRenderItem.Single(group.first())
            } else {
                val avgLat = group.sumOf { it.position.latitude } / group.size
                val avgLng = group.sumOf { it.position.longitude } / group.size
                MapRenderItem.Cluster(
                    center = GeoPoint(avgLat, avgLng),
                    count = group.size,
                    representativeRouteId = group.first().routeId,
                    vehicleIds = group.map { it.vehicleId.value },
                )
            }
        }
    }

    /**
     * A mayor zoom (más acercado), celdas más chicas. Los valores están
     * calibrados para que a zoom ~16 (nivel calle, el default de la app)
     * prácticamente no se agrupe, y a zoom ~11-12 (vista de toda la ciudad)
     * sí se agrupen colectivos que están a pocas cuadras entre sí.
     */
    private fun cellSizeForZoom(zoomLevel: Float): Double {
        val baseCellSizeAtZoom10 = 0.03 // ~3.3km
        val zoomDelta = (zoomLevel - 10f).coerceAtLeast(0f)
        return baseCellSizeAtZoom10 / 2.0.pow(zoomDelta.toDouble())
    }
}
