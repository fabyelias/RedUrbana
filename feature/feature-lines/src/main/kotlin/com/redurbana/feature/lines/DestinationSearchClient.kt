package com.redurbana.feature.lines

import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.ReverseGeoOptions
import com.mapbox.search.ResponseInfo
import com.mapbox.search.SearchCallback
import com.mapbox.search.SearchEngine
import com.mapbox.search.SearchEngineSettings
import com.mapbox.search.SearchOptions
import com.mapbox.search.SearchSelectionCallback
import com.mapbox.search.SearchSuggestionsCallback
import com.mapbox.search.result.SearchResult
import com.mapbox.search.result.SearchSuggestion
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Wrapper suspend sobre [SearchEngine] (mismo SDK real de Mapbox): reverse
 * geocoding (¿qué lugar es este punto que tocaste?, ver ExploreMapScreen) y
 * forward geocoding (buscar una dirección escrita, ver la barra de búsqueda
 * en la misma pantalla) — este último se había sacado en algún momento
 * ("la única forma de elegir destino es tocando el mapa") y se volvió a
 * agregar a pedido.
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

    /**
     * Primer paso de la búsqueda por texto: sugerencias SIN coordenadas
     * todavía (así funciona el SDK de Search de Mapbox — hace falta
     * [selectSuggestion] para resolver una en un punto real). [proximity]
     * prioriza resultados cerca de esa ubicación (la del usuario) en vez de
     * cualquier lugar del mundo con ese nombre.
     */
    suspend fun searchSuggestions(query: String, proximity: Point?): Result<List<SearchSuggestion>> =
        suspendCancellableCoroutine { continuation ->
            val options = SearchOptions.Builder()
                .apply { proximity?.let { proximity(it) } }
                .build()
            val task = searchEngine.search(
                query,
                options,
                object : SearchSuggestionsCallback {
                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        continuation.resume(Result.success(suggestions))
                    }

                    override fun onError(e: Exception) {
                        continuation.resume(Result.failure(e))
                    }
                },
            )
            continuation.invokeOnCancellation { task.cancel() }
        }

    /** Segundo paso: resuelve una sugerencia elegida a un [SearchResult] real, con coordenadas. */
    suspend fun selectSuggestion(suggestion: SearchSuggestion): Result<SearchResult> =
        suspendCancellableCoroutine { continuation ->
            val task = searchEngine.select(
                suggestion,
                object : SearchSelectionCallback {
                    override fun onResult(suggestion: SearchSuggestion, result: SearchResult, responseInfo: ResponseInfo) {
                        continuation.resume(Result.success(result))
                    }

                    override fun onResults(suggestion: SearchSuggestion, results: List<SearchResult>, responseInfo: ResponseInfo) {
                        val first = results.firstOrNull()
                        continuation.resume(
                            if (first != null) Result.success(first)
                            else Result.failure(NoSuchElementException("Sin resultados para esa sugerencia")),
                        )
                    }

                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        // No esperado en el flujo normal (un lugar puntual) — puede
                        // pasar para sugerencias de tipo categoría/marca, que acá no se ofrecen.
                        continuation.resume(Result.failure(NoSuchElementException("Esa sugerencia no se pudo resolver a un lugar puntual")))
                    }

                    override fun onError(e: Exception) {
                        continuation.resume(Result.failure(e))
                    }
                },
            )
            continuation.invokeOnCancellation { task.cancel() }
        }
}
