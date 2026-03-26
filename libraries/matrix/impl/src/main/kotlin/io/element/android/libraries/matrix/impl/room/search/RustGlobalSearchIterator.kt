/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.search

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.search.GlobalSearchIterator
import io.element.android.libraries.matrix.api.room.search.GlobalSearchResult
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import io.element.android.libraries.matrix.impl.timeline.item.event.TimelineEventContentMapper
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.GlobalSearchIterator as InnerGlobalSearchIterator
import org.matrix.rustcomponents.sdk.GlobalSearchResult as InnerGlobalSearchResult
import org.matrix.rustcomponents.sdk.RoomSearchResult as InnerRoomSearchResult

class RustGlobalSearchIterator(
    private val inner: InnerGlobalSearchIterator,
) : GlobalSearchIterator {
    private val contentMapper = TimelineEventContentMapper()

    override suspend fun nextBatch(): List<GlobalSearchResult>? {
        return inner.nextEvents()?.map { it.toGlobalSearchResult(contentMapper) }
    }
}

private fun InnerGlobalSearchResult.toGlobalSearchResult(contentMapper: TimelineEventContentMapper): GlobalSearchResult {
    return GlobalSearchResult(
        roomId = RoomId(roomId),
        result = result.toRoomSearchResult(contentMapper),
    )
}

private fun InnerRoomSearchResult.toRoomSearchResult(contentMapper: TimelineEventContentMapper): RoomSearchResult {
    val displayName = when (val profile = senderProfile) {
        is ProfileDetails.Ready -> profile.displayName
        else -> null
    }
    val avatarUrl = when (val profile = senderProfile) {
        is ProfileDetails.Ready -> profile.avatarUrl
        else -> null
    }
    return RoomSearchResult(
        eventId = EventId(eventId),
        senderId = UserId(sender),
        senderDisplayName = displayName,
        senderAvatarUrl = avatarUrl,
        content = contentMapper.map(content),
        timestamp = timestamp.toLong(),
    )
}
