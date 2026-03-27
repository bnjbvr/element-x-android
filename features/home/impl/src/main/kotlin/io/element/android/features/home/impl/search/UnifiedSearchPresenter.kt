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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MESSAGE_SEARCH_DEBOUNCE_MS = 100L

class UnifiedSearchPresenter @Inject constructor(
    private val client: MatrixClient,
    private val dataSourceFactory: RoomListSearchDataSource.Factory,
) : Presenter<UnifiedSearchState> {
    @OptIn(FlowPreview::class)
    @Composable
    override fun present(): UnifiedSearchState {
        val searchQuery = remember { TextFieldState() }
        var messageResults by remember { mutableStateOf<ImmutableList<GlobalSearchResultItem>>(persistentListOf()) }
        var isSearchingMessages by remember { mutableStateOf(false) }
        var hasSearchedMessages by remember { mutableStateOf(false) }
        var hasMoreMessages by remember { mutableStateOf(false) }
        var currentIterator by remember { mutableStateOf<GlobalSearchIterator?>(null) }
        val roomInfoCache = remember { mutableMapOf<String, RoomInfo?>() }
        val coroutineScope = rememberCoroutineScope()

        // Room filtering — instant, per-keystroke
        val roomDataSource = remember { dataSourceFactory.create(coroutineScope) }
        LaunchedEffect(searchQuery.text) {
            roomDataSource.setSearchQuery(searchQuery.text.toString())
        }
        val roomResults by roomDataSource.roomSummaries.collectAsState(initial = persistentListOf())

        // Message search — debounced auto-trigger
        // A counter to force immediate search when the Search button is pressed
        var immediateSearchTrigger by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            snapshotFlow { searchQuery.text.toString().trim() to immediateSearchTrigger }
                .debounce { (query, _) ->
                    if (query.isBlank()) 0L else MESSAGE_SEARCH_DEBOUNCE_MS
                }
                .distinctUntilChanged { old, new -> old.first == new.first }
                .collect { (query, _) ->
                    if (query.isBlank()) {
                        messageResults = persistentListOf()
                        hasMoreMessages = false
                        hasSearchedMessages = false
                        currentIterator = null
                        return@collect
                    }
                    isSearchingMessages = true
                    roomInfoCache.clear()
                    try {
                        val iterator = client.search(query)
                        currentIterator = iterator
                        val batch = iterator.nextBatch()
                        messageResults = batch?.map { it.toResultItem(client, roomInfoCache) }?.toImmutableList() ?: persistentListOf()
                        hasMoreMessages = batch != null && batch.isNotEmpty()
                    } catch (e: Exception) {
                        Timber.e(e, "Global message search failed")
                        messageResults = persistentListOf()
                        hasMoreMessages = false
                        currentIterator = null
                    }
                    isSearchingMessages = false
                    hasSearchedMessages = true
                }
        }

        fun handleEvent(event: UnifiedSearchEvent) {
            when (event) {
                is UnifiedSearchEvent.SearchMessages -> {
                    // Bump the trigger to force the debounced flow to re-emit immediately
                    immediateSearchTrigger++
                }
                is UnifiedSearchEvent.Clear -> {
                    searchQuery.clearText()
                    messageResults = persistentListOf()
                    hasMoreMessages = false
                    hasSearchedMessages = false
                    currentIterator = null
                    roomInfoCache.clear()
                }
                is UnifiedSearchEvent.LoadMoreMessages -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        isSearchingMessages = true
                        try {
                            val batch = iterator.nextBatch()
                            if (batch != null) {
                                messageResults = (messageResults + batch.map { it.toResultItem(client, roomInfoCache) }).toImmutableList()
                                hasMoreMessages = batch.isNotEmpty()
                            } else {
                                hasMoreMessages = false
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Global message search load more failed")
                            hasMoreMessages = false
                        }
                        isSearchingMessages = false
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
            messageResults = messageResults,
            isSearchingMessages = isSearchingMessages,
            hasSearchedMessages = hasSearchedMessages,
            hasMoreMessages = hasMoreMessages,
            eventSink = ::handleEvent,
        )
    }
}

internal suspend fun GlobalSearchResult.toResultItem(
    client: MatrixClient,
    roomInfoCache: MutableMap<String, RoomInfo?>,
): GlobalSearchResultItem {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    val roomInfo = roomInfoCache.getOrPut(roomId.value) {
        client.getRoomInfoFlow(roomId).firstOrNull()?.orElse(null)
    }

    val roomDisplayName = roomInfo?.name ?: roomId.value
    val roomAvatar = AvatarData(
        id = roomId.value,
        name = roomDisplayName,
        url = roomInfo?.avatarUrl,
        size = AvatarSize.TimelineRoom,
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
        formattedTimestamp = dateFormat.format(Date(result.timestamp)),
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
