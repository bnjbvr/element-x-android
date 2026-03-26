/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.test.room.search

import io.element.android.libraries.matrix.api.room.search.RoomSearchIterator
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult

class FakeRoomSearchIterator(
    private val results: List<List<RoomSearchResult>> = emptyList(),
) : RoomSearchIterator {
    private var currentPage = 0

    override suspend fun nextBatch(): List<RoomSearchResult>? {
        if (currentPage >= results.size) return null
        return results[currentPage++]
    }
}
