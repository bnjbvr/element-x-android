/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room.search

import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent

/**
 * Represents a single search result from a room message search.
 *
 * @property eventId The event ID of the matching message.
 * @property senderId The user ID of the message sender.
 * @property senderDisplayName The display name of the sender, if available.
 * @property senderAvatarUrl The avatar URL of the sender, if available.
 * @property content The event content of the matching message.
 * @property timestamp The timestamp of the event in milliseconds since Unix epoch.
 */
data class RoomSearchResult(
    val eventId: EventId,
    val senderId: UserId,
    val senderDisplayName: String?,
    val senderAvatarUrl: String?,
    val content: EventContent,
    val timestamp: Long,
)
