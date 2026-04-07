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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
/**
 * 空状态视图组件
 *
 * 通用的空状态展示组件，支持点击刷新功能
 *
 * ## 功能特性
 * - 显示自定义图标、标题和副标题
 * - 支持点击刷新操作
 * - 内置防抖处理（500ms）
 * - 符合 Material Design 3 设计规范
 *
 * ## 使用场景
 * - 搜索结果为空
 * - 网络加载失败
 * - 数据列表为空
 *
 * @param modifier 修饰符
 * @param icon 显示的图标，默认为音乐列表图标
 * @param title 主标题文字
 * @param subtitle 副标题文字，默认为"点击刷新"
 * @param onRefresh 刷新回调函数，点击任意位置触发（带500ms防抖）
 *
 * @since 2025-11-24
 */
@Composable
fun EmptyStateView(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.QueueMusic,
    title: String = "暂无数据",
    subtitle: String = "点击刷新",
    onRefresh: (() -> Unit)? = null
) {
    // 防抖状态：记录上次点击时间
    var lastClickTime by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    // 点击处理函数（带防抖）
    val handleClick: () -> Unit = {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= 500) { // 500ms 防抖间隔
            lastClickTime = currentTime
            coroutineScope.launch {
                onRefresh?.invoke()
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (onRefresh != null) {
                    Modifier.clickable(
                        indication = null, // 无点击波纹效果
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        handleClick()
                    }
                } else {
                    Modifier
                }
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 图标
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        // 主标题
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 副标题（带刷新提示）
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
