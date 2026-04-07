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

/**
 * 首页模块
 *
 * 职责：
 * - 展示用户音乐库概览（本地音乐、我喜欢）
 * - 展示最近播放记录（最多5首）
 * - 展示推荐歌单（2列网格布局）
 * - 提供快速导航入口
 *
 * 架构特点：
 * - MVVM架构：通过ViewModel管理UI状态
 * - 单向数据流：状态向下传递，事件向上传递
 * - 组件复用：使用通用SongListItem组件
 * - 状态驱动：根据isScanning/isLoading/errorMessage动态显示UI
 *
 * 性能优化：
 * - 使用LazyColumn实现列表懒加载
 * - 状态收集使用collectAsState避免不必要重组
 * - 图片加载使用Coil异步加载和缓存
 *
 * @since 2025-11-11
 */
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.minimalistmusic.domain.model.RecommendPlaylist
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.HomeViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.components.ListEndIndicator
import com.minimalistmusic.ui.components.SongListItem
import com.minimalistmusic.ui.components.SyncReminderDialogHost
import com.minimalistmusic.ui.theme.Spacing
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * 首页屏幕组件
 *
 * 作为应用的主入口，展示用户音乐库的核心内容和快速访问入口。
 *
 * UI结构：
 * ```
 * TopAppBar (标题 + 刷新 + 搜索)
 * ├── 我的音乐
 * │   ├── 本地音乐卡片 (显示歌曲数量)
 * │   └── 我喜欢卡片 (显示收藏数量)
 * ├── 最近播放 (最多5首)
 * │   └── 歌曲列表 (使用SongListItem组件)
 * └── 推荐歌单 (2列网格)
 *     └── 歌单卡片
 * ```
 *
 * 状态管理：
 * - [localSongs]: 本地扫描的音乐列表
 * - [favoriteSongs]: 收藏的音乐列表
 * - [playHistory]: 播放历史（倒序）
 * - [recommendPlaylists]: API获取的推荐歌单
 * - [isScanning]: 本地音乐扫描状态
 * - [errorMessage]: 错误信息（自动2秒后消失）
 *
 * 交互流程：
 * 1. 点击"本地音乐"/"我喜欢" → 导航到对应列表页
 * 2. 点击"最近播放"歌曲 → 播放歌曲并导航到播放器
 * 3. 点击收藏按钮 → 触发同步提醒对话框（未登录时）
 * 4. 点击推荐歌单 → 导航到歌单详情页
 *
 * @param navController 导航控制器
 * @param homeViewModel 首页ViewModel，管理本地音乐和推荐内容
 * @param playerViewModel 播放器ViewModel，控制音乐播放
 * @param accountSyncViewModel 账号同步ViewModel，管理收藏状态
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,  // 优化 (2025-11-23): 移除默认值，由调用方传入共享实例
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    cachedMusicViewModel: CachedMusicViewModel = hiltViewModel(),
) {
    // ========== 状态收集 ==========
    // 使用collectAsState()确保状态变化时自动重组UI
    val favoriteSongs by homeViewModel.favoriteSongs.collectAsStateWithLifecycle()
    val favoritePlaylists by homeViewModel.favoritePlaylists.collectAsStateWithLifecycle()
    val recommendPlaylists by homeViewModel.recommendPlaylists.collectAsStateWithLifecycle()
    // ========== 分页加载播放历史 (2025-11-19 重构) ==========
    // 使用 ViewModel 管理状态，确保页面切换时数据不丢失
    val playHistory by homeViewModel.pagedPlayHistory.collectAsStateWithLifecycle()  // 分页显示用
    val isLoadingHistory by homeViewModel.isLoadingHistory.collectAsStateWithLifecycle()
    val hasMoreHistory by homeViewModel.hasMoreHistory.collectAsStateWithLifecycle()
    val playHistoryCount by homeViewModel.playHistoryCount.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()

    // 修复 (2025-11-22): 手动管理播放列表，避免播放过程中列表引用变化导致续播失败
    val allPlayHistoryFlow by homeViewModel.playHistory.collectAsStateWithLifecycle()
    var allPlayHistory by remember { mutableStateOf(allPlayHistoryFlow) }

    // 只在安全的时候更新播放列表
    // 修复 (2025-11-22 第二版): 更加保守的更新策略
    // 架构优化 (2025-11-22 第四版): 播放列表完全独立，UI 数据变化不影响播放
    // - MusicService 维护自己的播放列表快照，保证播放稳定性
    // - UI 只负责显示，删除/添加只影响 UI，不影响正在播放的列表
    // - 只有用户再次点击播放时，才用新列表覆盖 MusicService 的播放列表

    LaunchedEffect(allPlayHistoryFlow, playbackState.currentSong, playbackState.isPlaying) {
        val currentSong = playbackState.currentSong
        val isPlayingFromThisList = currentSong != null &&
            allPlayHistory.any { it.id == currentSong.id }
        // 安全更新条件：
        // 1. 当前没有歌曲 → 可以更新
        // 2. 播放的不是这个列表 → 可以更新
        // 注意：删除操作不再特殊处理，播放过程中 UI 锁定列表引用
        val isSafeToUpdate = currentSong == null || !isPlayingFromThisList
        if (isSafeToUpdate) {
            allPlayHistory = allPlayHistoryFlow
        }
    }
    // 长按删除对话框状态 (2025-11-20)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var songToDelete by remember { mutableStateOf<Song?>(null) }
    // LazyColumn 状态，用于监听滚动位置
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 动态标题：当"播放记录"标题滑出可视区域时显示"播放记录"
    val showRecentPlayTitle by remember {
        derivedStateOf {
            // 检查第一个可见项是否超过了"我的音乐"区域（索引1是"播放记录"标题）
            listState.firstVisibleItemIndex >= 2
        }
    }
    // 2025-11-20: 更名为"聆听足迹"，更有画面感符合产品调性
    // 初始加载 - 只在首次创建时加载，后续返回页面不重新加载
    LaunchedEffect(Unit) {
        if (playHistory.isEmpty() && !isLoadingHistory) {
            homeViewModel.loadMorePlayHistory()
        }
    }
    val context = LocalContext.current
    // P0重构：收集收藏歌曲ID列表，用于判断歌曲是否被收藏
    // 避免在SongListItem组件中直接注入AccountSyncViewModel
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    // 缓存状态管理 (架构优化 2025-11-15):
    // - 收集全局缓存状态Map，当缓存状态变化时自动重组UI
    // - cachedSongCount 从 CacheStateManager 派生，自动同步
    // - 移除了手动刷新逻辑，CachedMusicViewModel 会自动监听缓存完成事件
    val cachedSongCount by cachedMusicViewModel.cachedSongCount.collectAsStateWithLifecycle()
    val cacheStates by cachedMusicViewModel.cacheStateMap.collectAsStateWithLifecycle()
    // LaunchedEffect(cachedSongs.size) 已移除 (2025-11-15)
    // 原因：CachedMusicViewModel 已经在 init 中监听 cacheCompletedEvents，会自动刷新
    // 这个 LaunchedEffect 会造成循环触发（loadCachedSongs() → cachedSongs 变化 → 再次触发）
    // 协程作用域，用于双击标题栏滚动到顶部 (2025-11-19)
    val titleCoroutineScope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = {
                    // 动态标题：根据滚动位置切换 (2025-11-19)
                    // 支持双击返回页面顶部 (2025-11-19)
                    // 优化 (2025-11-20): 聆听足迹标题样式与列表保持一致
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    // 双击标题栏，快速滚动到页面顶部
                                    titleCoroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                },
                                onTap = {
                                    titleCoroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            )
                        }
                    ) {
                        if (showRecentPlayTitle) {
                            Text(
                                text = "聆听足迹",
                                fontWeight = FontWeight.Bold
                            )
                            if (playHistoryCount > 0) {
                                Text(
                                    text = "($playHistoryCount)",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            Text(
                                text = "极简音乐",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                actions = {
                    // 搜索按钮
                    IconButton(onClick = { navController.navigate("search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            // 2025-11-24: 添加快速滑动条
            LazyColumnScrollbar(
                state = listState,
                settings = ScrollbarSettings(
                    thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    thumbSelectedColor = MaterialTheme.colorScheme.primary,
                    thumbMinLength = 0.1f,  // 修复 (2025-11-25): 使用thumbMinLength代替thumbMaxLength，固定滑动条最小长度
                    selectionMode = ScrollbarSelectionMode.Thumb
                )
            ) {
            LazyColumn(
                state = listState,  // 添加状态监听 (2025-11-19)
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+垂直内边距 (2025-11-28)
                verticalArrangement = Arrangement.spacedBy(Spacing.Content.medium)  // MD3规范：12dp模块间距
            ) {
                // 我的音乐
                item {
                    Column {
                        MyMusicCards(
                            favoriteSongsCount = favoriteSongs.size,
                            onCachedMusicClick = {
                                // 2025-11-14：导航到"已缓存音乐"页面
                                navController.navigate("cached_music")
                            },
                            onFavoriteClick = {
                                navController.navigate("favorites") {
                                    launchSingleTop = true
                                }
                            },
                            cachedSongsCount = cachedSongCount
                        )
                    }
                }
                // 聆听足迹标题 (2025-11-19重构：移除stickyHeader，使用动态TopAppBar标题)
                // (2025-11-20优化: 更名为"聆听足迹"，数量使用次要样式，符合MD3设计规范)
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "聆听足迹",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (playHistoryCount > 0) {
                                Text(
                                    text = "($playHistoryCount)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                if (playHistory.isEmpty()) {
                    // 空状态提示
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "暂无聆听记录",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "开始播放音乐后会在这里显示",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                    }
                } else {
                    // 分页显示播放历史 (2025-11-19)
                    // 修复 (2025-11-25): 添加 key 参数，基于歌曲ID识别item，与底部菜单播放列表保持一致
                    // 优势：删除/添加时只动画变化的item，而不是全部重建
                    items(
                        count = playHistory.size,
                        key = { index -> playHistory[index].id }
                    ) { index ->
                        val song = playHistory[index]
                        val isPlaying = playbackState.currentSong?.id == song.id
                        SongListItem(
                            song = song,
                            isFavorite = favoriteSongIds.contains(song.id),
                            isCached = cacheStates[song.id] ?: song.isLocal,
                            lastPlayedTime = song.addedAt,
                            onClick = {
                                // 修复 (2025-11-21): 避免重复点击正在播放的歌曲
                                if (!playerViewModel.canPlaySong(song.id)) {
                                    return@SongListItem
                                }
                                // 使用全部播放历史进行播放，确保播放列表完整
                                // 根据歌曲ID找到在全部列表中的正确索引
                                val fullIndex = allPlayHistory.indexOfFirst { it.id == song.id }
                                if (fullIndex >= 0) {
                                    playerViewModel.playSongs(allPlayHistory, fullIndex)
                                } else {
                                    // 回退：如果找不到（理论上不应该发生），使用分页列表
                                    playerViewModel.playSongs(playHistory, index)
                                }
//                                navController.navigate("player") {
//                                    launchSingleTop = true
//                                }
                            },
                            onFavoriteClick = {
                                accountSyncViewModel.toggleFavorite(song)
                            },
                            onLongClick = {
                                // 长按删除播放记录 (2025-11-20)
                                // 修复 (2025-11-22): 正在播放的歌曲不允许删除
                                if (isPlaying && playbackState.isPlaying) {
                                    Toast.makeText(context, "歌曲正在播放中，无法删除播放记录", Toast.LENGTH_SHORT).show()
                                } else {
                                    songToDelete = song
                                    showDeleteDialog = true
                                }
                            },
                            showIndex = true,
                            index = index + 1,
                            isPlaying = isPlaying,
                            isActuallyPlaying = playbackState.isPlaying  // 传递实际播放状态
                        )
                        // 滚动到倒数第3个时触发加载更多
                        if (index == playHistory.size - 3 && hasMoreHistory && !isLoadingHistory) {
                            LaunchedEffect(index) {
                                homeViewModel.loadMorePlayHistory()
                            }
                        }
                    }
                    // 加载指示器
                    if (isLoadingHistory) {
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
                    if (!hasMoreHistory && playHistory.isNotEmpty()) {
                        item {
                            ListEndIndicator(
                                itemCount = playHistoryCount,
                                titleText = "聆听足迹",
                                onScrollToTop = {
                                    titleCoroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            )
                        }
                    }
                }
                // 推荐歌单（如果API可用）
                if (recommendPlaylists.isNotEmpty()) {
                    item {
                        SectionHeader(title = "推荐歌单")
                    }
                    items(recommendPlaylists.chunked(2)) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            row.forEach { playlist ->
                                val isFavorite = favoritePlaylists.any { it.id == playlist.id }
                                PlaylistCard(
                                    playlist = playlist,
                                    modifier = Modifier.weight(1f),
                                    isFavorite = isFavorite,
                                    onClick = {
                                        // 所有歌单（包括歌手类型）都进入歌单详情页
                                        val sourceParam = if (playlist.source == com.minimalistmusic.domain.model.PlaylistSource.ARTIST_PLAYLIST) "artist" else "playlist"
                                        val encodedName = java.net.URLEncoder.encode(playlist.name, "UTF-8")
                                        val encodedCover = java.net.URLEncoder.encode(playlist.cover, "UTF-8")
                                        val encodedDesc = playlist.description?.let { java.net.URLEncoder.encode(it, "UTF-8") } ?: "null"
                                        navController.navigate("playlist_detail/${playlist.id}/$encodedName/$sourceParam/$encodedCover/${playlist.playCount}/$encodedDesc")
                                    },
                                    onFavoriteClick = {
                                        homeViewModel.toggleFavoritePlaylist(playlist)
                                    }
                                )
                            }
                            if (row.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            }  // LazyColumnScrollbar结束
        }
    }
    // 旧的错误提示逻辑已移除 (2025-11-15)
    // 现在统一使用BaseViewModel的errorMessage (SharedFlow) + Toast显示
    // 同步提醒对话框 - 使用简化版本
    SyncReminderDialogHost(
        accountSyncViewModel = accountSyncViewModel,
        navController = navController
    )
    // 删除播放记录确认对话框 (2025-11-20)
    if (showDeleteDialog && songToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                songToDelete = null
            },
            title = {
                Text("删除播放记录")
            },
            text = {
                Text("确定要删除「${songToDelete?.title}」的播放记录吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        songToDelete?.let { song ->
                            homeViewModel.deletePlayHistoryBySongId(song.id)
                            // 架构优化 (2025-11-22): 不同步更新 MusicService 播放列表
                            // MusicService 维护独立的播放列表快照，删除只影响 UI
                            // 播放列表在用户再次点击播放时才会更新
                        }
                        showDeleteDialog = false
                        songToDelete = null
                    }
                ) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        songToDelete = null
                    }
                ) {
                    Text("取消")
                }
            }
        )
    }
}
/**
 * 章节标题组件
 *
 * 用于展示首页各个模块的标题（如"我的音乐"、"最近播放"）。
 * 支持可选的右侧操作按钮（如"查看更多"、"全部"）。
 *
 * UI布局：
 * ```
 * [标题文字]           [操作按钮(可选)]
 * ```
 *
 * 使用场景：
 * - "我的音乐" - 有"全部"按钮（仅当有本地音乐时）
 * - "最近播放" - 有"查看更多"按钮
 * - "推荐歌单" - 无操作按钮
 *
 * @param title 标题文字
 * @param actionText 操作按钮文字（null时不显示按钮）
 * @param onActionClick 操作按钮点击回调
 */
@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onActionClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (actionText != null) {
            TextButton(onClick = onActionClick) {
                Text(actionText)
            }
        }
    }
}
/**
 * 我的音乐卡片组
 *
 * 展示两张并排的卡片：本地音乐和我喜欢。
 * 每张卡片显示对应的歌曲数量，点击可导航到详情页。
 *
 * UI布局（1:1权重分配）：
 * ```
 * ┌───────────┬───────────┐
 * │ 本地音乐   │ 我喜欢    │
 * │ 图标      │ 图标      │
 * │ 标题      │ 标题      │
 * │ XX 首     │ XX 首     │
 * └───────────┴───────────┘
 * ```
 *
 * 设计细节：
 * - 使用weight(1f)确保两张卡片等宽
 * - 图标使用primary主题色
 * - 卡片高度固定100dp
 * - 12dp间距
 *
 * @param localSongsCount 本地音乐数量
 * @param favoriteSongsCount 收藏音乐数量
 * @param onCachedMusicClick 本地音乐卡片点击回调
 * @param onFavoriteClick 我喜欢卡片点击回调
 */
@Composable
fun MyMusicCards(
    favoriteSongsCount: Int,
    cachedSongsCount: Int,
    onCachedMusicClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SimpleCard(
            title = "我喜欢",
            count = favoriteSongsCount,
            icon = Icons.Filled.Favorite,
            onClick = onFavoriteClick,
            modifier = Modifier.weight(1f),
            iconTint = MaterialTheme.colorScheme.primary//Color(0xFFFF0000)  // 鲜红色
        )
        SimpleCard(
            title = "已缓存",
            count = cachedSongsCount,
            icon = Icons.Filled.MusicNote,
            onClick = onCachedMusicClick,
            modifier = Modifier.weight(1f)
        )
    }
}
/**
 * 简单卡片组件
 *
 * 通用的音乐统计卡片，显示图标、标题和数量。
 * 用于首页"我的音乐"模块中的本地音乐和我喜欢卡片。
 *
 * UI结构（从上到下）：
 * ```
 * ┌─────────┐
 * │ 🎵      │  ← 图标（28dp）
 * │         │
 * │ 本地音乐 │  ← 标题
 * │ 100 首  │  ← 数量
 * └─────────┘
 * ```
 *
 * 视觉设计：
 * - 圆角：12dp
 * - 高度：固定100dp
 * - 背景：surfaceVariant主题色
 * - 图标颜色：primary主题色
 * - 内边距：16dp
 *
 * 交互：
 * - 点击整张卡片触发onClick回调
 * - 无点击涟漪效果（使用clickable而非Button）
 *
 * @param title 卡片标题（如"本地音乐"）
 * @param count 歌曲数量
 * @param icon Material Icons图标
 * @param onClick 点击回调
 * @param modifier 修饰符（通常传入weight(1f)）
 */
@Composable
fun SimpleCard(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = MaterialTheme.colorScheme.primary,  // 图标颜色，默认为主题色
) {
    Surface(
        modifier = modifier
            .height(100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = iconTint
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "$count 首",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
// P0重构说明（2025-11-11）：
// SongListItem 已移至 components/SongListItem.kt
// - 消除了与 SearchScreen 和 RecommendPlaylistDetailScreen 的代码重复
// - 解耦了 ViewModel 依赖，提升了可测试性和可复用性
/**
 * 推荐歌单卡片组件
 *
 * 展示单个推荐歌单的封面、名称和播放次数。
 * 在首页以2列网格形式展示，使用chunked(2)实现。
 *
 * UI结构（从上到下）：
 * ```
 * ┌─────────────┐
 * │             │
 * │   封面图片   │  ← 1:1宽高比，12dp圆角
 * │             │
 * ├─────────────┤
 * │ 歌单名称     │  ← 最多2行，超出省略
 * │ 100万+次播放 │  ← 播放次数（格式化）
 * └─────────────┘
 * ```
 *
 * 播放次数格式化规则：
 * - >= 1亿：显示"X亿+次播放"
 * - >= 1万：显示"X万+次播放"
 * - > 0：显示"X次播放"
 * - = 0：不显示
 *
 * 性能优化：
 * - 使用Coil的AsyncImage异步加载图片
 * - 图片自动缓存（内存+磁盘）
 * - 使用aspectRatio确保封面不变形
 *
 * @param playlist 推荐歌单数据模型
 * @param onClick 点击回调（导航到歌单详情页）
 * @param modifier 修饰符（通常传入weight(1f)用于网格布局）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistCard(
    playlist: RecommendPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null,
    networkRefreshTrigger: Long = 0L,  // 网络恢复时触发图片重新加载
) {
    Column(
        modifier = if (onLongClick != null) {
            modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
        } else {
            modifier.clickable(onClick = onClick)
        }
    ) {
        // 封面图片，添加占位符和错误处理 (2025-11-17优化)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 使用SubcomposeAsyncImage替代AsyncImage，支持加载状态
            // 2025-11-20: 使用 networkRefreshTrigger 作为 key，网络恢复时触发重新加载
            // 2025-11-28: 优化图片加载，减少闪烁
            coil.compose.SubcomposeAsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(playlist.cover)
                    .crossfade(0)  // 缩短淡入时间，减少闪烁感（原500ms）
                    .memoryCachePolicy(coil.request.CachePolicy.ENABLED)  // 强制启用内存缓存
                    .diskCachePolicy(coil.request.CachePolicy.ENABLED)    // 强制启用磁盘缓存
                    .memoryCacheKey("${playlist.cover}?para=600y600")
                    .diskCacheKey("${playlist.cover}?para=600y600")
                    .setParameter("refresh_trigger", networkRefreshTrigger)  // 网络恢复时触发重新加载
                    .build(),
                contentDescription = playlist.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            ) {
                val state = painter.state
                when {
                    state is coil.compose.AsyncImagePainter.State.Loading -> {
                        // 优化：Loading状态下也尝试显示图片（如果有缓存会立即显示）
                        // 避免占位图闪烁
                        androidx.compose.foundation.Image(
                            painter = painter,
                            contentDescription = playlist.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    state is coil.compose.AsyncImagePainter.State.Error -> {
                        // 加载失败：显示占位图标
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }
                    state is coil.compose.AsyncImagePainter.State.Success -> {
                        // 加载成功：显示图片
                        androidx.compose.foundation.Image(
                            painter = painter,
                            contentDescription = playlist.name,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            // 描述信息显示在封面图片右下角 (2025-11-16)
            if (!playlist.description.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .background(
                            Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = playlist.description ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 歌单名称、播放次数和收藏按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // 左侧：歌单名称和播放次数
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 歌单名称
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // 播放次数
                if (playlist.playCount > 0) {
                    val playCount = playlist.playCount
                    Text(
                        when {
                            playCount >= 100000000 -> "${playCount / 100000000}亿+次播放"
                            playCount >= 10000 -> "${playCount / 10000}万+次播放"
                            playCount > 0 -> "${playCount}次播放"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            // 右侧：收藏图标（垂直居中对齐，显示在名称和播放次数之间）
            // 2025-11-25：添加爱心收藏动画
            // 修复 (2025-11-25): 使用当前isFavorite作为初始值,避免数据异步加载触发动画
            if (onFavoriteClick != null) {
                var previousPlaylistCardFavorite by androidx.compose.runtime.remember(playlist.id) { androidx.compose.runtime.mutableStateOf(isFavorite) }
                var shouldAnimatePlaylistCardFavorite by androidx.compose.runtime.remember(playlist.id) { androidx.compose.runtime.mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(isFavorite) {
                    val justFavorited = previousPlaylistCardFavorite == false && isFavorite
                    shouldAnimatePlaylistCardFavorite = justFavorited
                    previousPlaylistCardFavorite = isFavorite
                    if (justFavorited) {
                        kotlinx.coroutines.delay(400)
                        shouldAnimatePlaylistCardFavorite = false
                    }
                }
                val playlistCardHeartScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (shouldAnimatePlaylistCardFavorite) 1.5f else 1.0f,
                    animationSpec = if (shouldAnimatePlaylistCardFavorite) {
                        androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                    } else {
                        androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
                    },
                    label = "playlistCardHeartScale"
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .size(32.dp)
                        .padding(top = 8.dp)
                ) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = playlistCardHeartScale
                                scaleY = playlistCardHeartScale
                            }
                    )
                }
            }
        }
    }
}