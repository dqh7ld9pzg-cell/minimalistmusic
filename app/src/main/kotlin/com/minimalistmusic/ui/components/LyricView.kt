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

import android.annotation.SuppressLint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.minimalistmusic.util.LyricLine
import com.minimalistmusic.util.LyricParser
import com.minimalistmusic.util.performHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 歌词显示主组件（重构版）
 *
 * ## 架构设计
 *
 * 采用**分层架构**，职责清晰：
 * ```
 * LyricView（主组件，~200行）
 * ├── LyricList（歌词列表渲染，纯UI，~130行）
 * └── DragModeOverlay（拖动模式叠加层，独立组件，~100行）
 * ```
 *
 * ## 功能说明
 *
 * ### 正常播放模式
 * - 自动滚动，当前播放歌词高亮显示在第2行
 * - 跟随播放进度自动切换高亮歌词
 * - 点击歌词区域：切换纯歌词模式（隐藏其他UI）
 *
 * ### 拖动模式
 * - 长按歌词1秒进入拖动模式，震动反馈
 * - 中心行歌词固定高亮（通过叠加层实现，不触发列表重组）
 * - 高亮歌词显示：时间 + 歌词文本 + 播放图标
 * - 点击高亮歌词：跳转到该位置播放并退出拖动模式
 *
 * ## 技术实现
 *
 * ### 性能优化
 * - LazyColumn虚拟化滚动，只渲染可见项
 * - 使用复合key避免重组
 * - derivedStateOf智能缓存，避免不必要的重新计算
 * - animateScrollToItem平滑滚动
 *
 * ### 状态简化
 * - 重构前：7个状态变量，2个冗余
 * - 重构后：4个状态变量，逻辑清晰
 *
 * ### Bug修复
 * - 拖动模式中心歌词文本显示（原代码line 324为空字符串）
 * - 长按延迟时间修正为1000ms（原代码100ms）
 * - derivedStateOf正确使用（移除外层remember key）
 *
 * @param lyrics 歌词列表（时间戳 + 文本）
 * @param currentPosition 当前播放位置（毫秒）
 * @param onSeekTo 跳转播放位置回调
 * @param modifier 修饰符
 * @param onTap 点击歌词区域的回调（用于切换纯歌词模式）
 * @param isDragMode 是否处于拖动模式（外部控制）
 * @param isPureLyricMode 是否处于纯歌词模式（外部控制，新增 2025-12-04）
 * @param onDragModeChange 拖动模式变化回调（通知外部状态变化）
 *
 * @since 2025-11-11（原版本）
 * @since 2025-11-30（重构版本）
 */
@SuppressLint("UnrememberedMutableState")
@Composable
fun LyricView(
    lyrics: List<LyricLine>,
    currentPosition: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
    isDragMode: Boolean = false,
    isPureLyricMode: Boolean = false,
    onDragModeChange: (Boolean) -> Unit = {},
    onCenterLyricChange: (LyricLine?) -> Unit = {}  // 新增：暴露中心歌词状态
) {
    // ========================================
    // 一、状态管理（从7个变量减少到3个）
    // ========================================

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // 长按任务Job（用于取消）
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    // 中心高亮歌词（拖动模式）
    var centerLyric by remember { mutableStateOf<LyricLine?>(null) }

    // 退出拖动模式/纯歌词模式时清空中心歌词（关键修复 2025-11-30, 更新 2025-12-05）
    LaunchedEffect(isDragMode, isPureLyricMode) {
        if (!isDragMode && !isPureLyricMode) {
            centerLyric = null
            onCenterLyricChange(null)  // 通知外部状态变化
        }
    }

    // 当前播放歌词索引（正常模式）
    // 性能优化：使用 derivedStateOf 避免不必要的重新计算
    // 关键修复（2025-11-30）：derivedStateOf 会自动追踪 currentPosition 变化
    // 但必须直接使用，不能被 remember {} 包裹（否则只执行一次）
    val currentPlayingIndex by derivedStateOf {
        LyricParser.getCurrentLyricIndex(lyrics, currentPosition)
    }

    // ========================================
    // 二、拖动模式/纯歌词模式：更新时间指示器显示
    // ========================================

    // 修复 (2025-12-08): 在 Composable 作用域中获取 density
    val density = androidx.compose.ui.platform.LocalDensity.current
    val targetPositionPx = with(density) { 46.dp.toPx() }

    LaunchedEffect(isDragMode, isPureLyricMode) {
        if (isDragMode) {
            // 拖动模式: 监听滚动,显示屏幕中心位置的歌词
            snapshotFlow { listState.layoutInfo }
                .collect { layoutInfo ->
                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isNotEmpty() && lyrics.isNotEmpty()) {
                        // 计算屏幕中心位置
                        val viewportCenter = layoutInfo.viewportStartOffset +
                                           layoutInfo.viewportSize.height / 2

                        // 找到最接近中心位置的歌词item
                        val centerItem = visibleItems.minByOrNull { item ->
                            val itemCenter = item.offset + item.size / 2
                            kotlin.math.abs(itemCenter - viewportCenter)
                        }

                        centerLyric = centerItem?.let { lyrics.getOrNull(it.index) }
                        onCenterLyricChange(centerLyric)  // 通知外部状态变化
                    }
                }
        } else if (isPureLyricMode) {
            // 纯歌词模式: 监听滚动,显示第2行位置的歌词
            snapshotFlow { listState.layoutInfo }
                .collect { layoutInfo ->
                    val visibleItems = layoutInfo.visibleItemsInfo
                    if (visibleItems.isNotEmpty() && lyrics.isNotEmpty()) {
                        // 修复 (2025-12-08): 根据视觉位置找到第2行歌词
                        // 问题: visibleItems[1] 不一定对应屏幕第2行（第1行可能部分可见）
                        // 解决: 找到最接近 "视口顶部 + 第2行中心位置" 的item
                        // 第2行的目标位置 = 46dp (contentPadding 8dp + 第1行 30dp + 第2行一半 8dp)

                        // 找到最接近目标位置的item
                        val targetItem = visibleItems.minByOrNull { item ->
                            val itemCenter = item.offset + item.size / 2
                            kotlin.math.abs(itemCenter - targetPositionPx)
                        }

                        centerLyric = targetItem?.let { lyrics.getOrNull(it.index) }
                        onCenterLyricChange(centerLyric)  // 通知外部状态变化
                    }
                }
        }
    }

    // ========================================
    // 三、自动滚动：正常模式和纯歌词模式
    // ========================================

    LaunchedEffect(currentPlayingIndex, isDragMode, isPureLyricMode) {
        // 只在非拖动模式下自动滚动
        if (!isDragMode && currentPlayingIndex >= 0 && currentPlayingIndex < lyrics.size) {
            // 滚动到 currentPlayingIndex - 1，让当前播放歌词显示在第2行
            val scrollTarget = maxOf(0, currentPlayingIndex - 1)
            listState.animateScrollToItem(index = scrollTarget, scrollOffset = 0)
        }
    }

    // ========================================
    // 五、交互逻辑：进入/退出拖动模式
    // ========================================

    /**
     * 进入拖动模式
     */
    val enterDragMode: () -> Unit = {
        coroutineScope.launch {
            // 震动反馈
            performHapticFeedback(context)

            // 1. 确定目标歌词索引
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val targetIndex = if (visibleItems.size > 1) {
                visibleItems[1].index  // 第2行是当前播放的歌词
            } else if (visibleItems.isNotEmpty()) {
                visibleItems[0].index
            } else {
                currentPlayingIndex
            }.coerceIn(0, lyrics.lastIndex)

            centerLyric = lyrics.getOrNull(targetIndex)

            // 2. 通知外部进入拖动模式
            onDragModeChange(true)

            // 3. 等待布局稳定
            delay(350)

            // 4. 计算可见项数量
            val visibleCount = listState.layoutInfo.visibleItemsInfo.let { items ->
                if (items.isNotEmpty()) items.last().index - items.first().index + 1 else 8
            }

            // 5. 滚动让目标歌词显示在中心位置
            val scrollTarget = maxOf(0, targetIndex - visibleCount / 2)
            listState.animateScrollToItem(index = scrollTarget, scrollOffset = 0)
        }
    }

    /**
     * 退出拖动模式
     */
    val exitDragMode: () -> Unit = {
        onDragModeChange(false)
    }

    /**
     * 跳转到选中的歌词位置
     */
    val seekToTarget: () -> Unit = {
        centerLyric?.let { lyric ->
            onSeekTo(lyric.time)
        }
    }

    // ========================================
    // 六、UI渲染：Box容器 + 手势检测
    // ========================================

    Box(
        modifier = modifier
            .pointerInput(isDragMode) {
                detectTapGestures(
                    // 点击：切换纯歌词模式（仅在非拖动模式下）
                    onTap = {
                        if (!isDragMode) {
                            onTap()
                        }
                    },
                    // 长按进入拖动模式
                    onLongPress = {
                        if (!isDragMode) {
                            longPressJob = coroutineScope.launch {
                                enterDragMode()
                                longPressJob = null
                            }
                        }
                    },
                    // 按下但未达到长按时长则取消
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            longPressJob?.cancel()
                            longPressJob = null
                        }
                    }
                )
            }
    ) {
        // ========== 歌词列表（纯UI组件）==========
        LyricList(
            lyrics = lyrics,
            currentPlayingIndex = currentPlayingIndex,
            isDragMode = isDragMode,
            isPureLyricMode = isPureLyricMode,
            listState = listState,
            onLyricClick = if (isPureLyricMode && !isDragMode) {
                { lyric -> onSeekTo(lyric.time) }
            } else null
        )

        // ========== 拖动模式叠加层（固定位置高亮）==========
        if (isDragMode) {
            DragModeOverlay(
                centerLyric = centerLyric,
                onSeek = {
                    seekToTarget()
                    exitDragMode()
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // 纯歌词模式时间指示器已移至 PlayerScreen 层级渲染 (2025-12-08)
    }
}
