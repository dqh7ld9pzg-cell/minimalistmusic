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

/**
 * 播放控制组件
 *
 * 播放器核心组件之一，提供完整的播放控制功能。
 *
 * ## 核心功能
 *
 * ### 1. 进度控制
 * - 进度条显示（Slider）
 * - 当前时间/总时长显示（MM:SS格式）
 * - 拖拽跳转播放位置
 *
 * ### 2. 播放控制按钮
 * - 播放模式切换（顺序/随机/单曲循环）
 * - 上一曲按钮
 * - 播放/暂停按钮（大号，居中）
 * - 下一曲按钮
 * - 播放列表按钮
 *
 * ### 3. 播放模式
 * - **顺序播放**：按列表顺序播放
 * - **随机播放**：随机选择下一首
 * - **单曲循环**：重复播放当前歌曲
 *
 * ## UI布局
 * ```
 * 进度条
 * [00:00] ━━━━━━━●━━━━ [03:45]
 *
 * 控制按钮
 * [模式] [上一曲] [播放/暂停] [下一曲] [列表]
 * ```
 *
 * ## 组件参数
 * - [progress]: 播放进度（0.0-1.0）
 * - [currentPosition]: 当前位置（毫秒）
 * - [duration]: 总时长（毫秒）
 * - [isPlaying]: 播放状态
 * - [playMode]: 播放模式
 * - [onProgressChange]: 进度变化回调
 * - [onPlayModeToggle]: 播放模式切换回调
 * - [onSkipToPrevious]: 上一曲回调
 * - [onTogglePlayPause]: 播放/暂停回调
 * - [onSkipToNext]: 下一曲回调
 * - [onPlaylistClick]: 播放列表回调
 *
 * ## 性能优化
 * - 使用方法引用（::）避免lambda开销
 * - 最小化重组范围
 *
 * @since 2025-11-11
 */
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimalistmusic.domain.model.PlayMode
import com.minimalistmusic.util.formatDuration
/**
 * 播放器控制区域组件
 *
 * 包含：
 * 1. 进度条
 * 2. 时间显示
 * 3. 播放控制按钮（播放模式/上一曲/播放暂停/下一曲/播放列表）
 *
 * @param progress 播放进度 (0.0 - 1.0)
 * @param currentPosition 当前播放位置（毫秒）
 * @param duration 歌曲总时长（毫秒）
 * @param isPlaying 是否正在播放
 * @param playMode 当前播放模式
 * @param onProgressChange 进度变化回调（仅在松手时调用，避免频繁seekTo）
 * @param onPlayModeToggle 播放模式切换回调
 * @param onSkipToPrevious 上一曲回调
 * @param onTogglePlayPause 播放/暂停回调
 * @param onSkipToNext 下一曲回调
 * @param onPlaylistClick 播放列表按钮回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    progress: Float,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    playMode: PlayMode,
    onProgressChange: (Float) -> Unit,
    onPlayModeToggle: () -> Unit,
    onSkipToPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipToNext: () -> Unit,
    onPlaylistClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDraggingPositionChange: ((Long) -> Unit)? = null,  // 拖动时实时位置回调
    cacheProgress: com.minimalistmusic.domain.model.CacheProgress? = null,  // 缓存进度（可选）
    cacheEnabled: Boolean = false  // 智能缓存总开关
) {
    // region 进度条状态管理
    /**
     *  修复 (2025-11-18): 使用本地状态跟踪拖动进度，只在松手时才真正seekTo
     *  这样可以避免拖动过程中频繁调用seekTo导致"文件不存在"错误
     */
    var isDragging by remember { mutableStateOf(false) }
    /**
     *  修复 (2025-11-29): 使用单一的 localProgress 状态,避免闪烁
     *  策略:
     *    1. 非拖动时: localProgress 跟随 progress 自动更新
     *    2. 拖动时: localProgress 由用户手势控制
     *    3. 松手时: 先更新 localProgress, 再调用 seekTo, 避免闪烁
     */
    var localProgress by remember { mutableFloatStateOf(progress) }
    /**
     *  修复 (2025-11-29): 在组件外部创建 interactionSource,传递给 Slider
     *  用于检测按下/拖动状态,实现滑块放大效果
     */
    val interactionSource = remember { MutableInteractionSource() }
    /**
     *  MD3 最佳实践 (2025-11-29): 检测按下/拖动状态
     *  按下或拖动时滑块放大,提供明确的视觉反馈
     */
    var isPressed by remember { mutableStateOf(false) }
    // 非拖动时同步 localProgress 到 progress
    LaunchedEffect(progress) {
        if (!isDragging) {
            localProgress = progress
        }
    }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press,
                is DragInteraction.Start -> {
                    isPressed = true
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel,
                is DragInteraction.Stop,
                is DragInteraction.Cancel -> {
                    isPressed = false
                }
            }
        }
    }
    // MD3 动画: 按下时滑块从 16dp 放大到 20dp
    val thumbSize by animateDpAsState(
        targetValue = if (isPressed) 20.dp else 16.dp,
        animationSpec = tween(durationMillis = 100),
        label = "thumbSize"
    )
    // endregion
    // region 播放控制操作防抖设计
    // 防抖设计 (2025-11-29): 上/下一首、播放按钮防抖
    var lastSkipTime by remember { mutableLongStateOf(0L) }
    val skipDebounceMs = 500L  // 500ms防抖间隔
    // endregion
    // region 播放控制器组件
    Column(modifier = modifier.fillMaxWidth()) {
        /**
         *  进度条（2025-11-29：MD3 规范优化）
         *  设计改进：
         *  1. 细轨道（3dp）：精致、不占空间
         *  2. 圆形滑块（16dp）：标准 MD3 尺寸
         *  3. 涟漪效果：使用 SliderDefaults.Thumb 自动提供
         *  4. 进度条两端与时间文字对齐（MD3 最佳实践）
         */
        Slider(
            value = localProgress,
            onValueChange = { newProgress ->
                isDragging = true
                localProgress = newProgress
                // 拖动时实时计算位置并通知LyricView
                val draggingPosition = (duration * newProgress).toLong()
                onDraggingPositionChange?.invoke(draggingPosition)
            },
            onValueChangeFinished = {
                // 修复 (2025-11-29): 立即结束拖动状态,无需延迟
                // localProgress 已经是最新值,不会闪烁
                isDragging = false
                onProgressChange(localProgress)
                // 松手时清除拖动位置,恢复使用实际播放位置
                onDraggingPositionChange?.invoke(-1L)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),  // 移除水平内边距，与时间文字对齐
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,  // 紫色滑块
                activeTrackColor = MaterialTheme.colorScheme.primary,  // 已播放轨道（紫色）
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)  // 未播放轨道（灰色）
            ),
            interactionSource = interactionSource,  // 修复 (2025-11-29): 传递 interactionSource 给 Slider
            thumb = {
                // MD3 最佳实践 (2025-11-29): 按下时滑块放大 + 颜色加深
                // - 默认状态: 16dp 圆形,主题紫色
                // - 按下/拖动: 20dp 圆形,颜色加深 10%
                // - 平滑动画过渡,100ms
                Box(
                    modifier = Modifier
                        .size(thumbSize)  // 动态大小: 16dp ↔ 20dp
                        .background(
                            color = if (isPressed) {
                                // 按下时颜色加深 10%
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            shape = CircleShape
                        )
                )
            },
            track = { sliderState ->
                // 自定义三层轨道（MD3 优化 2025-12-04）
                // 底层：未缓存区域 - onSurface 12%（灰色背景）
                // 中层：已缓存区域 - tertiary 75%（淡紫色，与主紫色形成明暗对比）
                // 顶层：已播放区域 - primary 100%（深紫色）
                val inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                val activeTrackColor = MaterialTheme.colorScheme.primary
                val cacheTrackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.75f)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)  // 增加高度以容纳放大的滑块 (20dp)
                ) {
                    val trackHeight = 3.dp.toPx()
                    // 使用动画后的 thumbSize 计算 offset
                    val currentThumbRadius = thumbSize.toPx() / 2
                    val defaultThumbRadius = 16.dp.toPx() / 2
                    val horizontalOffset = currentThumbRadius - defaultThumbRadius
                    // 轨道实际宽度 = Canvas宽度 + 左右补偿
                    val trackWidth = size.width + horizontalOffset * 2
                    val trackStart = -horizontalOffset
                    // 垂直居中对齐轨道
                    val trackTop = (size.height - trackHeight) / 2

                    // 计算各层进度宽度
                    val activeWidth = trackWidth * localProgress
                    // 显示缓存进度（移除总开关限制，让用户始终看到缓存状态）
                    val cacheWidth = if (cacheProgress != null && cacheProgress.progressPercent > 0) {
                        trackWidth * (cacheProgress.progressPercent / 100f)
                    } else {
                        0f
                    }

                    // 第1层：绘制未缓存轨道（灰色背景）
                    drawRoundRect(
                        color = inactiveTrackColor,
                        topLeft = androidx.compose.ui.geometry.Offset(trackStart, trackTop),
                        size = androidx.compose.ui.geometry.Size(trackWidth, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                    )

                    // 第2层：绘制已缓存轨道（绿色，覆盖在灰色上）
                    if (cacheWidth > 0f) {
                        drawRoundRect(
                            color = cacheTrackColor,
                            topLeft = androidx.compose.ui.geometry.Offset(trackStart, trackTop),
                            size = androidx.compose.ui.geometry.Size(cacheWidth, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                        )
                    }

                    // 第3层：绘制已播放轨道（紫色，覆盖在绿色上）
                    if (activeWidth > 0f) {
                        drawRoundRect(
                            color = activeTrackColor,
                            topLeft = androidx.compose.ui.geometry.Offset(trackStart, trackTop),
                            size = androidx.compose.ui.geometry.Size(activeWidth, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2)
                        )
                    }
                }
            }
        )
        /**
         *  时间显示（修复 2025-11-18: 拖动时显示拖动位置对应的时间）
         *  修复 (2025-11-29): 使用 localProgress 计算显示位置
         *  修复 (2025-12-08): 添加 horizontal padding 与进度条轨道对齐
         *  - 进度条轨道做了 horizontalOffset 补偿，向左右扩展了 thumbRadius
         *  - 时间文字需要相应的 padding 来对齐轨道的视觉起止点
         */
        val displayPosition = (duration * localProgress).toLong()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),  // thumb半径 (16dp / 2 = 8dp)
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(displayPosition),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        /**
         *  控制按钮
         *  修复 (2025-11-21): 优化按钮布局，符合 Material Design 3 规范
         *  1. 播放模式和播放列表按钮对齐进度条两端
         *  2. 增大上一首/下一首按钮尺寸，提升可用性
         *  3. 减小控制区域与进度条的间距
         */
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放模式按钮（左端）
            IconButton(
                onClick = onPlayModeToggle,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = when (playMode) {
                        PlayMode.SEQUENCE -> Icons.Filled.Repeat
                        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                        PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                    },
                    contentDescription = "播放模式",
                    tint = if (playMode != PlayMode.SEQUENCE) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
            // 中间播放控制区域
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 上一曲按钮（增大尺寸）
                // 防抖设计 (2025-11-29): 避免快速连点导致切歌混乱
                IconButton(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSkipTime > skipDebounceMs) {
                            lastSkipTime = currentTime
                            onSkipToPrevious()
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipPrevious,
                        contentDescription = "上一曲",
                        modifier = Modifier.size(36.dp)
                    )
                }
                // 播放/暂停按钮
                FilledIconButton(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSkipTime > skipDebounceMs) {
                            lastSkipTime = currentTime
                            onTogglePlayPause()
                        }
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "播放/暂停",
                        modifier = Modifier.size(36.dp)
                    )
                }
                // 下一曲按钮（增大尺寸）
                IconButton(
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSkipTime > skipDebounceMs) {
                            lastSkipTime = currentTime
                            onSkipToNext()
                        }
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        Icons.Filled.SkipNext,
                        contentDescription = "下一曲",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            // 播放列表按钮（右端）
            IconButton(
                onClick = onPlaylistClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = "播放列表",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    // endregion
}
