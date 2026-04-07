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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.minimalistmusic.presentation.viewmodel.HomeViewModel
import com.minimalistmusic.ui.components.ImmersiveTopAppBar

/**
 * 收藏歌单页面
 *
 * 显示用户收藏的所有歌单
 * @since 2025-11-19
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFavoritePlaylistsScreen(
    navController: NavHostController,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val favoritePlaylists by homeViewModel.favoritePlaylists.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = { Text("我收藏的歌单", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (favoritePlaylists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "还没有收藏的歌单",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else {
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val rows = favoritePlaylists.chunked(2)
                items(
                    items = rows,
                    key = { row -> row.map { it.id }.joinToString("-") }
                ) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { playlist ->
                            FavoritePlaylistCard(
                                playlist = playlist,
                                modifier = Modifier.weight(1f),
                                isFavorite = true,
                                onClick = {
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
    }
}
/**
 * 收藏歌单卡片组件
 * 与发现页的PlaylistCard样式保持一致
 */
@Composable
fun FavoritePlaylistCard(
    playlist: com.minimalistmusic.domain.model.RecommendPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onFavoriteClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        // 封面图片
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            // 占位图标
            Icon(
                Icons.Filled.QueueMusic,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
            // 实际封面图片
            AsyncImage(
                model = playlist.cover,
                contentDescription = playlist.name,
                modifier = Modifier.fillMaxSize()
            )
            // 描述信息显示在封面图片右下角
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
                var previousPlaylistFavorite by remember(playlist.id) { mutableStateOf(isFavorite) }
                var shouldAnimatePlaylistFavorite by remember(playlist.id) { mutableStateOf(false) }
                LaunchedEffect(isFavorite) {
                    val justFavorited = previousPlaylistFavorite == false && isFavorite
                    shouldAnimatePlaylistFavorite = justFavorited
                    previousPlaylistFavorite = isFavorite
                    if (justFavorited) {
                        kotlinx.coroutines.delay(400)
                        shouldAnimatePlaylistFavorite = false
                    }
                }
                val playlistCardHeartScale by animateFloatAsState(
                    targetValue = if (shouldAnimatePlaylistFavorite) 1.5f else 1.0f,
                    animationSpec = if (shouldAnimatePlaylistFavorite) {
                        spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    } else {
                        spring(stiffness = Spring.StiffnessHigh)
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
