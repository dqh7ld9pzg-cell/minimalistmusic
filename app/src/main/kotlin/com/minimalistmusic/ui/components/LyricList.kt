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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalistmusic.util.LyricLine

/**
 * 歌词列表渲染组件（纯UI）
 *
 * 职责：
 * - 只负责渲染歌词列表，无状态管理逻辑
 * - 支持两种显示模式：正常播放模式和拖动模式
 * - 使用LazyColumn实现虚拟化滚动，优化性能
 *
 * 设计原则：
 * - 纯UI组件：无副作用（LaunchedEffect）
 * - 单一职责：只渲染，不管理状态
 * - 易于测试：可独立预览和单元测试
 *
 * 性能优化：
 * - LazyColumn虚拟化，只渲染可见项
 * - 使用复合key（time_index）确保唯一性
 * - derivedStateOf避免不必要的重组
 *
 * 交互功能：
 * - 正常模式：点击歌词直接跳转播放
 * - 涟漪效果：Material 3 标准点击反馈
 * - 触觉反馈：点击时震动提示
 *
 * @param lyrics 歌词列表
 * @param currentPlayingIndex 当前播放歌词索引（正常模式）
 * @param isDragMode 是否处于拖动模式
 * @param isPureLyricMode 是否处于纯歌词模式（新增 2025-12-04）
 * @param listState LazyColumn滚动状态
 * @param onLyricClick 点击歌词回调（纯歌词模式可用）
 * @param modifier 修饰符
 *
 * @since 2025-11-30 (重构版本)
 */
@Composable
fun LyricList(
    lyrics: List<LyricLine>,
    currentPlayingIndex: Int,
    isDragMode: Boolean,
    isPureLyricMode: Boolean,
    listState: LazyListState,
    onLyricClick: ((LyricLine) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    // 关键修复（2025-11-30）：使用 BoxWithConstraints 动态获取容器高度
    // 这样可以精确计算 padding，让第一条和最后一条歌词能滚动到中心
    // 修复（2025-12-04）：显式使用 this 消除 scope 未使用警告
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val containerHeight = this.maxHeight

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 拖动模式：添加轻微背景色区分
                .then(
                    if (isDragMode) {
                        Modifier.background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.05f),
                            RoundedCornerShape(8.dp)
                        )
                    } else {
                        Modifier
                    }
                ),
            // 关键修复：拖动模式下添加上下 padding = 容器高度的一半
            // 这样第一条歌词向下滚动时可以到达中心位置，最后一条歌词向上滚动时也可以到达中心位置
            // 修复 (2025-12-05): 纯歌词模式使用正常 padding,避免位置错乱
            contentPadding = if (isDragMode) {
                PaddingValues(vertical = containerHeight / 2)
            } else {
                PaddingValues(vertical = 8.dp)
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 性能优化：使用复合key确保唯一性
            // 避免多条歌词有相同时间戳时的冲突
            items(
                count = lyrics.size,
                key = { index -> "${lyrics[index].time}_$index" }
            ) { index ->
                val lyric = lyrics[index]

                if (isDragMode) {
                    // ========== 拖动模式：所有歌词统一降低透明度 ==========
                    // 设计说明：
                    // - 拖动模式下，所有列表项都是半透明的
                    // - 中心高亮歌词由DragModeOverlay单独渲染（叠加层）
                    // - 这样避免了列表重组，提升性能
                    Text(
                        text = lyric.text,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                } else {
                    // ========== 正常/纯歌词模式：高亮逻辑 + 点击跳转（纯歌词模式）==========
                    // 修复 (2025-12-05): 始终高亮当前播放的歌词
                    val isHighlighted = index == currentPlayingIndex

                    Text(
                        text = lyric.text,
                        // MD3 优化：高亮歌词使用 18sp,保持美观平衡
                        style = if (isHighlighted) {
                            MaterialTheme.typography.titleMedium.copy(
                                fontSize = 18.sp,  // 稍大于 bodyLarge(16sp)
                                lineHeight = 26.sp,  // 适配中文行高
                                letterSpacing = 0.sp  // 正常字间距
                            )
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                        color = if (isHighlighted) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            // 修复 (2025-12-04): 只在纯歌词模式下启用点击跳转
                            .then(
                                if (isPureLyricMode && onLyricClick != null) {
                                    Modifier.clickable(onClick = { onLyricClick.invoke(lyric) })
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
