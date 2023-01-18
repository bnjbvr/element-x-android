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

package io.element.android.x.features.roomlist.model

import androidx.compose.runtime.Immutable
import io.element.android.x.matrix.ui.model.MatrixUser
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class RoomListState(
    val matrixUser: MatrixUser?,
    val roomList: ImmutableList<RoomListRoomSummary>,
    val filter: String,
    val isLoginOut: Boolean,
    val eventSink: (RoomListEvents) -> Unit = {}
)