/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.search

import app.cash.molecule.RecompositionMode
import app.cash.molecule.launchMolecule
import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.datasource.aRoomListRoomSummaryFactory
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.eventformatter.test.FakeRoomLatestEventFormatter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.search.GlobalSearchResult
import io.element.android.libraries.matrix.api.room.search.RoomSearchResult
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.room.search.FakeGlobalSearchIterator
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.tests.testutils.testCoroutineDispatchers
import io.element.android.tests.testutils.testWithLifecycleOwner
import io.element.android.tests.testutils.withFakeLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UnifiedSearchPresenterTest {
    @Test
    fun `present - initial state is empty`() = runTest {
        val presenter = createPresenter()
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            assertThat(initialState.roomResults).isEmpty()
            assertThat(initialState.messageResults).isEmpty()
            assertThat(initialState.isSearchingMessages).isFalse()
            assertThat(initialState.hasSearchedMessages).isFalse()
            assertThat(initialState.hasMoreMessages).isFalse()
            assertThat(initialState.searchQuery.text.toString()).isEmpty()
        }
    }

    @Test
    fun `present - room filtering updates as user types`() = runTest {
        val roomList = FakeDynamicRoomList()
        val roomListService = FakeRoomListService(
            createRoomListLambda = { roomList }
        )
        val presenter = createPresenter(roomListService = roomListService)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            assertThat(initialState.roomResults).isEmpty()

            // Emit some rooms
            roomList.summaries.emit(listOf(aRoomSummary()))
            advanceUntilIdle()
            val withRooms = expectMostRecentItem()
            assertThat(withRooms.roomResults).hasSize(1)

            // Clear rooms
            roomList.summaries.emit(emptyList())
            advanceUntilIdle()
            val emptyRooms = expectMostRecentItem()
            assertThat(emptyRooms.roomResults).isEmpty()
        }
    }

    @Test
    fun `present - search messages returns results`() = runTest {
        val searchResults = listOf(aGlobalSearchResult())
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(client = client)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(UnifiedSearchEvent.SearchMessages)
            advanceUntilIdle()
            val searchState = expectMostRecentItem()
            assertThat(searchState.messageResults).hasSize(1)
            assertThat(searchState.isSearchingMessages).isFalse()
            assertThat(searchState.hasSearchedMessages).isTrue()
        }
    }

    @Test
    fun `present - clear resets both rooms and messages`() = runTest {
        val searchResults = listOf(aGlobalSearchResult())
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(searchResults)) },
        )
        val presenter = createPresenter(client = client)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(UnifiedSearchEvent.SearchMessages)
            advanceUntilIdle()
            val withResults = expectMostRecentItem()
            assertThat(withResults.messageResults).isNotEmpty()

            withResults.eventSink(UnifiedSearchEvent.Clear)
            advanceUntilIdle()
            val clearedState = expectMostRecentItem()
            assertThat(clearedState.messageResults).isEmpty()
            assertThat(clearedState.searchQuery.text.toString()).isEmpty()
            assertThat(clearedState.hasSearchedMessages).isFalse()
        }
    }

    @Test
    fun `present - load more messages appends results`() = runTest {
        val page1 = listOf(aGlobalSearchResult(eventId = "\$event1"))
        val page2 = listOf(aGlobalSearchResult(eventId = "\$event2"))
        val client = FakeMatrixClient(
            searchResult = { FakeGlobalSearchIterator(results = listOf(page1, page2)) },
        )
        val presenter = createPresenter(client = client)
        presenter.testWithLifecycleOwner {
            val initialState = awaitItem()
            initialState.searchQuery.edit { append("hello") }
            initialState.eventSink(UnifiedSearchEvent.SearchMessages)
            advanceUntilIdle()
            val afterSearch = expectMostRecentItem()
            assertThat(afterSearch.messageResults).hasSize(1)
            assertThat(afterSearch.hasMoreMessages).isTrue()

            afterSearch.eventSink(UnifiedSearchEvent.LoadMoreMessages)
            advanceUntilIdle()
            val afterLoadMore = expectMostRecentItem()
            assertThat(afterLoadMore.messageResults).hasSize(2)
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
        val moleculeScope = CoroutineScope(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler) + moleculeJob)
        val stateFlow = moleculeScope.launchMolecule(RecompositionMode.Immediate) {
            withFakeLifecycleOwner {
                presenter.present()
            }
        }

        // Initial state
        val initialState = stateFlow.value
        assertThat(initialState.messageResults).isEmpty()
        assertThat(initialState.hasSearchedMessages).isFalse()

        // Perform search
        initialState.searchQuery.edit { append("hello") }
        initialState.eventSink(UnifiedSearchEvent.SearchMessages)
        advanceUntilIdle()

        // Verify search results are in the StateFlow
        val searchState = stateFlow.value
        assertThat(searchState.messageResults).hasSize(1)
        assertThat(searchState.searchQuery.text.toString()).isEqualTo("hello")
        assertThat(searchState.hasSearchedMessages).isTrue()

        // Simulate navigating away and back: the StateFlow retains its value
        advanceUntilIdle()
        val preservedState = stateFlow.value
        assertThat(preservedState.messageResults).hasSize(1)
        assertThat(preservedState.searchQuery.text.toString()).isEqualTo("hello")
        assertThat(preservedState.hasSearchedMessages).isTrue()

        moleculeJob.cancel()
    }

    private fun TestScope.createPresenter(
        client: FakeMatrixClient = FakeMatrixClient(),
        roomListService: RoomListService = FakeRoomListService(),
    ): UnifiedSearchPresenter {
        return UnifiedSearchPresenter(
            client = client,
            dataSourceFactory = object : RoomListSearchDataSource.Factory {
                override fun create(coroutineScope: CoroutineScope): RoomListSearchDataSource {
                    return RoomListSearchDataSource(
                        roomListService = roomListService,
                        roomSummaryFactory = aRoomListRoomSummaryFactory(
                            dateFormatter = FakeDateFormatter(),
                            roomLatestEventFormatter = FakeRoomLatestEventFormatter(),
                        ),
                        coroutineDispatchers = testCoroutineDispatchers(),
                        coroutineScope = coroutineScope,
                    )
                }
            },
        )
    }
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
