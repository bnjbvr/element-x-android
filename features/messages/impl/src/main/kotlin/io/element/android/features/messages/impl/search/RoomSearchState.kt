/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import io.element.android.libraries.architecture.AsyncData
import kotlinx.collections.immutable.ImmutableList

data class RoomSearchState(
    val searchQuery: TextFieldState,
    val hasMoreResults: Boolean,
    val searchResults: AsyncData<ImmutableList<RoomSearchResultItem>>,
    val eventSink: (RoomSearchEvent) -> Unit,
)
