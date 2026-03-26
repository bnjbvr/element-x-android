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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GlobalSearchPresenter @Inject constructor(
    private val client: MatrixClient,
) : Presenter<GlobalSearchState> {
    @Composable
    override fun present(): GlobalSearchState {
        val searchQuery = remember { TextFieldState() }
        var results by remember { mutableStateOf<ImmutableList<GlobalSearchResultItem>>(persistentListOf()) }
        var isSearching by remember { mutableStateOf(false) }
        var hasMoreResults by remember { mutableStateOf(false) }
        var hasSearched by remember { mutableStateOf(false) }
        var currentIterator by remember { mutableStateOf<GlobalSearchIterator?>(null) }
        val coroutineScope = rememberCoroutineScope()

        // Cache room info to avoid repeated lookups
        val roomInfoCache = remember { mutableMapOf<String, RoomInfo?>() }

        fun handleEvent(event: GlobalSearchEvent) {
            when (event) {
                is GlobalSearchEvent.Search -> {
                    val query = searchQuery.text.toString().trim()
                    if (query.isBlank()) return
                    coroutineScope.launch {
                        isSearching = true
                        results = persistentListOf()
                        roomInfoCache.clear()
                        val iterator = client.search(query)
                        currentIterator = iterator
                        val batch = iterator.nextBatch()
                        results = batch?.map { it.toResultItem(client, roomInfoCache) }?.toImmutableList() ?: persistentListOf()
                        hasMoreResults = batch != null && batch.isNotEmpty()
                        isSearching = false
                        hasSearched = true
                    }
                }
                is GlobalSearchEvent.Clear -> {
                    searchQuery.clearText()
                    results = persistentListOf()
                    hasMoreResults = false
                    hasSearched = false
                    currentIterator = null
                    roomInfoCache.clear()
                }
                is GlobalSearchEvent.LoadMore -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        isSearching = true
                        val batch = iterator.nextBatch()
                        if (batch != null) {
                            results = (results + batch.map { it.toResultItem(client, roomInfoCache) }).toImmutableList()
                            hasMoreResults = batch.isNotEmpty()
                        } else {
                            hasMoreResults = false
                        }
                        isSearching = false
                    }
                }
            }
        }

        return GlobalSearchState(
            searchQuery = searchQuery,
            results = results,
            isSearching = isSearching,
            hasMoreResults = hasMoreResults,
            hasSearched = hasSearched,
            eventSink = ::handleEvent,
        )
    }
}

private suspend fun GlobalSearchResult.toResultItem(
    client: MatrixClient,
    roomInfoCache: MutableMap<String, RoomInfo?>,
): GlobalSearchResultItem {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    val roomInfo = roomInfoCache.getOrPut(roomId.value) {
        client.getRoomInfoFlow(roomId).firstOrNull()?.getOrNull()
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

private fun EventContent.toSearchDescription(): String = when (this) {
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
