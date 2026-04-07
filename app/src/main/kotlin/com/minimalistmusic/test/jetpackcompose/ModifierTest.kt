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

package com.minimalistmusic.test.jetpackcompose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
@OptIn(ExperimentalMaterial3Api::class)
val topBar: @Composable () -> Unit = {
    TopAppBar(
        title = { Text("标题") },  // 包含 Composable
        actions = {
            IconButton(onClick = {}) {
                Icon(Icons.Default.Search, "搜索")
            }
        })
}
@Preview
@Composable
fun OrderMatters() {
    Column {
        topBar.invoke()
        // 顺序1：先边距，后背景
        Box(
            modifier = Modifier
                .padding(20.dp)
                .background(Color.Red)
                .size(100.dp)
        )
        // 顺序2：1顺序反过来
        Box(
            modifier = Modifier
                .background(Color.Red)
                .size(100.dp)
                .padding(20.dp)
        )
        // 顺序3：1基础上，先背景，后边距
        Box(
            modifier = Modifier
                .background(Color.Green)
                .padding(20.dp)
                .background(Color.Cyan)
                .size(100.dp)
        )
    }
}