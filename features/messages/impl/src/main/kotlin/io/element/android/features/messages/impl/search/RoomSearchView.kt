/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomSearchView(
    state: RoomSearchState,
    onBackClick: () -> Unit,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(CommonStrings.screen_room_search_title)) },
                navigationIcon = { BackButton(onClick = onBackClick) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SearchInputRow(
                searchQuery = state.searchQuery,
                onSearchClick = { state.eventSink(RoomSearchEvent.Search) },
            )
            SearchResultsList(
                results = state.results,
                isSearching = state.isSearching,
                hasMoreResults = state.hasMoreResults,
                hasSearched = state.hasSearched,
                onLoadMore = { state.eventSink(RoomSearchEvent.LoadMore) },
                onResultClick = onResultClick,
            )
        }
    }
}

@Composable
private fun SearchInputRow(
    searchQuery: TextFieldState,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            state = searchQuery,
            modifier = Modifier.weight(1f),
            textStyle = ElementTheme.typography.fontBodyLgRegular.copy(
                color = ElementTheme.colors.textPrimary,
            ),
            lineLimits = TextFieldLineLimits.SingleLine,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            onKeyboardAction = { onSearchClick() },
            cursorBrush = SolidColor(ElementTheme.colors.textActionAccent),
            decorator = { innerTextField ->
                Surface(
                    shape = RoundedCornerShape(99.dp),
                    border = BorderStroke(1.dp, ElementTheme.colors.borderInteractiveSecondary),
                    color = ElementTheme.colors.bgSubtleSecondary,
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        if (searchQuery.text.isEmpty()) {
                            Text(
                                text = stringResource(CommonStrings.screen_room_search_placeholder),
                                color = ElementTheme.colors.textSecondary,
                                style = ElementTheme.typography.fontBodyLgRegular,
                            )
                        }
                        innerTextField()
                    }
                }
            },
        )
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = CompoundIcons.Search(),
                contentDescription = stringResource(CommonStrings.action_search),
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<RoomSearchResultItem>,
    isSearching: Boolean,
    hasMoreResults: Boolean,
    hasSearched: Boolean,
    onLoadMore: () -> Unit,
    onResultClick: (EventId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lazyListState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
            hasMoreResults && !isSearching && lastVisibleItem != null &&
                lastVisibleItem.index >= lazyListState.layoutInfo.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            items = results,
            key = { it.eventId.value },
        ) { item ->
            SearchResultRow(item = item, onClick = { onResultClick(item.eventId) })
            HorizontalDivider()
        }
        if (isSearching) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
        if (!isSearching && results.isEmpty() && hasSearched) {
            item {
                Text(
                    text = stringResource(CommonStrings.screen_room_search_no_results),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    item: RoomSearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(avatarData = item.senderAvatar, avatarType = AvatarType.User)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.senderDisplayName,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.formattedTimestamp,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.contentDescription,
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun RoomSearchViewPreview(
    @PreviewParameter(RoomSearchStateProvider::class) state: RoomSearchState,
) = ElementPreview {
    RoomSearchView(
        state = state,
        onBackClick = {},
        onResultClick = {},
    )
}
