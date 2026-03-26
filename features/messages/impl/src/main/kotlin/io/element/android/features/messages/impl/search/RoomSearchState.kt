/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import kotlinx.collections.immutable.ImmutableList

data class RoomSearchState(
    val searchQuery: TextFieldState,
    val results: ImmutableList<RoomSearchResultItem>,
    val isSearching: Boolean,
    val hasMoreResults: Boolean,
    val hasSearched: Boolean,
    val eventSink: (RoomSearchEvent) -> Unit,
)
