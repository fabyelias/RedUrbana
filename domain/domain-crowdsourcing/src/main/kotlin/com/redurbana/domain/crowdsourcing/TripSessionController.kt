package com.redurbana.domain.crowdsourcing

import com.redurbana.domain.transport.model.RouteId

/**
 * Separado de [CrowdSourcingRepository] a propósito: LocationReporter (la
 * implementación real, en data-crowdsourcing) ya depende de
 * [com.redurbana.domain.crowdsourcing.usecase.ReportCrowdPingUseCase], que a
 * su vez depende de [CrowdSourcingRepository] — si esto viviera en esa misma
 * interfaz, la implementación de CrowdSourcingRepository necesitaría inyectar
 * a LocationReporter y se armaría un ciclo de dependencias en Hilt.
 *
 * Marca qué línea es el "viaje activo" ahora mismo: el reporte de pings solo
 * corre mientras haya un viaje activo Y el opt-in esté prendido. `null`
 * significa "ningún viaje activo" (se llama al salir de la pantalla del
 * mapa con línea elegida).
 */
interface TripSessionController {
    fun setActiveTrip(routeId: RouteId?)
}
