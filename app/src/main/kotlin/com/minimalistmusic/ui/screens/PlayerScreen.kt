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

package com.minimalistmusic.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.minimalistmusic.ui.components.EnhancedPlaylistBottomSheet
import com.minimalistmusic.ui.components.LyricView
import com.minimalistmusic.ui.components.PlayerControls
import com.minimalistmusic.ui.components.SyncReminderDialogHost
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import com.minimalistmusic.ui.components.SyncReminderDialogHost
import kotlinx.coroutines.launch

/**
 * 播放器主界面
 *
 * 作为应用的核心功能模块，提供完整的音乐播放控制和歌词查看能力。
 *
 * ## 核心功能
 *
 * ### 1. 歌曲信息展示
 * - 专辑封面（1:1比例，12dp圆角）
 * - 歌曲标题（单行，超出省略）
 * - 艺术家名称（带收藏按钮）
 *
 * ### 2. 歌词系统
 * **正常播放模式：**
 * - 当前播放位置歌词高亮显示
 * - 歌词自动滚动，始终在第2行显示
 * - 支持拖动进度条快速定位
 *
 * **歌词拖动模式：**
 * - 长按歌词1秒进入拖动模式
 * - 进入时震动反馈
 * - 封面淡出（300ms动画）
 * - 显示"歌词拖动模式"横幅提示
 * - 高亮歌词固定在列表中间位置
 * - 滑动选择，点击确认跳转播放
 *
 * **退出拖动模式的触发条件：**
 * - 点击横幅右侧"取消"按钮
 * - 点击高亮歌词确认跳转
 * - 按下返回键
 * - 切换歌曲（上一曲/下一曲）
 * - 点击播放列表中的其他歌曲
 *
 * ### 3. 播放控制
 * - 播放模式切换（顺序/随机/单曲循环）
 * - 上一曲/下一曲
 * - 播放/暂停
 * - 进度条拖拽（显示当前时间/总时长）
 * - 播放列表抽屉
 *
 * ### 4. 收藏功能
 * - 实时显示收藏状态
 * - 支持防抖点击（500ms）
 * - 未登录时弹出同步提醒对话框
 *
 * ## UI结构
 * ```
 * TopAppBar
 * ├── "正在播放"标题（正常模式）
 * └── "歌词拖动模式"横幅（拖动模式）
 *
 * 内容区域
 * ├── 封面图片（拖动模式下隐藏）
 * ├── 歌曲信息（标题 + 艺术家 + 收藏按钮）
 * ├── 歌词区域（LyricView组件）
 * └── 播放控制区域（PlayerControls组件）
 * ```
 *
 * ## 状态管理
 * - [playbackState]: 播放状态（当前歌曲、进度、播放模式等）
 * - [lyrics]: 当前歌曲的歌词列表
 * - [isLyricDragMode]: 是否处于歌词拖动模式
 * - [showPlaylist]: 是否显示播放列表抽屉
 * - [favoriteSongIds]: 收藏歌曲ID集合
 *
 * ## 性能优化
 * - 动画使用animateFloatAsState平滑过渡
 * - 收藏按钮防抖（500ms）避免频繁请求
 * - 使用BackHandler拦截返回键事件
 * - 组件拆分（LyricView、PlayerControls、PlaylistBottomSheet）减少重组范围
 *
 * ## 架构优化
 * - 原1025行代码拆分为5个独立文件
 * - 职责单一：PlayerScreen仅负责布局和状态协调
 * - 解耦ViewModel：通过参数传递避免组件直接注入
 *
 * @param navController 导航控制器
 * @param viewModel 播放器ViewModel，管理播放状态和歌词
 * @param accountSyncViewModel 账号同步ViewModel，管理收藏状态
 *
 * @see com.minimalistmusic.ui.components.LyricView 歌词显示组件
 * @see com.minimalistmusic.ui.components.PlayerControls 播放控制组件
 * @see com.minimalistmusic.ui.components.EnhancedPlaylistBottomSheet 播放列表底部抽屉
 *
 * @since 2025-11-11
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    navController: NavHostController,
    onDismiss: () -> Unit = {},
    viewModel: PlayerViewModel,
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    cachedMusicViewModel: CachedMusicViewModel = hiltViewModel(),
) {
    // 获取用户偏好设置（用于读取智能缓存开关）
    val context = LocalContext.current
    val userPreferencesDataStore = remember {
        com.minimalistmusic.data.local.UserPreferencesDataStore(context)
    }

    // ========== 状态收集 ==========
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val lyrics by viewModel.currentLyrics.collectAsStateWithLifecycle()
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    // 缓存状态响应式刷新
    val cacheMap by cachedMusicViewModel.cacheStateMap.collectAsStateWithLifecycle()
    // 缓存进度响应式更新
    val cacheProgressMap by cachedMusicViewModel.cacheProgressMap.collectAsStateWithLifecycle()
    // 智能缓存总开关
    val cacheEnabled by userPreferencesDataStore.cacheEnabled.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    // ========== 本地UI状态 ==========
    var showPlaylist by remember { mutableStateOf(false) }
    var isLyricDragMode by remember { mutableStateOf(false) }
    var isPureLyricMode by remember { mutableStateOf(false) }  // 纯歌词模式 (2025-11-14新增)
    var draggingPosition by remember { mutableLongStateOf(-1L) }  // 进度条拖动时的位置 (2025-11-29新增)
    var centerLyric by remember { mutableStateOf<com.minimalistmusic.util.LyricLine?>(null) }  // 中心歌词状态 (2025-12-08新增)
    var lyricViewTopOffset by remember { mutableFloatStateOf(0f) }  // LyricView顶部偏移量 (2025-12-08新增)
    // ========== 拖拽关闭状态 (2025-11-18新增) ==========
    var offsetY by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    val dismissThreshold = 600f  // 关闭阈值：200dp
    // ========== 返回键拦截 ==========
    // 优先级：拖动模式 > 纯歌词模式 > 关闭播放器
    BackHandler(enabled = true) {
        when {
            isLyricDragMode -> isLyricDragMode = false      // 退出拖动模式
            isPureLyricMode -> isPureLyricMode = false      // 退出纯歌词模式
            else -> onDismiss()                              // 关闭播放器浮层
        }
    }
    // ========== 主界面布局 ==========
    // 播放器作为浮层渲染,拖拽时可以看到下层页面
    // 修复 (2025-11-21): 使用 graphicsLayer 代替 offset，避免影响布局计算导致标题栏变宽
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY
            }
    ) {
        Scaffold(
            topBar = {
                Column {
                    // 使用标准 TopAppBar，透明背景
                    TopAppBar(
                        title = {
                            Text(text = "正在播放")
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    if (isLyricDragMode) {
                                        isLyricDragMode = false
                                    } else {
                                        onDismiss()
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "返回"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        )
                    )
                    // 分割线
                    Divider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                    // 拖动模式横幅（TopBar下方）
                    AnimatedVisibility(
                        visible = isLyricDragMode,
                        enter = slideInVertically(
                            initialOffsetY = { -it },
                            animationSpec = tween(300)
                        ) + fadeIn(tween(300)),
                        exit = slideOutVertically(
                            targetOffsetY = { -it },
                            animationSpec = tween(300)
                        ) + fadeOut(tween(300))
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            tonalElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // 左侧：提示信息
                                Column {
                                    Text(
                                        text = "歌词拖动模式",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "滑动选择歌词，点击选中歌词位置确认跳转",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                // 右侧：取消按钮
                                TextButton(
                                    onClick = { isLyricDragMode = false },
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text(
                                        text = "取消",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { paddingValues ->
            // ========== 内容区域 ==========
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ========== 可拖拽区域：仅封面和歌名 ==========
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 拖拽区域：封面 + 歌名
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectVerticalDragGestures(
                                    onDragEnd = {
                                        coroutineScope.launch {
                                            if (offsetY > dismissThreshold) {
                                                // 修复 (2025-11-18): 超过阈值时添加平滑关闭动画
                                                // 从当前位置动画到屏幕底部，然后关闭播放器,效果不好，先禁用
//                                                val animatable = Animatable(offsetY)
//                                                animatable.animateTo(
//                                                    targetValue = offsetY + 300f, // 动画到屏幕底部
//                                                    animationSpec = tween(
//                                                        durationMillis = 1200, // 快速但平滑的退出动画
//                                                        easing = androidx.compose.animation.core.FastOutLinearInEasing
//                                                    )
//                                                ) {
//                                                    offsetY = value
//                                                }
                                                // 动画完成后关闭播放器
                                                onDismiss()
                                                offsetY = 0f
                                            } else {
                                                // 未超过阈值：回弹动画
                                                val animatable = Animatable(offsetY)
                                                animatable.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = tween(
                                                        durationMillis = 240,
                                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                                    )
                                                ) {
                                                    offsetY = value
                                                }
                                            }
                                        }
                                    },
                                    onVerticalDrag = { _, dragAmount ->
                                        // 只允许向下拖拽
                                        val newOffset = offsetY + dragAmount
                                        offsetY = if (newOffset > 0f) newOffset else 0f
                                    }
                                )
                            }
                    ) {
                        Spacer(modifier = Modifier.height(16.dp))
                        // ========== 歌曲封面（拖动模式或纯歌词模式下隐藏）==========
                        // 2025-11-20：添加"已缓存"标识在封面左下角
                        // 修复 (2025-11-29): 进入隐藏状态时立即消失（无动画），退出时平滑淡入
                        if (!isLyricDragMode && !isPureLyricMode) {
                            // 只在非隐藏状态下渲染封面，条件渲染确保立即消失
                            // 使用 AnimatedVisibility 仅用于淡入效果
                            AnimatedVisibility(
                                visible = true,  // 进入此分支已经确保可见
                                enter = fadeIn(tween(300)) + scaleIn(tween(300)),
                                exit = fadeOut(tween(0))  // exit 不会触发（因为使用 if 控制）
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.65f)
                                        .aspectRatio(1f)
                                ) {
                                    SubcomposeAsyncImage(
                                        model = currentSong?.albumArt,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                    ) {
                                        val state = painter.state
                                        if (state is coil.compose.AsyncImagePainter.State.Success) {
                                            // 加载成功：显示图片
                                            SubcomposeAsyncImageContent()
                                        } else {
                                            // 加载中/失败/无封面：显示占位符
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        MaterialTheme.colorScheme.primaryContainer,
                                                        RoundedCornerShape(12.dp)
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(0.5f),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                    // 已缓存标识显示在封面左下角
                                    if (cacheMap[currentSong?.id] ?: false) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                                .background(
                                                    Color.Black.copy(alpha = 0.7f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = "已缓存",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        // ========== 歌名 + 纯歌词模式关闭按钮 ==========
                        // MD3 优化 (2025-12-04): 关闭按钮固定在右上角，歌名居中显示
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            // 歌名居中显示
                            Text(
                                text = currentSong?.title ?: "暂无播放",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = if (isPureLyricMode) 48.dp else 0.dp) // 给关闭按钮留出空间
                            )
                            // 纯歌词模式：关闭按钮固定在右上角
                            if (isPureLyricMode) {
                                IconButton(
                                    onClick = { isPureLyricMode = false },
                                    modifier = Modifier.align(Alignment.CenterEnd)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "关闭纯歌词模式",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    // ========== 拖拽区域结束 ==========
                    // MD3优化 (2025-11-29): 歌名到歌手信息间距从2dp增加到8dp，符合MD3规范
                    Spacer(modifier = Modifier.height(8.dp))
                    // ========== 歌手信息区域（不可拖拽）==========
                    // MD3优化 (2025-11-29): 改用fillMaxWidth确保爱心图标位置固定，添加歌手名截断
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 歌手名（带weight确保正确截断）
                        Text(
                            text = currentSong?.artist ?: "",
                            style = MaterialTheme.typography.bodyMedium,  // MD3优化：次要信息用bodyMedium
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,  // MD3优化：添加单行限制
                            overflow = TextOverflow.Ellipsis,  // MD3优化：添加省略号
                            modifier = Modifier
                                .weight(1f, fill = false)  // 使用weight但不填充，让内容居中
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    currentSong?.artist?.let { artistName ->
                                        // 先关闭播放器浮层，再导航到歌手详情页
                                        // 修复 (2025-11-20): 对歌手名称进行URL编码，避免特殊字符（如"/"）导致导航崩溃
                                        val encodedArtistName =
                                            java.net.URLEncoder.encode(artistName, "UTF-8")
                                        onDismiss()
                                        navController.navigate("artist_detail/$encodedArtistName")
                                    }
                                }
                        )
                        // MD3优化 (2025-11-29): 文字到图标间距从2dp增加到8dp
                        Spacer(modifier = Modifier.width(8.dp))
                        // 喜欢按钮
                        var lastClickTime by remember { mutableLongStateOf(0L) }
                        currentSong?.let { song ->
                            val isFavorite = favoriteSongIds.contains(song.id)
                            // 2025-11-25：添加爱心收藏动画
                            // 修复 (2025-11-25): 使用当前isFavorite作为初始值,避免数据异步加载触发动画
                            var previousFavorite by remember(song.id) { mutableStateOf(isFavorite) }
                            var shouldAnimateFavorite by remember(song.id) { mutableStateOf(false) }
                            // 监听收藏状态变化，只在 false → true 时触发动画
                            LaunchedEffect(isFavorite) {
                                val justFavorited = previousFavorite == false && isFavorite
                                shouldAnimateFavorite = justFavorited
                                previousFavorite = isFavorite
                                if (justFavorited) {
                                    kotlinx.coroutines.delay(400)
                                    shouldAnimateFavorite = false
                                }
                            }
                            // 爱心缩放动画：1.0 → 1.5 → 1.0
                            val heartScale by animateFloatAsState(
                                targetValue = if (shouldAnimateFavorite) 1.5f else 1.0f,
                                animationSpec = if (shouldAnimateFavorite) {
                                    spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMedium
                                    )
                                } else {
                                    spring(stiffness = Spring.StiffnessHigh)
                                },
                                label = "playerHeartScale"
                            )
                            IconButton(
                                onClick = {
                                    //性能优化：收藏爱心点击防抖
                                    val currentClickTime = System.currentTimeMillis()
                                    if (currentClickTime - lastClickTime > 500) {
                                        lastClickTime = currentClickTime
                                        accountSyncViewModel.toggleFavorite(song)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = if (isFavorite) "取消喜欢" else "喜欢",
                                    tint = if (isFavorite) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurface.copy(
                                        alpha = 0.6f
                                    ),  // P1优化：使用主题颜色
                                    modifier = Modifier.graphicsLayer {
                                        // 应用缩放动画（只在收藏成功时生效）
                                        scaleX = heartScale
                                        scaleY = heartScale
                                    }
                                )
                            }
                        }
                    }
                }
                // ========== 歌曲信息区域结束 ==========
                Spacer(
                    modifier = Modifier
                        .weight(0.1f)
                        .heightIn(min = 1.dp)
                )
                // ========== 歌词显示区域（使用独立组件）==========
                // 纯歌词模式：点击歌词区域切换为纯歌词模式，隐藏封面
                // 修复 (2025-11-29): 拖动进度条时使用draggingPosition,否则使用实际播放位置
                if (lyrics.isNotEmpty()) {
                    LyricView(
                        lyrics = lyrics,
                        currentPosition = if (draggingPosition >= 0) draggingPosition else playbackState.currentPosition,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(4f)
                            .heightIn(max = 300.dp)
                            .padding(2.dp)
                            .onGloballyPositioned { coordinates ->
                                // 获取 LyricView 在屏幕中的绝对位置（顶部Y坐标）
                                lyricViewTopOffset = coordinates.positionInRoot().y
                            },
                        onSeekTo = { position ->
                            viewModel.seekTo(position)
                        },
                        isDragMode = isLyricDragMode,
                        isPureLyricMode = isPureLyricMode,
                        onDragModeChange = { dragMode ->
                            isLyricDragMode = dragMode
                            if (dragMode) {
                                // 进入拖动模式时，退出纯歌词模式
                                isPureLyricMode = false
                            }
                        },
                        onTap = {
                            // 点击歌词区域：切换纯歌词模式
                            isPureLyricMode = !isPureLyricMode
                        },
                        onCenterLyricChange = { lyric ->
                            centerLyric = lyric  // 更新中心歌词状态
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(3f)
                            .heightIn(max = 300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无歌词",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
                // 歌词到进度条的间距
                // MD3优化 (2025-11-29): 使用固定8dp代替弹性weight，让歌词有更多展示空间
                // 符合MD3规范：紧密关联组件间距8-12dp
                Spacer(modifier = Modifier.height(8.dp))
                // ========== 播放控制区域（使用独立组件）==========
                // 性能优化：直接使用 ViewModel 方法引用和内联 lambda
                // 修复 (2025-11-29): 添加拖动位置回调,实现进度条拖动时歌词同步滚动
                // 新增 (2025-12-04): 添加缓存进度显示
                PlayerControls(
                    progress = playbackState.progress,
                    currentPosition = playbackState.currentPosition,
                    duration = playbackState.duration,
                    isPlaying = playbackState.isPlaying,
                    playMode = playbackState.playMode,
                    onProgressChange = { progress ->
                        val position = (playbackState.duration * progress).toLong()
                        viewModel.seekTo(position)
                    },
                    onPlayModeToggle = viewModel::togglePlayMode,
                    onSkipToPrevious = {
                        viewModel.skipToPrevious()
                    },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onSkipToNext = {
                        viewModel.skipToNext()
                    },
                    onPlaylistClick = {
                        showPlaylist = true
                    },
                    onDraggingPositionChange = { position ->
                        draggingPosition = position
                    },
                    cacheProgress = currentSong?.id?.let { songId ->
                        cacheProgressMap[songId]
                    },
                    cacheEnabled = cacheEnabled
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        // ========== 播放列表底部抽屉（使用独立组件）==========
        // 性能优化：使用方法引用和内联 lambda
        if (showPlaylist) {
            EnhancedPlaylistBottomSheet(
                playlist = playbackState.playlist,
                currentIndex = playbackState.currentIndex,
                currentPlayMode = playbackState.playMode,
                onDismiss = { showPlaylist = false },
                onSongClick = { index ->
                    // 点击列表歌曲时退出拖动模式
                    //if (isLyricDragMode) isLyricDragMode = false
                    viewModel.seekToSong(index)
                },
                onPlayModeToggle = viewModel::togglePlayMode,  // 方法引用，零开销
                isPlaying = playbackState.isPlaying,  // 传递实际播放状态
                currentSongId = playbackState.currentSong?.id,  // 修复 (2025-11-21): 使用歌曲ID准确判断当前播放
                accountSyncViewModel = accountSyncViewModel,
                cachedMusicViewModel = cachedMusicViewModel  // 修复 (2025-11-25): 传入共享实例,避免重复实例化
            )
        }
        // ========== 同步提醒对话框 ==========
        SyncReminderDialogHost(
            accountSyncViewModel = accountSyncViewModel,
            navController = navController
        )

        // ========== 纯歌词模式时间指示器（提升到PlayerScreen层级，2025-12-08）==========
        // 设计说明：
        // - 在 PlayerScreen 层级渲染，突破 LyricView 的 padding 限制
        // - 可以贴近屏幕左边缘，避免被歌词条目遮挡
        // - 位置计算：使用 onGloballyPositioned 获取 LyricView 的实际顶部位置 + 内部第2行偏移
        if (isPureLyricMode && !isLyricDragMode && lyrics.isNotEmpty() && lyricViewTopOffset > 0f) {
            val density = LocalDensity.current
            // LyricView 内部第2行位置 = contentPadding(8dp) + 第1行高度(30dp) + 第2行高度一半(8dp) = 46dp
            val lyricViewInternalOffset = with(density) { 46.dp.toPx() }
            val totalTopOffsetPx = lyricViewTopOffset + lyricViewInternalOffset
            val totalTopOffsetDp = with(density) { totalTopOffsetPx.toDp() }

            com.minimalistmusic.ui.components.PureLyricTimeLabel(
                centerLyric = centerLyric,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = totalTopOffsetDp, start = 4.dp)  // 贴近屏幕边缘（4dp）
            )
        }
    }
}
