/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room.search

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Represents a single search result from a global message search across all rooms.
 *
 * @property roomId The room ID where the matching message was found.
 * @property result The search result details (event, sender, content, timestamp).
 */
data class GlobalSearchResult(
    val roomId: RoomId,
    val result: RoomSearchResult,
)
