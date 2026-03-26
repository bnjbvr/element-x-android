/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.room.search

/**
 * An iterator that provides paginated search results for messages across all rooms.
 * Each call to [nextBatch] returns the next page of results, or null if there are no more.
 */
interface GlobalSearchIterator {
    /**
     * Returns the next batch of global search results, or null if there are no more results.
     */
    suspend fun nextBatch(): List<GlobalSearchResult>?
}
