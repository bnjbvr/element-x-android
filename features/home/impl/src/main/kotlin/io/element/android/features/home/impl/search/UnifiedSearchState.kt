/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import androidx.compose.foundation.text.input.TextFieldState
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.architecture.AsyncData
import kotlinx.collections.immutable.ImmutableList

data class UnifiedSearchState(
    val searchQuery: TextFieldState,
    val roomResults: ImmutableList<RoomListRoomSummary>,
    val messageResults: AsyncData<ImmutableList<GlobalSearchResultItem>>,
    val hasMoreMessages: Boolean,
    val eventSink: (UnifiedSearchEvent) -> Unit,
)
