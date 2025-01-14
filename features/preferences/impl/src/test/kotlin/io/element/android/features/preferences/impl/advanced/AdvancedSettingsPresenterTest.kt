/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.element.android.features.preferences.impl.advanced

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.featureflag.test.InMemoryPreferencesStore
import io.element.android.tests.testutils.WarmUpRule
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class AdvancedSettingsPresenterTest {

    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state`() = runTest {
        val store = InMemoryPreferencesStore()
        val presenter = AdvancedSettingsPresenter(store)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.isDeveloperModeEnabled).isFalse()
            assertThat(initialState.isRichTextEditorEnabled).isFalse()
        }
    }

    @Test
    fun `present - developer mode on off`() = runTest {
        val store = InMemoryPreferencesStore()
        val presenter = AdvancedSettingsPresenter(store)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.isDeveloperModeEnabled).isFalse()
            initialState.eventSink.invoke(AdvancedSettingsEvents.SetDeveloperModeEnabled(true))
            assertThat(awaitItem().isDeveloperModeEnabled).isTrue()
            initialState.eventSink.invoke(AdvancedSettingsEvents.SetDeveloperModeEnabled(false))
            assertThat(awaitItem().isDeveloperModeEnabled).isFalse()
        }
    }

    @Test
    fun `present - rich text editor on off`() = runTest {
        val store = InMemoryPreferencesStore()
        val presenter = AdvancedSettingsPresenter(store)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            val initialState = awaitItem()
            assertThat(initialState.isRichTextEditorEnabled).isFalse()
            initialState.eventSink.invoke(AdvancedSettingsEvents.SetRichTextEditorEnabled(true))
            assertThat(awaitItem().isRichTextEditorEnabled).isTrue()
            initialState.eventSink.invoke(AdvancedSettingsEvents.SetRichTextEditorEnabled(false))
            assertThat(awaitItem().isRichTextEditorEnabled).isFalse()
        }
    }
}
