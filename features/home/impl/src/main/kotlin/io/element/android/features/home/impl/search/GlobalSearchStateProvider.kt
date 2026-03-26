/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

class GlobalSearchStateProvider : PreviewParameterProvider<GlobalSearchState> {
    override val values: Sequence<GlobalSearchState> = sequenceOf(
        anEmptyGlobalSearchState(),
        aSearchingGlobalSearchState(),
        aGlobalSearchStateWithResults(),
        aGlobalSearchStateNoResults(),
    )
}

fun anEmptyGlobalSearchState() = GlobalSearchState(
    searchQuery = TextFieldState(),
    results = persistentListOf(),
    isSearching = false,
    hasMoreResults = false,
    hasSearched = false,
    eventSink = {},
)

fun aSearchingGlobalSearchState() = GlobalSearchState(
    searchQuery = TextFieldState("hello"),
    results = persistentListOf(),
    isSearching = true,
    hasMoreResults = false,
    hasSearched = false,
    eventSink = {},
)

fun aGlobalSearchStateWithResults() = GlobalSearchState(
    searchQuery = TextFieldState("hello"),
    results = listOf(
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
        GlobalSearchResultItem(
            eventId = EventId("\$event3"),
            roomId = RoomId("!room1:matrix.org"),
            roomDisplayName = "General Chat",
            roomAvatar = AvatarData(id = "!room1:matrix.org", name = "General Chat", size = AvatarSize.TimelineRoom),
            senderDisplayName = "Charlie",
            senderAvatar = AvatarData(id = "@charlie:matrix.org", name = "Charlie", size = AvatarSize.TimelineRoom),
            contentDescription = "📎 report.pdf",
            formattedTimestamp = "24/03/2026 09:15",
        ),
    ).toImmutableList(),
    isSearching = false,
    hasMoreResults = true,
    hasSearched = true,
    eventSink = {},
)

fun aGlobalSearchStateNoResults() = GlobalSearchState(
    searchQuery = TextFieldState("xyznonexistent"),
    results = persistentListOf(),
    isSearching = false,
    hasMoreResults = false,
    hasSearched = true,
    eventSink = {},
)
