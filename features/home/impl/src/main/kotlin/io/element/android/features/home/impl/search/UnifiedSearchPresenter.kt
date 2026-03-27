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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.search.GlobalSearchIterator
import io.element.android.libraries.matrix.api.room.search.GlobalSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.AudioMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.FileMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.ImageMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.LegacyCallInviteContent
import io.element.android.libraries.matrix.api.timeline.item.event.LiveLocationContent
import io.element.android.libraries.matrix.api.timeline.item.event.LocationMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.PollContent
import io.element.android.libraries.matrix.api.timeline.item.event.RedactedContent
import io.element.android.libraries.matrix.api.timeline.item.event.StickerContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnknownContent
import io.element.android.libraries.matrix.api.timeline.item.event.VideoMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.VoiceMessageType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber

private const val MESSAGE_SEARCH_DEBOUNCE_MS = 750L

@Inject
class UnifiedSearchPresenter(
    private val client: MatrixClient,
    private val dataSourceFactory: RoomListSearchDataSource.Factory,
    private val dateFormatter: DateFormatter,
) : Presenter<UnifiedSearchState> {
    @OptIn(FlowPreview::class)
    @Composable
    override fun present(): UnifiedSearchState {
        val searchQuery = remember { TextFieldState() }
        val hasMoreMessages = remember { mutableStateOf(false) }
        var currentIterator by remember { mutableStateOf<GlobalSearchIterator?>(null) }
        val roomInfoCache = remember { mutableStateMapOf<String, RoomInfo>() }
        val coroutineScope = rememberCoroutineScope()

        val searchMessagesResults = remember { mutableStateOf<AsyncData<ImmutableList<GlobalSearchResultItem>>>(AsyncData.Uninitialized) }

        // Room filtering — instant, per-keystroke
        val roomDataSource = remember { dataSourceFactory.create(coroutineScope) }
        LaunchedEffect(searchQuery.text) {
            roomDataSource.setSearchQuery(searchQuery.text.toString())
        }
        val roomResults by roomDataSource.roomSummaries.collectAsState(initial = persistentListOf())

        LaunchedEffect(Unit) {
            snapshotFlow { searchQuery.text.toString().trim() }
                .distinctUntilChanged()
                .debounce { query, ->
                    if (query.isBlank()) 0L else MESSAGE_SEARCH_DEBOUNCE_MS
                }
                .collect { query ->
                    roomInfoCache.clear()
                    currentIterator = search(
                        query = query,
                        roomInfoCache = roomInfoCache,
                        searchResults = searchMessagesResults,
                        hasMoreResults = hasMoreMessages,
                    )
                }
        }

        fun handleEvent(event: UnifiedSearchEvent) {
            when (event) {
                is UnifiedSearchEvent.Clear -> {
                    searchQuery.clearText()
                    coroutineScope.launch {
                        search(
                            query = "",
                            roomInfoCache = roomInfoCache,
                            searchResults = searchMessagesResults,
                            hasMoreResults = hasMoreMessages,
                        )
                    }
                }
                is UnifiedSearchEvent.LoadMoreMessages -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        loadMore(
                            iterator = iterator,
                            roomInfoCache = roomInfoCache,
                            searchResults = searchMessagesResults,
                            hasMoreResults = hasMoreMessages,
                        )
                    }
                }
                is UnifiedSearchEvent.UpdateVisibleRange -> coroutineScope.launch {
                    roomDataSource.updateVisibleRange(visibleRange = event.range)
                }
            }
        }

        return UnifiedSearchState(
            searchQuery = searchQuery,
            roomResults = roomResults,
            messageResults = searchMessagesResults.value,
            hasMoreMessages = hasMoreMessages.value,
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
        val maxIterations = 20
        runCatchingExceptions {
            iterator = client.search(query)
            val results = mutableListOf<GlobalSearchResultItem>()
            repeat(maxIterations) {
                val batch = iterator.nextBatch()?.map { it.toResultItem(client, dateFormatter, roomInfoCache) } ?: return@repeat
                results += batch
                // Display intermediate results before loading the full set, to improve perceived performance
                searchResults.value = AsyncData.Loading(prevData = results.toImmutableList())
            }
            results
        }
            .onSuccess { results ->
                searchResults.value = AsyncData.Success(results.toImmutableList())
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

internal suspend fun GlobalSearchResult.toResultItem(
    client: MatrixClient,
    dateFormatter: DateFormatter,
    roomInfoCache: MutableMap<String, RoomInfo>,
): GlobalSearchResultItem {
    var roomInfo = roomInfoCache[roomId.value]
    if (roomInfo == null) {
        roomInfo = client.getRoomInfoFlow(roomId).firstOrNull()?.orElse(null)
        roomInfo?.let { roomInfoCache[roomId.value] = it }
    }

    val roomDisplayName = roomInfo?.name ?: roomId.value
    val roomAvatar = AvatarData(
        id = roomId.value,
        name = roomDisplayName,
        url = roomInfo?.avatarUrl,
        size = AvatarSize.SearchResultRoomAvatar,
    )

    return GlobalSearchResultItem(
        eventId = result.eventId,
        roomId = roomId,
        roomDisplayName = roomDisplayName,
        roomAvatar = roomAvatar,
        senderDisplayName = result.senderDisplayName ?: result.senderId.value,
        senderAvatar = AvatarData(
            id = result.senderId.value,
            name = result.senderDisplayName,
            url = result.senderAvatarUrl,
            size = AvatarSize.TimelineRoom,
        ),
        contentDescription = result.content.toSearchDescription(),
        formattedTimestamp = dateFormatter.format(result.timestamp, DateFormatterMode.TimeOrDate, useRelative = true)
    )
}

internal fun EventContent.toSearchDescription(): String = when (this) {
    is MessageContent -> when (val msgType = type) {
        is ImageMessageType -> msgType.caption ?: "📷 Photo"
        is VideoMessageType -> msgType.caption ?: "🎥 Video"
        is FileMessageType -> "📎 ${msgType.filename}"
        is AudioMessageType -> "🎵 Audio"
        is VoiceMessageType -> "🎤 Voice message"
        is LocationMessageType -> "📍 Location"
        else -> body
    }
    is RedactedContent -> "Message deleted"
    is StickerContent -> bestDescription
    is PollContent -> "📊 $question"
    is UnableToDecryptContent -> "Unable to decrypt"
    is LiveLocationContent -> "📍 Location"
    is LegacyCallInviteContent -> "Call invite"
    is UnknownContent -> "Message"
    else -> "Message"
}
