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

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.minimalistmusic.domain.model.Song
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
/**
 * 通用歌曲列表项组件
 *
 * 用于在不同场景下显示歌曲信息的统一组件。支持基础显示、序号显示、播放状态等多种模式。
 *
 * 设计原则：
 * - 单向数据流：所有状态通过参数向下传递，所有事件通过回调向上传递
 * - 解耦ViewModel：组件不直接依赖任何ViewModel，由父组件提供数据和处理逻辑
 * - 高度可复用：通过参数配置支持不同的显示模式
 *
 * @param song 歌曲数据
 * @param isFavorite 是否已收藏
 * @param onClick 点击歌曲时的回调
 * @param onFavoriteClick 点击收藏按钮时的回调
 * @param modifier 修饰符
 * @param showIndex 是否显示序号（默认false）
 * @param index 序号值（当showIndex=true时有效）
 * @param isPlaying 是否正在播放（默认false）
 * @param isCached 是否已缓存（默认false，仅对在线歌曲有效）- Added 2025-11-13
 * @param isProtected 是否受白名单保护（默认false）- Added 2025-11-22
 * @param lastPlayedTime 最近播放时间戳（毫秒）- Added 2025-11-19
 * @param cacheTime 缓存完成时间戳（毫秒）- Added 2025-11-19
 * @param favoriteDate 收藏日期时间戳（毫秒）- Added 2025-11-19
 *
 * 使用场景：
 * - HomeScreen：最近播放列表
 * - SearchScreen：搜索结果列表
 * - PlaylistDetailScreen：歌单详情列表
 * - PlayHistoryScreen：播放历史列表
 * - FavoriteSongsScreen：我喜欢的音乐列表
 *
 * 重构说明（2025-11-11）：
 * - 从3个重复组件中抽取：SongListItem、SearchSongItem、PlaylistSongItem
 * - 消除了约200行重复代码
 * - 解耦ViewModel依赖，提升可测试性和可复用性
 *
 * 缓存状态显示（2025-11-13）：
 * - 在线歌曲已缓存时，在歌曲信息右侧显示缓存图标
 * - 本地歌曲不显示缓存图标（默认已缓存）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongListItem(
    song: Song,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    showIndex: Boolean = false,
    index: Int = 0,
    isPlaying: Boolean = false,
    isActuallyPlaying: Boolean = true, // 新增：是否真正在播放（非暂停状态）（2025-11-20）
    isCached: Boolean = true, // 新增：是否已缓存（2025-11-13）
    isProtected: Boolean = false, // 新增：是否受白名单保护（2025-11-22）
    lastPlayedTime: Long? = null, // 新增：最近播放时间（2025-11-19）
    cacheTime: Long? = null, // 新增：缓存完成时间（2025-11-19）
    favoriteDate: Long? = null, // 新增：收藏日期（2025-11-19）
    onLongClick: (() -> Unit)? = null,
    showFavoriteButton: Boolean = true // 新增：是否显示收藏按钮（2025-11-20）
) {
    // 爱心动画状态管理 (2025-11-25)
    // 修复 (2025-11-25 v2): 完全移除LaunchedEffect监听，避免StateFlow异步加载触发动画
    // 问题：
    // - 页面首次渲染时，isFavorite可能从false变为true（StateFlow数据到达）
    // - LaunchedEffect(isFavorite)会检测到变化并触发动画
    // - 导致进入页面时所有已收藏的歌曲都有动画效果
    // 解决：
    // - 移除LaunchedEffect监听
    // - 动画状态由外部通过点击事件手动控制
    // - 缩放：1.5倍
    // - 弹性：LowBouncy
    // - 时长：400ms
    var shouldAnimateFavorite by remember(song.id) { mutableStateOf(false) }
    // 协程作用域，用于延迟重置动画标志
    val coroutineScope = rememberCoroutineScope()
    // 爱心缩放动画：1.0 → 1.5 → 1.0 (优化：从1.3增大到1.5)
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
    // 根据是否提供了长按回调，选择不同的 Modifier
    // 优化 (2025-11-20): 减小左侧padding，使数字更靠近左边缘
    // 优化 (2025-11-21): 垂直间距从8dp增加到12dp，符合MD3标准，提升视觉舒适度
    val clickModifier = if (onLongClick != null) {
        modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick, onLongClick = onLongClick
            )
            .padding(vertical = 12.dp, horizontal = 0.dp)  // MD3优化：垂直间距12dp
            .padding(end = 8.dp)  // 只保留右侧padding给爱心按钮
    } else {
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 0.dp)  // MD3优化：垂直间距12dp
            .padding(end = 8.dp)  // 只保留右侧padding给爱心按钮
    }
    // 2025-11-20：为正在播放的歌曲添加音频波形动画
    // 只有当歌曲是当前歌曲且正在播放（非暂停）时才显示动画
    val shouldAnimate = isPlaying && isActuallyPlaying
    Row(
        modifier = clickModifier, // 使用动态生成的 Modifier
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 序号或播放指示器（可选）
        // 优化 (2025-11-20): 减小宽度从32dp到28dp，间距从12dp到8dp
        if (showIndex) {
            Box(
                modifier = Modifier.width(28.dp),  // 从32dp减小到28dp
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    // 播放中：显示音频波形动画（2025-11-20：优化为波形动画）
                    SimpleAudioWaveIndicator(
                        isAnimating = shouldAnimate, modifier = Modifier.size(20.dp)
                    )
                } else {
                    // 未播放：显示序号
                    Text(
                        text = index.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))  // 从12dp减小到8dp
        }
        // 封面 - 2025-11-17: 添加紫色风格的音乐符号占位符
        // 2025-11-19: 添加"已缓存"标识在左下角
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (song.albumArt.isNullOrEmpty()) {
                // 空图片：显示紫色背景的音乐符号占位符
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MusicNote,
                            contentDescription = "音乐",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else {
                // 有图片URL：使用SubcomposeAsyncImage加载，支持加载中、加载失败占位符
                SubcomposeAsyncImage(
                    model = song.albumArt,
                    contentDescription = "${song.title} 专辑封面",
                    modifier = Modifier.fillMaxSize()
                ) {
                    val state = painter.state
                    when {
                        state is AsyncImagePainter.State.Loading -> {
                            // 加载中：显示紫色背景的音乐符号占位符
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = "音乐",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        state is AsyncImagePainter.State.Error || state is AsyncImagePainter.State.Empty -> {
                            // 加载失败或空状态：显示紫色背景的音乐符号占位符
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.MusicNote,
                                        contentDescription = "音乐",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                        }
                        else -> {
                            // 加载成功：显示图片
                            // 修复 (2025-11-21): 使用Crop确保图片完整填充封面区域，避免左右留白
                            SubcomposeAsyncImageContent(
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                    }
                }
            }
            // 2025-11-19：缓存状态标签显示在封面左下角
            // 2025-11-22：白名单保护标识显示在封面右下角
            if (isCached && !song.isLocal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(topEnd = 4.dp, bottomStart = 8.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "已缓存",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }
            }
            // 2025-11-22：白名单保护标识（右下角）
            if (isProtected && isCached && !song.isLocal) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 2.dp, end = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shield,
                        contentDescription = "受保护",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        // 歌曲信息
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 2025-11-19：艺术家显示（缓存状态标签已移至封面左下角）
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // 2025-11-19：显示最近播放时间
            if (lastPlayedTime != null) {
                Text(
                    text = "最近播放: ${formatPlayedTime(lastPlayedTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
            // 2025-11-19：显示缓存时间
            if (cacheTime != null) {
                Text(
                    text = "最近播放: ${formatPlayedTime(cacheTime)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
            // 2025-11-19：显示收藏日期
            if (favoriteDate != null) {
                Text(
                    text = "收藏日期: ${formatPlayedTime(favoriteDate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1
                )
            }
        }
        // 收藏按钮（2025-11-20：支持隐藏）
        // 2025-11-25：添加爱心收藏成功动画，类似网易云音乐效果
        // 修复 (2025-11-25 v2): 手动触发动画，只在点击收藏时触发
        if (showFavoriteButton) {
            IconButton(
                onClick = {
                    // 只在从未收藏→收藏时触发动画
                    if (!isFavorite) {
                        shouldAnimateFavorite = true
                        coroutineScope.launch {
                            kotlinx.coroutines.delay(400)
                            shouldAnimateFavorite = false
                        }
                    }
                    // 执行外部回调
                    onFavoriteClick()
                },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                    tint = if (isFavorite) Color(0xFFFF0000)  // 鲜红色
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            // 应用缩放动画 (只在收藏成功时生效)
                            scaleX = heartScale
                            scaleY = heartScale
                        }
                )
            }
        }
    }
}
/**
 * 格式化播放时间
 *
 * 智能显示时间格式：
 * - 今天：显示"今天 HH:mm"
 * - 昨天：显示"昨天 HH:mm"
 * - 今年：显示"MM月dd日 HH:mm"
 * - 往年：显示"yyyy年MM月dd日 HH:mm"
 *
 * @since 2025-11-19
 */
private fun formatPlayedTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val date = Date(timestamp)
    val nowDate = Date(now)
    val dayFormatter = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    val timeFormatter = SimpleDateFormat("HH:mm", Locale.CHINA)
    val monthDayTimeFormatter = SimpleDateFormat("MM月dd日 HH:mm", Locale.CHINA)
    val fullFormatter = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.CHINA)
    val dateStr = dayFormatter.format(date)
    val nowStr = dayFormatter.format(nowDate)
    // 计算昨天的日期
    val yesterdayCalendar = Calendar.getInstance()
    yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayStr = dayFormatter.format(yesterdayCalendar.time)
    return when {
        dateStr == nowStr -> "今天 ${timeFormatter.format(date)}"
        dateStr == yesterdayStr -> "昨天 ${timeFormatter.format(date)}"
        date.year == nowDate.year -> monthDayTimeFormatter.format(date)
        else -> fullFormatter.format(date)
    }
}
/**
 * 音频波形播放指示器
 *
 * 显示三条竖线模拟音频频谱效果，每条线独立上下跳动
 * 类似 Spotify、Apple Music 等主流音乐应用的播放动画
 *
 * @param isAnimating 是否播放动画
 * @param modifier 修饰符
 */
//@Composable
//fun AudioWaveIndicator(
//    isAnimating: Boolean,
//    modifier: Modifier = Modifier
//) {
//    // 三条波形线的动画
//    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
//
//    // 第一条线：800ms周期
//    val bar1Height by infiniteTransition.animateFloat(
//        initialValue = 0.3f,
//        targetValue = 1f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(800, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "bar1"
//    )
//
//    // 第二条线：600ms周期，与第一条错开
//    val bar2Height by infiniteTransition.animateFloat(
//        initialValue = 0.5f,
//        targetValue = 1f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(600, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "bar2"
//    )
//
//    // 第三条线：700ms周期
//    val bar3Height by infiniteTransition.animateFloat(
//        initialValue = 0.4f,
//        targetValue = 1f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(700, easing = FastOutSlowInEasing),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "bar3"
//    )
//
//    Row(
//        modifier = modifier,
//        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
//        verticalAlignment = Alignment.Bottom
//    ) {
//        // 三条波形线
//        listOf(
//            if (isAnimating) bar1Height else 0.3f,
//            if (isAnimating) bar2Height else 0.5f,
//            if (isAnimating) bar3Height else 0.4f
//        ).forEach { height ->
//            Box(
//                modifier = Modifier
//                    .width(3.dp)
//                    .fillMaxHeight(height)
//                    .background(
//                        color = MaterialTheme.colorScheme.primary,
//                        shape = RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp)
//                    )
//            )
//        }
//    }
//}
/**
 * 优化版音频波形播放指示器
 * 提供更丝滑的动画效果和更自然的波形跳动
 */
//@Composable
//fun AudioWaveIndicator(
//    isAnimating: Boolean,
//    modifier: Modifier = Modifier
//) {
//    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
//
//    // 使用自定义的缓动函数 - 修正导入问题
//    val customEasing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
//
//    // 第一条线：主节奏，850ms周期
//    val bar1Height by infiniteTransition.animateFloat(
//        initialValue = 0.2f,
//        targetValue = 0.9f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(
//                durationMillis = 850,
//                easing = customEasing
//            ),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "bar1"
//    )
//
//    // 第二条线：次节奏，650ms周期，错开相位
//    val bar2Height by infiniteTransition.animateFloat(
//        initialValue = 0.4f,
//        targetValue = 1f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(
//                durationMillis = 650,
//                easing = customEasing
//            ),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "bar2"
//    )
//
//    // 第三条线：辅助节奏，750ms周期
//    val bar3Height by infiniteTransition.animateFloat(
//        initialValue = 0.3f,
//        targetValue = 0.8f,
//        animationSpec = infiniteRepeatable(
//            animation = tween(
//                durationMillis = 750,
//                easing = customEasing
//            ),
//            repeatMode = RepeatMode.Reverse
//        ),
//        label = "bar3"
//    )
//
//    Row(
//        modifier = modifier,
//        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
//        verticalAlignment = Alignment.Bottom
//    ) {
//        listOf(
//            if (isAnimating) bar1Height else 0.2f,
//            if (isAnimating) bar2Height else 0.4f,
//            if (isAnimating) bar3Height else 0.3f
//        ).forEachIndexed { index, height ->
//            Box(
//                modifier = Modifier
//                    .width(2.dp)
//                    .fillMaxHeight(height)
//                    .background(
//                        color = MaterialTheme.colorScheme.primary.copy(
//                            alpha = 0.7f + index * 0.1f
//                        ),
//                        shape = RoundedCornerShape(50)
//                    )
//            )
//        }
//    }
//}
/**
 * 修复编译错误的音频波形播放指示器 - 终极版本
 * 专为音乐播放App设计，符合MD3原则，高性能且丝滑
 */
@Composable
fun AudioWaveIndicator(
    isAnimating: Boolean, barCount: Int = 5, modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audioWave")
    // 预定义动画参数（非Composable部分）
    val durations = remember { listOf(780, 650, 850, 720, 930, 680, 810) }
    val minHeights = remember { listOf(0.15f, 0.25f, 0.2f, 0.3f, 0.1f) }
    val maxHeights = remember { listOf(0.9f, 1.0f, 0.8f, 0.7f, 0.95f) }
    // 创建动画状态 - 这部分在remember外部
    val barAnimations = remember(barCount) {
        List(barCount) { index ->
            BarAnimationConfig(
                duration = durations[index % durations.size],
                minHeight = minHeights[index % minHeights.size],
                maxHeight = maxHeights[index % maxHeights.size]
            )
        }
    }.mapIndexed { index, config ->
        // 动画创建必须在remember外部
        infiniteTransition.animateFloat(
            initialValue = config.minHeight,
            targetValue = config.maxHeight,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = config.duration, easing = FastOutSlowInEasing
                ), repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }
    WaveformCanvas(
        isAnimating = isAnimating,
        barAnimations = barAnimations,
        barCount = barCount,
        modifier = modifier
    )
}
/**
 * 存储条形动画配置的数据类（非Composable）
 */
private data class BarAnimationConfig(
    val duration: Int, val minHeight: Float, val maxHeight: Float
)
@Composable
private fun WaveformCanvas(
    isAnimating: Boolean, barAnimations: List<State<Float>>, barCount: Int, modifier: Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
    // 预定义初始高度
    val initialHeights = remember(barCount) {
        when (barCount) {
            3 -> listOf(0.3f, 0.5f, 0.4f)
            4 -> listOf(0.2f, 0.4f, 0.6f, 0.3f)
            5 -> listOf(0.15f, 0.35f, 0.5f, 0.35f, 0.15f)
            else -> List(barCount) { 0.3f + it * 0.1f % 0.6f }
        }
    }
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val spacing = 3.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * spacing
        val startX = (size.width - totalWidth) / 2
        for (i in 0 until barCount) {
            val heightFraction = if (isAnimating) {
                barAnimations[i].value
            } else {
                initialHeights[i]
            }
            val barHeight = size.height * heightFraction
            val barTop = size.height - barHeight
            // 使用渐变色，中间条形更突出
            val color = when {
                i == barCount / 2 -> primaryColor
                abs(i - barCount / 2) == 1 -> primaryColor.copy(alpha = 0.8f)
                else -> secondaryColor
            }
            // 绘制圆角条形
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barWidth + spacing), barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2, barWidth / 2
                )
            )
        }
    }
}
@Composable
private fun SimpleWaveformCanvas(
    isAnimating: Boolean,
    animations: List<Float>,
    initialHeights: List<Float>,
    barCount: Int,
    modifier: Modifier
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barWidth = 2.dp.toPx()
        val spacing = 2.dp.toPx()
        val totalWidth = barCount * barWidth + (barCount - 1) * spacing
        val startX = (size.width - totalWidth) / 2
        for (i in 0 until barCount) {
            val heightFraction = if (isAnimating) animations[i] else initialHeights[i]
            val barHeight = size.height * heightFraction
            val barTop = size.height - barHeight
            // 根据位置调整透明度
            val alpha = 0.6f + (0.4f * (1 - abs(i - barCount / 2) / (barCount / 2f)))
            drawRoundRect(
                color = color.copy(alpha = alpha),
                topLeft = Offset(startX + i * (barWidth + spacing), barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2, barWidth / 2
                )
            )
        }
    }
}
/**
 * 更安全简单的版本 - 避免复杂remember逻辑
 */
@Composable
fun SimpleAudioWaveIndicator(
    isAnimating: Boolean, modifier: Modifier = Modifier, barCount: Int = 5
) {
    val infiniteTransition = rememberInfiniteTransition(label = "simpleAudioWave")
    // 为每个条形单独创建动画，避免在remember中创建复杂逻辑
    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.7f, animationSpec = infiniteRepeatable(
            animation = tween(780, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar1"
    )
    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f, animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar2"
    )
    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f, animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar3"
    )
    val anim4 by infiniteTransition.animateFloat(
        initialValue = 0.35f, targetValue = 0.9f, animationSpec = infiniteRepeatable(
            animation = tween(720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar4"
    )
    val anim5 by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.7f, animationSpec = infiniteRepeatable(
            animation = tween(930, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "bar5"
    )
    val animations = listOf(anim1, anim2, anim3, anim4, anim5)
    // 优化 (2025-11-23): 调大暂停状态的波形高度，使其更明显可见
    // 原值: 0.15f, 0.35f, 0.5f, 0.35f, 0.15f
    // 新值: 增加约40-50%，保持对称的波形轮廓
    val initialHeights = listOf(0.25f, 0.5f, 0.7f, 0.5f, 0.25f)
    SimpleWaveformCanvas(
        isAnimating = isAnimating,
        animations = animations,
        initialHeights = initialHeights,
        barCount = barCount,
        modifier = modifier
    )
}
/**
 * 极简版本 - 3条波形，最适合紧凑空间，完全避免编译问题
 */
@Composable
fun SimpleAudioWaveIndicator2(
    isAnimating: Boolean, modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "minimalAudioWave")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f, animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "minimal_bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f, animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "minimal_bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f, animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "minimal_bar3"
    )
    val heights = if (isAnimating) listOf(bar1, bar2, bar3) else listOf(0.3f, 0.5f, 0.4f)
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val spacing = 3.dp.toPx()
        val totalWidth = 3 * barWidth + 2 * spacing
        val startX = (size.width - totalWidth) / 2
        heights.forEachIndexed { i, heightFraction ->
            val barHeight = size.height * heightFraction
            val barTop = size.height - barHeight
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barWidth + spacing), barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                    barWidth / 2, barWidth / 2
                )
            )
        }
    }
}
/**
 * 极简版本 - 3条波形，最适合紧凑空间
 */
@Composable
fun SimpleAudioWaveIndicator3(
    isAnimating: Boolean, modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "minimalAudioWave")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.9f, animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "minimal_bar1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 1.0f, animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "minimal_bar2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.8f, animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse
        ), label = "minimal_bar3"
    )
    val heights = if (isAnimating) listOf(bar1, bar2, bar3) else listOf(0.3f, 0.5f, 0.4f)
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val barWidth = 3.dp.toPx()
        val spacing = 3.dp.toPx()
        val totalWidth = 3 * barWidth + 2 * spacing
        val startX = (size.width - totalWidth) / 2
        heights.forEachIndexed { i, heightFraction ->
            val barHeight = size.height * heightFraction
            val barTop = size.height - barHeight
            drawRoundRect(
                color = color,
                topLeft = Offset(startX + i * (barWidth + spacing), barTop),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}