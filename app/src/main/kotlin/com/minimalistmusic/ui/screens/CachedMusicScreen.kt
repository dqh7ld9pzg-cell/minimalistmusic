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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShieldMoon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.minimalistmusic.ui.components.ImmersiveTopAppBar
import com.minimalistmusic.ui.components.ListEndIndicator
import com.minimalistmusic.ui.components.SongListItem
import com.minimalistmusic.ui.theme.Spacing
import com.minimalistmusic.presentation.viewmodel.AccountSyncViewModel
import com.minimalistmusic.presentation.viewmodel.CachedMusicViewModel
import com.minimalistmusic.presentation.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSelectionMode
import my.nanihadesuka.compose.ScrollbarSettings

/**
 * 已缓存音乐页面 (2025-11-19重构)
 *
 * 新版设计（网易云音乐风格）：
 * - 头部区域：左侧最近缓存的歌曲封面，右侧显示标题、数量/大小、缓存小技巧
 * - 歌曲列表支持分页加载（每页20条）
 * - 长按进入多选删除模式
 *
 * 功能：
 * - 显示所有已缓存的在线歌曲列表
 * - 支持长按进入多选删除模式
 * - 提供全选/取消全选功能
 * - 批量删除缓存
 * - 播放全部功能
 *
 * 交互：
 * - 正常模式：点击歌曲播放
 * - 长按→进入多选模式
 * - 多选模式：勾选歌曲，点击删除按钮批量删除
 * - 多选模式：点击返回或"取消"退出多选模式
 *
 * 架构：
 * - MVVM模式
 * - 使用CachedMusicViewModel管理状态
 * - 组件复用SongListItem
 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CachedMusicScreen(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    viewModel: CachedMusicViewModel,  // 优化 (2025-11-23): 移除默认值，由调用方传入共享实例，消除闪烁
    accountSyncViewModel: AccountSyncViewModel = hiltViewModel(),
) {
    // 状态收集
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val favoriteSongIds by accountSyncViewModel.favoriteSongIds.collectAsStateWithLifecycle()
    val protectedSongIds by viewModel.protectedSongIds.collectAsStateWithLifecycle() // 2025-11-22: 白名单保护状态
    // 播放状态（2025-11-20：用于显示序号和播放动画）
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    // 优化 (2025-11-23): 直接使用共享 ViewModel 的 StateFlow，消除闪烁
    // 原问题：
    // - 使用 remember + LaunchedEffect 的中间层导致初始显示 emptyList()
    // - 然后 LaunchedEffect 触发更新，产生闪烁
    // 解决方案：
    // - ViewModel 已在 Activity 创建时初始化，数据已预加载
    // - 直接使用 StateFlow，无需中间状态管理
    val cachedSongs by viewModel.cachedSongs.collectAsStateWithLifecycle()
    val maxCachedSongs by viewModel.maxCachedSongs.collectAsStateWithLifecycle() // 最大缓存歌曲数
    // ========== 错误消息监听 (2025-11-15) ==========
    // 统一使用BaseViewModel的errorMessage (SharedFlow)
    val context = LocalContext.current
    // ========== 同步缓存状态 (2025-11-25) ==========
    // 修复：每次进入页面时同步数据库与文件系统
    // 原因：
    // - ViewModel现在是Activity级别单例，init只执行一次
    // - 需要在每次进入页面时同步，发现未被实时监控到的完整缓存
    // 实现 (2025-11-25 修复)：
    // - 使用 LaunchedEffect(Unit) 仅在进入时触发一次
    // - 避免使用 currentBackStackEntry 作为 key，防止离开时二次触发
        // 将业务层记录的管理和exoPlayer缓存区分开来
//    LaunchedEffect(Unit) {
//        viewModel.syncCache()
//    }
    // 架构优化 (2025-12-02): 移除 refreshMaxCachedSongs() 调用
    // 原因：maxCachedSongs 现在直接使用 DataStore 的响应式 StateFlow
    // 用户在 Profile 页面修改后，DataStore 会自动通知所有订阅者 无需手动刷新，实现真正的响应式架构
    // 多选模式状态
    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<Long>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var showUnprotectDialog by remember { mutableStateOf(false) }
    // 是否全选
    val isAllSelected = selectedSongIds.size == cachedSongs.size && cachedSongs.isNotEmpty()
    // 分页状态 (2025-11-19)
    // 优化 (2025-11-23): 统一分页大小为30条，与其他列表页面保持一致
    val pageSize = 30
    var displayCount by remember { mutableStateOf(pageSize) }
    val displaySongs = cachedSongs.take(displayCount)
    val hasMore = displayCount < cachedSongs.size
    // 滚动状态监听
    val listState = rememberLazyListState()
    // 自动加载更多
    LaunchedEffect(listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        // 当滚动到距离底部5个item时加载更多
        if (hasMore && lastVisibleIndex >= displayCount - 3) {
            displayCount += pageSize
        }
    }
    // 获取最近缓存的歌曲封面
    val latestSongCover = cachedSongs.firstOrNull()?.albumArt
    // 协程作用域，用于滚动到顶部
    val coroutineScope = rememberCoroutineScope()
    // 退出多选模式
    fun exitMultiSelectMode() {
        isMultiSelectMode = false
        selectedSongIds = emptySet()
    }
    // 2025-11-19：拦截系统返回键，多选模式下退出多选模式而不是返回上一页
    BackHandler(enabled = isMultiSelectMode) {
        exitMultiSelectMode()
    }
    Scaffold(
        topBar = {
            ImmersiveTopAppBar(
                title = {
                    Text(
                        text = "已缓存",
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
                        if (isMultiSelectMode) {
                            exitMultiSelectMode()
                        } else {
                            navController.navigateUp()
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (isMultiSelectMode) {
                        // 加入白名单按钮
                        IconButton(
                            onClick = { showProtectDialog = true },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = "加入白名单",
                                tint = if (selectedSongIds.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        // 移出白名单按钮
                        IconButton(
                            onClick = { showUnprotectDialog = true },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShieldMoon,  // 使用不同图标区分
                                contentDescription = "移出白名单",
                                tint = if (selectedSongIds.isNotEmpty())
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        // 删除按钮
                        IconButton(
                            onClick = {
                                showDeleteDialog = true
                            },
                            enabled = selectedSongIds.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除",
                                tint = if (selectedSongIds.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                        // 取消按钮
                        TextButton(onClick = { exitMultiSelectMode() }) {
                            Text("取消")
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
                // 加载中
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                // 列表为空
                cachedSongs.isEmpty() && !isLoading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "暂无已缓存音乐",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "播放在线歌曲后会自动缓存",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
                // 显示歌曲列表
                else -> {
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
                        state = listState,
                        contentPadding = Spacing.lazyColumnPadding(),  // MD3规范：16dp水平+12dp垂直内边距
                        verticalArrangement = Arrangement.spacedBy(Spacing.Content.medium)  // MD3规范：12dp歌曲间距
                    ) {
                        // 头部区域 (2025-11-19新增：网易云风格)
                        // 优化 (2025-11-20): 独立卡片风格，四周圆角，与其他页面风格统一
                        // MD3优化 (2025-11-29): 使用contentPadding代替Spacer，与首页保持一致
                        // 修复：之前用Spacer(8dp) + verticalArrangement(12dp) = 20dp，大于首页的12dp
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
                                // 修复 (2025-11-20): 播放按钮在封面内部底部居中
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
                                            Icons.Filled.CloudDone,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp),
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    }
                                    // 最近缓存的歌曲封面
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
                                    if (cachedSongs.isNotEmpty()) {
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
                                                    val firstSong = cachedSongs.firstOrNull()
                                                    if (firstSong != null && !playerViewModel.canPlaySong(firstSong.id)) {
                                                        return@clickable
                                                    }
                                                    playerViewModel.playSongs(cachedSongs, 0)
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
                                            text = "已缓存的在线音乐",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "${cachedSongs.size} 首 / $maxCachedSongs 首 ",//有点冗余和影响美观先注释 · $cacheSize",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    // 缓存说明整合 (2025-11-23优化)
                                    // 设计理念：
                                    // 1. 优先级1：核心价值 - 不消耗流量 + 离线播放（醒目显示）
                                    // 2. 优先级2：管理规则 - 自动清理 + 白名单保护（简洁说明）
                                    // 信息层级清晰，符合用户认知顺序
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // 核心价值：不消耗流量 + 离线播放（醒目显示，主题色+加粗+图标）
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.CloudDone,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "再次播放不消耗流量·支持断网播放",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        // 管理规则：自动清理 + 白名单保护（次要颜色，简洁）
                                        // 注：使用"优先保护"而非"自动保护"，避免暗示所有红心歌曲都受保护
                                        Text(
                                            text = "超限清理最久未播放·红心歌曲优先保护",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                        )
                                    }
                                }
                            }
                            }  // Surface结束
                        }
                        // 头部与列表之间的间距
                        // MD3优化 (2025-11-29): 移除此Spacer，依靠verticalArrangement(12dp)自然形成间距
                        // 原因：SongListItem自带padding(vertical=12dp)，加上verticalArrangement(12dp)和Spacer会导致间距过大
                        // 多选模式：全选/取消全选按钮（在歌曲列表上方）
                        if (isMultiSelectMode) {
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
                                            selectedSongIds = if (isAllSelected) {
                                                emptySet()
                                            } else {
                                                cachedSongs.map { song -> song.id }.toSet()
                                            }
                                        }
                                    ) {
                                        Checkbox(
                                            checked = isAllSelected,
                                            onCheckedChange = {
                                                selectedSongIds = if (it) {
                                                    cachedSongs.map { song -> song.id }.toSet()
                                                } else {
                                                    emptySet()
                                                }
                                            }
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
                        // 歌曲列表
                        // 修复 (2025-11-25): 使用 items(count) 代替 itemsIndexed(list)，避免列表引用变化导致全部重建
                        // 原因：itemsIndexed 依赖列表引用，StateFlow 更新时即使内容相同也会触发全部item动画
                        // 解决：只依赖 count（Int），通过 key 识别item，与底部菜单播放列表保持一致
                        items(
                            count = displaySongs.size,
                            key = { index -> displaySongs[index].id }
                        ) { index ->
                            val song = displaySongs[index]
                            val isPlaying = playbackState.currentSong?.id == song.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 多选模式：显示复选框
                                if (isMultiSelectMode) {
                                    Checkbox(
                                        checked = selectedSongIds.contains(song.id),
                                        onCheckedChange = {
                                            selectedSongIds = if (it) {
                                                selectedSongIds + song.id
                                            } else {
                                                selectedSongIds - song.id
                                            }
                                        }
                                    )
                                }
                                // 歌曲信息（2025-11-20：添加序号和播放动画）
                                // 2025-11-22：添加白名单保护状态
                                SongListItem(
                                    song = song,
                                    isFavorite = favoriteSongIds.contains(song.id),
                                    cacheTime = song.addedAt,  // 传递缓存时间 (2025-11-19)
                                    showIndex = true,  // 显示序号
                                    index = index + 1,  // 序号从1开始
                                    isPlaying = isPlaying,  // 播放动画
                                    isActuallyPlaying = playbackState.isPlaying,  // 传递实际播放状态
                                    isProtected = protectedSongIds.contains(song.id),  // 2025-11-22: 白名单保护状态
                                    onClick = {
                                        if (isMultiSelectMode) {
                                            // 多选模式：切换选中状态
                                            selectedSongIds =
                                                if (selectedSongIds.contains(song.id)) {
                                                    selectedSongIds - song.id
                                                } else {
                                                    selectedSongIds + song.id
                                                }
                                        } else {
                                            // 正常模式：播放歌曲
                                            // 修复 (2025-11-21): 避免重复点击正在播放的歌曲
                                            if (!playerViewModel.canPlaySong(song.id)) {
                                                return@SongListItem
                                            }
                                            playerViewModel.playSongs(
                                                cachedSongs,
                                                cachedSongs.indexOf(song)
                                            )
//                                            navController.navigate("player") {
//                                                launchSingleTop = true
//                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!isMultiSelectMode) {
                                            isMultiSelectMode = true
                                            selectedSongIds = setOf(song.id)
                                        }
                                    },
                                    onFavoriteClick = {
                                        if (!isMultiSelectMode) {
                                            accountSyncViewModel.toggleFavorite(song)
                                        }
                                    },
                                    isCached = true, // 已缓存音乐页面所有歌曲都是已缓存
                                    modifier = Modifier.weight(1f)
                                )
                            }
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
                        if (!hasMore && cachedSongs.isNotEmpty()) {
                            item {
                                ListEndIndicator(
                                    itemCount = cachedSongs.size,
                                    titleText = "已缓存",
                                    onScrollToTop = {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    }  // LazyColumnScrollbar结束
                }
            }
        }
        // 旧的错误消息Snackbar已移除 (2025-11-15)
        // 现在统一使用BaseViewModel的errorMessage (SharedFlow) + Toast显示
    }
    // 批量删除确认对话框
    if (showDeleteDialog) {
        // 检查是否包含正在播放的歌曲
        val currentSongId = playbackState.currentSong?.id
        val isPlayingSelected = currentSongId != null && selectedSongIds.contains(currentSongId)
        val canDeleteCount = if (isPlayingSelected) {
            selectedSongIds.size - 1
        } else {
            selectedSongIds.size
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除缓存") },
            text = {
                val message = if (isPlayingSelected) {
                    "已选择 ${selectedSongIds.size} 首歌曲，其中包含正在播放的歌曲。\n\n将删除其他 $canDeleteCount 首歌曲的缓存，正在播放的歌曲将跳过删除以保证播放稳定性。\n\n删除后这些歌曲将不再占用存储空间，下次播放时需要重新加载。"
                } else {
                    "确定要删除已选择的 ${selectedSongIds.size} 首歌曲的缓存吗？\n\n删除后这些歌曲将不再占用存储空间，下次播放时需要重新加载。"
                }
                Text(message)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 架构优化 (2025-11-22): 批量删除缓存
                        // 优化 (2025-11-22 第二版): 过滤掉正在播放的歌曲
                        val songsToDelete = cachedSongs.filter {
                            selectedSongIds.contains(it.id) && it.id != currentSongId
                        }
                        if (songsToDelete.isEmpty()) {
                            Toast.makeText(context, "无法删除正在播放的歌曲", Toast.LENGTH_SHORT).show()
                        } else {
                            songsToDelete.forEach { song ->
                                viewModel.deleteSongCache(song)
                            }
                            val deletedCount = songsToDelete.size
                            Toast.makeText(context, "已删除 $deletedCount 首歌曲的缓存", Toast.LENGTH_SHORT).show()
                        }
                        showDeleteDialog = false
                        exitMultiSelectMode()
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    // 加入白名单确认对话框
    if (showProtectDialog) {
        AlertDialog(
            onDismissRequest = { showProtectDialog = false },
            title = { Text("加入白名单") },
            text = {
                Text("确定要将已选择的 ${selectedSongIds.size} 首歌曲加入白名单吗？\n\n加入白名单的歌曲将受到保护，不会被自动清理。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 批量加入白名单
                        // 修复 (2025-11-22): 只计算原本不在白名单中的歌曲数量
                        val notProtectedCount = selectedSongIds.count { !protectedSongIds.contains(it) }
                        viewModel.addToWhitelist(selectedSongIds.toList()) {
                            if (notProtectedCount > 0) {
                                Toast.makeText(context, "已加入白名单保护 $notProtectedCount 首", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "选中的歌曲已在白名单中", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showProtectDialog = false
                        exitMultiSelectMode()
                    }
                ) {
                    Text("加入", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showProtectDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
    // 移出白名单确认对话框
    if (showUnprotectDialog) {
        AlertDialog(
            onDismissRequest = { showUnprotectDialog = false },
            title = { Text("移出白名单") },
            text = {
                Text("确定要将已选择的 ${selectedSongIds.size} 首歌曲移出白名单吗？\n\n移出后这些歌曲可能会在缓存清理时被删除。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 批量移出白名单
                        // 修复 (2025-11-22): 只计算原本在白名单中的歌曲数量
                        val protectedCount = selectedSongIds.count { protectedSongIds.contains(it) }
                        viewModel.removeFromWhitelist(selectedSongIds.toList()) {
                            if (protectedCount > 0) {
                                Toast.makeText(context, "已从白名单移除 $protectedCount 首", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "选中的歌曲不在白名单中", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showUnprotectDialog = false
                        exitMultiSelectMode()
                    }
                ) {
                    Text("移出", color = MaterialTheme.colorScheme.tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnprotectDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
