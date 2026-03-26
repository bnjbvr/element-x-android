/*
 * Copyright (c) 2025 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.appyx.launchMolecule
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.EventId

@ContributesNode(RoomScope::class)
@AssistedInject
class RoomSearchNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: RoomSearchPresenter,
) : Node(buildContext, plugins = plugins) {
    interface Callback : Plugin {
        fun viewInTimeline(eventId: EventId)
    }

    private val callback: Callback = callback()
    private val stateFlow = launchMolecule { presenter.present() }

    @Composable
    override fun View(modifier: Modifier) {
        val state by stateFlow.collectAsState()
        RoomSearchView(
            state = state,
            onBackClick = ::navigateUp,
            onResultClick = { eventId -> callback.viewInTimeline(eventId) },
            modifier = modifier,
        )
    }
}
