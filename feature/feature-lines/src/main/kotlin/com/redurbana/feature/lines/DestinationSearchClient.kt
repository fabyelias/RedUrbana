package com.redurbana.feature.lines

import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.ReverseGeoOptions
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.result.SearchResult
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wrapper suspend sobre [SearchEngine] (mismo SDK real de Mapbox), recortado
 * a reverse geocoding: la búsqueda por texto se sacó — la única forma de
 * elegir destino ahora es tocando el mapa (ver ExploreMapScreen), así que
 * lo único que hace falta es "¿qué lugar es este punto?".
 *
 * El SDK se auto-inicializa (como el Maps SDK) leyendo el mismo token
 * público de app/src/main/res/values/mapbox.xml.
 */
@Singleton
class DestinationSearchClient @Inject constructor() {

    private val searchEngine: SearchEngine by lazy {
        SearchEngine.createSearchEngine(ApiType.SEARCH_BOX, SearchEngineSettings())
    }

    suspend fun reverseGeocode(point: Point): Result<SearchResult> =
        suspendCancellableCoroutine { continuation ->
            val task = searchEngine.search(
                ReverseGeoOptions(center = point),
                object : SearchCallback {
                    override fun onResults(results: List<SearchResult>, responseInfo: ResponseInfo) {
                        val first = results.firstOrNull()
                        continuation.resume(
                            if (first != null) Result.success(first)
                            else Result.failure(NoSuchElementException("Sin resultados para ese punto del mapa")),
                        )
                    }

                    override fun onError(e: Exception) {
                        continuation.resume(Result.failure(e))
                    }
                },
            )
            continuation.invokeOnCancellation { task.cancel() }
        }
}
