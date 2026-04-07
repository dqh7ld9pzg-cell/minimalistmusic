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

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.HomeViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.components.ListEndIndicator
import com.minimalistmusic.ui.components.SongListItem
import com.minimalistmusic.ui.theme.Spacing
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 我喜欢的音乐页面 (2025-11-19重构)
 *
 * 新版设计（网易云音乐风格）：
 * - 头部区域：左侧最近喜欢的歌曲封面，右侧显示标题、数量、收藏歌单入口
 * - 歌曲列表支持分页加载（每页20条）
 * - 长按进入排序模式
 *
 * 功能：
 * - 显示所有收藏的歌曲列表
 * - 长按进入排序模式（类似QQ音乐）
 * - 排序模式支持拖拽排序
 * - 排序模式支持批量取消喜欢
 * - 排序模式显示拖拽手柄标识
 *
 * 架构：
 * - MVVM模式
 * - 使用HomeViewModel管理收藏歌曲数据
 * - 使用AccountSyncViewModel管理收藏状态
 * - 组件复用SongListItem
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn( ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoriteSongsScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,  // 优化 (2025-11-23): 移除默认值，由调用方传入共享实例，消除闪烁
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    cachedMusicViewModel: CachedMusicViewModel = hiltViewModel(),
) {
    // 状态收集
    val favoriteSongs by homeViewModel.favoriteSongs.collectAsStateWithLifecycle()
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val cacheMap by cachedMusicViewModel.cacheStateMap.collectAsStateWithLifecycle()
    val favoritePlaylists by homeViewModel.favoritePlaylists.collectAsStateWithLifecycle()
    // 播放状态（2025-11-20：用于显示序号和播放动画）
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    // 震动反馈（用于长按进入排序模式）
    val haptic = LocalHapticFeedback.current
    // 排序模式状态
    var isSortMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<Long>()) }
    var showRemoveDialog by remember { mutableStateOf(false) }
    // 可拖拽的歌曲列表（用于排序）
    var sortedSongs by remember(favoriteSongs) {
        mutableStateOf(favoriteSongs.toList())
    }
    // 当收藏列表变化时，更新排序列表
    // 修复 (2025-11-22): 只在非播放状态或播放的不是当前列表时更新,避免播放过程中列表引用变化导致续播失败
    // 修复 (2025-11-22 第二版): 移除 isPlaying 依赖，使用更保守的策略
    // 架构优化 (2025-11-22 第四版): 播放列表完全独立，UI 数据变化不影响播放
    // - MusicService 维护自己的播放列表快照，保证播放稳定性
    // - UI 只负责显示，删除/添加只影响 UI，不影响正在播放的列表
    // - 只有用户再次点击播放时，才用新列表覆盖 MusicService 的播放列表
    // 性能优化 (2025-11-22): 移除 playbackState.isPlaying 依赖，减少不必要的重组
    // - isPlaying 在 LaunchedEffect 内部未被使用
    // - 播放动画依赖 SongListItem 中的 isActuallyPlaying 参数，不受此影响
    LaunchedEffect(favoriteSongs, playbackState.currentSong) {
        val currentSong = playbackState.currentSong
        val isPlayingFromThisList = currentSong != null &&
            sortedSongs.any { it.id == currentSong.id }
        // 安全更新条件：
        // 1. 当前没有歌曲 → 可以更新
        // 2. 播放的不是这个列表 → 可以更新
        // 注意：删除操作不再特殊处理，播放过程中 UI 锁定列表引用
        val isSafeToUpdate = currentSong == null || !isPlayingFromThisList
        if (isSafeToUpdate) {
            sortedSongs = favoriteSongs.toList()
        }
    }
    // 分页状态 (2025-11-19)
    // 优化 (2025-11-23): 统一分页大小为30条，与其他列表页面保持一致
    val pageSize = 30
    var displayCount by remember { mutableStateOf(pageSize) }
    val displaySongs = sortedSongs.take(displayCount)
    val hasMore = displayCount < sortedSongs.size
    // 滚动状态监听 (2025-11-24: 使用reorderable支持拖拽排序)
    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            // 拖拽排序时更新列表顺序
            // 注意：from和to是包含header items的索引，需要减去header数量
            // header包括：顶部留白(1) + 头部卡片(1) + 间距(1) + 全选行(可能1) = 3或4个
            val headerCount = if (isSortMode) 4 else 3
            val fromIndex = from.index - headerCount
            val toIndex = to.index - headerCount
            if (fromIndex >= 0 && toIndex >= 0 && fromIndex < sortedSongs.size && toIndex < sortedSongs.size) {
                sortedSongs = sortedSongs.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        }
    )
    // 自动加载更多
    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        // 当滚动到距离底部5个item时加载更多
        if (hasMore && lastVisibleIndex >= displayCount - 5) {
            displayCount += pageSize
        }
    }
    // 是否全选
    val isAllSelected = selectedSongIds.size == sortedSongs.size && sortedSongs.isNotEmpty()
    // 退出排序模式
    fun exitSortMode() {
        isSortMode = false
        selectedSongIds = emptySet()
    }
    // 2025-11-19：拦截系统返回键，排序模式下退出排序模式而不是返回上一页
    BackHandler(enabled = isSortMode) {
        exitSortMode()
    }
    // 获取最近喜欢的歌曲封面
    val latestSongCover = sortedSongs.firstOrNull()?.albumArt
    // 协程作用域，用于滚动到顶部
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = {
                    Text(
                        text = "我喜欢",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSortMode) {
                            exitSortMode()
                        } else {
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isSortMode) {
                        // 删除按钮
                        IconButton(
                            onClick = { showRemoveDialog = true },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "取消喜欢",
                                tint = if (selectedSongIds.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        // 完成按钮 (2025-11-19：保存排序)
                        TextButton(onClick = {
                            // 保存用户的排序到数据库
                            accountSyncViewModel.updateFavoriteSongsOrder(sortedSongs)
                            exitSortMode()
                        }) {
                            Text("完成")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 列表为空
                sortedSongs.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "还没有喜欢的音乐",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "点击歌曲的爱心图标添加到我喜欢",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                // 显示歌曲列表
                else -> {
                    // 2025-11-24: 添加快速滑动条
                    // 修复 (2025-11-29): 排序模式下禁用LazyColumnScrollbar，避免与拖拽功能冲突
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isSortMode) {
                            // 非排序模式：显示快速滑动条
                            LazyColumnScrollbar(
                                state = listState,
                                settings = ScrollbarSettings(
                                    thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    thumbSelectedColor = MaterialTheme.colorScheme.primary,
                                    thumbMinLength = 0.1f,
                                    selectionMode = ScrollbarSelectionMode.Thumb
                                )
                            ) {
                                FavoriteSongsLazyColumn(
                                    listState = listState,
                                    isSortMode = isSortMode,
                                    reorderableState = reorderableState,
                                    latestSongCover = latestSongCover,
                                    sortedSongs = sortedSongs,
                                    favoritePlaylists = favoritePlaylists,
                                    playerViewModel = playerViewModel,
                                    navController = navController,
                                    isAllSelected = isAllSelected,
                                    selectedSongIds = selectedSongIds,
                                    onSelectAll = { isSelected ->
                                        selectedSongIds = if (isSelected) {
                                            sortedSongs.map { it.id }.toSet()
                                        } else {
                                            emptySet()
                                        }
                                    },
                                    displaySongs = displaySongs,
                                    playbackState = playbackState,
                                    favoriteSongIds = favoriteSongIds,
                                    cacheMap = cacheMap,
                                    onSongClick = { song, index ->
                                        if (isSortMode) {
                                            selectedSongIds = if (selectedSongIds.contains(song.id)) {
                                                selectedSongIds - song.id
                                            } else {
                                                selectedSongIds + song.id
                                            }
                                        } else {
                                            if (!playerViewModel.canPlaySong(song.id)) {
                                                return@FavoriteSongsLazyColumn
                                            }
                                            playerViewModel.playSongs(sortedSongs, sortedSongs.indexOf(song))
                                        }
                                    },
                                    onLongClick = { song ->
                                        if (!isSortMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            isSortMode = true
                                            selectedSongIds = setOf(song.id)
                                        }
                                    },
                                    onFavoriteClick = { song ->
                                        if (!isSortMode) {
                                            accountSyncViewModel.toggleFavorite(song)
                                        }
                                    },
                                    onMoveUp = { index ->
                                        val newList = sortedSongs.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index - 1]
                                        newList[index - 1] = temp
                                        sortedSongs = newList
                                    },
                                    onMoveDown = { index ->
                                        val newList = sortedSongs.toMutableList()
                                        val temp = newList[index]
                                        newList[index] = newList[index + 1]
                                        newList[index + 1] = temp
                                        sortedSongs = newList
                                    },
                                    onCheckboxChange = { song, checked ->
                                        selectedSongIds = if (checked) {
                                            selectedSongIds + song.id
                                        } else {
                                            selectedSongIds - song.id
                                        }
                                    },
                                    hasMore = hasMore,
                                    coroutineScope = coroutineScope
                                )
                            }
                        } else {
                            // 排序模式：直接显示LazyColumn，支持拖拽
                            FavoriteSongsLazyColumn(
                                listState = listState,
                                isSortMode = isSortMode,
                                reorderableState = reorderableState,
                                latestSongCover = latestSongCover,
                                sortedSongs = sortedSongs,
                                favoritePlaylists = favoritePlaylists,
                                playerViewModel = playerViewModel,
                                navController = navController,
                                isAllSelected = isAllSelected,
                                selectedSongIds = selectedSongIds,
                                onSelectAll = { isSelected ->
                                    selectedSongIds = if (isSelected) {
                                        sortedSongs.map { it.id }.toSet()
                                    } else {
                                        emptySet()
                                    }
                                },
                                displaySongs = displaySongs,
                                playbackState = playbackState,
                                favoriteSongIds = favoriteSongIds,
                                cacheMap = cacheMap,
                                onSongClick = { song, index ->
                                    if (isSortMode) {
                                        selectedSongIds = if (selectedSongIds.contains(song.id)) {
                                            selectedSongIds - song.id
                                        } else {
                                            selectedSongIds + song.id
                                        }
                                    } else {
                                        if (!playerViewModel.canPlaySong(song.id)) {
                                            return@FavoriteSongsLazyColumn
                                        }
                                        playerViewModel.playSongs(sortedSongs, sortedSongs.indexOf(song))
                                    }
                                },
                                onLongClick = { song ->
                                    if (!isSortMode) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSortMode = true
                                        selectedSongIds = setOf(song.id)
                                    }
                                },
                                onFavoriteClick = { song ->
                                    if (!isSortMode) {
                                        accountSyncViewModel.toggleFavorite(song)
                                    }
                                },
                                onMoveUp = { index ->
                                    val newList = sortedSongs.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index - 1]
                                    newList[index - 1] = temp
                                    sortedSongs = newList
                                },
                                onMoveDown = { index ->
                                    val newList = sortedSongs.toMutableList()
                                    val temp = newList[index]
                                    newList[index] = newList[index + 1]
                                    newList[index + 1] = temp
                                    sortedSongs = newList
                                },
                                onCheckboxChange = { song, checked ->
                                    selectedSongIds = if (checked) {
                                        selectedSongIds + song.id
                                    } else {
                                        selectedSongIds - song.id
                                    }
                                },
                                hasMore = hasMore,
                                coroutineScope = coroutineScope
                            )
                        }
                    }  // Box结束 (Modifier.fillMaxSize)
                }  // else结束
            }  // when结束
        }  // Box结束 (padding)
        // 批量取消喜欢确认对话框
        if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("取消喜欢") },
            text = {
                Text("确定要取消喜欢已选择的 ${selectedSongIds.size} 首歌曲吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 批量取消喜欢
                        val songsToRemove = sortedSongs.filter { selectedSongIds.contains(it.id) }
                        songsToRemove.forEach { song ->
                            accountSyncViewModel.toggleFavorite(song)
                        }
                        showRemoveDialog = false
                        exitSortMode()
                    }
                ) {
                    Text("取消喜欢", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("取消")
                }
            }
        )
        }
    }  // Scaffold结束
}  // FavoriteSongsScreen函数结束
/**
 * 我喜欢页面的LazyColumn内容（抽取为独立组件以便复用）
 *
 * 修复 (2025-11-29): 将LazyColumn内容抽取为独立组件
 * - 非排序模式：被LazyColumnScrollbar包裹
 * - 排序模式：直接渲染，避免触摸事件冲突
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FavoriteSongsLazyColumn(
    listState: androidx.compose.foundation.lazy.LazyListState,
    isSortMode: Boolean,
    reorderableState: sh.calvin.reorderable.ReorderableLazyListState,
    latestSongCover: String?,
    sortedSongs: List<com.minimalistmusic.domain.model.Song>,
    favoritePlaylists: List<com.minimalistmusic.domain.model.RecommendPlaylist>,
    playerViewModel: PlayerViewModel,
    navController: NavHostController,
    isAllSelected: Boolean,
    selectedSongIds: Set<Long>,
    onSelectAll: (Boolean) -> Unit,
    displaySongs: List<com.minimalistmusic.domain.model.Song>,
    playbackState: com.minimalistmusic.domain.model.PlaybackState,
    favoriteSongIds: Set<Long>,
    cacheMap: Map<Long, Boolean>,
    onSongClick: (com.minimalistmusic.domain.model.Song, Int) -> Unit,
    onLongClick: (com.minimalistmusic.domain.model.Song) -> Unit,
    onFavoriteClick: (com.minimalistmusic.domain.model.Song) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onCheckboxChange: (com.minimalistmusic.domain.model.Song, Boolean) -> Unit,
    hasMore: Boolean,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
) {
    LazyColumn(
        state = listState,
        contentPadding = Spacing.lazyColumnPadding(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Content.medium),
        modifier = Modifier
    ) {
        // 头部区域 (2025-11-19新增：网易云风格)
        // 优化 (2025-11-20): 独立卡片风格，四周圆角，与其他页面风格统一
        // MD3优化 (2025-11-29): 使用contentPadding代替Spacer，与首页保持一致
        // 修复：之前用Spacer(12dp) + verticalArrangement(12dp) = 24dp，远大于首页的12dp
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),  // 四周圆角，独立卡片感
                color = MaterialTheme.colorScheme.surfaceVariant,  // 与首页卡片一致
                tonalElevation = 1.dp  // 轻微色调提升
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                // 左侧封面（包含内部播放按钮）
                // 修复 (2025-11-20): 播放按钮在封面内部底部居中，与已缓存页面保持一致
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    // 占位图标
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = androidx.compose.ui.graphics.Color(0xFFFF0000).copy(alpha = 0.3f)
                        )
                    }
                    // 最近喜欢的歌曲封面
                    if (!latestSongCover.isNullOrEmpty()) {
                        AsyncImage(
                            model = latestSongCover,
                            contentDescription = "封面",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 播放全部按钮（在封面内部底部居中，符合MD3设计）
                    // 优化 (2025-11-20): 使用半透明毛玻璃效果，更轻盈不突兀
                    // MD3优化 (2025-11-21): 调整按钮位置，距底部8dp让按钮更贴近边缘，视觉更平衡
                    if (sortedSongs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)  // 距离底部8dp（MD3推荐8-12dp，选择8dp让按钮更贴近底部）
                                .clip(RoundedCornerShape(20.dp))  // 更圆润的圆角
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)  // 半透明白色/黑色（根据主题）
                                )
                                .clickable {
                                    // 修复 (2025-11-21): 全部播放时检查第一首歌曲
                                    val firstSong = sortedSongs.firstOrNull()
                                    if (firstSong != null && !playerViewModel.canPlaySong(firstSong.id)) {
                                        return@clickable
                                    }
                                    playerViewModel.playSongs(sortedSongs, 0)
//                                                    navController.navigate("player") {
//                                                        launchSingleTop = true
//                                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),  // 稍大的内边距
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)  // 增加图标和文字间距
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),  // 稍大的图标
                                tint = MaterialTheme.colorScheme.primary  // 使用主题色作为图标颜色
                            )
                            Text(
                                text = "播放全部",
                                style = MaterialTheme.typography.labelMedium,  // 使用labelMedium
                                color = MaterialTheme.colorScheme.onSurface,  // 使用surface上的文字颜色
                                fontWeight = FontWeight.Medium  // 中等字重，更清晰
                            )
                        }
                    }
                }
                // 右侧信息区域
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(120.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 标题和数量
                    Column {
                        Text(
                            text = "我喜欢的音乐",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${sortedSongs.size} 首歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    // 收藏歌单入口
                    Row(
                        modifier = Modifier
                            .clickable {
                                // 导航到收藏歌单页面
                                navController.navigate("favorite_playlists")
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.LibraryMusic,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "我收藏的歌单 (${favoritePlaylists.size})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            }
            }  // Surface结束
        }
        // 头部与列表之间的间距
        // MD3优化 (2025-11-29): 移除此Spacer，依靠verticalArrangement(12dp)自然形成间距
        // 原因：SongListItem自带padding(vertical=12dp)，加上verticalArrangement(12dp)和Spacer会导致间距过大
        // 实际效果：头部卡片 → 12dp(verticalArrangement) → 12dp(SongListItem上padding) → 歌曲内容 = 总计24dp视觉间距
        // 排序模式：全选/取消全选按钮（在歌曲列表上方）
        if (isSortMode) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 左侧：全选/取消全选
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            onSelectAll(!isAllSelected)
                        }
                    ) {
                        Checkbox(
                            checked = isAllSelected,
                            onCheckedChange = { onSelectAll(it) }
                        )
                        Text(
                            text = if (isAllSelected) "全部取消" else "全部选中",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    // 右侧：已选择数量提示
                    if (selectedSongIds.isNotEmpty()) {
                        Text(
                            text = "已选择 ${selectedSongIds.size} 首",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        // 歌曲列表 (2025-11-24: 添加ReorderableItem支持拖拽排序)
        // 修复 (2025-11-25): 使用 items(count) 代替 itemsIndexed(list)，避免列表引用变化导致全部重建
        // 原因：itemsIndexed 依赖列表引用，StateFlow 更新时即使内容相同也会触发全部item动画
        // 解决：只依赖 count（Int），通过 key 识别item，与底部菜单播放列表保持一致
        items(
            count = displaySongs.size,
            key = { index -> displaySongs[index].id }
        ) { index ->
            val song = displaySongs[index]
            val isPlaying = playbackState.currentSong?.id == song.id
            ReorderableItem(
                state = reorderableState,
                key = song.id
            ) { isDragging ->
                // sh.calvin.reorderable库：整个item默认可拖拽
                // 长按任意位置即可开始拖拽
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // 排序模式：显示复选框
                if (isSortMode) {
                    Checkbox(
                        checked = selectedSongIds.contains(song.id),
                        onCheckedChange = { onCheckboxChange(song, it) }
                    )
                }
                // 歌曲信息
                SongListItem(
                    song = song,
                    isFavorite = favoriteSongIds.contains(song.id),
                    favoriteDate = song.favoritedAt,
                    showFavoriteButton = !isSortMode,
                    onClick = { onSongClick(song, index) },
                    // 方案2修复：排序模式禁用长按，让ReorderableItem的拖拽生效
                    onLongClick = if (!isSortMode) {
                        { onLongClick(song) }
                    } else {
                        null  // 排序模式下不处理长按，让事件穿透到ReorderableItem
                    },
                    onFavoriteClick = { onFavoriteClick(song) },
                    isCached = cacheMap[song.id] ?: song.isLocal,
                    showIndex = true,
                    index = index + 1,
                    isPlaying = isPlaying,
                    isActuallyPlaying = playbackState.isPlaying,  // 传递实际播放状态
                    modifier = Modifier.weight(1f)
                )
                // 排序模式：显示上下移动按钮和拖拽手柄
                // 2025-11-24 优化：使用标准MD图标，统一图标大小为24dp，符合MD3规范
                if (isSortMode) {
                    // 上移按钮（第一项不显示）
                    if (index > 0) {
                        IconButton(
                            onClick = { onMoveUp(index) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowUp,
                                contentDescription = "上移",
                                modifier = Modifier.size(24.dp),  // 统一图标大小
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                    // 下移按钮（最后一项不显示）
                    if (index < sortedSongs.size - 1) {
                        IconButton(
                            onClick = { onMoveDown(index) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "下移",
                                modifier = Modifier.size(24.dp),  // 统一图标大小
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(40.dp))
                    }
                    // 拖拽手柄标识（三条横杠）
                    // 方案2实现：长按条目任意位置即可拖拽（SongListItem的长按已在排序模式禁用）
                    // 拖拽手柄图标仅作视觉提示，长按手柄或歌曲信息区域都能触发拖拽
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = "长按拖拽排序",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
            }  // ReorderableItem结束
        }
        // 加载更多指示器
        if (hasMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        // 底线提示（仅当没有更多数据且列表非空时显示）
        if (!hasMore && sortedSongs.isNotEmpty()) {
            item {
                ListEndIndicator(
                    itemCount = sortedSongs.size,
                    titleText = "我喜欢",
                    onScrollToTop = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                )
            }
        }
    }  // LazyColumn结束
}  // FavoriteSongsLazyColumn函数结束
