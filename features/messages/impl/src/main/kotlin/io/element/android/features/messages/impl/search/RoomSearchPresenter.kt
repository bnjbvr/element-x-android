/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.search.RoomSearchIterator
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RoomSearchPresenter @Inject constructor(
    private val room: BaseRoom,
) : Presenter<RoomSearchState> {
    @Composable
    override fun present(): RoomSearchState {
        val searchQuery = remember { TextFieldState() }
        var results by remember { mutableStateOf<ImmutableList<RoomSearchResultItem>>(persistentListOf()) }
        var isSearching by remember { mutableStateOf(false) }
        var hasMoreResults by remember { mutableStateOf(false) }
        var currentIterator by remember { mutableStateOf<RoomSearchIterator?>(null) }
        val coroutineScope = rememberCoroutineScope()

        fun handleEvent(event: RoomSearchEvent) {
            when (event) {
                is RoomSearchEvent.Search -> {
                    val query = searchQuery.text.toString().trim()
                    if (query.isBlank()) return
                    coroutineScope.launch {
                        isSearching = true
                        results = persistentListOf()
                        val iterator = room.search(query)
                        currentIterator = iterator
                        val batch = iterator.nextBatch()
                        results = batch?.map { it.toResultItem() }?.toImmutableList() ?: persistentListOf()
                        hasMoreResults = batch != null && batch.isNotEmpty()
                        isSearching = false
                    }
                }
                is RoomSearchEvent.Clear -> {
                    searchQuery.clearText()
                    results = persistentListOf()
                    hasMoreResults = false
                    currentIterator = null
                }
                is RoomSearchEvent.LoadMore -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        isSearching = true
                        val batch = iterator.nextBatch()
                        if (batch != null) {
                            results = (results + batch.map { it.toResultItem() }).toImmutableList()
                            hasMoreResults = batch.isNotEmpty()
                        } else {
                            hasMoreResults = false
                        }
                        isSearching = false
                    }
                }
            }
        }

        return RoomSearchState(
            searchQuery = searchQuery,
            results = results,
            isSearching = isSearching,
            hasMoreResults = hasMoreResults,
            eventSink = ::handleEvent,
        )
    }
}

private fun RoomSearchResult.toResultItem(): RoomSearchResultItem {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return RoomSearchResultItem(
        eventId = eventId,
        senderDisplayName = senderDisplayName ?: senderId.value,
        senderAvatar = AvatarData(
            id = senderId.value,
            name = senderDisplayName,
            url = senderAvatarUrl,
            size = AvatarSize.TimelineRoom,
        ),
        contentBody = contentBody,
        formattedTimestamp = dateFormat.format(Date(timestamp)),
    )
}
