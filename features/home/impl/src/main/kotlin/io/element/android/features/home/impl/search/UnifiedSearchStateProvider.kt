/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class UnifiedSearchStateProvider : PreviewParameterProvider<UnifiedSearchState> {
    override val values: Sequence<UnifiedSearchState> = sequenceOf(
        anEmptyUnifiedSearchState(),
        aUnifiedSearchStateWithRoomsOnly(),
        aUnifiedSearchStateWithMessagesOnly(),
        aUnifiedSearchStateWithBoth(),
        aUnifiedSearchStateSearching(),
        aUnifiedSearchStateNoResults(),
    )
}

private fun anEmptyUnifiedSearchState() = UnifiedSearchState(
    searchQuery = TextFieldState(),
    roomResults = persistentListOf(),
    messageResults = persistentListOf(),
    isSearchingMessages = false,
    hasSearchedMessages = false,
    hasMoreMessages = false,
    eventSink = {},
)

private fun aUnifiedSearchStateWithRoomsOnly() = UnifiedSearchState(
    searchQuery = TextFieldState("general"),
    roomResults = listOf(
        aRoomListRoomSummary(id = "!room1:matrix.org", name = "General Chat"),
        aRoomListRoomSummary(id = "!room2:matrix.org", name = "General Announcements"),
    ).toImmutableList(),
    messageResults = persistentListOf(),
    isSearchingMessages = false,
    hasSearchedMessages = false,
    hasMoreMessages = false,
    eventSink = {},
)

private fun aUnifiedSearchStateWithMessagesOnly() = UnifiedSearchState(
    searchQuery = TextFieldState("hello"),
    roomResults = persistentListOf(),
    messageResults = sampleMessageResults(),
    isSearchingMessages = false,
    hasSearchedMessages = true,
    hasMoreMessages = true,
    eventSink = {},
)

private fun aUnifiedSearchStateWithBoth() = UnifiedSearchState(
    searchQuery = TextFieldState("hello"),
    roomResults = listOf(
        aRoomListRoomSummary(id = "!room1:matrix.org", name = "General Chat"),
    ).toImmutableList(),
    messageResults = sampleMessageResults(),
    isSearchingMessages = false,
    hasSearchedMessages = true,
    hasMoreMessages = true,
    eventSink = {},
)

private fun aUnifiedSearchStateSearching() = UnifiedSearchState(
    searchQuery = TextFieldState("hello"),
    roomResults = persistentListOf(),
    messageResults = persistentListOf(),
    isSearchingMessages = true,
    hasSearchedMessages = false,
    hasMoreMessages = false,
    eventSink = {},
)

private fun aUnifiedSearchStateNoResults() = UnifiedSearchState(
    searchQuery = TextFieldState("xyznonexistent"),
    roomResults = persistentListOf(),
    messageResults = persistentListOf(),
    isSearchingMessages = false,
    hasSearchedMessages = true,
    hasMoreMessages = false,
    eventSink = {},
)

private fun sampleMessageResults() = listOf(
    GlobalSearchResultItem(
        eventId = EventId("\$event1"),
        roomId = RoomId("!room1:matrix.org"),
        roomDisplayName = "General Chat",
        roomAvatar = AvatarData(id = "!room1:matrix.org", name = "General Chat", size = AvatarSize.TimelineRoom),
        senderDisplayName = "Alice",
        senderAvatar = AvatarData(id = "@alice:matrix.org", name = "Alice", size = AvatarSize.TimelineRoom),
        contentDescription = "Hello world! This is a test message.",
        formattedTimestamp = "26/03/2026 10:00",
    ),
    GlobalSearchResultItem(
        eventId = EventId("\$event2"),
        roomId = RoomId("!room2:matrix.org"),
        roomDisplayName = "Design Team",
        roomAvatar = AvatarData(id = "!room2:matrix.org", name = "Design Team", size = AvatarSize.TimelineRoom),
        senderDisplayName = "Bob",
        senderAvatar = AvatarData(id = "@bob:matrix.org", name = "Bob", size = AvatarSize.TimelineRoom),
        contentDescription = "📷 Photo",
        formattedTimestamp = "25/03/2026 14:30",
    ),
).toImmutableList()
