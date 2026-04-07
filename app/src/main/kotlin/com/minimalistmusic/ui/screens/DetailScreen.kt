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
 * 统一详情页面（重构 2025-11-27）
 *
 * 整合原 RecommendPlaylistDetailScreen 和 ArtistDetailScreen
 *
 * 功能：
 * - 显示歌单或歌手的详细信息
 * - 显示歌曲列表
 * - 支持播放、收藏歌曲
 * - 支持分页加载（歌手详情）
 * - 支持收藏歌单/歌手
 *
 * 架构优化：
 * - 统一使用 SearchViewModel 作为数据源
 * - 消除了 PlaylistDetailViewModel 的冗余
 * - 减少了 96% 的重复代码
 *
 * @param navController 导航控制器
 * @param playlistId 歌单ID或歌手ID
 * @param playlistName 歌单名称或歌手名称
 * @param source 来源类型（普通歌单/歌手歌单）
 * @param cover 封面URL
 * @param playCount 播放次数
 * @param description 描述信息
 */
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.minimalistmusic.domain.model.PlaylistSource
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.DetailType
import com.minimalistmusic.presentation.viewmodel.HomeViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import com.minimalistmusic.presentation.viewmodel.SearchViewModel
import com.minimalistmusic.ui.components.EmptyStateView
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.components.ListEndIndicator
import com.minimalistmusic.ui.components.SongListItem
import com.minimalistmusic.ui.components.SyncReminderDialogHost
import com.minimalistmusic.ui.theme.Spacing
import com.minimalistmusic.util.PlayCountCalculator
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    navController: NavHostController,
    playlistId: Long,
    playlistName: String,
    source: PlaylistSource,
    cover: String = "",
    playCount: Long = 0L,
    description: String? = null,
    searchViewModel: SearchViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel = hiltViewModel(),
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
    cachedMusicViewModel: CachedMusicViewModel = hiltViewModel(),
) {
    // UiState 模式 (2025-12-03): 统一状态管理
    val uiState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val cacheStates by cachedMusicViewModel.cacheStateMap.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val favoritePlaylists by homeViewModel.favoritePlaylists.collectAsStateWithLifecycle()

    // 根据详情类型判断是否还有更多数据
    val hasMore = when (uiState.detailType) {
        DetailType.PLAYLIST -> false // 普通歌单不支持分页
        DetailType.ARTIST -> uiState.hasMoreArtistSongs
        null -> false
    }
    val isLoadingMore = when (uiState.detailType) {
        DetailType.PLAYLIST -> false
        DetailType.ARTIST -> uiState.isLoadingMoreArtistSongs
        null -> false
    }
    // 判断当前歌单/歌手是否已收藏
    val isFavorited = when (uiState.detailType) {
        DetailType.PLAYLIST -> {
            uiState.playlistDetail?.let { playlist ->
                favoritePlaylists.any { it.id == playlist.id }
            } ?: false
        }
        DetailType.ARTIST -> {
            uiState.selectedArtist?.let { artist ->
                favoritePlaylists.any { it.id == artist.id }
            } ?: false
        }
        null -> false
    }
    // LazyColumn 滚动状态
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // 图片查看器状态
    var showImageViewer by remember { mutableStateOf(false) }
    // 系统返回键处理
    BackHandler(enabled = showImageViewer) {
        showImageViewer = false
    }
    // 初始化：加载详情数据
    LaunchedEffect(playlistId, source) {
        searchViewModel.loadDetail(
            playlistId = playlistId,
            playlistName = playlistName,
            source = source,
            cover = cover,
            playCount = playCount,
            description = description
        )
    }
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = {
                    Text(
                        text = "歌单/歌手详情",
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
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        // 修复闪烁 (2025-11-27): 判断是否是初始状态（还未开始加载）
        // detailType为null表示还没开始加载，应该显示loading
        val isInitialLoading = uiState.detailType == null || (uiState.isSearching && uiState.songResults.isEmpty())
        if (isInitialLoading) {
            // 加载中状态（包括初始状态）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.songResults.isEmpty() && !uiState.isSearching) {
            // 空状态
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 头部信息（即使为空也显示）
                DetailHeader(
                    currentDetailType = uiState.detailType,
                    playlistDetail = uiState.playlistDetail,
                    selectedArtist = uiState.selectedArtist,
                    songsCount = uiState.songsCount,
                    isFavorited = isFavorited,
                    showImageViewer = showImageViewer,
                    onShowImageViewerChange = { showImageViewer = it },
                    onFavoriteClick = {
                        handleFavoriteClick(
                            currentDetailType = uiState.detailType,
                            playlistDetail = uiState.playlistDetail,
                            selectedArtist = uiState.selectedArtist,
                            source = source,
                            homeViewModel = homeViewModel
                        )
                    },
                    onPlayAll = { /* 没有歌曲，不执行 */ },
                    songs = emptyList(),
                )
                // 空状态提示
                EmptyStateView(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    icon = Icons.Filled.QueueMusic,
                    title = "暂无歌曲数据",
                    subtitle = "点击任意位置刷新",
                    onRefresh = {
                        searchViewModel.loadDetail(
                            playlistId = playlistId,
                            playlistName = playlistName,
                            source = source,
                            cover = cover,
                            playCount = playCount,
                            description = description
                        )
                    }
                )
            }
        } else {
            // 正常显示列表
            LazyColumnScrollbar(
                state = listState,
                settings = ScrollbarSettings(
                    thumbUnselectedColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    thumbSelectedColor = MaterialTheme.colorScheme.primary,
                    thumbMinLength = 0.1f,
                    selectionMode = ScrollbarSelectionMode.Thumb
                )
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+垂直内边距 (2025-11-28)
                    verticalArrangement = Arrangement.spacedBy(Spacing.Content.small)  // MD3规范：8dp歌曲列表间距
                ) {
                    // 头部信息
                    item {
                        DetailHeader(
                            currentDetailType = uiState.detailType,
                            playlistDetail = uiState.playlistDetail,
                            selectedArtist = uiState.selectedArtist,
                            songsCount = uiState.songsCount,
                            isFavorited = isFavorited,
                            showImageViewer = showImageViewer,
                            onShowImageViewerChange = { showImageViewer = it },
                            onFavoriteClick = {
                                handleFavoriteClick(
                                    currentDetailType = uiState.detailType,
                                    playlistDetail = uiState.playlistDetail,
                                    selectedArtist = uiState.selectedArtist,
                                    source = source,
                                    homeViewModel = homeViewModel
                                )
                            },
                            onPlayAll = {
                                val firstSong = uiState.songResults.firstOrNull()
                                if (firstSong != null && !playerViewModel.canPlaySong(firstSong.id)) {
                                    return@DetailHeader
                                }
                                playerViewModel.playSongs(uiState.songResults, 0)
                            },
                            songs = uiState.songResults,
                        )
                    }
                    // 歌曲列表
                    items(uiState.songResults.size) { index ->
                        val song = uiState.songResults[index]
                        val isPlaying = playbackState.currentSong?.id == song.id
                        SongListItem(
                            song = song,
                            isFavorite = favoriteSongIds.contains(song.id),
                            isCached = cacheStates[song.id] ?: song.isLocal,
                            onClick = {
                                if (!playerViewModel.canPlaySong(song.id)) {
                                    return@SongListItem
                                }
                                playerViewModel.playSongs(uiState.songResults, index)
                            },
                            onFavoriteClick = {
                                accountSyncViewModel.toggleFavorite(song)
                            },
                            showIndex = true,
                            index = index + 1,
                            isPlaying = isPlaying,
                            isActuallyPlaying = playbackState.isPlaying
                        )
                    }
                    // 加载更多按钮
                    if (hasMore && uiState.songResults.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp)
                                    )
                                } else {
                                    androidx.compose.material3.TextButton(
                                        onClick = { searchViewModel.loadMoreDetailSongs() }
                                    ) {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }
                    // 底线提示
                    if (!hasMore && uiState.songResults.isNotEmpty()) {
                        item {
                            ListEndIndicator(
                                itemCount = uiState.songResults.size,
                                titleText = when (uiState.detailType) {
                                    DetailType.PLAYLIST -> "歌单详情"
                                    DetailType.ARTIST -> "歌手详情"
                                    null -> "详情"
                                },
                                onScrollToTop = {
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(0)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    // 同步提醒对话框
    SyncReminderDialogHost(
        accountSyncViewModel = accountSyncViewModel,
        navController = navController
    )
    // 图片查看器
    if (showImageViewer) {
        val imageUrl = when (uiState.detailType) {
            DetailType.PLAYLIST -> uiState.playlistDetail?.cover
            DetailType.ARTIST -> uiState.selectedArtist?.avatar
            null -> null
        }
        val imageName = when (uiState.detailType) {
            DetailType.PLAYLIST -> "${uiState.playlistDetail?.name}_封面"
            DetailType.ARTIST -> "${uiState.selectedArtist?.name}_头像"
            null -> "图片"
        }
        if (!imageUrl.isNullOrBlank()) {
            com.minimalistmusic.ui.components.ImageViewer(
                imageUrl = imageUrl,
                imageName = imageName,
                onDismiss = { showImageViewer = false }
            )
        }
    }
}
/**
 * 详情页头部组件
 */
@Composable
private fun DetailHeader(
    currentDetailType: DetailType?,
    playlistDetail: com.minimalistmusic.domain.model.RecommendPlaylist?,
    selectedArtist: com.minimalistmusic.domain.model.SearchResult.ArtistResult?,
    songsCount: Int,
    isFavorited: Boolean,
    showImageViewer: Boolean,
    onShowImageViewerChange: (Boolean) -> Unit,
    onFavoriteClick: () -> Unit,
    onPlayAll: () -> Unit,
    songs: List<com.minimalistmusic.domain.model.Song>,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 封面/头像
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            enabled = when (currentDetailType) {
                                DetailType.PLAYLIST -> !playlistDetail?.cover.isNullOrBlank()
                                DetailType.ARTIST -> !selectedArtist?.avatar.isNullOrEmpty()
                                null -> false
                            },
                            onClick = { onShowImageViewerChange(true) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 占位图标
                    Icon(
                        imageVector = when (currentDetailType) {
                            DetailType.PLAYLIST -> Icons.Filled.QueueMusic
                            DetailType.ARTIST -> Icons.Filled.Person
                            null -> Icons.Filled.QueueMusic
                        },
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    // 实际封面/头像
                    val coverUrl = when (currentDetailType) {
                        DetailType.PLAYLIST -> playlistDetail?.cover
                        DetailType.ARTIST -> selectedArtist?.avatar
                        null -> null
                    }
                    if (!coverUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    // 播放全部按钮
                    if (songs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                                )
                                .clickable(onClick = onPlayAll)
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "播放全部",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                // 信息栏
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 名称
                    Text(
                        text = when (currentDetailType) {
                            DetailType.PLAYLIST -> playlistDetail?.name ?: "未知歌单"
                            DetailType.ARTIST -> selectedArtist?.name ?: "未知歌手"
                            null -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    // 歌曲数量和播放次数
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$songsCount 首歌曲",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        // 优化 (2025-11-27): 使用remember缓存播放次数计算结果
                        // 避免每次recompose时重复计算
                        val estimatedPlayCount = remember(currentDetailType, playlistDetail, selectedArtist) {
                            when (currentDetailType) {
                                DetailType.PLAYLIST -> playlistDetail?.playCount ?: 0L
                                DetailType.ARTIST -> selectedArtist?.let {
                                    PlayCountCalculator.estimateArtistPlayCount(
                                        albumSize = it.albumSize,
                                        musicSize = it.musicSize
                                    )
                                } ?: 0L
                                null -> 0L
                            }
                        }
                        if (estimatedPlayCount > 0) {
                            Text(
                                text = PlayCountCalculator.formatPlayCount(estimatedPlayCount),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                    // 描述信息/别名
                    when (currentDetailType) {
                        DetailType.PLAYLIST -> {
                            if (!playlistDetail?.description.isNullOrBlank()) {
                                Text(
                                    text = playlistDetail?.description ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        DetailType.ARTIST -> {
                            if (!selectedArtist?.alias.isNullOrEmpty()) {
                                Text(
                                    text = selectedArtist?.alias?.joinToString(", ") ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        null -> {}
                    }
                    // 收藏按钮（带动画）
                    var previousFavorited by remember { mutableStateOf(isFavorited) }
                    var shouldAnimate by remember { mutableStateOf(false) }
                    LaunchedEffect(isFavorited) {
                        val justFavorited = previousFavorited == false && isFavorited
                        shouldAnimate = justFavorited
                        previousFavorited = isFavorited
                        if (justFavorited) {
                            kotlinx.coroutines.delay(400)
                            shouldAnimate = false
                        }
                    }
                    val heartScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (shouldAnimate) 1.5f else 1.0f,
                        animationSpec = if (shouldAnimate) {
                            androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            )
                        } else {
                            androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessHigh)
                        },
                        label = "heartScale"
                    )
                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorited) "取消收藏" else "收藏",
                            tint = if (isFavorited) androidx.compose.ui.graphics.Color(0xFFFF0000) else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.graphicsLayer {
                                scaleX = heartScale
                                scaleY = heartScale
                            }
                        )
                    }
                }
            }
        }
    }
}
/**
 * 处理收藏点击
 */
private fun handleFavoriteClick(
    currentDetailType: DetailType?,
    playlistDetail: com.minimalistmusic.domain.model.RecommendPlaylist?,
    selectedArtist: com.minimalistmusic.domain.model.SearchResult.ArtistResult?,
    source: PlaylistSource,
    homeViewModel: HomeViewModel,
) {
    when (currentDetailType) {
        DetailType.PLAYLIST -> {
            playlistDetail?.let { playlist ->
                homeViewModel.toggleFavoritePlaylist(playlist)
            }
        }
        DetailType.ARTIST -> {
            selectedArtist?.let { artist ->
                val artistPlaylist = com.minimalistmusic.domain.model.RecommendPlaylist(
                    id = artist.id,
                    name = artist.name,
                    cover = artist.avatar ?: "",
                    playCount = (artist.albumSize * 10 + artist.musicSize * 5).toLong() * 10000000L,
                    description = "歌手：${artist.name}" + if (artist.alias.isNotEmpty()) " (${artist.alias.joinToString(", ")})" else "",
                    source = PlaylistSource.ARTIST_PLAYLIST,
                    artistId = artist.id
                )
                homeViewModel.toggleFavoritePlaylist(artistPlaylist)
            }
        }
        null -> {}
    }
}
