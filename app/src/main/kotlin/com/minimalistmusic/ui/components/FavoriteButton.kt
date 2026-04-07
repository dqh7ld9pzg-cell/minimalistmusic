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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
/**
 * 带收藏动画的爱心按钮组件（2025-11-25）
 *
 * 特性：
 * - 类似网易云音乐的爱心收藏动画
 * - 只在收藏成功时（false → true）触发缩放动画
 * - 取消收藏时无动画，直接切换状态
 * - 使用 spring 弹簧动画，丝滑流畅
 *
 * 优化（相比 SongListItem 中的实现）：
 * - 缩放效果更大：1.0 → 1.5 → 1.0（原1.3增大到1.5）
 * - 更柔和的弹簧效果：DampingRatioLowBouncy（原MediumBouncy更柔和）
 * - 动画时长延长：400ms（原300ms更流畅）
 *
 * @param isFavorite 是否已收藏
 * @param onClick 点击回调（父组件负责切换收藏状态）
 * @param modifier 修饰符
 * @param contentDescription 无障碍描述
 *
 * @since 2025-11-25
 */
@Composable
fun AnimatedFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    // 爱心动画状态管理
    // 修复 (2025-11-25): 使用当前isFavorite作为初始值,避免数据异步加载触发动画
    var previousFavorite by remember { mutableStateOf(isFavorite) }
    var shouldAnimateFavorite by remember { mutableStateOf(false) }
    // 监听收藏状态变化，只在 false → true 时触发动画
    LaunchedEffect(isFavorite) {
        // 后续变化：只在 false → true 时触发动画
        val justFavorited = previousFavorite == false && isFavorite
        shouldAnimateFavorite = justFavorited
        previousFavorite = isFavorite
        // 动画完成后重置标志（优化：400ms，比原来的300ms更流畅）
        if (justFavorited) {
            kotlinx.coroutines.delay(400)
            shouldAnimateFavorite = false
        }
    }
    // 爱心缩放动画：1.0 → 1.5 → 1.0 (优化：从1.3增大到1.5，视觉冲击更明显)
    val heartScale by animateFloatAsState(
        targetValue = if (shouldAnimateFavorite) 1.5f else 1.0f,
        animationSpec = if (shouldAnimateFavorite) {
            spring(
                // 优化：使用 LowBouncy 代替 MediumBouncy，更柔和丝滑
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMedium
            )
        } else {
            spring(stiffness = Spring.StiffnessHigh)
        },
        label = "heartScale"
    )
    IconButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = contentDescription
                ?: if (isFavorite) "取消喜欢" else "喜欢",
            tint = if (isFavorite) Color(0xFFFF0000)  // 鲜红色
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            modifier = Modifier.graphicsLayer {
                // 应用缩放动画（只在收藏成功时生效）
                scaleX = heartScale
                scaleY = heartScale
            }
        )
    }
}
/**
 * 较小尺寸的收藏按钮（用于歌曲列表条目）
 *
 * 预设尺寸：IconButton 40dp, Icon 20dp
 * 适用场景：SongListItem、底部播放列表等紧凑空间
 *
 * @since 2025-11-25
 */
@Composable
fun SmallAnimatedFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedFavoriteButton(
        isFavorite = isFavorite,
        onClick = onClick,
        modifier = modifier.size(40.dp)
    )
}
/**
 * 中等尺寸的收藏按钮（用于播放页面）
 *
 * 预设尺寸：IconButton 48dp, Icon 24dp
 * 适用场景：PlayerScreen 等主要交互页面
 *
 * @since 2025-11-25
 */
@Composable
fun MediumAnimatedFavoriteButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedFavoriteButton(
        isFavorite = isFavorite,
        onClick = onClick,
        modifier = modifier.size(48.dp)
    )
}
