package com.redurbana.feature.lines

import com.mapbox.geojson.Point
import com.mapbox.search.ApiType
import com.mapbox.search.ResponseInfo
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
 * Wrapper suspend sobre [SearchEngine] (geocoding real de Mapbox), mismo
 * estilo `Result<T>` que ya usa TransportDataProvider. El SDK se
 * auto-inicializa (como el Maps SDK) leyendo el mismo token público de
 * app/src/main/res/values/mapbox.xml — no requiere configuración extra acá.
 *
 * ApiType.SEARCH_BOX en vez de GEOCODING: GEOCODING dejó de devolver datos
 * de POI (comercios, lugares) en diciembre de 2024, y buscar un destino tipo
 * "Aeroparque" u "Obelisco" es exactamente ese caso de uso.
 */
@Singleton
class DestinationSearchClient @Inject constructor() {

    private val searchEngine: SearchEngine by lazy {
        SearchEngine.createSearchEngine(ApiType.SEARCH_BOX, SearchEngineSettings())
    }

    suspend fun search(query: String, proximity: Point): Result<List<SearchSuggestion>> =
        suspendCancellableCoroutine { continuation ->
            val task = searchEngine.search(
                query,
                SearchOptions(limit = 8, proximity = proximity),
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

    suspend fun resolve(suggestion: SearchSuggestion): Result<SearchResult> =
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
                            else Result.failure(NoSuchElementException("Sin resultados para esta sugerencia")),
                        )
                    }

                    override fun onSuggestions(suggestions: List<SearchSuggestion>, responseInfo: ResponseInfo) {
                        // La sugerencia era una categoría/expansión, no un lugar puntual.
                        continuation.resume(Result.failure(IllegalStateException("La sugerencia no resolvió a un lugar puntual")))
                    }

                    override fun onError(e: Exception) {
                        continuation.resume(Result.failure(e))
                    }
                },
            )
            continuation.invokeOnCancellation { task.cancel() }
        }
}
