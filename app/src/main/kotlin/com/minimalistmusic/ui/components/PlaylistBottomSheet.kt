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
 * 播放列表底部抽屉
 *
 * 播放器核心组件之一，显示当前播放列表和播放模式。
 *
 * ## 核心功能
 *
 * ### 1. 播放列表展示
 * - 显示当前播放队列（所有歌曲）
 * - 当前播放歌曲高亮显示
 * - 歌曲封面、标题、艺术家、时长
 * - 收藏状态显示
 *
 * ### 2. 列表操作
 * - 点击歌曲切换播放
 * - 点击收藏按钮切换收藏状态
 * - 当前播放歌曲显示动画指示器
 *
 * ### 3. 播放模式
 * - 顶部显示播放模式按钮
 * - 点击切换模式（顺序/随机/单曲循环）
 * - 模式图标和文字提示
 *
 * ## UI结构
 * ```
 * ModalBottomSheet (70%屏幕高度)
 * ├── 头部
 * │   ├── 播放队列标题
 * │   ├── 歌曲数量
 * │   └── 播放模式按钮
 * └── 列表
 *     ├── 歌曲1 (当前播放，高亮)
 *     ├── 歌曲2
 *     └── ...
 * ```
 *
 * ## 列表项内容
 * ```
 * [序号/动画] [封面] [标题 - 艺术家] [时长] [收藏]
 * ```
 *
 * ## 状态显示
 * - 当前播放：显示动画指示器（GraphicEq图标）
 * - 非播放：显示序号
 * - 收藏：红色爱心图标
 * - 未收藏：空心爱心图标
 *
 * ## 交互行为
 * - 点击歌曲 → 切换播放
 * - 点击收藏 → 切换收藏状态（触发同步提醒）
 * - 点击模式 → 切换播放模式
 * - 下拉/点击外部 → 关闭抽屉
 *
 * @param playlist 播放列表
 * @param currentIndex 当前播放索引
 * @param currentPlayMode 当前播放模式
 * @param onDismiss 关闭回调
 * @param onSongClick 歌曲点击回调
 * @param onPlayModeToggle 播放模式切换回调
 * @param accountSyncViewModel 账号同步ViewModel（管理收藏状态）
 *
 * @since 2025-11-11
 */
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.minimalistmusic.domain.model.PlayMode
import com.minimalistmusic.domain.model.Song
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import kotlinx.coroutines.launch
/**
 * 增强的播放列表底部抽屉
 *
 * 功能：
 * 1. 显示当前播放列表
 * 2. 高亮当前播放歌曲
 * 3. 支持点击切歌
 * 4. 支持喜欢/取消喜欢
 * 5. 播放模式切换
 * 6. 显示缓存状态（2025-11-15）
 * 7. 播放中的歌曲显示动画指示器（2025-11-15）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedPlaylistBottomSheet(
    playlist: List<Song>,
    currentIndex: Int,
    currentPlayMode: PlayMode,
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
    onPlayModeToggle: () -> Unit,
    isPlaying: Boolean = true,  // 新增：是否正在播放（2025-11-20）
    currentSongId: Long? = null,  // 新增：当前播放歌曲ID（2025-11-21）用于准确判断当前播放歌曲
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
    cachedMusicViewModel: CachedMusicViewModel = hiltViewModel() // 任务6：注入缓存ViewModel
) {
    val listState = rememberLazyListState()
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsState()
    val cacheStateMap by cachedMusicViewModel.cacheStateMap.collectAsState() // 任务6：收集缓存状态
    // 协程作用域，用于滚动到顶部 (2025-11-20)
    val coroutineScope = rememberCoroutineScope()
    // 初始滚动到当前播放歌曲，使其显示在可见区域第一条
    // 修复 (2025-11-21): 只在菜单首次打开时滚动到当前播放歌曲
    // 使用Unit作为key，确保只在组件创建时执行一次，点击切歌时不滚动
    LaunchedEffect(Unit) {
        if (currentSongId != null) {
            // 根据歌曲ID找到在playlist中的索引
            // 原因: currentIndex是ExoPlayer的mediaItems索引，与playlist索引可能不一致
            val songIndex = playlist.indexOfFirst { it.id == currentSongId }
            if (songIndex >= 0) {
                // scrollToItem会将指定索引的项滚动到可见区域的第一条位置
                // 如果是列表末尾的歌曲，会尽可能滚动到最高位置
                listState.scrollToItem(songIndex)
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        // Material3 1.3.1 修复：使用 windowInsets 控制高度，而不是 modifier
        // 注意：ModalBottomSheet 内容区域的高度限制改为在内部 Column 上设置
        dragHandle = null  // 可选：移除默认拖拽手柄，使用自定义设计
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)  // 限制高度为屏幕的 70%
        ) {
            // 头部 (2025-11-20优化：支持点击标题快速滑至顶部)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                ) {
                    Text(
                        text = "播放列表",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(${playlist.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Row {
                    // 播放模式切换
                    IconButton(onClick = onPlayModeToggle) {
                        Icon(
                            imageVector = when (currentPlayMode) {
                                PlayMode.SEQUENCE -> Icons.Filled.Repeat
                                PlayMode.SHUFFLE -> Icons.Filled.Shuffle
                                PlayMode.REPEAT_ONE -> Icons.Filled.RepeatOne
                            },
                            contentDescription = "播放模式",
                            tint = if (currentPlayMode != PlayMode.SEQUENCE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            }
            // 歌曲列表 (2025-11-20重构：复用SongListItem组件)
            // 优化 (2025-11-20): 添加水平padding和间距，移除分割线，与其他列表风格统一
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)  // 使用间距替代分割线
            ) {
                // 性能优化：添加 key 参数，使用歌曲 ID 作为唯一标识
                // 这样 Compose 可以更高效地识别和复用组件，减少不必要的重组
                items(
                    count = playlist.size,
                    key = { index -> playlist[index].id }  // 使用歌曲 ID 作为唯一 key
                ) { index ->
                    val song = playlist[index]
                    // 修复 (2025-11-21): 使用歌曲ID判断当前播放歌曲，而非索引
                    // 原因: playlist索引与ExoPlayer的mediaItems索引可能不一致（因为path为空的歌曲被过滤）
                    val isCurrent = song.id == currentSongId
                    val isFavorite = favoriteSongIds.contains(song.id)
                    // 获取歌曲的缓存状态
                    val isCached = cacheStateMap[song.id] ?: false
                    // 2025-11-20重构：复用SongListItem组件
                    // 统一动画标识、序号、收藏颜色、缓存标识位置
                    Box(
                        modifier = Modifier
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                else Color.Transparent
                            )
                    ) {
                        SongListItem(
                            song = song,
                            isFavorite = isFavorite,
                            onClick = { onSongClick(index) },
                            onFavoriteClick = {
                                accountSyncViewModel.toggleFavorite(song)
                            },
                            showIndex = true,
                            index = index + 1,
                            isPlaying = isCurrent,
                            isActuallyPlaying = isCurrent && isPlaying,  // 只有当前歌曲且正在播放时才有动画
                            isCached = isCached
                        )
                    }
                }
                // 底线提示（2025-11-20：使用 ListEndIndicator 统一风格）
                // 显示"我是底线"和"点击播放列表可快速滑至顶部"提示
                item {
                    ListEndIndicator(
                        itemCount = playlist.size,
                        titleText = "播放列表",
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
// 2025-11-20：PlaylistBottomSheetItem 已被移除
// 现在统一复用 SongListItem 组件，确保以下效果一致：
// - 左侧显示序号/播放动画
// - 收藏按钮颜色为鲜红色
// - 已缓存标识显示在封面左下角
