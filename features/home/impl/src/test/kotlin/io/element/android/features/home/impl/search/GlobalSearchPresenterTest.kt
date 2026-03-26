/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import com.google.common.truth.Truth.assertThat
import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.search.GlobalSearchResult
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.search.FakeGlobalSearchIterator
import io.element.android.tests.testutils.testWithLifecycleOwner
import io.element.android.tests.testutils.withFakeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlobalSearchPresenterTest {
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
        val searchResults = listOf(aGlobalSearchResult())
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(client = client)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(GlobalSearchEvent.Search)
            advanceUntilIdle()
            val searchingState = expectMostRecentItem()
            assertThat(searchingState.results).hasSize(1)
            assertThat(searchingState.isSearching).isFalse()
            assertThat(searchingState.hasSearched).isTrue()
        }
    }

    @Test
    fun `present - clear resets state`() = runTest {
        val searchResults = listOf(aGlobalSearchResult())
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(client = client)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(GlobalSearchEvent.Search)
            advanceUntilIdle()
            val withResults = expectMostRecentItem()
            assertThat(withResults.results).isNotEmpty()

            withResults.eventSink(GlobalSearchEvent.Clear)
            val clearedState = expectMostRecentItem()
            assertThat(clearedState.results).isEmpty()
            assertThat(clearedState.searchQuery.text.toString()).isEmpty()
            assertThat(clearedState.hasSearched).isFalse()
        }
    }

    @Test
    fun `present - load more appends results`() = runTest {
        val page1 = listOf(aGlobalSearchResult(eventId = "\$event1"))
        val page2 = listOf(aGlobalSearchResult(eventId = "\$event2"))
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(page1, page2)) },
        )
        val presenter = createPresenter(client = client)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(GlobalSearchEvent.Search)
            advanceUntilIdle()
            val afterSearch = expectMostRecentItem()
            assertThat(afterSearch.results).hasSize(1)
            assertThat(afterSearch.hasMoreResults).isTrue()

            afterSearch.eventSink(GlobalSearchEvent.LoadMore)
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
            initialState.eventSink(GlobalSearchEvent.Search)
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `present - state is preserved in molecule StateFlow`() = runTest {
        val searchResults = listOf(aGlobalSearchResult())
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(client = client)
        val moleculeJob = kotlinx.coroutines.Job()
        val moleculeScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + moleculeJob)
        val stateFlow = moleculeScope.launchMolecule(RecompositionMode.Immediate) {
            withFakeLifecycleOwner {
                presenter.present()
            }
        }

        // Initial state
        val initialState = stateFlow.value
        assertThat(initialState.results).isEmpty()
        assertThat(initialState.hasSearched).isFalse()

        // Perform search
        initialState.searchQuery.edit { append("hello") }
        initialState.eventSink(GlobalSearchEvent.Search)
        advanceUntilIdle()

        // Verify search results are in the StateFlow
        val searchState = stateFlow.value
        assertThat(searchState.results).hasSize(1)
        assertThat(searchState.searchQuery.text.toString()).isEqualTo("hello")
        assertThat(searchState.hasSearched).isTrue()

        // Simulate navigating away and back: the StateFlow retains its value
        advanceUntilIdle()
        val preservedState = stateFlow.value
        assertThat(preservedState.results).hasSize(1)
        assertThat(preservedState.searchQuery.text.toString()).isEqualTo("hello")
        assertThat(preservedState.hasSearched).isTrue()

        moleculeJob.cancel()
    }

    private fun createPresenter(
        client: FakeMatrixClient = FakeMatrixClient(),
    ) = GlobalSearchPresenter(client = client)
}

private fun aGlobalSearchResult(
    eventId: String = "\$event1",
    roomId: String = "!room1:matrix.org",
) = GlobalSearchResult(
    roomId = RoomId(roomId),
    result = RoomSearchResult(
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
    ),
)
