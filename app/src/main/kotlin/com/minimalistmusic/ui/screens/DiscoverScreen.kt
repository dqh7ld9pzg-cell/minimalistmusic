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

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.presentation.viewmodel.DiscoverViewModel
import com.minimalistmusic.presentation.viewmodel.HomeViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.theme.Spacing

/**
 * 发现页面
 *
 * 功能：
 * - 显示推荐歌单（使用免费API）
 * - 点击歌单查看详情
 * - 刷新推荐内容
 *
 * 重构说明（2025-11-11）：
 * - 从 DiscoverAndProfileScreens.kt (675行) 中拆分
 * - 包含 DiscoverScreen 和 PlaylistDetailSheet（专用底部抽屉）
 * - 提升文件可维护性和可读性
 */
/**
 * 发现页
 *
 * @param navController 导航控制器
 * @param refreshTrigger 刷新触发器，当值变化时刷新歌单（用于双击导航栏刷新）
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    refreshTrigger: Long = 0L,
    discoverViewModel: DiscoverViewModel = hiltViewModel(),
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    val recommendPlaylists by discoverViewModel.recommendPlaylists.collectAsStateWithLifecycle()
    val playlistSongs by discoverViewModel.playlistSongs.collectAsStateWithLifecycle()
    val isLoading by discoverViewModel.isLoading.collectAsStateWithLifecycle()
    val selectedPlaylist by discoverViewModel.selectedPlaylist.collectAsStateWithLifecycle()
    // 获取收藏歌单列表，用于判断是否已收藏
    val favoritePlaylists by homeViewModel.favoritePlaylists.collectAsStateWithLifecycle()
    // 双击刷新监听已统一迁移到 PullToRefreshState 处理 (2025-11-29 修复)
    // 刷新状态管理 (2025-11-29: 使用新的 PullToRefreshBox API)
    var isPullRefreshing by rememberSaveable { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    // 处理双击Tab刷新逻辑 (同步到新的API)
    var lastRefreshTrigger by rememberSaveable { mutableStateOf(refreshTrigger) }
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0 && refreshTrigger != lastRefreshTrigger) {
            // 开始下拉刷新（通过状态触发）
            isPullRefreshing = true
            discoverViewModel.loadRecommendPlaylists(isForceRefresh = true)
            lastRefreshTrigger = refreshTrigger
        }
    }
    // 监听 ViewModel 加载状态，同步结束下拉刷新
    LaunchedEffect(isLoading) {
        if (!isLoading && isPullRefreshing) {
            // 延迟一点时间让用户看到完成状态
            kotlinx.coroutines.delay(200)
            isPullRefreshing = false
        }
    }
    // 长按收藏对话框状态
    var showCollectDialog by remember { mutableStateOf(false) }
    var playlistToCollect by remember {
        mutableStateOf<com.minimalistmusic.domain.model.RecommendPlaylist?>(
            null
        )
    }
    // 刷新按钮旋转动画状态
    var refreshRotation by remember { mutableStateOf(0f) }
    val animatedRotation by animateFloatAsState(
        targetValue = refreshRotation,
        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
        label = "refreshRotation"
    )
    // 网络状态监听：当网络恢复时重新加载失败的图片
    val context = LocalContext.current
    var networkRefreshTrigger by remember { mutableStateOf(0L) }
    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 网络恢复，触发图片重新加载
                networkRefreshTrigger = System.currentTimeMillis()
            }
        }
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
    // 移除Snackbar提示（2025-11-17）
    // SwipeRefresh组件自带下拉刷新提示，无需额外提醒
    // 移除LaunchedEffect：ViewModel的init中已自动加载
    // 用户切换导航或返回时不会重新加载
    // 下拉刷新状态 (2025-11-29: 升级到 Material3 1.3.1，使用新的 PullToRefreshBox API)
    // 新 API：更方便的状态管理，修复老版本可见性和动画问题
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = { Text("推荐歌单", fontWeight = FontWeight.Bold) },
                actions = {
                    // 搜索按钮
                    IconButton(onClick = { navController.navigate("search") }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索")
                    }
                }
            )
        }
    ) { paddingValues ->
        // 2025-11-29: 使用新的 PullToRefreshBox API，自动处理嵌套滚动和动画
        // 修复老版本 Material3 1.1.2 的可见性和动画问题
        // 样式优化：自定义指示器颜色，使用应用主题紫色匹配整体设计风格
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = isPullRefreshing,
            onRefresh = {
                // 开始下拉刷新的回调
                isPullRefreshing = true
                discoverViewModel.loadRecommendPlaylists(isForceRefresh = true)
            },
            // 自定义指示器：使用主题 primary 色（紫色）
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isPullRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,  // 指示器颜色：紫色
                    containerColor = MaterialTheme.colorScheme.surface  // 容器背景：跟随主题
                )
            }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (isLoading && recommendPlaylists.isEmpty()) {
                    // 加载中状态
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (recommendPlaylists.isEmpty()) {
                    // 空状态
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "加载推荐内容中...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { discoverViewModel.loadRecommendPlaylists(true) }) {
                            Text("点击刷新")
                        }
                    }
                } else {
                    // 推荐歌单列表
                    LazyColumn(
                        contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+垂直内边距 (2025-11-28)
                        verticalArrangement = Arrangement.spacedBy(Spacing.Content.large)  // MD3规范：16dp卡片间距
                    ) {
                        // 标题（带精选入口）
//                    item {
//                        Row(
//                            modifier = Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween,
//                            verticalAlignment = Alignment.CenterVertically
//                        ) {
//                            Text(
//                                text = "推荐歌单",
//                                style = MaterialTheme.typography.titleLarge,
//                                fontWeight = FontWeight.Bold
//                            )
//                            TextButton(onClick = {
//                                navController.navigate("featured_playlists")
//                            }) {
//                                Text("精选经典")
//                            }
//                        }
//                    }
                        // 歌单列表（优化：添加稳定key避免闪烁 2025-11-28）
                        items(
                            count = recommendPlaylists.size,
                            key = { index -> recommendPlaylists[index].id }  // 添加稳定key，避免不必要的重组
                        ) { index ->
                            val playlist = recommendPlaylists[index]
                            val isFavorite = favoritePlaylists.any { it.id == playlist.id }
                            PlaylistCard(
                                playlist = playlist,
                                isFavorite = isFavorite,
                                networkRefreshTrigger = networkRefreshTrigger,  // 网络恢复时刷新图片
                                onClick = {
                                    discoverViewModel.onPlayListClicked(playlist)
                                    // 所有歌单（包括歌手类型）都进入歌单详情页
                                    val sourceParam =
                                        if (playlist.source == com.minimalistmusic.domain.model.PlaylistSource.ARTIST_PLAYLIST) "artist" else "playlist"
                                    val encodedName =
                                        java.net.URLEncoder.encode(playlist.name, "UTF-8")
                                    val encodedCover =
                                        java.net.URLEncoder.encode(playlist.cover, "UTF-8")
                                    val encodedDesc = playlist.description?.let {
                                        java.net.URLEncoder.encode(
                                            it,
                                            "UTF-8"
                                        )
                                    } ?: "null"
                                    navController.navigate("playlist_detail/${playlist.id}/$encodedName/$sourceParam/$encodedCover/${playlist.playCount}/$encodedDesc")
                                },
                                onLongClick = {
                                    playlistToCollect = playlist
                                    showCollectDialog = true
                                },
                                onFavoriteClick = {
                                    homeViewModel.toggleFavoritePlaylist(playlist)
                                }
                            )
                        }
                    }
                }
                // 歌单详情底部抽屉（仅用于 DiscoverScreen）
                if (selectedPlaylist != null && playlistSongs.isNotEmpty()) {
                    PlaylistDetailSheet(
                        playlistName = selectedPlaylist!!.name,
                        playlistDescription = selectedPlaylist!!.description ?: "",
                        songs = playlistSongs,
                        onDismiss = { discoverViewModel.clearPlaylistSongs() },
                        onSongClick = { song ->
                            // 获取播放URL并播放
                            discoverViewModel.playSong(song) { playUrl ->
                                if (playUrl != null) {
                                    val songWithUrl = song.copy(path = playUrl.path)
                                    playerViewModel.playSongs(listOf(songWithUrl), 0)
                                    navController.navigate("player") {
                                        launchSingleTop = true
                                    }
                                }
                            }
                        }
                    )
                }
                // Material Design 3确认对话框
                if (showCollectDialog && playlistToCollect != null) {
                    CollectPlaylistDialog(
                        playlist = playlistToCollect!!,
                        onDismiss = { showCollectDialog = false },
                        onConfirm = {
                            homeViewModel.toggleFavoritePlaylist(playlistToCollect!!)
                            showCollectDialog = false
                        }
                    )
                }
            }
        }
    }
}
/**
 * 歌单详情底部抽屉
 *
 * 专用于 DiscoverScreen，显示歌单中的歌曲列表
 *
 * @param playlistName 歌单名称
 * @param songs 歌曲列表
 * @param onDismiss 关闭回调
 * @param onSongClick 歌曲点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailSheet(
    playlistName: String,
    playlistDescription: String = "",
    songs: List<Song>,
    onDismiss: () -> Unit,
    onSongClick: (Song) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 头部：歌单名称和关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlistName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${songs.size} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    // 显示歌单描述（如果有）
                    if (playlistDescription.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playlistDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // 歌曲列表
            songs.forEachIndexed { index, song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSongClick(song) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 封面
                    AsyncImage(
                        model = song.albumArt,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    // 歌曲信息
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = song.artist,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // 播放按钮
                    IconButton(onClick = { onSongClick(song) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "播放")
                    }
                }
                // 分隔线
                if (index < songs.size - 1) {
                    Divider(modifier = Modifier.padding(start = 60.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
/**
 * 收藏歌单确认对话框
 *
 * Material Design 3风格
 */
@Composable
fun CollectPlaylistDialog(
    playlist: com.minimalistmusic.domain.model.RecommendPlaylist,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                androidx.compose.material.icons.Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("收藏歌单")
        },
        text = {
            Text("要将「${playlist.name}」添加到我喜欢的歌单吗？")
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text("收藏")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
