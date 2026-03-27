/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.search.GlobalSearchIterator
import io.element.android.libraries.matrix.api.room.search.GlobalSearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.collections.orEmpty
import kotlin.collections.plus
import kotlin.collections.plusAssign

@Inject
class GlobalSearchPresenter(
    private val client: MatrixClient,
    private val dateFormatter: DateFormatter,
) : Presenter<GlobalSearchState> {
    @Composable
    override fun present(): GlobalSearchState {
        val searchQuery = remember { TextFieldState() }
        var results by remember { mutableStateOf<ImmutableList<GlobalSearchResultItem>>(persistentListOf()) }
        var isSearching by remember { mutableStateOf(false) }
        val hasMoreResults = remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }
        var currentIterator by remember { mutableStateOf<GlobalSearchIterator?>(null) }
        val coroutineScope = rememberCoroutineScope()

        // Cache room info to avoid repeated lookups
        val roomInfoCache = remember { mutableStateMapOf<String, RoomInfo>() }

        val searchResults = remember { mutableStateOf<AsyncData<ImmutableList<GlobalSearchResultItem>>>(AsyncData.Uninitialized) }

        fun handleEvent(event: GlobalSearchEvent) {
            when (event) {
                is GlobalSearchEvent.Clear -> {
                    searchQuery.clearText()
                    coroutineScope.launch {
                        search(
                            query = "",
                            roomInfoCache = roomInfoCache,
                            searchResults = searchResults,
                            hasMoreResults = hasMoreResults,
                        )
                    }
                }
                is GlobalSearchEvent.LoadMore -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        loadMore(
                            iterator = iterator,
                            roomInfoCache = roomInfoCache,
                            searchResults = searchResults,
                            hasMoreResults = hasMoreResults,
                        )
                    }
                }
            }
        }

        return GlobalSearchState(
            searchQuery = searchQuery,
            results = results,
            isSearching = isSearching,
            hasMoreResults = hasMoreResults.value,
            hasSearched = hasSearched,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun search(
        query: String,
        roomInfoCache: MutableMap<String, RoomInfo>,
        searchResults: MutableState<AsyncData<ImmutableList<GlobalSearchResultItem>>>,
        hasMoreResults: MutableState<Boolean>,
    ): GlobalSearchIterator? {
        roomInfoCache.clear()

        if (query.isBlank()) {
            searchResults.value = AsyncData.Uninitialized
            hasMoreResults.value = false
            return null
        }

        searchResults.value = AsyncData.Loading()

        var iterator: GlobalSearchIterator? = null
        runCatchingExceptions {
            iterator = client.search(query)
            val results = mutableListOf<GlobalSearchResult>()
            while (true) {
                val batch = iterator.nextBatch() ?: break
                results += batch
            }
            results
        }
            .onSuccess { results ->
                searchResults.value = AsyncData.Success(results.map { it.toResultItem(client, dateFormatter, roomInfoCache) }.toImmutableList())
                hasMoreResults.value = results.isNotEmpty()
            }
            .onFailure {
                Timber.e(it, "Global message search failed")
                searchResults.value = AsyncData.Failure(it)
                hasMoreResults.value = false
            }
        return iterator
    }

    private suspend fun loadMore(
        iterator: GlobalSearchIterator,
        roomInfoCache: MutableMap<String, RoomInfo>,
        searchResults: MutableState<AsyncData<ImmutableList<GlobalSearchResultItem>>>,
        hasMoreResults: MutableState<Boolean>,
    ) {
        searchResults.value = AsyncData.Loading()
        runCatchingExceptions {
            iterator.nextBatch().orEmpty()
        }
            .onSuccess { batch ->
                val currentResults = searchResults.value.dataOrNull().orEmpty()
                val newItems = batch.map { it.toResultItem(client, dateFormatter, roomInfoCache) }
                searchResults.value = AsyncData.Success((currentResults + newItems).toImmutableList())
                hasMoreResults.value = batch.isNotEmpty()
            }
            .onFailure {
                Timber.e(it, "Global message search load more failed")
                searchResults.value = AsyncData.Failure(it)
                hasMoreResults.value = false
            }
    }
}
