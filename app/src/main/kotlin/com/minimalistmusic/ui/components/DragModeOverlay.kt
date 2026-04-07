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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimalistmusic.util.LyricLine
import com.minimalistmusic.util.formatDuration

/**
 * 拖动模式叠加层
 *
 * 职责：
 * - 在屏幕中心显示固定的高亮歌词行
 * - 包含三部分：时间标签 + 歌词文本 + 播放图标
 * - 点击后跳转到该歌词位置并退出拖动模式
 *
 * 设计原则：
 * - 独立渲染：不触发列表重组，性能优化
 * - 固定位置：始终在屏幕中心，不随滚动移动
 * - 视觉突出：带背景色和圆角，区分于普通歌词
 *
 * 修复说明（2025-11-30）：
 * - 原代码line 324将歌词文本设为空字符串（text = ""）
 * - 导致拖动模式下中心高亮区域看不到歌词内容
 * - 现已修复：正确显示 centerLyric.text
 *
 * @param centerLyric 中心高亮歌词（为null时不显示）
 * @param onSeek 点击跳转回调
 * @param modifier 修饰符
 *
 * @since 2025-11-30 (重构版本)
 */
@Composable
fun DragModeOverlay(
    centerLyric: LyricLine?,
    onSeek: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 只有在提供了中心歌词时才渲染叠加层
    centerLyric?.let { lyric ->
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                // 蒙版背景，突出显示，防止与背景歌词形成错乱
                .background(MaterialTheme.colorScheme.background.copy(alpha = 1.0f))
                // Material 3风格：使用默认ripple效果
                .clickable(onClick = onSeek)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // ========== 左侧：时间标签 ==========
            Text(
                text = formatDuration(lyric.time),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.widthIn(min = 40.dp)
            )

            // ========== 中间：歌词文本 ==========
            Text(
                text = lyric.text,  // 显示高亮歌词文本
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis  // 文本过长时显示省略号
            )

            // ========== 右侧：播放图标 ==========
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "播放此处",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

/**
 * 纯歌词模式时间指示器（叠加层）
 *
 * 职责：
 * - 悬浮在歌词上方，显示当前中心位置的时间
 * - 使用渐变线条横贯屏幕，优雅标识当前时间位置
 * - 类似波点音乐的设计，简洁美观
 *
 * 设计原则（基于 Material Design 3 + 用户反馈优化）：
 * - **叠加层设计**: 不影响歌词列表布局，完全独立渲染
 * - **视觉层级**: 时间高亮(primary色)，线条渐变消失
 * - **呼吸感**: 完全透明背景，渐变线条，轻量不干扰
 * - **对齐原则**: 与底部播放控制器时间左对齐(0dp padding)
 * - **色彩系统**: 时间使用 primary 高亮，线条从 30% 渐变到 0%
 * - **字体规范**: labelMedium (12sp)，提升可读性
 *
 * 视觉效果：
 * ```
 *        歌词内容
 * 01:23 ━━━━━━━━━━━━━━━━━━━━  ← 悬浮在歌词上方
 *   ↑   ↑        ↑        ↑   ↑
 * 高亮  30%      5%      30% (对称渐变)
 * 时间  左端     中间     右端
 * ```
 *
 * @param centerLyric 中心歌词（为null时不显示）
 * @param modifier 修饰符
 *
 * @since 2025-12-05
 */
/**
 * 纯歌词模式时间指示器（优化版 2025-12-08）
 *
 * 设计优化 v2：
 * - 完全透明背景：轻量简洁，不干扰歌词阅读
 * - 小字体：labelSmall (11sp)，更精致
 * - 贴近边缘：在 PlayerScreen 层级渲染，突破 LyricView padding 限制
 *
 * 功能说明：
 * - 显示第2行位置歌词的时间戳（LRC文件中的time字段）
 * - 注意：此时间可能比实际唱到该句的时间早几秒（LRC歌词的常见设计）
 *
 * @param centerLyric 第2行位置的歌词
 * @param modifier 修饰符
 *
 * @since 2025-12-08 (优化版 v2)
 */
@Composable
fun PureLyricTimeLabel(
    centerLyric: LyricLine?,
    modifier: Modifier = Modifier
) {
    centerLyric?.let { lyric ->
        Text(
            text = formatDuration(lyric.time),
            style = MaterialTheme.typography.labelSmall.copy(  // 调小字体
                letterSpacing = 0.5.sp
            ),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier  // 完全透明背景，去掉 background 和 padding
        )
    }
}
