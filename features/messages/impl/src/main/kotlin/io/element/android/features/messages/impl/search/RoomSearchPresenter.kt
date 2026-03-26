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
import kotlinx.coroutines.launch
import timber.log.Timber
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
        var hasSearched by remember { mutableStateOf(false) }
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
                        try {
                            val iterator = room.search(query)
                            currentIterator = iterator
                            val batch = iterator.nextBatch()
                            results = batch?.map { it.toResultItem() }?.toImmutableList() ?: persistentListOf()
                            hasMoreResults = batch != null && batch.isNotEmpty()
                        } catch (e: Exception) {
                            Timber.e(e, "Room search failed")
                            results = persistentListOf()
                            hasMoreResults = false
                            currentIterator = null
                        }
                        isSearching = false
                        hasSearched = true
                    }
                }
                is RoomSearchEvent.Clear -> {
                    searchQuery.clearText()
                    results = persistentListOf()
                    hasMoreResults = false
                    hasSearched = false
                    currentIterator = null
                }
                is RoomSearchEvent.LoadMore -> {
                    val iterator = currentIterator ?: return
                    coroutineScope.launch {
                        isSearching = true
                        try {
                            val batch = iterator.nextBatch()
                            if (batch != null) {
                                results = (results + batch.map { it.toResultItem() }).toImmutableList()
                                hasMoreResults = batch.isNotEmpty()
                            } else {
                                hasMoreResults = false
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Room search load more failed")
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
            hasSearched = hasSearched,
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
        contentDescription = content.toSearchDescription(),
        formattedTimestamp = dateFormat.format(Date(timestamp)),
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
