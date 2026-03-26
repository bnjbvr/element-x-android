/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

sealed interface UnifiedSearchEvent {
    data object SearchMessages : UnifiedSearchEvent
    data object Clear : UnifiedSearchEvent
    data object LoadMoreMessages : UnifiedSearchEvent
    data class UpdateVisibleRange(val range: IntRange) : UnifiedSearchEvent
}
