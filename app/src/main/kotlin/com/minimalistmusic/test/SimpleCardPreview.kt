/*
 * Copyright (C) 2025 JG.Y
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.minimalistmusic.test

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.minimalistmusic.ui.screens.SimpleCard
import com.minimalistmusic.ui.theme.MinimalistMusicTheme
import java.util.concurrent.CopyOnWriteArrayList

@Preview(name = "Dark Mode")
@Preview(showBackground = true,
    device = "id:pixel_5",
    locale = "fr-rFR",
) // showBackground = true 会给预览添加一个白色背景
@Composable
fun SimpleCardPreview() {
    // 为了让预览正常工作，最好包裹在你的应用主题中
    MinimalistMusicTheme {
//        if (LocalInspectionMode.current) {
//            Text("预览模式：Hello World")
//        } else {
//            Text("生产模式：Hello World")
//        }
        SimpleCard(
            title = "本地音乐1",
            count = 124,
            icon = Icons.Filled.MusicNote,
            onClick = {} // 在预览中，点击事件可以是一个空lambda
        )
    }
}
fun testLambda(){
    val subscribers = CopyOnWriteArrayList<StateFlowSlot<String>>()
    subscribers.forEach { slot ->
    }
}
class StateFlowSlot<t: Any>{
}