/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.search

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.search.RoomSearchIterator
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import org.matrix.rustcomponents.sdk.ProfileDetails
import org.matrix.rustcomponents.sdk.RoomSearchIterator as InnerRoomSearchIterator
import org.matrix.rustcomponents.sdk.RoomSearchResult as InnerRoomSearchResult
import org.matrix.rustcomponents.sdk.TimelineItemContent

class RustRoomSearchIterator(
    private val inner: InnerRoomSearchIterator,
) : RoomSearchIterator {
    override suspend fun nextBatch(): List<RoomSearchResult>? {
        return inner.nextEvents()?.map { it.toRoomSearchResult() }
    }
}

private fun InnerRoomSearchResult.toRoomSearchResult(): RoomSearchResult {
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
        contentBody = content.extractBody(),
        timestamp = timestamp.toLong(),
    )
}

private fun TimelineItemContent.extractBody(): String {
    return when (this) {
        is TimelineItemContent.MsgLike -> content.toString()
        else -> ""
    }
}
