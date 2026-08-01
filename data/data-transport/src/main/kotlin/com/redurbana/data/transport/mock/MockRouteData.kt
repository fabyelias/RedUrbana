package com.redurbana.data.transport.mock

import com.redurbana.domain.transport.model.Branch
import com.redurbana.domain.transport.model.GeoPoint
import com.redurbana.domain.transport.model.ReliabilityScore
import com.redurbana.domain.transport.model.RouteDetails
import com.redurbana.domain.transport.model.RouteId

/**
 * Recorridos simplificados (pocos waypoints, suficientes para animar de forma
 * creíble) alrededor del Congreso, CABA — la misma zona de la referencia visual.
 * Coordenadas aproximadas, solo para simulación/desarrollo.
 */
internal object MockRouteData {

    private val congreso = GeoPoint(-34.6095, -58.3924)

    // Línea 60 — recorrido rojo, eje Av. Rivadavia / Av. Callao aprox.
    val route60Path = listOf(
        GeoPoint(-34.6035, -58.3810),
        GeoPoint(-34.6060, -58.3855),
        GeoPoint(-34.6080, -58.3895),
        congreso,
        GeoPoint(-34.6115, -58.3960),
        GeoPoint(-34.6145, -58.4005),
        GeoPoint(-34.6175, -58.4050),
    )

    // Línea 152 — recorrido azul, eje norte-sur por Av. Corrientes/Belgrano aprox.
    val route152Path = listOf(
        GeoPoint(-34.5920, -58.3720),
        GeoPoint(-34.5970, -58.3790),
        GeoPoint(-34.6020, -58.3860),
        GeoPoint(-34.6070, -58.3920),
        GeoPoint(-34.6120, -58.3980),
        GeoPoint(-34.6170, -58.4040),
    )

    // Línea 59 — recorrido verde, cruzando por el Congreso.
    val route59Path = listOf(
        GeoPoint(-34.6180, -58.3850),
        GeoPoint(-34.6140, -58.3880),
        GeoPoint(-34.6095, -58.3910),
        congreso,
        GeoPoint(-34.6060, -58.3970),
        GeoPoint(-34.6020, -58.4020),
    )

    // Línea 37 — recorrido amarillo, eje Av. Independencia aprox.
    val route37Path = listOf(
        GeoPoint(-34.6210, -58.3790),
        GeoPoint(-34.6230, -58.3860),
        GeoPoint(-34.6250, -58.3930),
        GeoPoint(-34.6270, -58.4000),
        GeoPoint(-34.6290, -58.4070),
    )

    val routes: Map<RouteId, RouteDetails> = mapOf(
        RouteId("60") to RouteDetails(
            routeId = RouteId("60"),
            shortName = "60",
            company = "MONSA",
            branches = listOf(Branch("R1", "Retiro / Constitución", route60Path)),
            colorSeed = "60-red",
            reliability = ReliabilityScore(92),
        ),
        RouteId("152") to RouteDetails(
            routeId = RouteId("152"),
            shortName = "152",
            company = "Nuevos Rumbos",
            branches = listOf(Branch("R2", "Olivos / Constitución", route152Path)),
            colorSeed = "152-blue",
            reliability = ReliabilityScore(88),
        ),
        RouteId("59") to RouteDetails(
            routeId = RouteId("59"),
            shortName = "59",
            company = "Microómnibus Norte",
            branches = listOf(Branch("R1", "Est. Bs. As. / Constitución", route59Path)),
            colorSeed = "59-green",
            reliability = ReliabilityScore(95),
        ),
        RouteId("37") to RouteDetails(
            routeId = RouteId("37"),
            shortName = "37",
            company = "Ciudad de Buenos Aires SATCI",
            branches = listOf(Branch("R1", "Villa Soldati", route37Path)),
            colorSeed = "37-yellow",
            reliability = ReliabilityScore(84),
        ),
    )
}
