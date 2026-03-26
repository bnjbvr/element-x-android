/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class RoomSearchStateProvider : PreviewParameterProvider<RoomSearchState> {
    override val values: Sequence<RoomSearchState> = sequenceOf(
        anEmptyRoomSearchState(),
        aSearchingRoomSearchState(),
        aRoomSearchStateWithResults(),
        aRoomSearchStateNoResults(),
    )
}

fun anEmptyRoomSearchState() = RoomSearchState(
    searchQuery = TextFieldState(),
    results = persistentListOf(),
    isSearching = false,
    hasMoreResults = false,
    hasSearched = false,
    eventSink = {},
)

fun aSearchingRoomSearchState() = RoomSearchState(
    searchQuery = TextFieldState("hello"),
    results = persistentListOf(),
    isSearching = true,
    hasMoreResults = false,
    hasSearched = false,
    eventSink = {},
)

fun aRoomSearchStateWithResults() = RoomSearchState(
    searchQuery = TextFieldState("hello"),
    results = listOf(
        RoomSearchResultItem(
            eventId = EventId("\$event1"),
            senderDisplayName = "Alice",
            senderAvatar = AvatarData(id = "@alice:matrix.org", name = "Alice", size = AvatarSize.TimelineRoom),
            contentBody = "Hello world! This is a test message.",
            formattedTimestamp = "26/03/2026 10:00",
        ),
        RoomSearchResultItem(
            eventId = EventId("\$event2"),
            senderDisplayName = "Bob",
            senderAvatar = AvatarData(id = "@bob:matrix.org", name = "Bob", size = AvatarSize.TimelineRoom),
            contentBody = "Hello there, how are you doing today?",
            formattedTimestamp = "25/03/2026 14:30",
        ),
        RoomSearchResultItem(
            eventId = EventId("\$event3"),
            senderDisplayName = "Charlie",
            senderAvatar = AvatarData(id = "@charlie:matrix.org", name = "Charlie", size = AvatarSize.TimelineRoom),
            contentBody = "Hey hello! Just wanted to say hi.",
            formattedTimestamp = "24/03/2026 09:15",
        ),
    ).toImmutableList(),
    isSearching = false,
    hasMoreResults = true,
    hasSearched = true,
    eventSink = {},
)

fun aRoomSearchStateNoResults() = RoomSearchState(
    searchQuery = TextFieldState("xyznonexistent"),
    results = persistentListOf(),
    isSearching = false,
    hasMoreResults = false,
    hasSearched = true,
    eventSink = {},
)
