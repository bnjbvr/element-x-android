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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.timeline.factories.event.TimelineItemContentMessageFactory
import io.element.android.features.messages.impl.utils.messagesummary.MessageSummaryFormatter
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.search.RoomSearchIterator
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber

private const val SEARCH_DEBOUNCE_MS = 750L

@Inject
class RoomSearchPresenter(
    private val room: JoinedRoom,
    private val dateFormatter: DateFormatter,
    private val timelineItemContentMessageFactory: TimelineItemContentMessageFactory,
    private val summaryFormatter: MessageSummaryFormatter,
) : Presenter<RoomSearchState> {
    @OptIn(FlowPreview::class)
    @Composable
    override fun present(): RoomSearchState {
        val searchQuery = remember { TextFieldState() }
        val hasMoreResults = remember { mutableStateOf(false) }
        var currentIterator by remember { mutableStateOf<RoomSearchIterator?>(null) }
        val coroutineScope = rememberCoroutineScope()

        val searchResults = remember { mutableStateOf<AsyncData<ImmutableList<RoomSearchResultItem>>>(AsyncData.Uninitialized) }

        // Debounced auto-search
        LaunchedEffect(Unit) {
            snapshotFlow { searchQuery.text.toString().trim() }
                .distinctUntilChanged()
                .debounce { query ->
                    if (query.isBlank()) 0L else SEARCH_DEBOUNCE_MS
                }
                .collect { query ->
                    currentIterator = search(query, searchResults, hasMoreResults)
                }
        }

        fun handleEvent(event: RoomSearchEvent) {
            when (event) {
                is RoomSearchEvent.Clear -> {
                    searchQuery.clearText()
                    searchResults.value = AsyncData.Uninitialized
                }
                is RoomSearchEvent.LoadMore -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        loadMore(iterator, searchResults, hasMoreResults)
                    }
                }
            }
        }

        return RoomSearchState(
            searchQuery = searchQuery,
            hasMoreResults = hasMoreResults.value,
            searchResults = searchResults.value,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun search(
        query: String,
        searchResults: MutableState<AsyncData<ImmutableList<RoomSearchResultItem>>>,
        hasMoreResults: MutableState<Boolean>,
    ): RoomSearchIterator? {
        if (query.isBlank()) {
            searchResults.value = AsyncData.Uninitialized
            return null
        }

        var iterator: RoomSearchIterator? = null
        runCatchingExceptions {
            searchResults.value = AsyncData.Loading()
            iterator = room.search(query)
            iterator.nextBatch().orEmpty()
        }.onSuccess { results ->
            searchResults.value = AsyncData.Success(results.mapNotNull { it.toResultItem() }.toImmutableList())
            hasMoreResults.value = results.isNotEmpty()
        }.onFailure { e ->
            Timber.e(e, "Room search failed")
            searchResults.value = AsyncData.Failure(e)
        }

        return iterator
    }

    private suspend fun loadMore(
        iterator: RoomSearchIterator,
        searchResults: MutableState<AsyncData<ImmutableList<RoomSearchResultItem>>>,
        hasMoreResults: MutableState<Boolean>,
    ) {
        runCatchingExceptions {
            searchResults.value = AsyncData.Loading(searchResults.value.dataOrNull())
            iterator.nextBatch().orEmpty()
        }.onSuccess { results ->
            val currentResults = searchResults.value.dataOrNull().orEmpty()
            searchResults.value = AsyncData.Success((currentResults + results.mapNotNull { it.toResultItem() }).toImmutableList())
            hasMoreResults.value = results.isNotEmpty()
        }.onFailure { e ->
            Timber.e(e, "Room search load more failed")
            searchResults.value = AsyncData.Failure(e, searchResults.value.dataOrNull())
        }
    }

    private fun RoomSearchResult.toResultItem(): RoomSearchResultItem? {
        val messageContent = content as? MessageContent ?: return null
        val timelineItemContent = timelineItemContentMessageFactory.create(
            content = messageContent,
            senderId = senderId,
            senderProfile = ProfileDetails.Ready(
                displayName = senderDisplayName,
                displayNameAmbiguous = false,
                avatarUrl = senderAvatarUrl,
            ),
            eventId = eventId,
        )
        val summary = summaryFormatter.format(timelineItemContent)
        return RoomSearchResultItem(
            eventId = eventId,
            senderDisplayName = senderDisplayName ?: senderId.value,
            senderAvatar = AvatarData(
                id = senderId.value,
                name = senderDisplayName,
                url = senderAvatarUrl,
                size = AvatarSize.TimelineRoom,
            ),
            contentDescription = summary,
            formattedTimestamp = dateFormatter.format(timestamp, DateFormatterMode.TimeOrDate, useRelative = true),
        )
    }
}
