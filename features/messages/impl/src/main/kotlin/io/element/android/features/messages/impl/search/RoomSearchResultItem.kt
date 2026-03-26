/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.EventId

/**
 * UI-friendly model for a room search result.
 */
data class RoomSearchResultItem(
    val eventId: EventId,
    val senderDisplayName: String,
    val senderAvatar: AvatarData,
    val contentBody: String,
    val formattedTimestamp: String,
)
