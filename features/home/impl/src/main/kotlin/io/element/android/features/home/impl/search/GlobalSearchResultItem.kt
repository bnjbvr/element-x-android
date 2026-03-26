/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * UI-friendly model for a global search result.
 */
data class GlobalSearchResultItem(
    val eventId: EventId,
    val roomId: RoomId,
    val roomDisplayName: String,
    val roomAvatar: AvatarData,
    val senderDisplayName: String,
    val senderAvatar: AvatarData,
    val contentDescription: String,
    val formattedTimestamp: String,
)
