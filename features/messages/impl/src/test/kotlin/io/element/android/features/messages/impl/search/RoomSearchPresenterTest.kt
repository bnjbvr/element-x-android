/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.search.FakeRoomSearchIterator
import io.element.android.tests.testutils.testWithLifecycleOwner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomSearchPresenterTest {
    @Test
    fun `present - initial state is empty`() = runTest {
        val presenter = createPresenter()
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            assertThat(initialState.results).isEmpty()
            assertThat(initialState.isSearching).isFalse()
            assertThat(initialState.hasMoreResults).isFalse()
            assertThat(initialState.hasSearched).isFalse()
            assertThat(initialState.searchQuery.text.toString()).isEmpty()
        }
    }

    @Test
    fun `present - search returns results`() = runTest {
        val searchResults = listOf(aRoomSearchResult())
        val room = FakeBaseRoom(
            searchResult = { FakeRoomSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(room = room)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(RoomSearchEvent.Search)
            advanceUntilIdle()
            val searchingState = expectMostRecentItem()
            assertThat(searchingState.results).hasSize(1)
            assertThat(searchingState.isSearching).isFalse()
            assertThat(searchingState.hasSearched).isTrue()
        }
    }

    @Test
    fun `present - clear resets state`() = runTest {
        val searchResults = listOf(aRoomSearchResult())
        val room = FakeBaseRoom(
            searchResult = { FakeRoomSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(room = room)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(RoomSearchEvent.Search)
            advanceUntilIdle()
            val withResults = expectMostRecentItem()
            assertThat(withResults.results).isNotEmpty()

            withResults.eventSink(RoomSearchEvent.Clear)
            val clearedState = expectMostRecentItem()
            assertThat(clearedState.results).isEmpty()
            assertThat(clearedState.searchQuery.text.toString()).isEmpty()
            assertThat(clearedState.hasSearched).isFalse()
        }
    }

    @Test
    fun `present - load more appends results`() = runTest {
        val page1 = listOf(aRoomSearchResult(eventId = "\$event1"))
        val page2 = listOf(aRoomSearchResult(eventId = "\$event2"))
        val room = FakeBaseRoom(
            searchResult = { FakeRoomSearchIterator(results = listOf(page1, page2)) },
        )
        val presenter = createPresenter(room = room)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(RoomSearchEvent.Search)
            advanceUntilIdle()
            val afterSearch = expectMostRecentItem()
            assertThat(afterSearch.results).hasSize(1)
            assertThat(afterSearch.hasMoreResults).isTrue()

            afterSearch.eventSink(RoomSearchEvent.LoadMore)
            advanceUntilIdle()
            val afterLoadMore = expectMostRecentItem()
            assertThat(afterLoadMore.results).hasSize(2)
        }
    }

    @Test
    fun `present - search with blank query does nothing`() = runTest {
        val presenter = createPresenter()
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.eventSink(RoomSearchEvent.Search)
            // No state change expected; blank query is ignored.
            ensureAllEventsConsumed()
        }
    }

    private fun createPresenter(
        room: FakeBaseRoom = FakeBaseRoom(),
    ) = RoomSearchPresenter(room = room)
}

private fun aRoomSearchResult(eventId: String = "\$event1") = RoomSearchResult(
    eventId = EventId(eventId),
    senderId = UserId("@alice:matrix.org"),
    senderDisplayName = "Alice",
    senderAvatarUrl = null,
    content = MessageContent(
        body = "Hello world",
        inReplyTo = null,
        isEdited = false,
        threadInfo = null,
        type = TextMessageType(
            body = "Hello world",
            formatted = null,
        ),
    ),
    timestamp = 1711440000000L,
)
