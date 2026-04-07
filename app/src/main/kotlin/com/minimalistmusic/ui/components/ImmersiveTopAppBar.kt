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

package com.minimalistmusic.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
/**
 * 沉浸式TopAppBar组件 (2025-11-21)
 *
 * 特性：
 * - 透明背景,与状态栏融为一体
 * - 移除默认windowInsets,手动控制状态栏padding
 * - 标题紧凑,最大化内容区域
 * - 符合Material Design 3规范
 * - 参考网易云音乐、QQ音乐的沉浸式设计
 *
 * 使用方法：
 * ```kotlin
 * Scaffold(
 *     topBar = {
 *         ImmersiveTopAppBar(
 *             title = { Text("标题") },
 *             navigationIcon = { IconButton(...) { ... } },
 *             actions = { IconButton(...) { ... } }
 *         )
 *     }
 * ) { paddingValues ->
 *     // 内容
 * }
 * ```
 *
 * @param title 标题内容
 * @param modifier 修饰符
 * @param navigationIcon 导航图标(返回按钮等)
 * @param actions 操作按钮(搜索、设置等)
 * @param scrollBehavior 滚动行为(可选)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersiveTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    // 外层Column包裹
    Column(modifier = modifier) {
        // 状态栏高度的Spacer设为0，由Scaffold或外部容器处理状态栏padding
        Spacer(modifier = Modifier.height(0.dp))
        // TopAppBar本体
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                // 透明背景,与状态栏融为一体
                containerColor = Color.Transparent,
                // 滚动时半透明背景,保持内容可读性
                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
            ),
            // 移除默认的windowInsets,高度由外层Column的Spacer控制
            windowInsets = WindowInsets(0, 0, 0, 0),
            scrollBehavior = scrollBehavior
        )
        // 添加细微分隔线,提供视觉层次,符合MD3设计规范
        // 使用极细的thickness和低透明度,既有边界感又不破坏沉浸式美感
        Divider(
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}
